from dataclasses import dataclass

from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.audit import record_audit
from app.core.database import get_db
from app.core.errors import api_error
from app.core.security import InvalidTokenError, decode_token
from app.models.identity import Account, Org, RoleBinding

bearer_scheme = HTTPBearer(auto_error=False)

VALID_ROLES = ("patient", "caregiver", "care_admin", "rehab_therapist")
CLINICIAN_ROLES = ("care_admin", "rehab_therapist")


@dataclass(slots=True)
class Principal:
    account_id: str
    display_name: str
    org_id: str
    org_name: str | None
    roles: tuple[str, ...]
    patient_ids: tuple[str, ...]

    @property
    def is_clinician(self) -> bool:
        return any(role in CLINICIAN_ROLES for role in self.roles)

    def has_role(self, *allowed: str) -> bool:
        return any(role in allowed for role in self.roles)


def _unauthorized() -> Exception:
    return api_error(401, "UNAUTHORIZED", "未认证或令牌失效")


def get_principal(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    db: Session = Depends(get_db),
) -> Principal:
    if credentials is None:
        raise _unauthorized()

    try:
        payload = decode_token(credentials.credentials, expected_type="access")
    except InvalidTokenError:
        raise _unauthorized()

    account = db.get(Account, payload["sub"])
    if account is None or account.status != "active":
        raise _unauthorized()
    # 令牌中的机构必须与账号当前机构一致，避免机构调整后旧令牌越界。
    if payload.get("org_id") != account.org_id:
        raise _unauthorized()

    return build_principal(db, account)


def build_principal(db: Session, account: Account) -> Principal:
    bindings = db.scalars(
        select(RoleBinding).where(RoleBinding.account_id == account.account_id)
    ).all()
    roles = tuple(
        sorted({binding.role for binding in bindings if binding.role in VALID_ROLES})
    )
    patient_ids = tuple(
        sorted({binding.patient_id for binding in bindings if binding.patient_id})
    )
    org = db.get(Org, account.org_id)
    return Principal(
        account_id=account.account_id,
        display_name=account.display_name,
        org_id=account.org_id,
        org_name=org.org_name if org else None,
        roles=roles,
        patient_ids=patient_ids,
    )


def require_roles(*allowed: str):
    """角色校验依赖；拒绝时返回 403 并写审计。"""

    def dependency(
        principal: Principal = Depends(get_principal),
        db: Session = Depends(get_db),
    ) -> Principal:
        if not principal.has_role(*allowed):
            record_audit(
                db,
                org_id=principal.org_id,
                actor_account_id=principal.account_id,
                action="authz.role_denied",
                outcome="denied",
                resource_type="role",
                detail={
                    "requiredRoles": list(allowed),
                    "actualRoles": list(principal.roles),
                },
            )
            db.commit()
            raise api_error(403, "FORBIDDEN", "无权限执行该操作")
        return principal

    return dependency

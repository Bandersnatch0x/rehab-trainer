from datetime import datetime
from uuid import uuid4

from fastapi import APIRouter, Depends, Query
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.audit import record_audit
from app.core.auth import VALID_ROLES, Principal, require_roles
from app.core.database import get_db
from app.core.errors import api_error
from app.core.pagination import apply_keyset, cursor_from, datetime_key, decode_cursor
from app.core.security import hash_password
from app.models.identity import Account, RoleBinding
from app.schemas.account import AccountCreate, AccountOut, AccountPage, AccountPatch

router = APIRouter(prefix="/accounts", tags=["accounts"])

# 本机构可被授予或收回的角色；患者、照护者不在管理范围。
MANAGED_ROLES = ("care_admin", "rehab_therapist")

# 谁能管账号：照护管理员与康复治疗师都能（治疗师需要把同事拉进来）。
ACCOUNT_MANAGERS = ("care_admin", "rehab_therapist")

# 各自**可授予**的角色。治疗师只能开通/调整治疗师，否则等于变相自升权——
# 授予 care_admin 就是拿到了分级确认与开方权（D4/D11/D12）。
GRANTABLE_ROLES = {
    "care_admin": ("care_admin", "rehab_therapist"),
    "rehab_therapist": ("rehab_therapist",),
}


def _grantable_roles(principal: Principal) -> tuple[str, ...]:
    """调用者可授予的角色集合；同时决定他能动哪些账号。"""
    for role in ("care_admin", "rehab_therapist"):
        if principal.has_role(role):
            return GRANTABLE_ROLES[role]
    return ()


def _deny_role_change(db: Session, principal: Principal, detail: dict) -> None:
    """越权改角色：审计后 403，与 require_roles 被拒时同一套动作名。"""
    record_audit(
        db,
        org_id=principal.org_id,
        actor_account_id=principal.account_id,
        action="authz.role_denied",
        outcome="denied",
        resource_type="account",
        resource_id=detail.get("targetAccountId"),
        detail=detail,
    )
    db.commit()
    raise api_error(403, "FORBIDDEN", "无权授予或调整该角色")


def _roles_by_account(
    db: Session, org_id: str, account_ids: list[str]
) -> dict[str, list[str]]:
    if not account_ids:
        return {}
    bindings = db.scalars(
        select(RoleBinding).where(
            RoleBinding.org_id == org_id,
            RoleBinding.account_id.in_(account_ids),
        )
    ).all()
    grouped: dict[str, set[str]] = {}
    for binding in bindings:
        if binding.role in VALID_ROLES:
            grouped.setdefault(binding.account_id, set()).add(binding.role)
    return {account_id: sorted(roles) for account_id, roles in grouped.items()}


def _to_out(account: Account, roles: list[str]) -> AccountOut:
    return AccountOut(
        account_id=account.account_id,
        display_name=account.display_name,
        username=account.username,
        status=account.status,
        roles=roles,
        created_at=account.created_at,
    )


def _load_scoped(db: Session, principal: Principal, account_id: str) -> Account:
    account = db.get(Account, account_id)
    if account is None:
        raise api_error(404, "NOT_FOUND", "账号不存在")
    if account.org_id != principal.org_id:
        record_audit(
            db,
            org_id=principal.org_id,
            actor_account_id=principal.account_id,
            action="authz.cross_org_access",
            outcome="denied",
            resource_type="account",
            resource_id=account_id,
            detail={
                "tokenOrgId": principal.org_id,
                "resourceOrgId": account.org_id,
            },
        )
        db.commit()
        raise api_error(403, "FORBIDDEN", "跨机构访问被拒绝")
    return account


def _managed_binding(db: Session, account_id: str, org_id: str) -> RoleBinding | None:
    return db.scalars(
        select(RoleBinding).where(
            RoleBinding.account_id == account_id,
            RoleBinding.org_id == org_id,
            RoleBinding.role.in_(MANAGED_ROLES),
        )
    ).first()


def _other_active_care_admin_exists(
    db: Session, org_id: str, exclude_account_id: str
) -> bool:
    count = db.scalar(
        select(func.count(func.distinct(RoleBinding.account_id)))
        .select_from(RoleBinding)
        .join(Account, Account.account_id == RoleBinding.account_id)
        .where(
            RoleBinding.org_id == org_id,
            RoleBinding.role == "care_admin",
            Account.status == "active",
            RoleBinding.account_id != exclude_account_id,
        )
    )
    return (count or 0) > 0


@router.get("", response_model=AccountPage)
def list_accounts(
    principal: Principal = Depends(require_roles(*ACCOUNT_MANAGERS)),
    db: Session = Depends(get_db),
    status: str | None = Query(default=None),
    cursor: str | None = Query(default=None),
    limit: int = Query(default=20, ge=1, le=100),
):
    keyset = decode_cursor(cursor, keys=("k", "i"))
    stmt = select(Account).where(Account.org_id == principal.org_id)
    if status is not None:
        stmt = stmt.where(Account.status == status)
    stmt = apply_keyset(
        stmt,
        Account.created_at,
        Account.account_id,
        keyset,
        descending=True,
        key_type=datetime.fromisoformat,
    )
    stmt = stmt.order_by(Account.created_at.desc(), Account.account_id.desc())
    rows = db.scalars(stmt.limit(limit + 1)).all()
    has_more = len(rows) > limit
    page = rows[:limit]

    next_cursor = None
    if has_more and page:
        last = page[-1]
        next_cursor = cursor_from(
            key=datetime_key(last.created_at), ident=last.account_id
        )

    roles_by_account = _roles_by_account(
        db, principal.org_id, [row.account_id for row in page]
    )
    return AccountPage(
        items=[
            _to_out(row, roles_by_account.get(row.account_id, [])) for row in page
        ],
        next_cursor=next_cursor,
    )


@router.post("", response_model=AccountOut, status_code=201)
def create_account(
    payload: AccountCreate,
    principal: Principal = Depends(require_roles(*ACCOUNT_MANAGERS)),
    db: Session = Depends(get_db),
):
    if payload.role.value not in _grantable_roles(principal):
        _deny_role_change(
            db,
            principal,
            {
                "requestedRole": payload.role.value,
                "grantableRoles": list(_grantable_roles(principal)),
            },
        )
    if db.scalar(select(Account).where(Account.username == payload.username)):
        raise api_error(409, "CONFLICT", "用户名已存在")

    account = Account(
        account_id=uuid4().hex,
        org_id=principal.org_id,
        username=payload.username,
        display_name=payload.display_name,
        password_hash=hash_password(payload.password),
        status="active",
    )
    db.add(account)
    db.add(
        RoleBinding(
            id=uuid4().hex,
            account_id=account.account_id,
            role=payload.role.value,
            org_id=principal.org_id,
            patient_id=None,
        )
    )
    record_audit(
        db,
        org_id=principal.org_id,
        actor_account_id=principal.account_id,
        action="account.created",
        resource_type="account",
        resource_id=account.account_id,
        detail={"role": payload.role.value, "username": payload.username},
    )
    # flush 也放进错误边界：并发插入同名用户会在 flush 抛唯一约束，若只包 commit 会漏成 500。
    try:
        db.flush()
        db.commit()
    except IntegrityError:
        db.rollback()
        raise api_error(409, "CONFLICT", "用户名已存在")

    return _to_out(account, [payload.role.value])


@router.patch("/{accountId}", response_model=AccountOut)
def update_account(
    accountId: str,
    payload: AccountPatch,
    principal: Principal = Depends(require_roles(*ACCOUNT_MANAGERS)),
    db: Session = Depends(get_db),
):
    account = _load_scoped(db, principal, accountId)
    binding = _managed_binding(db, account.account_id, principal.org_id)
    current_role = binding.role if binding else None
    grantable = _grantable_roles(principal)

    # 治疗师不能碰管理员账号，也不能把任何人提成管理员——否则等于变相自升权。
    if current_role is not None and current_role not in grantable:
        _deny_role_change(
            db,
            principal,
            {
                "targetAccountId": account.account_id,
                "targetRole": current_role,
                "grantableRoles": list(grantable),
            },
        )
    if payload.role is not None and payload.role.value not in grantable:
        _deny_role_change(
            db,
            principal,
            {
                "targetAccountId": account.account_id,
                "requestedRole": payload.role.value,
                "grantableRoles": list(grantable),
            },
        )

    new_status = (
        payload.status.value if payload.status is not None else account.status
    )
    new_role = payload.role.value if payload.role is not None else current_role

    removes_admin = current_role == "care_admin" and (
        new_role != "care_admin" or new_status != "active"
    )
    if removes_admin and not _other_active_care_admin_exists(
        db, principal.org_id, account.account_id
    ):
        raise api_error(
            409,
            "CONFLICT",
            "不能移除本机构最后一个照护管理员",
            {"reason": "last_care_admin"},
        )

    if account.account_id == principal.account_id:
        if payload.status is not None and new_status != "active":
            raise api_error(
                409, "CONFLICT", "不能禁用当前登录账号", {"reason": "self_disable"}
            )
        if payload.role is not None and new_role != current_role:
            raise api_error(
                409, "CONFLICT", "不能变更当前登录账号的角色", {"reason": "self_role_change"}
            )

    if payload.display_name is not None and payload.display_name != account.display_name:
        account.display_name = payload.display_name
        record_audit(
            db,
            org_id=principal.org_id,
            actor_account_id=principal.account_id,
            action="account.updated",
            resource_type="account",
            resource_id=account.account_id,
            detail={"displayName": payload.display_name},
        )

    if payload.status is not None and new_status != account.status:
        account.status = new_status
        record_audit(
            db,
            org_id=principal.org_id,
            actor_account_id=principal.account_id,
            action="account.disabled" if new_status == "disabled" else "account.updated",
            resource_type="account",
            resource_id=account.account_id,
            detail={"status": new_status},
        )

    if payload.role is not None and current_role != new_role:
        # 应用层去重：patient_id 为 NULL 时唯一约束挡不住同角色重复绑定。
        stale = db.scalars(
            select(RoleBinding).where(
                RoleBinding.account_id == account.account_id,
                RoleBinding.org_id == principal.org_id,
                RoleBinding.role.in_(MANAGED_ROLES),
            )
        ).all()
        for item in stale:
            db.delete(item)
        db.flush()
        db.add(
            RoleBinding(
                id=uuid4().hex,
                account_id=account.account_id,
                role=new_role,
                org_id=principal.org_id,
                patient_id=None,
            )
        )
        record_audit(
            db,
            org_id=principal.org_id,
            actor_account_id=principal.account_id,
            action="role.changed",
            resource_type="account",
            resource_id=account.account_id,
            detail={"from": current_role, "to": new_role},
        )

    db.commit()
    roles = _roles_by_account(db, principal.org_id, [account.account_id])
    return _to_out(account, roles.get(account.account_id, []))

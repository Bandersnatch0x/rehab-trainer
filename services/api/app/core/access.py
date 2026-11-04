from sqlalchemy.orm import Session

from app.core.audit import record_audit
from app.core.auth import Principal
from app.core.errors import api_error
from app.models.clinical import Patient


def get_patient_scoped(
    db: Session,
    principal: Principal,
    patient_id: str,
    *,
    enforce_scope: bool = True,
) -> Patient:
    """按机构与角色范围读取患者。

    - 跨机构：403 + 审计（D10，不使用 404 掩盖存在性）。
    - 同机构但非临床角色且未关联该患者：403 + 审计。
    """
    patient = db.get(Patient, patient_id)
    if patient is None:
        raise api_error(404, "NOT_FOUND", "患者不存在")

    if patient.org_id != principal.org_id:
        record_audit(
            db,
            org_id=principal.org_id,
            actor_account_id=principal.account_id,
            action="authz.cross_org_access",
            outcome="denied",
            resource_type="patient",
            resource_id=patient_id,
            detail={
                "tokenOrgId": principal.org_id,
                "resourceOrgId": patient.org_id,
            },
        )
        db.commit()
        raise api_error(403, "FORBIDDEN", "跨机构访问被拒绝")

    if (
        enforce_scope
        and not principal.is_clinician
        and patient.patient_id not in principal.patient_ids
    ):
        record_audit(
            db,
            org_id=principal.org_id,
            actor_account_id=principal.account_id,
            action="authz.out_of_scope_access",
            outcome="denied",
            resource_type="patient",
            resource_id=patient_id,
            detail={"roles": list(principal.roles)},
        )
        db.commit()
        raise api_error(403, "FORBIDDEN", "无权访问该患者")

    return patient

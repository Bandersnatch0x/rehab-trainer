from typing import Annotated
from uuid import uuid4

from fastapi import APIRouter, Depends, Header, Path
from sqlalchemy.orm import Session

from app.core.access import get_patient_scoped
from app.core.audit import record_audit
from app.core.auth import Principal, require_roles
from app.core.clinical import (
    HIGH_RISK_LEVEL,
    HIGH_RISK_REQUIRES_CONFIRMED_STAGE,
    TRAINING_MAX_HY_STAGE,
)
from app.core.database import get_db
from app.core.errors import api_error
from app.core.timeutils import to_utc_naive, utcnow
from app.models.clinical import Patient
from app.models.content import Exercise, ExerciseVariant
from app.models.prescription import Prescription, PrescriptionItem
from app.schemas.prescription import (
    PrescriptionCreate,
    PrescriptionItemIn,
    PrescriptionOut,
    PrescriptionPatch,
)

router = APIRouter(prefix="/prescriptions", tags=["prescriptions"])


def _bump_version(version: str) -> str:
    parts = version.split(".")
    try:
        parts[-1] = str(int(parts[-1]) + 1)
    except (ValueError, IndexError):
        return f"{version}.1"
    return ".".join(parts)


def _validate_items(
    db: Session, patient: Patient, items: list[PrescriptionItemIn]
) -> None:
    for item in items:
        exercise = db.get(Exercise, item.exercise_code)
        if exercise is None:
            raise api_error(
                422,
                "VALIDATION_FAILED",
                f"训练条目不存在：{item.exercise_code}",
            )
        if exercise.publication_status != "published":
            raise api_error(
                422,
                "VALIDATION_FAILED",
                f"训练条目未发布，不能开具：{item.exercise_code}",
            )
        if item.variant_code:
            variant = db.get(ExerciseVariant, item.variant_code)
            if variant is None or variant.exercise_code != exercise.code:
                raise api_error(
                    422,
                    "VALIDATION_FAILED",
                    f"难度变体不存在或不属于该训练条目：{item.variant_code}",
                )
        stages = list(exercise.applicable_stages or [])
        if stages and patient.hy_stage not in stages:
            raise api_error(
                422,
                "VALIDATION_FAILED",
                f"训练条目不适用于当前 H-Y 分级：{exercise.code}",
                {"exerciseCode": exercise.code, "hyStage": patient.hy_stage},
            )
        # D11：未确认分期不得用于高风险训练准入。
        if (
            HIGH_RISK_REQUIRES_CONFIRMED_STAGE
            and exercise.risk_level == HIGH_RISK_LEVEL
            and patient.stage_confirmation_status != "confirmed"
        ):
            raise api_error(
                422,
                "HIGH_RISK_NOT_ALLOWED",
                "分期未经康复治疗师确认，高风险训练条目不可用",
                {"exerciseCode": exercise.code},
            )


def _new_item(item: PrescriptionItemIn, prescription_id: str, position: int) -> PrescriptionItem:
    return PrescriptionItem(
        id=uuid4().hex,
        prescription_id=prescription_id,
        exercise_code=item.exercise_code,
        variant_code=item.variant_code,
        dosage=item.dosage.model_dump(by_alias=True) if item.dosage else None,
        position=position,
    )


@router.post(
    "",
    response_model=PrescriptionOut,
    response_model_exclude_none=True,
    status_code=201,
)
def create_prescription(
    payload: PrescriptionCreate,
    principal: Principal = Depends(require_roles("rehab_therapist")),
    db: Session = Depends(get_db),
):
    patient = get_patient_scoped(db, principal, payload.patient_id)

    # D5：H-Y 5 级仅宣教与转诊，不分配训练方案。
    if patient.hy_stage > TRAINING_MAX_HY_STAGE:
        raise api_error(
            422,
            "STAGE_NOT_ELIGIBLE",
            "H-Y 5 级仅宣教与转诊，不分配训练方案",
            {"hyStage": patient.hy_stage},
        )

    _validate_items(db, patient, payload.items)

    prescription = Prescription(
        id=uuid4().hex,
        org_id=patient.org_id,
        patient_id=patient.patient_id,
        prescribed_by=principal.account_id,
        version="1.0.0",
        mapping_table_version=payload.mapping_table_version,
        issued_at=to_utc_naive(payload.issued_at) or utcnow(),
        valid_from=to_utc_naive(payload.valid_from) or utcnow(),
        valid_until=to_utc_naive(payload.valid_until) or utcnow(),
        status="active",
    )
    prescription.items = [
        _new_item(item, prescription.id, position)
        for position, item in enumerate(payload.items)
    ]
    db.add(prescription)
    record_audit(
        db,
        org_id=patient.org_id,
        actor_account_id=principal.account_id,
        action="prescription.created",
        resource_type="prescription",
        resource_id=prescription.id,
        detail={"patientId": patient.patient_id},
    )
    db.commit()
    return PrescriptionOut.model_validate(prescription)


@router.patch(
    "/{prescriptionId}",
    response_model=PrescriptionOut,
    response_model_exclude_none=True,
)
def update_prescription(
    prescription_id: Annotated[str, Path(alias="prescriptionId")],
    payload: PrescriptionPatch,
    if_match: str = Header(..., alias="If-Match"),
    principal: Principal = Depends(require_roles("rehab_therapist")),
    db: Session = Depends(get_db),
):
    prescription = db.get(Prescription, prescription_id)
    if prescription is None:
        raise api_error(404, "NOT_FOUND", "处方不存在")
    if prescription.org_id != principal.org_id:
        record_audit(
            db,
            org_id=principal.org_id,
            actor_account_id=principal.account_id,
            action="authz.cross_org_access",
            outcome="denied",
            resource_type="prescription",
            resource_id=prescription_id,
            detail={
                "tokenOrgId": principal.org_id,
                "resourceOrgId": prescription.org_id,
            },
        )
        db.commit()
        raise api_error(403, "FORBIDDEN", "跨机构访问被拒绝")

    expected = if_match.strip().removeprefix("W/").strip('"')
    if expected != prescription.version:
        raise api_error(
            409,
            "VERSION_CONFLICT",
            "处方版本冲突，请基于最新版本重试",
            {"currentVersion": prescription.version},
        )

    patient = db.get(Patient, prescription.patient_id)
    if patient is None:
        raise api_error(404, "NOT_FOUND", "患者不存在")

    if payload.items is not None:
        if not payload.items:
            raise api_error(422, "VALIDATION_FAILED", "处方至少保留一个训练条目")
        if patient.hy_stage > TRAINING_MAX_HY_STAGE:
            raise api_error(
                422,
                "STAGE_NOT_ELIGIBLE",
                "H-Y 5 级仅宣教与转诊，不分配训练方案",
                {"hyStage": patient.hy_stage},
            )
        _validate_items(db, patient, payload.items)

    before = [
        {
            "exerciseCode": item.exercise_code,
            "variantCode": item.variant_code,
            "dosage": item.dosage,
        }
        for item in prescription.items
    ]

    if payload.items is not None:
        prescription.items = [
            _new_item(item, prescription.id, position)
            for position, item in enumerate(payload.items)
        ]
    if payload.valid_until is not None:
        prescription.valid_until = to_utc_naive(payload.valid_until)
    if payload.note is not None:
        prescription.note = payload.note

    previous_version = prescription.version
    prescription.version = _bump_version(previous_version)
    prescription.updated_at = utcnow()

    record_audit(
        db,
        org_id=prescription.org_id,
        actor_account_id=principal.account_id,
        action="prescription.updated",
        resource_type="prescription",
        resource_id=prescription.id,
        detail={
            "patientId": prescription.patient_id,
            "previousVersion": previous_version,
            "newVersion": prescription.version,
            # 旧版本内容留痕，满足「旧版本保留可追溯」。
            "previousItems": before,
        },
    )
    db.commit()
    db.refresh(prescription)
    return PrescriptionOut.model_validate(prescription)

from typing import Annotated
from uuid import uuid4

from fastapi import APIRouter, Depends, Path
from sqlalchemy.orm import Session

from app.core.access import get_patient_scoped
from app.core.audit import record_audit
from app.core.auth import Principal, require_roles
from app.core.database import get_db
from app.core.timeutils import to_utc_naive, utcnow
from app.models.clinical import Assessment, StageConfirmation
from app.schemas.assessment import (
    AssessmentCreate,
    AssessmentOut,
    StageConfirmationCreate,
    StageConfirmationOut,
)

router = APIRouter(tags=["assessments"])


@router.post(
    "/patients/{patientId}/stage-confirmations",
    response_model=StageConfirmationOut,
    response_model_exclude_none=True,
    status_code=201,
)
def confirm_stage(
    patient_id: Annotated[str, Path(alias="patientId")],
    payload: StageConfirmationCreate,
    principal: Principal = Depends(require_roles("rehab_therapist")),
    db: Session = Depends(get_db),
):
    patient = get_patient_scoped(db, principal, patient_id)

    confirmed_at = to_utc_naive(payload.confirmed_at) or utcnow()
    confirmation = StageConfirmation(
        id=uuid4().hex,
        patient_id=patient.patient_id,
        org_id=patient.org_id,
        stage=payload.stage,
        status="confirmed",
        confirmed_by=principal.account_id,
        confirmed_at=confirmed_at,
        note=payload.note,
    )
    db.add(confirmation)

    patient.hy_stage = payload.stage
    patient.stage_confirmation_status = "confirmed"
    patient.stage_confirmed_by = principal.account_id
    patient.stage_confirmed_at = confirmed_at

    record_audit(
        db,
        org_id=patient.org_id,
        actor_account_id=principal.account_id,
        action="stage_confirmation.created",
        resource_type="patient",
        resource_id=patient.patient_id,
        detail={"stage": payload.stage},
    )
    db.commit()
    return StageConfirmationOut.model_validate(confirmation)


@router.post(
    "/patients/{patientId}/assessments",
    response_model=AssessmentOut,
    response_model_exclude_none=True,
    status_code=201,
)
def create_assessment(
    patient_id: Annotated[str, Path(alias="patientId")],
    payload: AssessmentCreate,
    principal: Principal = Depends(
        require_roles("patient", "caregiver", "rehab_therapist")
    ),
    db: Session = Depends(get_db),
):
    patient = get_patient_scoped(db, principal, patient_id)

    assessment = Assessment(
        id=uuid4().hex,
        org_id=patient.org_id,
        patient_id=patient.patient_id,
        instrument=payload.instrument,
        score=payload.score,
        answers=payload.answers or {},
        # 量表分界待临床签核（C4），本版不计算风险等级、不输出诊断结论。
        risk_level=None,
        assessed_at=to_utc_naive(payload.assessed_at) or utcnow(),
        assessed_by=principal.account_id,
    )
    db.add(assessment)
    record_audit(
        db,
        org_id=patient.org_id,
        actor_account_id=principal.account_id,
        action="assessment.created",
        resource_type="assessment",
        resource_id=assessment.id,
        detail={"patientId": patient.patient_id, "instrument": payload.instrument},
    )
    db.commit()
    return AssessmentOut.model_validate(assessment)

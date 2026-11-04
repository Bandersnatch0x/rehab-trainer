from typing import Annotated
from uuid import uuid4

from fastapi import APIRouter, Depends, Path, Query
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.access import get_patient_scoped
from app.core.audit import record_audit
from app.core.auth import Principal, get_principal, require_roles
from app.core.database import get_db
from app.core.pagination import decode_cursor, encode_cursor
from app.models.clinical import Patient
from app.schemas.enums import PatientStatus
from app.schemas.patient import PatientCreate, PatientOut, PatientPage

router = APIRouter(prefix="/patients", tags=["patients"])


def _patient_out(patient: Patient) -> PatientOut:
    return PatientOut(
        id=patient.patient_id,
        org_id=patient.org_id,
        display_name=patient.display_name,
        birth_year=patient.birth_year,
        hy_stage=patient.hy_stage,
        stage_confirmation_status=patient.stage_confirmation_status,
        stage_confirmed_by=patient.stage_confirmed_by,
        stage_confirmed_at=patient.stage_confirmed_at,
        fall_risk=patient.fall_risk,
        symptoms=list(patient.symptoms or []),
        status=patient.status,
        created_at=patient.created_at,
    )


@router.get("", response_model=PatientPage, response_model_exclude_none=True)
def list_patients(
    principal: Principal = Depends(get_principal),
    db: Session = Depends(get_db),
    stage: int | None = Query(default=None, ge=1, le=5),
    status: PatientStatus | None = Query(default=None),
    cursor: str | None = Query(default=None),
    limit: int = Query(default=20, ge=1, le=100),
):
    offset = decode_cursor(cursor)
    stmt = select(Patient).where(Patient.org_id == principal.org_id)

    # 照护管理员与治疗师可见全部；患者与照护者只看关联患者。
    if not principal.is_clinician:
        if not principal.patient_ids:
            return PatientPage(items=[], next_cursor=None)
        stmt = stmt.where(Patient.patient_id.in_(principal.patient_ids))

    if stage is not None:
        stmt = stmt.where(Patient.hy_stage == stage)
    if status is not None:
        stmt = stmt.where(Patient.status == status.value)

    stmt = stmt.order_by(Patient.created_at.asc(), Patient.patient_id.asc())
    rows = db.scalars(stmt.offset(offset).limit(limit + 1)).all()
    has_more = len(rows) > limit
    page = rows[:limit]

    return PatientPage(
        items=[_patient_out(row) for row in page],
        next_cursor=encode_cursor(offset + limit) if has_more else None,
    )


@router.post(
    "",
    response_model=PatientOut,
    response_model_exclude_none=True,
    status_code=201,
)
def create_patient(
    payload: PatientCreate,
    principal: Principal = Depends(require_roles("care_admin", "rehab_therapist")),
    db: Session = Depends(get_db),
):
    patient = Patient(
        patient_id=uuid4().hex,
        org_id=principal.org_id,
        display_name=payload.display_name,
        birth_year=payload.birth_year,
        # 建档占位为 1 级；权威分期由治疗师分期确认（D11）。
        hy_stage=1,
        stage_confirmation_status="suspected",
        status="active",
        symptoms=[],
        note=payload.note,
    )
    db.add(patient)
    record_audit(
        db,
        org_id=principal.org_id,
        actor_account_id=principal.account_id,
        action="patient.created",
        resource_type="patient",
        resource_id=patient.patient_id,
    )
    db.commit()
    db.refresh(patient)
    return _patient_out(patient)


@router.get(
    "/{patientId}", response_model=PatientOut, response_model_exclude_none=True
)
def get_patient(
    patient_id: Annotated[str, Path(alias="patientId")],
    principal: Principal = Depends(get_principal),
    db: Session = Depends(get_db),
):
    patient = get_patient_scoped(db, principal, patient_id)
    return _patient_out(patient)

from datetime import datetime
from uuid import uuid4

from fastapi import APIRouter, Depends, Query
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.access import get_patient_scoped
from app.core.audit import record_audit
from app.core.auth import Principal, get_principal
from app.core.database import get_db
from app.core.pagination import decode_cursor, encode_cursor
from app.core.timeutils import to_utc_naive
from app.models.clinical import Patient
from app.models.prescription import Prescription
from app.models.record import SessionRecord
from app.schemas.common import ErrorBody
from app.schemas.session import (
    SessionBatchRequest,
    SessionBatchResult,
    SessionRecordCreate,
    SessionRecordOut,
    SessionRecordPage,
    SessionRecordResult,
)

router = APIRouter(prefix="/sessions", tags=["sessions"])


def _reject(
    client_record_id: str, code: str, message: str
) -> SessionRecordResult:
    return SessionRecordResult(
        client_record_id=client_record_id,
        accepted=False,
        error=ErrorBody(code=code, message=message),
    )


@router.post("", response_model=SessionBatchResult)
def upload_sessions(
    payload: SessionBatchRequest,
    principal: Principal = Depends(get_principal),
    db: Session = Depends(get_db),
):
    results: list[SessionRecordResult] = []
    resolved: dict[str, str] = {}

    for record in payload.records:
        # 同一批次内的重复 key 也必须幂等（autoflush=False，查询看不到未提交行）。
        if record.client_record_id in resolved:
            results.append(
                SessionRecordResult(
                    client_record_id=record.client_record_id,
                    accepted=True,
                    record_id=resolved[record.client_record_id],
                )
            )
            continue

        result = _accept_record(db, principal, record)
        if result.accepted and result.record_id:
            resolved[record.client_record_id] = result.record_id
        results.append(result)

    db.commit()
    return SessionBatchResult(results=results)


def _accept_record(
    db: Session, principal: Principal, record: SessionRecordCreate
) -> SessionRecordResult:
    patient = db.get(Patient, record.patient_id)
    if patient is None:
        return _reject(record.client_record_id, "NOT_FOUND", "患者不存在")

    if patient.org_id != principal.org_id:
        record_audit(
            db,
            org_id=principal.org_id,
            actor_account_id=principal.account_id,
            action="authz.cross_org_access",
            outcome="denied",
            resource_type="session_record",
            resource_id=record.client_record_id,
            detail={
                "tokenOrgId": principal.org_id,
                "resourceOrgId": patient.org_id,
                "patientId": record.patient_id,
            },
        )
        return _reject(record.client_record_id, "FORBIDDEN", "跨机构访问被拒绝")

    if not principal.is_clinician and patient.patient_id not in principal.patient_ids:
        record_audit(
            db,
            org_id=principal.org_id,
            actor_account_id=principal.account_id,
            action="authz.out_of_scope_access",
            outcome="denied",
            resource_type="session_record",
            resource_id=record.client_record_id,
            detail={"patientId": record.patient_id, "roles": list(principal.roles)},
        )
        return _reject(record.client_record_id, "FORBIDDEN", "无权为该患者上传记录")

    prescription = db.get(Prescription, record.prescription_id)
    if (
        prescription is None
        or prescription.patient_id != patient.patient_id
        or prescription.org_id != patient.org_id
    ):
        return _reject(
            record.client_record_id,
            "VALIDATION_FAILED",
            "处方不存在或不属于该患者",
        )

    # 幂等：以 clientRecordId 去重，重复提交返回已存在记录（契约约定）。
    existing = db.scalar(
        select(SessionRecord).where(
            SessionRecord.client_record_id == record.client_record_id
        )
    )
    if existing is not None:
        if existing.org_id != principal.org_id:
            record_audit(
                db,
                org_id=principal.org_id,
                actor_account_id=principal.account_id,
                action="authz.cross_org_access",
                outcome="denied",
                resource_type="session_record",
                resource_id=record.client_record_id,
                detail={"reason": "clientRecordIdMatchedOtherOrg"},
            )
            return _reject(
                record.client_record_id, "FORBIDDEN", "跨机构访问被拒绝"
            )
        return SessionRecordResult(
            client_record_id=record.client_record_id,
            accepted=True,
            record_id=existing.id,
        )

    session_record = SessionRecord(
        id=uuid4().hex,
        client_record_id=record.client_record_id,
        org_id=patient.org_id,
        patient_id=patient.patient_id,
        prescription_id=prescription.id,
        recorded_by=principal.account_id,
        recorded_at=to_utc_naive(record.recorded_at),
        checked_in=record.checked_in,
        completion_count=record.completion_count,
        movement_error_count=record.movement_error_count,
        interruption_count=record.interruption_count,
        valid_frame_ratio=record.valid_frame_ratio,
        is_doubtful=record.is_doubtful,
        model_version=record.model_version,
        model_sha256=record.model_sha256,
        metric_algorithm_version=record.metric_algorithm_version,
        threshold_config_version=record.threshold_config_version,
        metrics=record.metrics or {},
    )
    db.add(session_record)
    return SessionRecordResult(
        client_record_id=record.client_record_id,
        accepted=True,
        record_id=session_record.id,
    )


@router.get("", response_model=SessionRecordPage, response_model_exclude_none=True)
def list_sessions(
    patient_id: str = Query(alias="patientId"),
    principal: Principal = Depends(get_principal),
    db: Session = Depends(get_db),
    from_: datetime | None = Query(default=None, alias="from"),
    to: datetime | None = Query(default=None),
    cursor: str | None = Query(default=None),
    limit: int = Query(default=20, ge=1, le=100),
):
    offset = decode_cursor(cursor)
    get_patient_scoped(db, principal, patient_id)

    stmt = select(SessionRecord).where(
        SessionRecord.org_id == principal.org_id,
        SessionRecord.patient_id == patient_id,
    )
    from_naive = to_utc_naive(from_)
    to_naive = to_utc_naive(to)
    if from_naive is not None:
        stmt = stmt.where(SessionRecord.recorded_at >= from_naive)
    if to_naive is not None:
        stmt = stmt.where(SessionRecord.recorded_at <= to_naive)

    stmt = stmt.order_by(
        SessionRecord.recorded_at.desc(), SessionRecord.id.desc()
    )
    rows = db.scalars(stmt.offset(offset).limit(limit + 1)).all()
    has_more = len(rows) > limit
    page = rows[:limit]

    return SessionRecordPage(
        items=[SessionRecordOut.model_validate(row) for row in page],
        next_cursor=encode_cursor(offset + limit) if has_more else None,
    )

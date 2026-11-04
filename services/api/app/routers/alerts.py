from typing import Annotated

from fastapi import APIRouter, Depends, Path, Query
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.access import get_patient_scoped
from app.core.audit import record_audit
from app.core.auth import Principal, get_principal, require_roles
from app.core.database import get_db
from app.core.errors import api_error
from app.core.pagination import decode_cursor, encode_cursor
from app.core.timeutils import utcnow
from app.models.alert import AlertEvent
from app.schemas.alert import AlertOut, AlertPage, AlertPatch
from app.schemas.enums import AlertLevel, AlertStatus

router = APIRouter(prefix="/alerts", tags=["alerts"])


@router.get("", response_model=AlertPage)
def list_alerts(
    principal: Principal = Depends(get_principal),
    db: Session = Depends(get_db),
    patient_id: str | None = Query(default=None, alias="patientId"),
    level: AlertLevel | None = Query(default=None),
    status: AlertStatus | None = Query(default=None),
    cursor: str | None = Query(default=None),
    limit: int = Query(default=20, ge=1, le=100),
):
    offset = decode_cursor(cursor)
    stmt = select(AlertEvent).where(AlertEvent.org_id == principal.org_id)

    if patient_id is not None:
        get_patient_scoped(db, principal, patient_id)
        stmt = stmt.where(AlertEvent.patient_id == patient_id)
    elif not principal.is_clinician:
        if not principal.patient_ids:
            return AlertPage(items=[], next_cursor=None)
        stmt = stmt.where(AlertEvent.patient_id.in_(principal.patient_ids))

    if level is not None:
        stmt = stmt.where(AlertEvent.level == level.value)
    if status is not None:
        stmt = stmt.where(AlertEvent.status == status.value)

    stmt = stmt.order_by(
        AlertEvent.triggered_at.desc(), AlertEvent.id.desc()
    )
    rows = db.scalars(stmt.offset(offset).limit(limit + 1)).all()
    has_more = len(rows) > limit
    page = rows[:limit]

    return AlertPage(
        items=[AlertOut.model_validate(row) for row in page],
        next_cursor=encode_cursor(offset + limit) if has_more else None,
    )


@router.patch(
    "/{alertId}", response_model=AlertOut, response_model_exclude_none=True
)
def update_alert(
    alert_id: Annotated[str, Path(alias="alertId")],
    payload: AlertPatch,
    principal: Principal = Depends(
        require_roles("care_admin", "rehab_therapist")
    ),
    db: Session = Depends(get_db),
):
    alert = db.get(AlertEvent, alert_id)
    if alert is None:
        raise api_error(404, "NOT_FOUND", "预警事件不存在")
    if alert.org_id != principal.org_id:
        record_audit(
            db,
            org_id=principal.org_id,
            actor_account_id=principal.account_id,
            action="authz.cross_org_access",
            outcome="denied",
            resource_type="alert_event",
            resource_id=alert_id,
            detail={
                "tokenOrgId": principal.org_id,
                "resourceOrgId": alert.org_id,
            },
        )
        db.commit()
        raise api_error(403, "FORBIDDEN", "跨机构访问被拒绝")

    alert.status = payload.status.value
    if payload.note is not None:
        alert.note = payload.note
    if payload.status.value == "acknowledged" and alert.acknowledged_at is None:
        alert.acknowledged_at = utcnow()
    alert.handled_by = principal.account_id

    record_audit(
        db,
        org_id=alert.org_id,
        actor_account_id=principal.account_id,
        action="alert.updated",
        resource_type="alert_event",
        resource_id=alert.id,
        detail={"status": alert.status, "patientId": alert.patient_id},
    )
    db.commit()
    return AlertOut.model_validate(alert)

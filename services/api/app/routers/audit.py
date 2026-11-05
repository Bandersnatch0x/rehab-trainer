from datetime import datetime

from fastapi import APIRouter, Depends, Query
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.auth import Principal, require_roles
from app.core.database import get_db
from app.core.pagination import apply_keyset, cursor_from, datetime_key, decode_cursor
from app.core.timeutils import to_utc_naive
from app.models.audit import AuditEvent
from app.schemas.audit import AuditEventOut, AuditEventPage

router = APIRouter(tags=["audit"])


@router.get("/audit-events", response_model=AuditEventPage)
def list_audit_events(
    principal: Principal = Depends(
        require_roles("care_admin", "rehab_therapist")
    ),
    db: Session = Depends(get_db),
    action: str | None = Query(default=None, max_length=64),
    resource_type: str | None = Query(default=None, alias="resourceType"),
    actor_account_id: str | None = Query(default=None, alias="actorAccountId"),
    from_: datetime | None = Query(default=None, alias="from"),
    to: datetime | None = Query(default=None),
    cursor: str | None = Query(default=None),
    limit: int = Query(default=50, ge=1, le=200),
):
    keyset = decode_cursor(cursor, keys=("k", "i"))
    # 只返回本机构事件；系统级事件（org_id 为空）不对外暴露。
    stmt = select(AuditEvent).where(AuditEvent.org_id == principal.org_id)
    if action is not None:
        stmt = stmt.where(AuditEvent.action == action)
    if resource_type is not None:
        stmt = stmt.where(AuditEvent.resource_type == resource_type)
    if actor_account_id is not None:
        stmt = stmt.where(AuditEvent.actor_account_id == actor_account_id)
    from_naive = to_utc_naive(from_)
    to_naive = to_utc_naive(to)
    if from_naive is not None:
        stmt = stmt.where(AuditEvent.created_at >= from_naive)
    if to_naive is not None:
        stmt = stmt.where(AuditEvent.created_at <= to_naive)

    stmt = apply_keyset(
        stmt,
        AuditEvent.created_at,
        AuditEvent.id,
        keyset,
        descending=True,
        key_type=datetime.fromisoformat,
    )
    stmt = stmt.order_by(AuditEvent.created_at.desc(), AuditEvent.id.desc())
    rows = db.scalars(stmt.limit(limit + 1)).all()
    has_more = len(rows) > limit
    page = rows[:limit]

    next_cursor = None
    if has_more and page:
        last = page[-1]
        next_cursor = cursor_from(key=datetime_key(last.created_at), ident=last.id)

    return AuditEventPage(
        items=[AuditEventOut.model_validate(row) for row in page],
        next_cursor=next_cursor,
    )
from typing import Any
from uuid import uuid4

from sqlalchemy.orm import Session

from app.models.audit import AuditEvent


def record_audit(
    db: Session,
    *,
    org_id: str | None,
    actor_account_id: str | None,
    action: str,
    outcome: str = "success",
    resource_type: str | None = None,
    resource_id: str | None = None,
    detail: dict[str, Any] | None = None,
) -> AuditEvent:
    """写入一条审计事件；调用方负责 commit。"""
    event = AuditEvent(
        id=uuid4().hex,
        org_id=org_id,
        actor_account_id=actor_account_id,
        action=action,
        outcome=outcome,
        resource_type=resource_type,
        resource_id=resource_id,
        detail=detail or {},
    )
    db.add(event)
    return event

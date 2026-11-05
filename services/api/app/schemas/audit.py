from datetime import datetime
from typing import Any

from app.schemas.common import APIModel


class AuditEventOut(APIModel):
    id: str
    actor_account_id: str | None = None
    action: str
    resource_type: str | None = None
    resource_id: str | None = None
    outcome: str
    detail: dict[str, Any] = {}
    created_at: datetime


class AuditEventPage(APIModel):
    items: list[AuditEventOut]
    next_cursor: str | None = None
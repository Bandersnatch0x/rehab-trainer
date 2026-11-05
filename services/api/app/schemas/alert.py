from datetime import datetime

from app.schemas.common import APIModel
from app.schemas.enums import (
    AlertActionStatus,
    AlertLevel,
    AlertStatus,
    DeliveryChannel,
    DeliveryStatus,
)


class NotificationDeliveryOut(APIModel):
    channel: DeliveryChannel
    status: DeliveryStatus
    delivered_at: datetime


class AlertOut(APIModel):
    id: str
    patient_id: str
    rule_code: str
    level: AlertLevel
    triggered_at: datetime
    status: AlertStatus
    handled_by: str | None = None
    note: str | None = None
    deliveries: list[NotificationDeliveryOut] = []


class AlertPage(APIModel):
    items: list[AlertOut]
    next_cursor: str | None = None


class AlertPatch(APIModel):
    status: AlertActionStatus
    note: str | None = None

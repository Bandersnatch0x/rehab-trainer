from datetime import datetime
from typing import Any

from sqlalchemy import JSON, DateTime, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base
from app.core.timeutils import utcnow


class AlertEvent(Base):
    __tablename__ = "alert_event"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    org_id: Mapped[str] = mapped_column(
        ForeignKey("org.org_id"), nullable=False, index=True
    )
    patient_id: Mapped[str] = mapped_column(
        ForeignKey("patient.patient_id"), nullable=False, index=True
    )
    rule_code: Mapped[str] = mapped_column(String(64), nullable=False)
    level: Mapped[str] = mapped_column(String(16), nullable=False)
    triggered_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    status: Mapped[str] = mapped_column(String(16), default="open", nullable=False)
    acknowledged_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    handled_by: Mapped[str | None] = mapped_column(
        ForeignKey("account.account_id"), nullable=True
    )
    note: Mapped[str | None] = mapped_column(String(1024), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)

    deliveries: Mapped[list["NotificationDelivery"]] = relationship(
        back_populates="alert",
        cascade="all, delete-orphan",
        order_by="NotificationDelivery.created_at",
        # 列表端点逐行序列化 deliveries；默认懒加载会退化成 N+1。
        lazy="selectin",
    )


class NotificationDelivery(Base):
    __tablename__ = "notification_delivery"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    alert_id: Mapped[str] = mapped_column(
        ForeignKey("alert_event.id"), nullable=False, index=True
    )
    channel: Mapped[str] = mapped_column(String(32), nullable=False)
    status: Mapped[str] = mapped_column(String(16), nullable=False)
    delivered_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    detail: Mapped[dict[str, Any]] = mapped_column(JSON, default=dict, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)

    alert: Mapped["AlertEvent"] = relationship(back_populates="deliveries")

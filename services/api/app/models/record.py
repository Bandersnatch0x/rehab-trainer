from datetime import datetime
from typing import Any

from sqlalchemy import (
    JSON,
    Boolean,
    CheckConstraint,
    DateTime,
    Float,
    ForeignKey,
    Integer,
    String,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base
from app.core.timeutils import utcnow


class SessionRecord(Base):
    """训练记录；client_record_id 唯一以保证批量补传幂等（D14、T7）。"""

    __tablename__ = "session_record"
    __table_args__ = (
        CheckConstraint(
            "valid_frame_ratio IS NULL OR "
            "(valid_frame_ratio >= 0 AND valid_frame_ratio <= 1)",
            name="ck_session_record_valid_frame_ratio",
        ),
    )

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    client_record_id: Mapped[str] = mapped_column(
        String(128), unique=True, nullable=False, index=True
    )
    org_id: Mapped[str] = mapped_column(
        ForeignKey("org.org_id"), nullable=False, index=True
    )
    patient_id: Mapped[str] = mapped_column(
        ForeignKey("patient.patient_id"), nullable=False, index=True
    )
    prescription_id: Mapped[str] = mapped_column(
        ForeignKey("prescription.id"), nullable=False, index=True
    )
    recorded_by: Mapped[str] = mapped_column(
        ForeignKey("account.account_id"), nullable=False
    )
    recorded_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    checked_in: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    completion_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    movement_error_count: Mapped[int] = mapped_column(
        Integer, default=0, nullable=False
    )
    interruption_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    valid_frame_ratio: Mapped[float | None] = mapped_column(Float, nullable=True)
    is_doubtful: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    model_version: Mapped[str] = mapped_column(String(64), nullable=False)
    model_sha256: Mapped[str] = mapped_column(String(64), nullable=False)
    metric_algorithm_version: Mapped[str] = mapped_column(String(64), nullable=False)
    threshold_config_version: Mapped[str] = mapped_column(String(64), nullable=False)
    metrics: Mapped[dict[str, Any]] = mapped_column(JSON, default=dict, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)

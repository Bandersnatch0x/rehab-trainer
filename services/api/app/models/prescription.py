from datetime import datetime
from typing import Any

from sqlalchemy import JSON, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base
from app.core.timeutils import utcnow


class Prescription(Base):
    """处方；version 为乐观锁版本，调整处方时递增（D12）。"""

    __tablename__ = "prescription"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    org_id: Mapped[str] = mapped_column(
        ForeignKey("org.org_id"), nullable=False, index=True
    )
    patient_id: Mapped[str] = mapped_column(
        ForeignKey("patient.patient_id"), nullable=False, index=True
    )
    prescribed_by: Mapped[str] = mapped_column(
        ForeignKey("account.account_id"), nullable=False
    )
    version: Mapped[str] = mapped_column(String(32), default="1.0.0", nullable=False)
    mapping_table_version: Mapped[str] = mapped_column(String(32), nullable=False)
    issued_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    valid_from: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    valid_until: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    status: Mapped[str] = mapped_column(String(16), default="active", nullable=False)
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, default=utcnow, onupdate=utcnow, nullable=False
    )

    items: Mapped[list["PrescriptionItem"]] = relationship(
        back_populates="prescription",
        cascade="all, delete-orphan",
        order_by="PrescriptionItem.position",
    )


class PrescriptionItem(Base):
    __tablename__ = "prescription_item"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    prescription_id: Mapped[str] = mapped_column(
        ForeignKey("prescription.id"), nullable=False, index=True
    )
    exercise_code: Mapped[str] = mapped_column(
        ForeignKey("exercise.code"), nullable=False
    )
    variant_code: Mapped[str | None] = mapped_column(
        ForeignKey("exercise_variant.code"), nullable=True
    )
    dosage: Mapped[dict[str, Any] | None] = mapped_column(JSON, nullable=True)
    position: Mapped[int] = mapped_column(Integer, nullable=False, default=0)

    prescription: Mapped["Prescription"] = relationship(back_populates="items")

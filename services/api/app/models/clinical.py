from datetime import datetime
from typing import Any

from sqlalchemy import (
    JSON,
    CheckConstraint,
    DateTime,
    Float,
    ForeignKey,
    Integer,
    String,
    Text,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base
from app.core.timeutils import utcnow


class Patient(Base):
    __tablename__ = "patient"
    __table_args__ = (
        CheckConstraint("hy_stage BETWEEN 1 AND 5", name="ck_patient_hy_stage"),
    )

    patient_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    # 多机构：一患者一机构（D10）。
    org_id: Mapped[str] = mapped_column(
        ForeignKey("org.org_id"), nullable=False, index=True
    )
    display_name: Mapped[str] = mapped_column(String(128), nullable=False)
    birth_year: Mapped[int | None] = mapped_column(Integer, nullable=True)
    # 建档先占位为 1；权威分期以治疗师分期确认为准（D11）。
    hy_stage: Mapped[int] = mapped_column(Integer, default=1, nullable=False)
    stage_confirmation_status: Mapped[str] = mapped_column(
        String(16), default="suspected", nullable=False
    )
    stage_confirmed_by: Mapped[str | None] = mapped_column(String(64), nullable=True)
    stage_confirmed_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    fall_risk: Mapped[str | None] = mapped_column(String(16), nullable=True)
    symptoms: Mapped[list[Any]] = mapped_column(JSON, default=list, nullable=False)
    status: Mapped[str] = mapped_column(String(16), default="active", nullable=False)
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)


class StageConfirmation(Base):
    __tablename__ = "stage_confirmation"
    __table_args__ = (
        CheckConstraint(
            "stage BETWEEN 1 AND 5", name="ck_stage_confirmation_stage"
        ),
    )

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    patient_id: Mapped[str] = mapped_column(
        ForeignKey("patient.patient_id"), nullable=False, index=True
    )
    org_id: Mapped[str] = mapped_column(
        ForeignKey("org.org_id"), nullable=False, index=True
    )
    stage: Mapped[int] = mapped_column(Integer, nullable=False)
    status: Mapped[str] = mapped_column(String(16), default="confirmed", nullable=False)
    confirmed_by: Mapped[str] = mapped_column(
        ForeignKey("account.account_id"), nullable=False
    )
    confirmed_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)


class Assessment(Base):
    __tablename__ = "assessment"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    org_id: Mapped[str] = mapped_column(
        ForeignKey("org.org_id"), nullable=False, index=True
    )
    patient_id: Mapped[str] = mapped_column(
        ForeignKey("patient.patient_id"), nullable=False, index=True
    )
    instrument: Mapped[str] = mapped_column(String(64), nullable=False)
    score: Mapped[float | None] = mapped_column(Float, nullable=True)
    answers: Mapped[dict[str, Any]] = mapped_column(JSON, default=dict, nullable=False)
    # 量表分界值待临床签核（C4），本版不据此输出诊断结论。
    risk_level: Mapped[str | None] = mapped_column(String(16), nullable=True)
    assessed_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    assessed_by: Mapped[str] = mapped_column(
        ForeignKey("account.account_id"), nullable=False
    )
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)

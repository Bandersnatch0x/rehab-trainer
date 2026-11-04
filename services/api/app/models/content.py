from datetime import datetime
from typing import Any

from sqlalchemy import JSON, CheckConstraint, DateTime, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base
from app.core.timeutils import utcnow


class Exercise(Base):
    """训练条目；处方只能引用已发布条目（C1/C2）。"""

    __tablename__ = "exercise"

    code: Mapped[str] = mapped_column(String(64), primary_key=True)
    label: Mapped[str] = mapped_column(String(128), nullable=False)
    category: Mapped[str] = mapped_column(String(32), nullable=False)
    publication_status: Mapped[str] = mapped_column(
        String(16), default="draft", nullable=False
    )
    risk_level: Mapped[str] = mapped_column(
        String(16), default="low", nullable=False
    )
    applicable_stages: Mapped[list[Any]] = mapped_column(
        JSON, default=list, nullable=False
    )
    body_parts: Mapped[list[Any]] = mapped_column(JSON, default=list, nullable=False)
    joints: Mapped[list[Any]] = mapped_column(JSON, default=list, nullable=False)
    contraindications: Mapped[list[Any]] = mapped_column(
        JSON, default=list, nullable=False
    )
    danger_signals: Mapped[list[Any]] = mapped_column(JSON, default=list, nullable=False)
    default_dosage: Mapped[dict[str, Any] | None] = mapped_column(JSON, nullable=True)
    video_uri: Mapped[str | None] = mapped_column(String(512), nullable=True)
    audio_uri: Mapped[str | None] = mapped_column(String(512), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)

    variants: Mapped[list["ExerciseVariant"]] = relationship(
        back_populates="exercise",
        cascade="all, delete-orphan",
        order_by="ExerciseVariant.level",
    )


class ExerciseVariant(Base):
    __tablename__ = "exercise_variant"
    __table_args__ = (
        CheckConstraint("level BETWEEN 1 AND 3", name="ck_exercise_variant_level"),
    )

    code: Mapped[str] = mapped_column(String(64), primary_key=True)
    exercise_code: Mapped[str] = mapped_column(
        ForeignKey("exercise.code"), nullable=False, index=True
    )
    level: Mapped[int] = mapped_column(Integer, nullable=False)
    # H-Y 3–4 级禁无支撑站立。
    support_level: Mapped[str] = mapped_column(String(32), nullable=False)
    applicable_stages: Mapped[list[Any]] = mapped_column(
        JSON, default=list, nullable=False
    )
    default_dosage: Mapped[dict[str, Any] | None] = mapped_column(JSON, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, nullable=False)

    exercise: Mapped["Exercise"] = relationship(back_populates="variants")

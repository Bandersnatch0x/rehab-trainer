from pydantic import Field

from app.schemas.common import APIModel
from app.schemas.enums import (
    ExerciseCategory,
    PublicationStatus,
    RiskLevel,
    SupportLevel,
)


class Dosage(APIModel):
    sets: int | None = Field(default=None, ge=0)
    repetitions: int | None = Field(default=None, ge=0)
    # TODO: 待临床签核（C3）：BPM 个体化规则，本服务不计算 BPM。
    bpm: int | None = Field(default=None, ge=0)
    duration_minutes: float | None = Field(default=None, ge=0)
    frequency_per_week: int | None = Field(default=None, ge=0)
    rest_seconds: int | None = Field(default=None, ge=0)


class ExerciseVariantOut(APIModel):
    code: str
    level: int
    support_level: SupportLevel | None = None
    applicable_stages: list[int] = Field(default_factory=list)
    default_dosage: Dosage | None = None


class ExerciseOut(APIModel):
    code: str
    label: str
    category: ExerciseCategory
    publication_status: PublicationStatus
    risk_level: RiskLevel | None = None
    applicable_stages: list[int] = Field(default_factory=list)
    body_parts: list[str] = Field(default_factory=list)
    joints: list[str] = Field(default_factory=list)
    contraindications: list[str] = Field(default_factory=list)
    danger_signals: list[str] = Field(default_factory=list)
    variants: list[ExerciseVariantOut] = Field(default_factory=list)
    default_dosage: Dosage | None = None
    video_uri: str | None = None
    audio_uri: str | None = None


class ExercisePage(APIModel):
    items: list[ExerciseOut]
    next_cursor: str | None = None

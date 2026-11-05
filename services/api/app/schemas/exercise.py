from pydantic import Field

from app.schemas.common import APIModel
from app.schemas.enums import (
    ExerciseCategory,
    PublicationStatus,
    RiskLevel,
    SupportLevel,
)


class Dosage(APIModel):
    """剂量。上限是资源与安全兜底，不是临床规则；临床分界仍待签核（C3/C4）。"""

    sets: int | None = Field(default=None, ge=0, le=50)
    repetitions: int | None = Field(default=None, ge=0, le=500)
    # TODO: 待临床签核（C3）：BPM 个体化规则，本服务不计算 BPM。
    bpm: int | None = Field(default=None, ge=0, le=240)
    duration_minutes: float | None = Field(default=None, ge=0, le=600)
    frequency_per_week: int | None = Field(default=None, ge=0, le=14)
    rest_seconds: int | None = Field(default=None, ge=0, le=3600)


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
    # 版权标注：来源署名与水印文字。水印由客户端叠加显示，不烧录进文件。
    video_credit_line: str | None = None
    video_watermark_text: str | None = None


class ExercisePage(APIModel):
    items: list[ExerciseOut]
    next_cursor: str | None = None

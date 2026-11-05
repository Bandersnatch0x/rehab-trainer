import math
from datetime import datetime
from typing import Any

from pydantic import Field, field_validator

from app.schemas.common import APIModel
from app.schemas.enums import RiskLevel, StageConfirmationStatus

MAX_ANSWERS_FIELDS = 32
MAX_ANSWER_TEXT = 1000
MAX_ANSWERS_CHARS = 4096


class StageConfirmationCreate(APIModel):
    stage: int = Field(ge=1, le=5)
    confirmed_at: datetime
    note: str | None = None


class StageConfirmationOut(APIModel):
    patient_id: str
    stage: int
    status: StageConfirmationStatus
    confirmed_by: str
    confirmed_at: datetime
    note: str | None = None


class AssessmentCreate(APIModel):
    instrument: str = Field(min_length=1, max_length=64)
    score: float | None = None
    answers: dict[str, Any] | None = None
    assessed_at: datetime
    # 契约字段：代操作时与 subject 不同。本服务一律以登录账号为 assessedBy。
    assessed_by: str | None = None

    @field_validator("answers")
    @classmethod
    def _limit_answers(cls, value: dict[str, Any] | None) -> dict[str, Any] | None:
        """限制自由文本答案的体积与形状，避免单条记录撑爆存储与响应。"""
        if value is None:
            return value
        if len(value) > MAX_ANSWERS_FIELDS:
            raise ValueError(f"answers 最多 {MAX_ANSWERS_FIELDS} 个字段")
        total = 0
        for key, item in value.items():
            if len(key) > 64:
                raise ValueError("answers 字段名不超过 64 字")
            if item is not None and not isinstance(item, (str, int, float, bool)):
                raise ValueError("answers 只接受标量值")
            if isinstance(item, float) and not math.isfinite(item):
                raise ValueError("answers 不接受非有限数值")
            if isinstance(item, str) and len(item) > MAX_ANSWER_TEXT:
                raise ValueError(f"answers 单条文本不超过 {MAX_ANSWER_TEXT} 字")
            total += len(key) + len(str(item) if item is not None else "")
        if total > MAX_ANSWERS_CHARS:
            raise ValueError(f"answers 总长度不超过 {MAX_ANSWERS_CHARS} 字")
        return value


class AssessmentOut(APIModel):
    id: str
    patient_id: str
    instrument: str
    score: float | None = None
    answers: dict[str, Any] = {}
    risk_level: RiskLevel | None = None
    assessed_at: datetime
    assessed_by: str


class AssessmentPage(APIModel):
    items: list[AssessmentOut]
    next_cursor: str | None = None

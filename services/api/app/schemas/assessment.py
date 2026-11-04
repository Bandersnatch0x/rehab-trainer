from datetime import datetime
from typing import Any

from pydantic import Field

from app.schemas.common import APIModel
from app.schemas.enums import RiskLevel, StageConfirmationStatus


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


class AssessmentOut(APIModel):
    id: str
    patient_id: str
    instrument: str
    score: float | None = None
    risk_level: RiskLevel | None = None
    assessed_at: datetime
    assessed_by: str

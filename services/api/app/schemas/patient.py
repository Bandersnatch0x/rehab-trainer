from datetime import datetime

from pydantic import Field

from app.schemas.common import APIModel
from app.schemas.enums import PatientStatus, RiskLevel, StageConfirmationStatus


class PatientCreate(APIModel):
    display_name: str = Field(min_length=1, max_length=128)
    birth_year: int | None = Field(default=None, ge=1900, le=2100)
    note: str | None = None


class PatientOut(APIModel):
    id: str
    org_id: str
    display_name: str
    birth_year: int | None = None
    hy_stage: int
    stage_confirmation_status: StageConfirmationStatus
    stage_confirmed_by: str | None = None
    stage_confirmed_at: datetime | None = None
    fall_risk: RiskLevel | None = None
    symptoms: list[str] = Field(default_factory=list)
    status: PatientStatus
    created_at: datetime


class PatientPage(APIModel):
    items: list[PatientOut]
    next_cursor: str | None = None

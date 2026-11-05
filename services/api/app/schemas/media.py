from datetime import datetime

from pydantic import Field, field_validator

from app.schemas.common import APIModel
from app.schemas.enums import MediaAssetStatus


class MediaAssetOut(APIModel):
    id: str
    exercise_code: str | None = None
    public_url: str
    content_type: str
    size_bytes: int
    sha256: str
    watermark_text: str | None = None
    credit_line: str | None = None
    status: MediaAssetStatus
    applicable_stages: list[int] = Field(default_factory=list)
    published_at: datetime | None = None
    retired_at: datetime | None = None
    created_by: str | None = None
    created_at: datetime
    updated_at: datetime


class MediaAssetPage(APIModel):
    items: list[MediaAssetOut]
    next_cursor: str | None = None


class MediaAssetPatch(APIModel):
    watermark_text: str | None = Field(default=None, max_length=128)
    credit_line: str | None = Field(default=None, max_length=255)
    applicable_stages: list[int] | None = None
    status: MediaAssetStatus | None = None
    note: str | None = None

    @field_validator("applicable_stages")
    @classmethod
    def _stages_in_range(cls, value: list[int] | None) -> list[int] | None:
        if value is None:
            return None
        stages = sorted(set(value))
        if any(stage < 1 or stage > 5 for stage in stages):
            raise ValueError("适用分期必须在 1–5 之间")
        return stages

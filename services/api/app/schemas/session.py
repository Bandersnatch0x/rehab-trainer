from datetime import datetime
from typing import Any

from pydantic import Field

from app.schemas.common import APIModel, ErrorBody


class SessionRecordCreate(APIModel):
    # 幂等键必须是客户端生成的受控标识；禁止 `= + - @` 等可被表格软件解释的字符。
    client_record_id: str = Field(
        min_length=1,
        max_length=128,
        pattern=r"^[A-Za-z0-9][A-Za-z0-9._:-]*$",
    )
    patient_id: str = Field(min_length=1)
    prescription_id: str = Field(min_length=1)
    recorded_at: datetime
    # 契约字段；本服务以登录账号为 actor，subject 由 patientId 指定（D4）。
    recorded_by: str | None = None
    checked_in: bool = False
    # 上限对齐 PostgreSQL int4；超出会在 commit 时抛 DataError，整批 500。
    completion_count: int = Field(default=0, ge=0, le=2_147_483_647)
    movement_error_count: int = Field(default=0, ge=0, le=2_147_483_647)
    interruption_count: int = Field(default=0, ge=0, le=2_147_483_647)
    valid_frame_ratio: float | None = Field(default=None, ge=0, le=1)
    is_doubtful: bool = False
    # 四个版本字段对应 String(64) 列；超长在 PostgreSQL 上会让整批 500。
    model_version: str = Field(min_length=1, max_length=64)
    model_sha256: str = Field(min_length=1, max_length=64)
    metric_algorithm_version: str = Field(min_length=1, max_length=64)
    threshold_config_version: str = Field(min_length=1, max_length=64)
    metrics: dict[str, Any] | None = None


class SessionBatchRequest(APIModel):
    records: list[SessionRecordCreate] = Field(min_length=1, max_length=100)


class SessionRecordResult(APIModel):
    client_record_id: str
    accepted: bool
    record_id: str | None = None
    error: ErrorBody | None = None


class SessionBatchResult(APIModel):
    results: list[SessionRecordResult]


class SessionRecordOut(APIModel):
    id: str
    client_record_id: str
    patient_id: str
    prescription_id: str
    recorded_at: datetime
    checked_in: bool | None = None
    completion_count: int | None = None
    movement_error_count: int | None = None
    interruption_count: int | None = None
    valid_frame_ratio: float | None = None
    is_doubtful: bool | None = None
    model_version: str | None = None
    metric_algorithm_version: str | None = None
    threshold_config_version: str | None = None


class SessionRecordPage(APIModel):
    items: list[SessionRecordOut]
    next_cursor: str | None = None

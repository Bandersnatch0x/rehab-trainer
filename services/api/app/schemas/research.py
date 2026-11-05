"""科研聚合响应的 schema。

字段名与 packages/api-contract/openapi.yaml 的 Research* schema 一一对应；
计数型字段用可空整数表达「n<5 抑制」，不提供区间或插值。
"""

from datetime import datetime

from pydantic import Field

from app.schemas.common import APIModel


class CountCell(APIModel):
    count: int | None = None
    suppressed: bool


class RateCell(APIModel):
    value: float | None = None
    suppressed: bool


class Bucket(APIModel):
    key: str
    count: int | None = None
    suppressed: bool


class ResearchOverview(APIModel):
    org_id: str
    generated_at: datetime
    data_origin: str
    min_cell_count: int
    patient_count: CountCell
    hy_stage_distribution: list[Bucket]
    stage_confirmation_distribution: list[Bucket]
    age_distribution: list[Bucket]


class VersionDistribution(APIModel):
    key: str
    count: int | None = None
    suppressed: bool


class ResearchDataQuality(APIModel):
    org_id: str
    generated_at: datetime
    data_origin: str
    min_cell_count: int
    session_record_count: CountCell
    valid_frame_ratio_distribution: list[Bucket]
    doubtful_ratio: RateCell
    missing_ratio: RateCell
    model_version_distribution: list[VersionDistribution]
    metric_algorithm_version_distribution: list[VersionDistribution]
    threshold_config_version_distribution: list[VersionDistribution]
    note: str = Field(
        default="is_doubtful 为探索性变量：有效性阈值（未决项 12）尚未临床签核。"
    )

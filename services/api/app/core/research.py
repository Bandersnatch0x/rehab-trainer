"""科研聚合的公共约束：小样本抑制与 D23 导出 denylist。

约束来源：科研数据底座 v0（N3/N7）。聚合层只返回计数与分段分布，
任何计数小于 MIN_CELL_COUNT 的单元格一律抑制，不插值、不取整、不给区间。
"""

from typing import Any

from app.core.timeutils import utcnow

# 小样本抑制阈值：n < 5 不显示。
MIN_CELL_COUNT = 5

# 科研数据集只接受真实数据；demo 数据在查询层硬过滤（N1）。
RESEARCH_DATA_ORIGIN = "real"

# D23 未验证字段：步态绝对长度、米制速度、摆动相百分比、RAS 适用性结论。
# 这些字段不得进入任何科研输出，也不得出现在数据字典的可导出项里。
D23_PROHIBITED_FIELDS = frozenset(
    {
        "strideLengthCm",
        "strideLengthM",
        "gaitSpeedMps",
        "stepLengthCm",
        "swingPhasePercent",
        "swingPhasePct",
        "rasApplicability",
        "rasApplicable",
        "rasConclusion",
    }
)

# 字典里 d23_status 的取值。
D23_STATUS_ALLOWED = "allowed"
D23_STATUS_PROHIBITED = "prohibited"

AGE_BANDS: tuple[tuple[str, int | None, int | None], ...] = (
    ("under-60", None, 59),
    ("60-69", 60, 69),
    ("70-79", 70, 79),
    ("80-plus", 80, None),
)
AGE_UNKNOWN_KEY = "unknown"

VALID_FRAME_RATIO_BANDS: tuple[tuple[str, float | None, float | None], ...] = (
    ("missing", None, None),
    ("lt-0.5", None, 0.5),
    ("0.5-0.8", 0.5, 0.8),
    ("gte-0.8", 0.8, None),
)


def _is_small(count: int) -> bool:
    """需要抑制的单元格：0 < count < MIN_CELL_COUNT。

    count == 0 表示该分段没有任何个体，公布 0 不指向任何人，且「零缺失」
    本身是有价值的质量信号；只有 1–4 的计数才可能被反推回个体。
    """
    return 0 < count < MIN_CELL_COUNT


def suppressed(count: int) -> dict[str, Any]:
    """把计数包成可抑制的聚合单元。"""
    if _is_small(count):
        return {"count": None, "suppressed": True}
    return {"count": count, "suppressed": False}


def _hidden(key: str) -> dict[str, Any]:
    return {"key": key, "count": None, "suppressed": True}


def buckets(counts: dict[str, int], keys: tuple[str, ...]) -> list[dict[str, Any]]:
    """按固定键序输出分布；缺失键计 0，同样走抑制判定。

    一次抑制（primary）：计数在 1–4 之间的单元格隐藏。
    二次抑制（secondary）：若整组只隐藏了 1 个单元格，且总数本身已公开
    （≥ MIN_CELL_COUNT），其余单元格之和可由总数减去得出，等于直接公布
    被隐藏的值。此时再隐藏计数最小的可见单元格，使残差至少在两个未知
    单元格之间分摊。总数自身被抑制时无需二次抑制，因为无从相减。
    """
    cells = [(key, counts.get(key, 0)) for key in keys]
    total = sum(count for _, count in cells)
    hidden = {index for index, (_, count) in enumerate(cells) if _is_small(count)}
    if (
        len(hidden) == 1
        and len(cells) >= 2
        and total >= MIN_CELL_COUNT
    ):
        visible = [i for i in range(len(cells)) if i not in hidden]
        # 只从计数 >= MIN_CELL_COUNT 的可见格中挑：隐藏 0 计数的格子不增加歧义，
        # 残差仍会直接等于被一次抑制隐藏的小样本值。
        candidates = [i for i in visible if cells[i][1] >= MIN_CELL_COUNT] or visible
        hidden.add(min(candidates, key=lambda i: cells[i][1]))

    return [
        _hidden(key) if index in hidden else {"key": key, **suppressed(count)}
        for index, (key, count) in enumerate(cells)
    ]


def rate(numerator: int, denominator: int) -> dict[str, Any]:
    """比例；分子或分母不足 MIN_CELL_COUNT 时抑制。

    分子也要抑制：总数是公开的，给出精确比例即可反解出分子，
    绕过小样本抑制。分子为 0 时公布 0，因为没有任何个体可被识别。
    """
    if denominator < MIN_CELL_COUNT or _is_small(numerator):
        return {"value": None, "suppressed": True}
    return {"value": numerator / denominator, "suppressed": False}


def version_buckets(counts: dict[str, int]) -> list[tuple[str, int]]:
    """版本分层：1–4 条的版本不列出。

    若只丢弃了 1 个版本且总数已公开（≥ MIN_CELL_COUNT），其条数可由总数
    减去可见版本得出；此时再丢弃计数最小的可见版本，避免残差定位到单个
    版本。
    """
    visible = sorted(
        ((key, count) for key, count in counts.items() if not _is_small(count)),
        key=lambda item: item[0],
    )
    dropped = [key for key, count in counts.items() if _is_small(count)]
    total = sum(counts.values())
    if len(dropped) == 1 and visible and total >= MIN_CELL_COUNT:
        smallest = min(range(len(visible)), key=lambda i: visible[i][1])
        visible.pop(smallest)
    return visible


def age_band(birth_year: int | None, *, year: int | None = None) -> str:
    current_year = utcnow().year if year is None else year
    if birth_year is None:
        return AGE_UNKNOWN_KEY
    age = current_year - birth_year
    if age < 0:
        # 出生年份允许到 2100；未来年份不能落进 under-60 分段。
        return AGE_UNKNOWN_KEY
    for key, low, high in AGE_BANDS:
        if (low is None or age >= low) and (high is None or age <= high):
            return key
    return AGE_UNKNOWN_KEY


def valid_frame_ratio_band(value: float | None) -> str:
    if value is None:
        return "missing"
    for key, low, high in VALID_FRAME_RATIO_BANDS:
        if key == "missing":
            continue
        if (low is None or value >= low) and (high is None or value < high):
            return key
    return "gte-0.8"


# 科研输出字段 → 数据字典条目。字典校验据此确保「可导出字段必在字典里」。
RESEARCH_OUTPUT_FIELDS: dict[str, tuple[str, str]] = {
    "patientCount": ("patient", "data_origin"),
    "hyStageDistribution": ("patient", "hy_stage"),
    "stageConfirmationDistribution": ("patient", "stage_confirmation_status"),
    "ageDistribution": ("patient", "birth_year"),
    "sessionRecordCount": ("session_record", "recorded_at"),
    "validFrameRatioDistribution": ("session_record", "valid_frame_ratio"),
    "doubtfulRatio": ("session_record", "is_doubtful"),
    "missingRatio": ("session_record", "valid_frame_ratio"),
    "modelVersionDistribution": ("session_record", "model_version"),
    "metricAlgorithmVersionDistribution": (
        "session_record",
        "metric_algorithm_version",
    ),
    "thresholdConfigVersionDistribution": (
        "session_record",
        "threshold_config_version",
    ),
}


def find_denylisted_keys(payload: Any) -> set[str]:
    """递归收集载荷中出现的 D23 禁用字段名，供测试与自检使用。"""
    found: set[str] = set()
    if isinstance(payload, dict):
        for key, value in payload.items():
            if key in D23_PROHIBITED_FIELDS:
                found.add(key)
            found |= find_denylisted_keys(value)
    elif isinstance(payload, list):
        for item in payload:
            found |= find_denylisted_keys(item)
    return found

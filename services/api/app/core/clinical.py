"""临床配置占位。

TODO: 待临床签核（C4/C5）。量表分界值与预警阈值一律留空（None），
临床签核前不得据此输出诊断结论、风险等级或触发预警。

例外：训练有效性「存疑」标记的阈值机制（未决项 12）允许使用
明确标注的待签核占位值；该值只参与存疑标记，不阻断打卡，也不输出
诊断、风险等级或预警。签核后须替换占位值并按新版本号发布。
"""

from typing import Any

# 未决项 12：训练有效性阈值待临床签核。
# 机制先落：存疑标记由服务端依据有效帧占比与阈值配置版本派生，客户端自报值只作参考。
# 签核前使用保守占位下限；占位值不是临床结论，签核后须替换下表并按新版本号发布。
VALID_FRAME_RATIO_THRESHOLD_PLACEHOLDER = 0.8  # TODO: 待临床签核（未决项 12）

# 阈值配置版本 → 已签核的有效帧占比下限。None 表示该版本尚未签核。
VALID_FRAME_RATIO_THRESHOLDS: dict[str, float | None] = {
    "thresholds-2026.11": None,  # TODO: 待临床签核（未决项 12）
}

# H-Y 5 级仅宣教与转诊，不进入训练流程（D5，已裁决）。
TRAINING_MAX_HY_STAGE = 4
# 未确认分期不得用于高风险训练准入（D11，已裁决）。
HIGH_RISK_REQUIRES_CONFIRMED_STAGE = True
# 高风险训练条目的风险等级标识。
HIGH_RISK_LEVEL = "high"

# TODO: 待临床签核（C4）：量表选择与分界值。
ASSESSMENT_CUTOFFS: dict[str, dict[str, Any]] = {
    "HY_SELF": {"cutoff": None, "note": "H-Y 自评仅作线索，权威分期由治疗师确认"},
    "SARC-F": {"cutoff": None, "note": "肌少症初筛"},
    "SARC-CalF": {"cutoff": None, "note": "小腿围加项"},
    "RIGIDITY_5": {"cutoff": None, "note": "5 级主观僵硬感"},
    "STEADI": {"cutoff": None, "note": "跌倒筛查 3 问"},
    "Short FES-I": {"cutoff": None, "note": "跌倒恐惧"},
    "TUG": {"cutoff": None, "note": "升级判据"},
}

# TODO: 待临床签核（C5）：预警阈值与时间窗口，本单位仅为结构占位。
ALERT_RULES: dict[str, dict[str, Any]] = {
    "PAIN_PERSISTENT": {
        "threshold": None,
        "window_days": None,
        "note": "疼痛自评持续超过阈值",
    },
    "RIGIDITY_WORSENING": {
        "threshold": None,
        "baseline_days": None,
        "window_days": None,
        "note": "僵直较基线升高并持续",
    },
    "CRAMP_RECURRENT": {
        "threshold_minutes": None,
        "occurrences_per_week": None,
        "window_days": None,
        "note": "痉挛单次时长或周频次",
    },
    "FALL_RECURRENT": {
        "threshold": None,
        "window_days": None,
        "note": "跌倒次数或伴受伤",
    },
}


def valid_frame_ratio_threshold(threshold_config_version: str) -> float:
    """返回有效帧占比下限；该版本未签核时回落到待签核占位值。"""
    signed = VALID_FRAME_RATIO_THRESHOLDS.get(threshold_config_version)
    return signed if signed is not None else VALID_FRAME_RATIO_THRESHOLD_PLACEHOLDER


def derive_is_doubtful(
    valid_frame_ratio: float | None,
    threshold_config_version: str,
    reported_is_doubtful: bool = False,
) -> bool:
    """服务端派生「存疑」标记（D13、D14、T7）。

    客户端自报值只作保守参考：
    - 自报 true 保持 true，避免抹掉客户端基于动作幅度等信息的异常判定；
    - 自报 false 不构成证据，有效帧占比缺失或低于阈值时仍标记存疑；
    - 存疑只标记、不阻断打卡。
    """
    if reported_is_doubtful:
        return True
    if valid_frame_ratio is None:
        return True
    return valid_frame_ratio < valid_frame_ratio_threshold(threshold_config_version)

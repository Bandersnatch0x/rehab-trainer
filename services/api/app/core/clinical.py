"""临床配置占位。

TODO: 待临床签核（C4/C5）。以下分界值与预警阈值一律留空（None），
临床签核前不得据此输出诊断结论、风险等级或触发预警。
工程侧不得在本文件擅自填入数值。
"""

from typing import Any

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

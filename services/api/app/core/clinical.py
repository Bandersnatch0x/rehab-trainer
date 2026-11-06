"""临床配置：文献预设值（待临床签核）。

状态：literature_preset_pending_clinical_signoff
来源与证据：docs/clinical/literature-presets.md（逐条列出 DOI/URL、适用人群与证据强度）

重要边界：
- 这些数值是按文献/指南预设的工程默认值，**不是临床结论**，不得用于诊断。
- 自动预警生成（AUTO_ALERTS_ENABLED）与量表风险分级（RISK_LEVELS_ENABLED）
  在签核前保持关闭；签核后须替换本表、更新版本号并开启开关。
- 训练有效性「存疑」标记的阈值机制（未决项 12）允许使用明确标注的占位值；
  该值只参与存疑标记，不阻断打卡，也不输出诊断、风险等级或预警。
"""

from typing import Any

CLINICAL_PRESET_VERSION = "literature-2026.11"
CLINICAL_PRESET_STATUS = "literature_preset_pending_clinical_signoff"
CLINICAL_PRESET_SOURCES = "docs/clinical/literature-presets.md"

# 签核前不得自动生成预警或风险等级。
AUTO_ALERTS_ENABLED = False
RISK_LEVELS_ENABLED = False

# 未决项 12：训练有效性阈值待临床签核（数据质量阈值，不是临床分界）。
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

# 量表分界：文献预设（C4），签核前不参与风险分级。
# unit 说明分值语义；review_trigger 仅用于提示治疗师复核，不是诊断分界。
ASSESSMENT_CUTOFFS: dict[str, dict[str, Any]] = {
    "HY_SELF": {
        "cutoff": None,
        "unit": "H-Y stage",
        "review_trigger": 3,
        "confidence": "low",
        "source": "MDS Task Force 2004 (doi:10.1002/mds.20213)：H-Y 为医生评定，无验证过的自评分界",
        "note": "自评 ≥3 仅触发治疗师复核；不得用于诊断、风险分级或训练准入。",
    },
    "SARC-F": {
        "cutoff": 4,
        "unit": "score_0_10",
        "confidence": "high",
        "source": "AWGS 2019 (doi:10.1016/j.jamda.2019.12.012)；Ida 2018 meta (doi:10.1016/j.jamda.2018.04.001)",
        "note": "阳性提示肌少症风险；敏感性低（meta Se 0.21），需进一步评估。",
    },
    "SARC-CalF": {
        "cutoff": 11,
        "unit": "score_0_20",
        "confidence": "medium",
        "source": "AWGS 2019 (doi:10.1016/j.jamda.2019.12.012)；Yang 2018 (doi:10.1016/j.jamda.2017.12.016)",
        "note": "中国人群敏感性约 41–61%；部分研究提出 ≥7 更敏感，但非指南值。",
    },
    "RIGIDITY_5": {
        "cutoff": None,
        "unit": "grade_1_5",
        "confidence": "high",
        "source": "无标准自评量表；MDS-UPDRS item 22 为医生评定 0–4 (doi:10.1002/mds.22340)",
        "note": "未找到可靠自评分界，保持 None；仅作随访趋势。",
    },
    "STEADI": {
        "cutoff": 1,
        "unit": "yes_count_0_3",
        "rule": "any_yes",
        "confidence": "high",
        "source": "CDC STEADI Algorithm 2019 (https://www.cdc.gov/steadi/media/pdfs/STEADI-Algorithm-508.pdf)",
        "note": "任一关键问题答 yes 即视为跌倒风险；不是计分阈值，勿套用 12 项版的 ≥4。",
    },
    "Short FES-I": {
        "cutoff": 9,
        "unit": "score_7_28",
        "confidence": "medium",
        "source": "FES-I 官方分档 (https://sites.manchester.ac.uk/fes-i)：7–8 低、9–13 中、14–28 高；Delbaere 2010 (doi:10.1093/ageing/afp225)",
        "note": "取 ≥9 作为「中度及以上担忧」提示；若只关注高担忧应改为 14。",
    },
    "TUG": {
        "cutoff": 11.5,
        "unit": "seconds",
        "population": "parkinson",
        "confidence": "medium",
        "source": "Nocera 2013 (doi:10.1016/j.apmr.2013.02.020)：PD 专用 Se 0.66/Sp 0.62/AUC 0.69；社区通用 CDC 为 ≥12 s",
        "note": "PD 专用切点；不应单独用于跌倒风险判定。",
    },
}

# 预警阈值：文献预设（C5），签核前自动预警保持关闭。
# 各字段语义：
# - threshold/delta_threshold：绝对阈值 / 较基线变化阈值
# - min_days_in_window：窗口内至少满足天数
# - threshold_with_injury / injury_override：伴受伤时的单独升级规则
ALERT_RULES: dict[str, dict[str, Any]] = {
    "PAIN_PERSISTENT": {
        "threshold": 4,
        "delta_threshold": 2,
        "window_days": 7,
        "min_days_in_window": 4,
        "unit": "NRS_0_10",
        "confidence": "low",
        "source": "Farrar 2001 (doi:10.1016/S0304-3959(01)00349-9)；Cheung 2003 (doi:10.2165/00007256-200333020-00005)；7 天窗口为工程保守默认",
        "note": "NRS ≥4 为常用中度界值，非指南停止阈值；7 天内 ≥4 天或连续 7 天。",
    },
    "RIGIDITY_WORSENING": {
        "threshold": 1,
        "scale": "RIGIDITY_5",
        "baseline_days": 7,
        "window_days": 14,
        "confidence": "low",
        "source": "Goetz 2008 MDS-UPDRS (doi:10.1002/mds.22340)；单项 MCID 未找到，14 天窗口为工程默认",
        "note": "按 5 级自评升高 ≥1 级；若改用 0–10 自评则为 ≥2 分；须记录 ON/OFF 状态。",
    },
    "CRAMP_RECURRENT": {
        "threshold_minutes": 10,
        "occurrences_per_week": 2,
        "window_days": 14,
        "injury_override": True,
        "confidence": "low",
        "source": "Parkinson's UK 痉挛科普；Hallegraeff 2017 (doi:10.1186/s12875-017-0600-x)；频次阈值借自夜间腿痉挛 RCT 入组标准",
        "note": "单次 >10 分钟或 14 天内 ≥4 次；任何导致跌倒/受伤的痉挛单独升级；须区分 OFF 期肌张力障碍。",
    },
    "FALL_RECURRENT": {
        "threshold": 2,
        "window_days": 365,
        "threshold_with_injury": 1,
        "confidence": "high",
        "source": "CDC STEADI 2017 三级算法；World Falls Guidelines 2022 (doi:10.1093/ageing/afac205)；Pickering 2007 (doi:10.1002/mds.21598)",
        "note": "过去 12 个月 ≥2 次，或 1 次伴受伤；单次无伤作为中风险评估而非高危预警。",
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
from pathlib import Path

from app.core import clinical

ROOT = Path(__file__).resolve().parents[3]


def test_presets_are_explicitly_pending_signoff():
    assert clinical.CLINICAL_PRESET_STATUS == "literature_preset_pending_clinical_signoff"
    assert clinical.CLINICAL_PRESET_VERSION
    # 签核前不得开启自动预警或风险分级。
    assert clinical.AUTO_ALERTS_ENABLED is False
    assert clinical.RISK_LEVELS_ENABLED is False

    doc = ROOT / clinical.CLINICAL_PRESET_SOURCES
    assert doc.is_file(), "预设来源文档缺失"
    assert clinical.CLINICAL_PRESET_VERSION in doc.read_text(encoding="utf-8")


def test_assessment_cutoffs_have_values_or_explicit_no_cutoff():
    expected = {
        "HY_SELF": None,
        "SARC-F": 4,
        "SARC-CalF": 11,
        "RIGIDITY_5": None,
        "STEADI": 1,
        "Short FES-I": 9,
        "TUG": 11.5,
    }
    assert set(clinical.ASSESSMENT_CUTOFFS) == set(expected)
    for instrument, value in expected.items():
        entry = clinical.ASSESSMENT_CUTOFFS[instrument]
        assert entry["cutoff"] == value, instrument
        assert entry.get("source"), f"{instrument} 缺少来源"
        assert entry.get("confidence") in {"high", "medium", "low"}, instrument
        if value is not None:
            assert entry.get("unit"), f"{instrument} 缺少单位"

    # STEADI 是任一 yes 规则，不是 12 项版的 ≥4 分。
    assert clinical.ASSESSMENT_CUTOFFS["STEADI"]["rule"] == "any_yes"
    # H-Y 自评只触发复核，不是诊断分界。
    assert clinical.ASSESSMENT_CUTOFFS["HY_SELF"]["review_trigger"] == 3


def test_alert_rules_are_filled_and_sourced_but_disabled():
    expected = {
        "PAIN_PERSISTENT": ("threshold", 4),
        "RIGIDITY_WORSENING": ("threshold", 1),
        "CRAMP_RECURRENT": ("threshold_minutes", 10),
        "FALL_RECURRENT": ("threshold", 2),
    }
    assert set(clinical.ALERT_RULES) == set(expected)

    for rule, (field, value) in expected.items():
        entry = clinical.ALERT_RULES[rule]
        assert entry[field] == value, rule
        assert entry.get("source"), f"{rule} 缺少来源"
        assert entry.get("confidence") in {"high", "medium", "low"}, rule
        assert entry.get("window_days"), f"{rule} 缺少时间窗"

    assert clinical.ALERT_RULES["PAIN_PERSISTENT"]["delta_threshold"] == 2
    assert clinical.ALERT_RULES["FALL_RECURRENT"]["threshold_with_injury"] == 1
    assert clinical.ALERT_RULES["CRAMP_RECURRENT"]["injury_override"] is True
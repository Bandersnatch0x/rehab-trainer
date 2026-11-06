---
id: "0015"
type: "task"
status: closed
assignee: "claude"
part-of: D30
created: 2026-11-06
---

# 临床阈值文献预设（待签核）

目标：按公开文献/指南为量表分界、预警阈值、RAS 节拍与运动剂量给出可追溯的预设值，
使系统在没有临床签核的情况下也能运行，同时保持自动预警与风险分级关闭。

范围：`docs/clinical/literature-presets.md`、`services/api/app/core/clinical.py`、
Android 默认 BPM 文案、决策 D30、验收文档。不改契约、不改预警生成逻辑（本轮无生成器）。

## Log

### 2026-11-06 调研与落配置

- 三路并行文献调研：量表分界、预警阈值、RAS/运动剂量；关键引用（AWGS 2019、Delbaere 2010、Nocera 2013、Chang 2019）经二次检索核对。
- 关键纠正：Short FES-I 采用官方分档 ≥9（中度及以上），不采用来源不明的「≥11 = full >23」；CDC STEADI 三级分类标注为 2017 版；TUG 13.5 s 不作 PD 阈值。
- `ASSESSMENT_CUTOFFS`：SARC-F 4、SARC-CalF 11、Short FES-I 9、STEADI 任一 yes、TUG 11.5 s；HY_SELF 仅 ≥3 复核、RIGIDITY_5 不设界。
- `ALERT_RULES`：疼痛 NRS ≥4/Δ≥2（7 天）、僵直 ≥1 级（基线 7 天/持续 14 天）、痉挛 >10 分钟或 2 次/周、跌倒 ≥2 次/年或 1 次伴伤。
- `AUTO_ALERTS_ENABLED` 与 `RISK_LEVELS_ENABLED` 保持 `False`；新增 `test_clinical_presets.py` 3 项守护。
- Android 默认 BPM 64 → 100（90–110），注释标明文献预设与个体化规则。
- 验收报告、README、决策 D30 同步；签核清单见 `docs/clinical/literature-presets.md` 第 7 节。
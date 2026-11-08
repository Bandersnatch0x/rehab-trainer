---
id: "0039"
type: "task"
status: "done"
assignee: "claude"
part-of: null
created: 2026-11-08
---

# 三个 D 级预警阈值的文献补强（P1 遗留）

## 背景

`docs/clinical/literature-presets.md` 第 3 节里，4 条预警规则有 **3 条标 D 级**（工程保守
默认）：疼痛、僵直、痉挛。签核人看到「工程默认」四个字无法判断该不该签字——不知道是
「文献没查」还是「文献真没有」。

## 做了什么

对三项做定向检索，结论**写回文档第 3.2 节**，并把代码 `clinical.py` 的
`source` / `confidence` / `note` 同步。

### PAIN_PERSISTENT：low → medium

- **阈值**：NRS 轻-中度界值文献多落在 3–4（慢性肌骨痛，*Pain Res Manag* 2016, PMC5043012）；
- **窗口**：7 天内 ≥4 次日评的组内信度 **r=0.95**，显著优于单次评定（r=0.63）——
  「窗口内 ≥4 天」不是拍脑袋，是心理测量学依据（Jensen & McFarland 1993）。

### CRAMP_RECURRENT：low → medium

- **时长**：Parkinson's UK 明确「抽筋可持续至 10 分钟」；夜间腿痉挛诊断标准的系统综述中
  多项研究以「最长 10 分钟」为界定特征 → **>10 分钟属超出典型范围**（PMC5330021）；
- **频次**：NLC 诊断标准普遍以「≥1 次/周」为界；2024 年 RCT 抽筋人群均值 2.6–2.7 次/周
  （Tan, *JAMA Intern Med* 2024）→ 取 ≥2 次/周约对应该人群均值。

### RIGIDITY_WORSENING：维持 low

检索**确认无** MDS-UPDRS 僵硬单项 MCID/反应度研究：

- Shulman 2010 的临床重要差异是**总分层面**，不可拆分到条目；
- MDS 2023 摘要研究显示 rigidity 组间区分度差（balanced accuracy 0.335–0.469），单项
  评分本身噪声大。

**结论：维持「不设分界、仅作随访趋势」**，14 天窗口保留为工程默认并明确标注。
这一项没有可升级的证据，也不应假装有。

## 关键限制（已写入文档第 3.3 节）

升级的两项证据**均来自非 PD 人群**。签核人须明确回答：

1. 是否接受将非 PD 人群阈值外推至 PD？
2. 若不接受，改保守界值还是改为「仅提示、不预警」？
3. 僵硬项是否同意不设自动分界、只做趋势？

## 未做

- **未开启任何开关**。`AUTO_ALERTS_ENABLED` / `RISK_LEVELS_ENABLED` 仍为 `False`；
- 未替临床医生做外推决定——只补证据，不代替签核。

## 验证

`services/api/tests/test_clinical_presets.py` 3 项通过（该测试只断言 `confidence` 取值域
为 high/medium/low，改为 medium 不影响）。

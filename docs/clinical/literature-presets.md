# 临床阈值文献预设（待签核）

- 状态：**文献预设，待临床签核**（`literature-2026.11`）
- 生成日期：2026-11-06
- 对应配置：`services/api/app/core/clinical.py`
- 适用系统：帕金森病肌肉保护训练系统（H-Y 1–4 进入训练，5 级仅宣教）

> 本文不是临床指南，也不构成诊断或处方依据。所有数值由工程侧按公开文献/指南预设，
> 目的是让系统具备可运行的默认值；**必须由康复医师或康复治疗师逐条签核后**才能
> 开启自动预警与风险分级。签核前 `AUTO_ALERTS_ENABLED` 与 `RISK_LEVELS_ENABLED` 均为 `False`。

## 1. 证据分级口径

| 级别 | 含义 |
| --- | --- |
| A | 正式指南/学会立场声明，强推荐或高质量证据 |
| B | 系统综述/Meta 分析，结论一致但异质性较大 |
| C | 单项 RCT 或小样本方案研究 |
| D | 专家意见、工程保守默认或病例经验 |

## 2. 量表分界（C4）

| 量表 | 预设 | 证据 | 说明 |
| --- | --- | --- | --- |
| SARC-F | ≥4 / 10 | A | AWGS 2019 共识；meta 敏感性约 0.21、特异性约 0.90，阳性需进一步评估 |
| SARC-CalF | ≥11 / 20 | B | AWGS 2019 共识；中国社区敏感性约 41–61%，部分研究提出 ≥7 但非指南值 |
| Short FES-I | ≥9 / 28 | B | FES-I 官方分档 7–8 低、9–13 中、14–28 高；本文取「中度及以上」提示，若只关注高担忧改为 14 |
| STEADI 3 问 | 任一 yes | A | CDC STEADI 2019 规则；**不是** 12 项版的 ≥4 分 |
| TUG | ≥11.5 s | B | Nocera 2013 PD 专用切点（Se 0.66 / Sp 0.62 / AUC 0.69）；社区通用 CDC 为 ≥12 s |
| HY_SELF | 无分界；自评 ≥3 触发复核 | D | H-Y 为医生评定，无验证过的自评分界；复核触发不是诊断或准入依据 |
| RIGIDITY_5 | 不设分界（None） | A（无标准自评） | MDS-UPDRS 僵硬条目为医生评定 0–4；无验证过的自评分界 |

### 2.1 关键纠正

- **Short FES-I ≥11 不等于 full FES-I ≥20**。官方分档为 Short 7–8 / 9–13 / 14–28；未采用来源不明的「≥11 = high concern」写法。
- **CDC STEADI 2019 已改为二分类**（at risk / not at risk）；「≥2 次高危、1 次中危」来自 2017 版三级算法，引用时必须标年份。
- **TUG 13.5 s 不是 PD 专用阈值**；PD 用 11.5 s，社区通用用 12 s。
- **HY_SELF 与 RIGIDITY_5 不应被当作诊断分界**；前者只做复核触发，后者保持 `None`。

## 3. 预警阈值（C5）

| 规则 | 预设 | 时间窗 | 证据 |
| --- | --- | --- | --- |
| PAIN_PERSISTENT | NRS ≥4，或较基线升高 ≥2；窗口内 ≥4 天 | 7 天 | D（NRS 4 与 ≥2 有间接证据；7 天窗口为工程默认） |
| RIGIDITY_WORSENING | 5 级自评升高 ≥1 级（0–10 自评则 ≥2 分） | 基线 7 天，持续 14 天 | D（无单项 MCID；14 天为工程默认） |
| CRAMP_RECURRENT | 单次 >10 分钟，或 ≥2 次/周且 14 天内 ≥4 次 | 14 天 | D（时长有描述性证据；频次借自夜间腿痉挛 RCT 入组标准） |
| FALL_RECURRENT | 12 个月 ≥2 次，或 1 次伴受伤 | 365 天 | A/B（CDC 2017、World Falls Guidelines 2022、PD 队列） |

### 3.1 关键纠正

- **NRS ≥4 是常用「中度疼痛」界值，不是运动指南的停止阈值**。运动中的停止规则另见第 5 节。
- **未找到 rigidity 单项 MCID**；MDS-UPDRS Part III 总分 MCWP 4.4–4.6 不能拆分到单个僵硬条目。
- **未找到 PD 专属痉挛频率阈值**；频率数字来自夜间腿痉挛 RCT，属工程默认。
- **单次无伤跌倒不应忽略**：对 PD 患者按中风险评估并转介，不作为高危预警。

## 4. RAS 节拍与运动剂量

### 4.1 节拍语义

现有 Android 默认 64 BPM 存在歧义：

| 解释 | 实际步频 | 判断 |
| --- | --- | --- |
| 1 拍 = 1 步 | 64 步/分 | 对轻中度 PD 偏慢 |
| 1 拍 = 2 步（一个步态周期） | 约 128 步/分 | 偏快端 |
| 无固定映射 | 不可计算 | 不能作为临床预设 |

工程处理：把 BPM 明确定义为 **步/分（steps/min）**，1 拍 = 1 步；默认 **100**，起始范围 **90–110**。
若保留音乐 BPM，必须另存 `steps_per_beat` 与 `target_steps_per_min`，UI 同时显示两者。

### 4.2 个体化与进阶

1. 10 米自然步行 3 次，计算平均步频（步/分）。
2. 步行起始 = 100% 基线；虚弱者可从 90% 起步。
3. 原地踏步 = 110% 基线（Chang 2019：10 组 × 50 步，110% 平常步频）。
4. 每 2–4 周、RPE ≤13、无停止规则症状时 +5%，上限 120%。
5. 若步长明显缩短或冻结增加，回退 5% 或改用慢节拍（85–90% 基线）。

证据：RAS 干预本身为 A 级（APTA 2022 强推荐）；**具体 ±10%/110% 剂量为 C 级**（McIntosh 1997、Chang 2019、Ghai 2018 Meta）。固定 60–80 步/分未找到可靠来源，且 Nombela 2013 警告过慢固定节拍可能缩短步长。

### 4.3 训练剂量

| 类型 | 预设 | 证据 |
| --- | --- | --- |
| 有氧/步态 | 20–30 分钟/次，每周 3 次 | B/C（Ghai 2018；de Bruin 2010） |
| 抗阻 | 每周 2–3 次非连续日；起始 1 组 10–15 次，目标 2–3 组 8–12 次；起始 40–60% 1RM，目标 60–70% 1RM；无 1RM 条件用 RPE 12–14 | A（ACSM 2009/2011；APTA 2022；Parkinson's Foundation/ACSM） |
| 牵伸 | 每周 2–3 次（可每日）；静态保持 15–30 s（老年人/PD 可 30–60 s），2–4 次，每肌群累计约 60 s；避免弹震 | A（ACSM 通用）；PD 特异性剂量 D（APTA 2022 证据低/弱） |

现有 App 条目剂量（3×12、2×10、2×8、3×10）均为演示值；其中抗阻 2×8 在 8–12 次范围内，牵伸 2×10 与「2–4 次保持」的语义不一致，签核时需统一「次」指动作次数还是保持次数。

## 5. 强度与停止规则

- RPE（Borg 6–20）：起始 11–12；有氧目标 12–13；抗阻目标 12–14；≥17 降强度或停止。
- 说话测试：不能说出完整短句即为强度过高。
- PD 可能心率反应迟钝、体位性低血压，优先 RPE + 症状 + 说话测试，不单独依赖心率公式。

**绝对停止（按急症处理）**：胸痛/胸闷或放射痛伴出汗恶心；严重呼吸困难；眩晕/濒晕/失衡；新发意识模糊、言语不清、单侧无力或视力突变；心悸/不规则心跳；冷汗、苍白、发绀、意识丧失；跌倒伴头部撞击或受伤。

**相对停止（休息后评估）**：中度气短、疲劳、腿抽筋、关节/肌肉疼痛；RPE ≥17；站立时头晕/眼前发黑；冻结发作经提示仍不缓解。

来源：AHA 2013（doi:10.1161/CIR.0b013e31829b5b44）、ACSM 指南、Parkinson's Foundation/ACSM、APTA 2022（doi:10.1093/ptj/pzab302）。AHA/ACSM 原始指征用于监督下运动试验，家庭 App 采用症状版保守规则。

## 6. 证据来源

1. Chen LK, et al. AWGS 2019 consensus update on sarcopenia. *J Am Med Dir Assoc.* 2020;21(3):300–307.e2. doi:10.1016/j.jamda.2019.12.012
2. Ida S, et al. SARC-F meta-analysis. *J Am Med Dir Assoc.* 2018;19(8):685–689. doi:10.1016/j.jamda.2018.04.001
3. Yang M, et al. SARC-F vs SARC-CalF in Chinese community. *J Am Med Dir Assoc.* 2018;19(3):277.e1–277.e8. doi:10.1016/j.jamda.2017.12.016
4. Delbaere K, et al. FES-I longitudinal validation. *Age Ageing.* 2010;39(2):210–216. doi:10.1093/ageing/afp225
5. FES-I official site. https://sites.manchester.ac.uk/fes-i
6. CDC STEADI Algorithm (2019). https://www.cdc.gov/steadi/media/pdfs/STEADI-Algorithm-508.pdf
7. Nocera JR, et al. TUG in PD. *Arch Phys Med Rehabil.* 2013;94(7):1300–1305. doi:10.1016/j.apmr.2013.02.020
8. Goetz CG, et al. MDS-UPDRS. *Mov Disord.* 2008;23(15):2129–2170. doi:10.1002/mds.22340
9. Goetz CG, et al. MDS Task Force on H-Y. *Mov Disord.* 2004;19(9):1020–1028. doi:10.1002/mds.20213
10. Montero-Odasso M, et al. World Falls Guidelines 2022. *Age Ageing.* 2022;51(9):afac205. doi:10.1093/ageing/afac205
11. Pickering RM, et al. Falls in PD meta-analysis. *Mov Disord.* 2007;22(13):1892–1900. doi:10.1002/mds.21598
12. Farrar JT, et al. Pain NRS change. *Pain.* 2001;94(2):149–158. doi:10.1016/S0304-3959(01)00349-9
13. Cheung K, et al. DOMS. *Sports Med.* 2003;33(2):145–164. doi:10.2165/00007256-200333020-00005
14. McIntosh GC, et al. RAS gait facilitation. *J Neurol Neurosurg Psychiatry.* 1997;62(1):22–26. doi:10.1136/jnnp.62.1.22
15. Chang HY, et al. RAS stepping in place. *Medicine.* 2019;98(45):e17874. doi:10.1097/MD.0000000000017874
16. Ghai S, et al. Rhythmic cueing meta-analysis. *Sci Rep.* 2018;8:506. doi:10.1038/s41598-017-16232-5
17. Thaut MH, et al. RAS for falls in PD. *Clin Rehabil.* 2019;33(1):34–43. doi:10.1177/0269215518788615
18. de Bruin N, et al. Walking with music in PD. *Parkinsons Dis.* 2010;2010:483530. doi:10.4061/2010/483530
19. Nombela C, et al. Rhythm in PD. *Neurosci Biobehav Rev.* 2013;37(10 Pt 2):2564–2570. doi:10.1016/j.neubiorev.2013.08.003
20. Osborne JA, et al. APTA PD clinical practice guideline. *Phys Ther.* 2022;102(4):pzab302. doi:10.1093/ptj/pzab302
21. Chodzko-Zajko WJ, et al. ACSM exercise for older adults. *Med Sci Sports Exerc.* 2009;41(7):1510–1530. doi:10.1249/MSS.0b013e3181a0c95c
22. Garber CE, et al. ACSM quantity and quality of exercise. *Med Sci Sports Exerc.* 2011;43(7):1334–1359. doi:10.1249/MSS.0b013e318213fefb
23. Fletcher GF, et al. AHA exercise standards. *Circulation.* 2013;128(8):873–934. doi:10.1161/CIR.0b013e31829b5b44
24. Penko AL, et al. Borg RPE validity in PD. *Int J Exerc Sci.* 2017;10(1):76–86.

## 7. 签核清单

临床签核人需逐项确认或修改，并在下表签字/注明日期：

| 项 | 当前预设 | 签核结果 |
| --- | --- | --- |
| SARC-F / SARC-CalF 分界 | 4 / 11 | |
| Short FES-I 分界 | 9（中度及以上） | |
| STEADI 规则 | 任一 yes | |
| TUG 分界 | 11.5 s（PD 专用） | |
| HY_SELF 复核触发 | 自评 ≥3 | |
| RIGIDITY_5 是否设分界 | 不设 | |
| 疼痛预警阈值与窗口 | NRS ≥4 / Δ≥2，7 天 | |
| 僵硬预警阈值与窗口 | ≥1 级，基线 7 天/持续 14 天 | |
| 痉挛预警阈值与窗口 | >10 分钟或 2 次/周，14 天 | |
| 跌倒预警阈值与窗口 | ≥2 次/12 月，或 1 次伴伤 | |
| RAS 默认 BPM 与个体化规则 | 100（90–110）；SIP 110% 基线 | |
| 抗阻/牵伸剂量 | 见 4.3 | |
| RPE 与停止规则 | 见第 5 节 | |
| 是否开启自动预警/风险分级 | 签核前均关闭 | |

签核后动作：

1. 在 `clinical.py` 更新数值与 `CLINICAL_PRESET_VERSION`（例如 `signed-2026.12`）。
2. 将 `AUTO_ALERTS_ENABLED` / `RISK_LEVELS_ENABLED` 改为 `True` 的实现随票上线。
3. 更新 `docs/delivery/acceptance-report.md` 的「临床待签核清单」与验收证据。
4. 保留本文作为数值来源与版本追溯。
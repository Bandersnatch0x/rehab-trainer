# 训练条目数据模板

本目录存放训练条目的骨架数据，供内容团队与临床团队填充。

- 数据文件：`exercises.example.json`
- 校验脚本：`../scripts/validate_exercises.py`
- 契约真源：`packages/api-contract/openapi.yaml` 的 `Exercise` / `ExerciseVariant` / `Dosage`
- 领域语义：`docs/ontology/rehab.ttl` 的 `rt:Exercise` 及相关类与属性
- 范围依据：决策 `C1`（MVP 24 条 = 4 类 × 6，每条 2–3 个难度变体）

> 本文件是**模板**，不是生产种子数据。所有条目当前均为 `draft`，不得用于处方；
> 临床字段全部是待签核占位，工程侧没有填入任何禁忌、危险信号、剂量或适用分级。

## 一、骨架现状

| 项 | 当前值 |
| --- | --- |
| 训练条目 | 24 条 |
| 训练大类 | 4 类 × 6 条 |
| 编码 | `STR-001`–`STR-006`、`STG-001`–`STG-006`、`GAIT-001`–`GAIT-006`、`POST-001`–`POST-006` |
| 难度变体 | 每条 2 个（`-V1` 初级、`-V2` 中级），共 48 个 |
| 发布状态 | 全部 `draft` |
| 临床状态 | `pending`（见文件 `_meta.clinicalStatus`） |

训练大类与编码前缀的固定对应：

| 训练大类（术语表） | 契约枚举值 | 编码前缀 | 本体个体 |
| --- | --- | --- | --- |
| 牵伸放松 | `stretching-relaxation` | `STR` | `rt:catStretchingRelaxation` |
| 肌力维持 | `strength-maintenance` | `STG` | `rt:catStrengthMaintenance` |
| 节律步态 | `rhythmic-gait` | `GAIT` | `rt:catRhythmicGait` |
| 姿势矫正 | `postural-correction` | `POST` | `rt:catPosturalCorrection` |

## 二、字段逐项说明

### 顶层结构

| JSON 字段 | 契约字段 | 本体对应 | 说明 |
| --- | --- | --- | --- |
| `items` | `ExercisePage.items` | — | 训练条目数组；本文件按契约的列表响应结构组织 |
| `_meta` | 无（契约外） | — | 模板元信息：契约指针、范围、临床状态；不会作为 API 响应发送 |
| `_todo`（条目内） | 无（契约外） | — | 临床签核台账：逐项标记尚未签核的字段；签核完成后删除 |

`_todo` 不是契约字段，但当前 JSON 结构允许附加属性，且它是内容治理的必要标记。
骨架模式下，校验脚本会检查其中五个键都存在且以 `TODO: 待临床签核` 开头。

### `Exercise` 字段

下表「必填」按契约标注；模板的 Pydantic 模型要求表中所有字段都存在，
契约可选的字段也不能省略（可保留空数组、空对象或占位值）。

| JSON 字段 | 契约字段 | 本体对应 | 当前骨架值 | 填写与签核 |
| --- | --- | --- | --- | --- |
| `code` | `Exercise.code`（必填） | `rt:exerciseCode`（`rt:Exercise`，函数属性） | 大类前缀 + 三位序号 | 内容团队按编码规则分配；发布后不复用、不改名 |
| `label` | `Exercise.label`（必填） | `rdfs:label` | 动作类型中文名 | 内容团队填写；**只写动作类型**，不写剂量、频次或禁忌 |
| `category` | `Exercise.category`（必填） | `rt:inCategory` → `rt:ExerciseCategory` | 四类之一 | 固定四类，不新增（本体 `owl:oneOf` 与 SHACL 均约束） |
| `publicationStatus` | `Exercise.publicationStatus`（必填） | `rt:hasPublicationStatus` → `rt:Draft/Published/Retired` | `draft` | 康复治疗师签核并完成素材后才能改 `published`；契约规定处方只能引用已发布条目 |
| `riskLevel` | `Exercise.riskLevel` | `rt:hasRiskLevel` → `rt:LowRisk/MediumRisk/HighRisk` | 按支撑方式的工程占位 | **待临床复核**；影响 D11 高风险训练准入，不能由内容团队单独决定 |
| `applicableStages` | `Exercise.applicableStages` | `rt:applicableToStage` → `rt:HYStage`（SHACL 限定 `HYStage1`–`HYStage4`） | 空数组 | **必须临床签核**；5 级仅宣教与转诊，禁止出现（D5）。空数组表示「尚未裁决」，不是「不适用任何分级」 |
| `bodyParts` | `Exercise.bodyParts` | `rt:targetsBodyPart` → `rt:BodyPart` | 本体词汇表中的中文名 | 内容团队填写；取值限 `上肢`、`下肢`、`躯干`、`颈部`、`全身` |
| `joints` | `Exercise.joints` | `rt:targetsJoint` → `rt:Joint` | 本体词汇表中的中文名 | 内容团队填写；取值限 `肩关节`、`肘关节`、`腕关节`、`髋关节`、`膝关节`、`踝关节`、`脊柱` |
| `contraindications` | `Exercise.contraindications`（契约要求至少一条） | `rt:contraindicatedFor`；细化为 `rt:contraindicatedForSymptom`（症状）与 `rt:contraindicatedForCondition`（规则条件） | `["TODO: 待临床签核"]` | **必须临床签核**；SHACL `ExerciseSafetyShape` 要求至少一条 |
| `dangerSignals` | `Exercise.dangerSignals` | `rt:hasDangerSignal` → `rt:DangerSignal` | `["TODO: 待临床签核"]` | **必须临床签核**；含急症红 flag 与升级路径 |
| `variants` | `Exercise.variants` | `rt:hasVariant` / 反向 `rt:variantOf`；类 `rt:DifficultyVariant` | 2 个变体 | 临床确认难度分层；C1 允许每条 2–3 个，当前骨架固定 2 个 |
| `defaultDosage` | `Exercise.defaultDosage` | `rt:hasDefaultDosage`（函数属性；定义域含 `Exercise`） | `{}` | **必须临床签核**；空对象表示尚未给出剂量 |
| `videoUri` | `Exercise.videoUri`（URI） | 本体无对应属性 | `.invalid` 占位 URI | 内容团队拍摄后替换为真实 COS/CDN 地址；**逐条视频动作正确性须临床签字** |
| `audioUri` | `Exercise.audioUri`（URI） | 本体无对应属性 | `.invalid` 占位 URI | 节拍音频按 C1 程序生成；BPM 参数须临床签核 |

### `ExerciseVariant` 字段

| JSON 字段 | 契约字段 | 本体对应 | 当前骨架值 | 填写与签核 |
| --- | --- | --- | --- | --- |
| `variants[].code` | `ExerciseVariant.code`（必填） | `rt:variantCode` | `条目编码-V1/V2` | 内容团队按规则分配；不得与条目编码重复 |
| `variants[].level` | `ExerciseVariant.level`（必填） | `rt:variantLevel`（1–3） | `1` 或 `2` | 临床确认难度级别；C1 允许 1–3 |
| `variants[].supportLevel` | `ExerciseVariant.supportLevel` | `rt:hasSupportLevel`（本体挂在 `rt:Movement` 上） | `seated` / `supported-standing` / `unsupported-standing` | 内容团队提出，临床确认；H-Y 3–4 不得使用无支撑站立（SHACL `Stage34NoUnsupportedStandingShape`） |
| `variants[].applicableStages` | `ExerciseVariant.applicableStages` | `rt:applicableToStage`（定义域含 `DifficultyVariant`） | 空数组 | **必须临床签核**；同样禁止 5 级 |
| `variants[].defaultDosage` | `ExerciseVariant.defaultDosage` | `rt:hasDefaultDosage`（定义域含 `DifficultyVariant`） | `{}` | **必须临床签核**；变体剂量可覆盖条目默认剂量 |

### `Dosage` 字段

| JSON 字段 | 契约字段 | 本体对应 | 说明 |
| --- | --- | --- | --- |
| `defaultDosage.sets` | `Dosage.sets` | `rt:sets` | 组数；签核前留空 |
| `defaultDosage.repetitions` | `Dosage.repetitions` | `rt:repetitions` | 每组次数；签核前留空 |
| `defaultDosage.bpm` | `Dosage.bpm` | `rt:bpm` | 节拍；按 C3 由基线步频导出，个体化规则须临床签核 |
| `defaultDosage.durationMinutes` | `Dosage.durationMinutes` | `rt:durationMinutes` | 时长（分钟）；签核前留空 |
| `defaultDosage.frequencyPerWeek` | `Dosage.frequencyPerWeek` | `rt:frequencyPerWeek` | 每周频次；签核前留空 |
| `defaultDosage.restSeconds` | `Dosage.restSeconds` | `rt:restSeconds` | 组间休息（秒）；签核前留空 |

### 契约与本体的一处建模差异

契约把 `supportLevel` 放在 `ExerciseVariant` 上；本体把 `rt:hasSupportLevel` 放在
`rt:Movement` 上。落 RDF 时，应为每个变体生成对应的 `rt:Movement` 节点并挂载支撑方式，
不能把 `supportLevel` 直接写成 `rt:DifficultyVariant` 的属性。本条差异不改变 JSON 结构。

## 三、内容团队填写指南

### 3.1 谁填什么

1. **内容团队**：`label`、`bodyParts`、`joints`、`videoUri`、`audioUri`，以及提出 `variants[].supportLevel` 建议。
2. **康复医师 / 康复治疗师（签字）**：`contraindications`、`dangerSignals`、`applicableStages`
   （条目与变体）、`defaultDosage`（含 `bpm`）、`riskLevel`、`variants[].level` 与
   `variants[].supportLevel` 的最终确认、逐条视频动作正确性。
3. **发布执行**：临床签字与素材齐备后，由具备发布权限的账号把 `publicationStatus`
   从 `draft` 改为 `published`；在此之前处方不得引用该条目。

### 3.2 必须临床签字的内容

依据决策文档「临床签核要求」，以下字段不得由工程或内容侧自行拍板：

- 每条训练条目的**禁忌**与**危险信号**；
- **H-Y 归属与映射**，即 `applicableStages`；
- **剂量与进阶退阶规则**，即 `defaultDosage` 与 `variants[].level`；
- **BPM 个体化规则**，即 `defaultDosage.bpm`；
- **高风险准入**相关的 `riskLevel`；
- **逐条视频动作正确性**，即 `videoUri` 指向的素材内容；
- 5 级仅宣教边界与急症红 flag 升级路径。

`riskLevel` 当前值只是工程占位，按最高变体支撑方式推导：
`seated → low`、`supported-standing → medium`、`unsupported-standing → high`。
这不是临床结论，必须由临床团队复核后替换或确认。

### 3.3 占位符含义

| 占位 | 含义 | 下游影响 |
| --- | --- | --- |
| `"TODO: 待临床签核"` | 该字段尚未签核 | 不得用于处方或训练准入 |
| `applicableStages: []` | 尚未裁决适用分级 | 不能据此判断任何分级可用 |
| `defaultDosage: {}` | 尚未给出剂量 | 不能据此生成处方 |
| `publicationStatus: "draft"` | 未发布 | 契约禁止处方引用未发布条目 |
| `*.invalid` URI | 素材尚未交付 | 播放器不得把它当作真实地址 |
| `_todo` | 签核台账 | 签核完成前不得删除；签核完成后可删除，`--allow-filled` 允许缺失 |

## 四、如何新增一条训练条目

1. **选大类与前缀**：按上表确定 `category` 与编码前缀。
2. **分配编码**：MVP 阶段每个大类固定 6 条（`001`–`006`）。扩展时使用下一个序号
   （如 `STR-007`），并同步修改 `validate_exercises.py` 顶部的
   `EXPECTED_TOTAL` 与 `EXPECTED_PER_CATEGORY`；脚本会按这两个常量推导应有编码。
3. **复制骨架块**：从 `exercises.example.json` 复制一个同类条目，替换 `code`、
   `label`、`bodyParts`、`joints` 与 `variants` 编码。当前骨架固定 2 个变体
   （V1/V2）；若按 C1 扩展为 3 个，需同步修改 `EXPECTED_VARIANTS` 与脚本中的
   `expected_variants` 构造。
4. **只写动作类型**：`label` 不写组数、次数、时长、BPM 或禁忌；这些属于临床剂量层。
5. **保持临床字段占位**：新条目的 `contraindications`、`dangerSignals` 继续写
   `"TODO: 待临床签核"`；`applicableStages`、`defaultDosage` 保持空数组/空对象，
   `_todo` 五个键齐全，`publicationStatus` 保持 `draft`。
6. **交付素材**：拍摄视频、生成节拍音频后替换 `videoUri` / `audioUri`；
   在此之前保留 `.invalid` 占位。
7. **临床签核**：按 3.2 节完成签字，填写临床字段；签核记录应能对应到条目编码与版本。
8. **运行校验**：

   ```bash
   python services/api/scripts/validate_exercises.py
   # 临床字段已填写后：
   python services/api/scripts/validate_exercises.py --allow-filled
   ```

   骨架模式会检查：24 条、4 类各 6 条、编码连续、每条 2 个变体、每条至少一个禁忌占位、
   每条至少一个危险信号占位、任何 `applicableStages` 都不含 5 级、临床字段仍为占位、
   发布状态仍为 `draft`。
   已填写模式跳过占位检查，但仍检查结构、编码、变体与「不得出现 H-Y 5 级」。
   契约字段与枚举对齐检查需要 `PyYAML`；未安装时该项给出警告并跳过，不影响结构校验。
   脚本只依赖 `pydantic` 与可选的 `PyYAML`，不导入 `app/` 下的服务代码。
9. **更新映射表**：`applicableStages` 的最终内容进入「分期→条目映射表」，
   该表按 C2 做 semver 版本化并由治疗师签核；不要只改本 JSON 而不更新映射表版本。

## 五、边界

- 本目录只承载训练条目数据骨架；不改契约、本体、应用端或决策文档。
- 不编造临床内容：没有签核依据时，禁忌、危险信号、剂量、适用分级、风险等级一律保持占位。
- 术语使用 `.agent/keep/glossary.md` 的定义：训练条目、训练大类、难度变体、剂量、
  禁忌、危险信号、H-Y 分级、分期确认、处方、康复治疗师。

# Charter：rehab-trainer 架构基线

- 状态:草案
- 日期:2026-11-04
- 上位约束:`docs/ontology/rehab.ttl`(OWL 2)与 `docs/ontology/shapes.ttl`(SHACL),本文件的领域模型不得与本体冲突
- 决策来源:`.agent/keep/decisions/0001-需求基线与范围裁决.md`

## 1. 领域模型

实体名沿用 OWL 类名;数据库表名用 snake_case。关系名直接引用 OWL 对象属性。

### 1.1 组织与身份

| OWL 类 | 表 | 关键字段 | 关系 | 约束 |
| --- | --- | --- | --- | --- |
| `Organization` | `org` | `org_id`、`org_name` | — | D10 |
| `Role` 及其子类 `PatientRole`/`CaregiverRole`/`CareAdminRole`/`RehabTherapistRole` | `role_binding` | `role`、`org_id`、`patient_id?` | `affiliatedWithOrg`、`caresFor` | D4 |
| — | `account` | `account_id`、`display_name`、`status` | — | **本体缺口,见 §7** |
| — | `external_identity` | `provider`、`app_id`、`openid`、`unionid?` | — | T2,openid 仅作凭据 |

### 1.2 患者与分期

| OWL 类 | 表 | 关键字段 | 关系 | 约束 |
| --- | --- | --- | --- | --- |
| `Patient` | `patient` | `patient_id`、`org_id`、`status` | `belongsToOrg`、`hasHYStage`、`hasSymptom`、`hasFallRisk` | 一患者一机构(D10) |
| `HYStage` | 枚举列 | `stage_value`(1–5) | — | D5 |
| `StageConfirmation` | `stage_confirmation` | `status`、`confirmed_at` | `stageConfirmedBy` → `RehabTherapistRole` | D11 |
| `Assessment` 子类 `HYStageAssessment`/`FallRiskAssessment` | `assessment` | `instrument`、`score`、`timestamp` | `usesInstrument`、`assessesSymptom` | C4 |
| `AssessmentInstrument` | 枚举表 | `instrument_code` | — | C4 待签核 |

### 1.3 训练内容

| OWL 类 | 表 | 关键字段 | 关系 | 约束 |
| --- | --- | --- | --- | --- |
| `ExerciseCategory` | 枚举(固定 4 条) | `category_code` | — | 牵伸/肌力/节律步态/姿势矫正 |
| `Exercise` 子类 | `exercise` | `exercise_code`、`publication_status` | `inCategory`、`hasMovement`、`hasVariant`、`targetsBodyPart`、`targetsJoint`、`contraindicatedFor`、`hasDangerSignal`、`realizesIntervention` | 禁忌与危险信号必填(SHACL) |
| `Movement` | `movement` | `movement_code` | `involvesJoint`、`involvesBodyPart`、`usesLandmark`、`hasMovementPattern` | — |
| `DifficultyVariant` | `difficulty_variant` | `variant_code`、`variant_level`、`support_level` | `applicableToStage`、`variantOf` | H-Y 3–4 级禁无支撑站立 |
| `Dosage` | 嵌入 | `sets`、`repetitions`、`bpm`、`duration_minutes`、`frequency_per_week`、`rest_seconds` | — | BPM 待签核(C3) |
| `BodyPart`/`Joint`/`Landmark`/`MovementPattern` | 参考表 | code | — | `Landmark.landmark_index` 对应 33 点索引 |

### 1.4 风险与预警

| OWL 类 | 表 | 关键字段 | 关系 | 约束 |
| --- | --- | --- | --- | --- |
| `Symptom` 子类 `Rigidity`/`Pain`/`FreezingOfGait`/`Dizziness`/`Sarcopenia` | `symptom` | `symptom_code`、`severity` | `triggersAlert` | — |
| `Contraindication` | `contraindication` | `code`、`description` | — | 训练条目必填 |
| `DangerSignal` | `danger_signal` | `code`、`description` | — | 训练条目必填 |
| `AlertThreshold` | `alert_threshold` | `threshold_value`、`window` | — | 待签核(C5) |
| `AlertRule` | `alert_rule` | `rule_code`、`alert_level` | `hasTriggerSource` | C5 |
| `AlertEvent` | `alert_event` | `triggered_at`、`delivery_status` | `alertFor`、`triggeredByRule`、`deliveredVia` | D6 |
| `FallRisk`/`RiskLevel`/`AlertLevel`/`DeliveryChannel` | 枚举 | — | — | — |

### 1.5 处方与记录

| OWL 类 | 表 | 关键字段 | 关系 | 约束 |
| --- | --- | --- | --- | --- |
| `Prescription` | `prescription` | `issued_at`、`status`、`version` | `prescriptionFor`、`prescribedBy`、`referencesExercise`、`prescribedDosage` | 须治疗师签发(D12) |
| `SessionRecord` | `session_record` | 完成次数、错误次数、中断次数、有效帧占比、存疑标记、模型版本、模型 SHA-256、指标算法版本、阈值配置版本 | `recordsPrescription`、`recordedFor` | D14、T7 |
| `AssessmentRecord` | `assessment` | — | `recordsAssessment`、`recordedBy` | — |

## 2. 三端职责矩阵

| 功能 | Android App(手机+平板) | 微信小程序 | Web 后台 |
| --- | --- | --- | --- |
| 训练执行 | ✅ 实时 CV(端上) | ✅ 异步 CV(云端上传) | — |
| 训练记录 | ✅ | ✅ | ✅ |
| 症状自评 | ✅ | ✅ | — |
| 预警接收 | ✅ | ✅ 微信服务通知 | ✅ |
| 代操作切换 | — | ✅ | — |
| 分期确认 | — | — | ✅ 治疗师 |
| 处方开具/调整 | — | — | ✅ 治疗师 |
| 随访登记 | — | — | ✅ 照护管理员 |
| 权限管理 | — | — | ✅ |
| 离线视频 | ✅ 预缓存 | ⚠️ 受微信缓存上限约束(未决项 10) | — |

> 表中 ✅ 表示责任分工，不代表当前交付状态。小程序云端异步 CV 本轮未启动（D26），
> Android 端上实时 CV 只在训练页做动作计数；以 `docs/delivery/acceptance-report.md` 为准。

## 3. API 契约骨架

契约文件:`packages/api-contract/openapi.yaml`(已填充:15 路径 / 18 操作,OpenAPI 3.1)。

| 资源 | 端点 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| 认证 | `POST /auth/bind`、`POST /auth/refresh` | 绑定码 / openid | T2 |
| 组织 | `GET /orgs/{org_id}` | 内部账号 | D10 |
| 患者 | `GET/POST /patients`、`GET /patients/{id}` | 角色 + org 隔离 | D4、D10 |
| 分期 | `POST /patients/{id}/stage-confirmations` | 治疗师 | D11 |
| 评估 | `POST /patients/{id}/assessments` | 患者/照护者/治疗师 | C4 |
| 训练条目 | `GET /exercises`、`GET /exercises/{code}` | 全角色 | C1 |
| 处方 | `POST /prescriptions`、`PATCH /prescriptions/{id}` | 治疗师 | D12 |
| 训练记录 | `POST /sessions`(批量幂等)、`GET /sessions` | 患者/照护者 | D14、T7 |
| 预警 | `GET /alerts`、`PATCH /alerts/{id}` | 照护管理员/治疗师 | D6 |
| 内容清单 | `GET /content/manifest`、签名 URL | 全角色 | T3 |
| 审计 | `GET /audit-events`(本机构查询与 CSV 导出) | 照护管理员/治疗师 | 说明书第 5 节 |

## 4. 治疗师工作流

```
患者建档 → 分期录入
   ↓
无确认 ──→ 标记「疑似」,高风险条目不可用
   ↓
治疗师确认分期 → StageConfirmation(status=confirmed)
   ↓
系统按 H-Y + 症状修饰自动匹配基础方案
   ↓
治疗师增删条目 / 调剂量 → Prescription(version+1)
   ↓
签发 → 患者端可见
   ↓
患者训练 → SessionRecord(带模型与指标版本)
   ↓
治疗师查看趋势(跨版本需提示不可直接比较)
```

## 5. 运行时逻辑清单(本体无法形式化)

| 项 | 实现层 | 输入 → 输出 | 依赖 |
| --- | --- | --- | --- |
| 跨天/周时间窗(僵直 3 天、痉挛 2 周) | API 规则引擎 | 自评序列 → 是否触发 | C5 待签核 |
| 跨机构 403 | API 中间件 | account.org vs 资源 org → 拒绝+审计 | D10 |
| 训练有效性校验 | App / cv-worker | 关键点序列 → 存疑标记 | D13、未决项 12 |
| BPM 个体化 | App | 基线步频 → 处方 BPM | C3 |
| 量表分界 | API | 量表得分 → 风险等级 | C4 待签核 |
| 分期→条目映射 | API | H-Y + 症状 → 变体集合 | C2 待签核 |
| 跨版本趋势比较 | API / 前端 | 两条记录版本 → 是否可比 | T7 |
| 通知实际送达与兜底 | notification 服务 | 投递结果 → 重试/兜底 | D6、未决项 6 |

## 6. 未决项阻塞表

| 未决项 | 阻塞的设计 |
| --- | --- |
| 1 条目清单与排期 | 内容库表结构可定,数据填充不能 |
| 2 映射表 | 方案自动匹配规则不能定稿 |
| 3 绑定交互细节 | `/auth/bind` 契约不能定稿 |
| 4 治疗师工作台 UI | Web 后台页面不能定稿 |
| 5 用药责任人 | `medication` 模块不能开始 |
| 6 预警兜底通道 | notification 服务不能定稿 |
| 7 采集条件阈值 | App 采集前检查不能定稿 |
| 8 云端视频留存 | cv-worker 存储策略不能定稿 |
| 9 小程序视频采集限制 | 小程序训练流程不能定稿 |
| 10 小程序缓存上限 | 小程序离线能力不能承诺 |
| 11 CV 实测 | T6 不能定案,CV 模块不能开工 |
| 12 有效性阈值 | D13 规则不能定稿 |
| 13 手机端性能基线 | Lite/Full 选择不能定,离线预算不能定 |
| 14 手机端适老化排布 | Android UI 骨架不能定稿 |

## 7. 冲突待裁

2026-11-04 已补本体四项缺口并重跑 SHACL:**13/13 通过**。

- 新增 `Account`、`ExternalIdentity`、`BindingToken`、`DeviceSession`、`NotificationDelivery`、`DeliveryStatus`
- 新增 `accountBelongsToOrg`、`hasRoleBinding`、`hasExternalIdentity`、`deviceBoundToPatient`、`deliveryForAlert` 等属性
- 新增 `AccountShape`、`NotificationDeliveryShape`、`DeviceSessionShape`、`ExerciseCategoryShape`
- `contraindicatedFor` 保留兼容,新增 `contraindicatedForSymptom` / `contraindicatedForCondition` 两个子属性

仍未决一条:

| # | 问题 | 影响 |
| --- | --- | --- |
| 1 | `contraindicatedFor` 仍可同时指向 `Symptom` 与 `Contraindication` | 查询「某症状禁忌的所有条目」仍需 union;是否强制只用子属性、废弃上位属性,待定 |

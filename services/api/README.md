# services/api

rehab-trainer 后端 API 骨架。Python 3.13 + FastAPI + SQLAlchemy 2.x + Pydantic v2。

契约唯一真源：`packages/api-contract/openapi.yaml`。本服务实现该文件中的 15 个路径、17 个操作，
完整 URL 带 `/v1` 前缀（对应契约 `servers[0].url`）。

## 选型理由

- **与 `services/cv-worker` 同语言（Python）**：模型与指标相关的数据结构可共用，部署与依赖链少一套。
- **契约可直接转 Pydantic 模型**：`openapi.yaml` 的字段命名统一用 camelCase 别名映射，字段逐一对应，避免跨端手写类型漂移（D15）。
- **SQLite → PostgreSQL 迁移成本低**：同一套 SQLAlchemy 2.x 模型；开发期 `sqlite:///`，生产只替换
  `REHAB_DATABASE_URL` 并安装 `psycopg` 可选依赖，不写两套模型。

## 目录

```text
services/api/
├── app/
│   ├── main.py              # FastAPI 应用、/v1 路由挂载、统一错误响应
│   ├── core/                # 配置、数据库、鉴权、审计、分页、临床占位常量
│   ├── models/              # SQLAlchemy 模型（org / account / patient / ...）
│   ├── schemas/             # Pydantic 请求/响应模型（camelCase 别名）
│   ├── routers/             # 按契约 tag 划分：auth / patients / assessments / ...
│   └── seed.py              # 开发期演示数据
├── tests/                   # pytest + Starlette TestClient（基于 httpx）
├── pyproject.toml
└── .env.example
```

## 快速开始

在 `services/api` 目录执行（Windows PowerShell 示例）：

```powershell
# 1. 创建虚拟环境（不要装全局包）
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e ".[dev]"

# 2. 初始化数据库并写入演示数据
.\.venv\Scripts\python.exe -m app.seed

# 3. 启动服务
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

启动日志示例：

```text
INFO:     Started server process [12596]
INFO:     Waiting for application startup.
INFO:     Application startup complete.
INFO:     Uvicorn running on http://127.0.0.1:8000 (Press CTRL+C to quit)
```

- OpenAPI 文档：`http://127.0.0.1:8000/docs`
- 健康检查没有单独端点（契约未定义）；可用 `GET /v1/me` 验证鉴权链。

### 演示账号（仅 seed 后存在）

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 康复治疗师 | `therapist` | `therapist123` |
| 照护管理员 | `admin` | `admin123` |
| 患者 | `patient` | `patient123` |
| 照护者 | `caregiver` | `caregiver123` |

一次性绑定码 `demo-binding-token` 可换取患者账号令牌；患者 ID 为 `patient-demo`。
这些凭据仅用于本地开发。

## 数据库初始化

- 应用启动时（`app/main.py` 的 lifespan）自动执行 `Base.metadata.create_all`，可由
  `REHAB_AUTO_CREATE_SCHEMA=false` 关闭，改由迁移工具管理。
- 演示数据：`python -m app.seed`。脚本可重复执行，已存在的数据会跳过。
- 开发库默认是 `services/api/rehab_trainer.db`（已加入 `.gitignore`）。
- 生产 PostgreSQL：设置 `REHAB_DATABASE_URL=postgresql+psycopg://...`，并安装
  `pip install -e ".[postgres]"`。模型未使用 SQLite 专有类型，无需改表定义。

配置项见 `.env.example`（统一 `REHAB_` 前缀）。

## 跑测试

```powershell
.\.venv\Scripts\python.exe -m pytest
```

测试使用独立临时 SQLite 库，每个用例重建表并注入夹具数据；不触碰开发库。
覆盖重点：

- 跨机构访问返回 403 并写 `audit_event`
- 训练记录按 `clientRecordId` 幂等（重复提交返回同一记录）
- 处方 `If-Match` 版本冲突返回 409
- H-Y 5 级开处方被拒（D5）
- 未确认分期不返回高风险训练条目（D11）

## API 一览

| 方法 | 路径 | 角色要求 |
| --- | --- | --- |
| POST | `/v1/auth/bind` | 公开（一次性绑定码） |
| POST | `/v1/auth/login` | 公开 |
| GET | `/v1/me` | 任意已登录账号 |
| POST | `/v1/auth/refresh` | 公开（刷新令牌） |
| GET | `/v1/patients` | 临床角色看全机构；患者/照护者只看关联患者 |
| POST | `/v1/patients` | `care_admin`、`rehab_therapist` |
| GET | `/v1/patients/{patientId}` | 机构内 + 角色范围 |
| POST | `/v1/patients/{patientId}/stage-confirmations` | `rehab_therapist` |
| POST | `/v1/patients/{patientId}/assessments` | `patient`、`caregiver`、`rehab_therapist` |
| GET | `/v1/exercises` | 任意已登录账号 |
| GET | `/v1/exercises/{code}` | 任意已登录账号 |
| POST | `/v1/prescriptions` | `rehab_therapist` |
| PATCH | `/v1/prescriptions/{prescriptionId}` | `rehab_therapist` + `If-Match` |
| POST | `/v1/sessions` | 任意已登录账号（按患者范围逐条校验） |
| GET | `/v1/sessions` | 同患者范围 |
| GET | `/v1/alerts` | 临床角色看全机构；患者/照护者只看关联患者 |
| PATCH | `/v1/alerts/{alertId}` | `care_admin`、`rehab_therapist` |

### 关键行为

- **多机构隔离**：令牌中的 `orgId` 与账号当前机构必须一致；资源按 `patient.org_id` 过滤。跨机构访问返回
  403（不返回 404 掩盖存在性），并写 `audit_event`（action=`authz.cross_org_access`）。
- **角色校验**：分期确认与处方仅 `rehab_therapist`；预警处理限 `care_admin` / `rehab_therapist`；
  拒绝时返回 403 并写审计。
- **训练记录幂等**：`session_record.client_record_id` 数据库唯一；重复提交返回已存在记录的 `recordId`。
  每条记录必须携带 `modelVersion`、`modelSha256`、`metricAlgorithmVersion`、`thresholdConfigVersion`。
- **处方乐观锁**：`If-Match` 与当前 `version` 不一致返回 409；更新后版本递增（如 `1.0.0` → `1.0.1`），
  旧条目写入审计事件以保留可追溯性。
- **D5**：H-Y 5 级患者不返回训练条目、不可开处方。
- **D11**：分期未确认（`stageConfirmationStatus=suspected`）时，高风险条目不返回，也不可用于处方。
- **建档**：`POST /patients` 按契约只接收姓名等基础信息；新建档案的 `hyStage` 暂记为 1 级占位、
  状态为 `suspected`，权威分级须经治疗师分期确认后生效。
- **评估**：本版只记录评估事实，不计算风险等级，`riskLevel` 留空。

## 待办与占位

以下为**临床未签核**占位，代码中统一标 `TODO: 待临床签核`，工程侧不得自行填数值：

- 量表分界值（C4）：`app/core/clinical.py` 的 `ASSESSMENT_CUTOFFS`
- 预警阈值与时间窗口（C5）：`app/core/clinical.py` 的 `ALERT_RULES`
- BPM 个体化规则（C3）：`app/schemas/exercise.py` 的 `Dosage.bpm` 注释
- TOTP 二次验证：`app/routers/auth.py` 仅接收契约字段，未校验
- 通知投递与兜底通道（未决项 6）：仅持久化 `notification_delivery`，不做实际发送
- 分期→条目自动匹配（C2 / 未决项 2）：当前只校验条目适用分级，不做推荐

# rehab-trainer

> 帕金森病患者肌肉保护训练系统：治疗师确认分期并开具处方，患者在手机或平板完成训练，
> 姿态识别提供动作计数、步态指标与姿势矫正反馈；照护者经微信小程序查看记录、提交自评、
> 接收预警；照护管理员在 Web 后台完成随访管理。

## 系统概览

| 端 | 职责 |
| --- | --- |
| **Android App**（手机 + 平板） | 全功能客户端。姿态识别端上实时推理、视频离线预缓存、采集条件检查；手机为主设备，平板为兼容设备 |
| **微信小程序** | 契约客户端与五个业务页面。姿态识别规划为云端异步推理（训练后出报告），本轮未启动（D26），无实时反馈 |
| **Web 后台** | 照护管理员的档案、看板、预警中心、随访台账、权限管理；康复治疗师的分期确认与处方工作台 |

App 与小程序的功能清单一致，差异只在姿态识别的实现方式与实时性。

### 训练范围

- **四大训练类**：牵伸放松、肌力维持、节律步态、姿势矫正。
- **每条训练包含**：视频、节拍音频、时长与频次、禁忌提示、危险信号提示。
- **分期**：按 H-Y 1–5 级建档，训练方案分 1–4 级四套；5 级仅宣教与转诊，不进训练流程。
  未经康复治疗师确认的分期标记为「疑似」。

## 当前状态

需求基线已裁决（见 `.agent/keep/decisions/`），演示版全链路可运行。最近一次本地验证：2026-11-10。

| 部分 | 状态 |
| --- | --- |
| 后端 `services/api` | FastAPI + SQLAlchemy；Alembic 迁移 head `0005`，覆盖 18 张表；**170 项测试通过** |
| Web 后台 `apps/admin-web` | 10 个业务页已接真实 API（患者、详情、分期、处方、预警、随访台账、审计、科研、视频管理、账号安全）；**29 条 E2E 通过**（最近一次记录） |
| Android App | 端上实时推理已用于训练页动作计数；登录/绑定、真实方案、训练记录补传闭环已落地；**215 项单测通过** |
| 微信小程序 | 契约客户端与五个业务页面；**17 项测试通过**；云端异步 CV 未实现（占位见 `services/cv-worker`，D26） |
| 契约联调 | `scripts/smoke-api.py` 覆盖 **32 项契约操作**（27 个路径），含审计、科研聚合与媒体上传/上架 |
| 部署 | `services/api/Dockerfile` + `docker-compose.yml` + `deploy/`（Alembic、健康检查、自检）；手册见 `docs/deployment/README.md` |

演示版对未裁决与待临床签核内容使用 mock 并标注（D21）；步态指标在完成标定前不进训练成绩（D23）。
临床阈值与剂量目前均为文献预设，**未经专业签核**，自动预警与风险分级保持关闭。

## 验证

```powershell
# 一把跑完所有本地门禁（失败即停）
services/api/.venv/Scripts/python.exe scripts/verify_all.py

# 后端单测
services/api/.venv/Scripts/python.exe -m pytest services/api/tests

# Android 单测
cd apps/android; ./gradlew :app:testDebugUnitTest

# 小程序类型检查与单测
pnpm --filter @rehab-trainer/miniprogram typecheck
pnpm --filter @rehab-trainer/miniprogram test

# 管理端类型检查与 E2E（E2E 自动起后端与预览服务；优先用 services/api/.venv 的 python）
cd apps/admin-web; npx tsc --noEmit
# 默认端口 18765/4173，被占用时用 E2E_BACKEND_PORT / E2E_PREVIEW_PORT 覆盖
npx playwright test

# 契约联调（不加 --base-url 时脚本自建独立库并拉起 uvicorn）
# 配置文件格式：{"username":"therapist","password":"<seed 输出>","bindingToken":"<seed 输出>"}
services/api/.venv/Scripts/python.exe scripts/smoke-api.py --config .agent/tmp/smoke-config.json

# 部署静态自检
services/api/.venv/Scripts/python.exe deploy/verify.py

# 容器与真实 PostgreSQL（需 Docker；先在 .agent/tmp/deployment.env 填好秘密）
docker compose --env-file .agent/tmp/deployment.env up --build -d --wait
```

## 仓库结构

| 路径 | 用途 |
| --- | --- |
| `apps/` | 三端应用：Android、微信小程序、Web 后台 |
| `services/` | 服务：API（FastAPI）、云端 CV 推理（占位） |
| `packages/` | API 契约与生成客户端（TypeScript / Kotlin） |
| `docs/agents/` | 工作流、执行配方、票据惯例、领域文档消费规则 |
| `docs/architecture/` | 架构与仓库结构说明 |
| `docs/clinical/` | 临床阈值文献预设与签核清单（待签核） |
| `docs/delivery/` | 用户手册、部署与运维手册、验收与合规说明、需求规格、功能矩阵、客户交付包 |
| `docs/design/` | 设计系统与交互原型 |
| `.agent/keep/` | 持久区：票据、决策、术语表（入版本控制） |
| `.agent/tmp/` | 临时产物（忽略） |
| `.agents/` | 本地面：个人过程记录与装配的技能（忽略；`skills/` 子树随仓分发） |

## 文档入口

| 文档 | 位置 |
| --- | --- |
| 需求基线与范围 | `docs/architecture/charter.md` |
| 功能矩阵（验收依据 v1.1） | `docs/delivery/feature-matrix.md` |
| 验收报告 | `docs/delivery/acceptance-report.md` |
| 临床阈值与签核清单 | `docs/clinical/literature-presets.md` |
| 伦理与合规要件 | `docs/clinical/ethics-compliance.md` |
| 用户操作手册 | `docs/delivery/user-manual.md` |
| 部署与运维 | `docs/deployment/README.md`、`docs/delivery/operations-manual.md` |
| 后续待办与待决策 | `docs/delivery/next-actions.md`、`docs/delivery/open-decisions.md` |

## 开工

1. 读 [`AGENTS.md`](AGENTS.md)，按「开工前指针」加载规则、票据与术语表。
2. 工作流入口：[`.agents/skills/agent-workflow/SKILL.md`](.agents/skills/agent-workflow/SKILL.md)。
3. 交付门禁、提交流程与验收规程以 `AGENTS.md` 的「门禁」「验收」两段为准。

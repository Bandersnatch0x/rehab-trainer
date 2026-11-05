# rehab-trainer

帕金森病患者肌肉保护训练系统:康复治疗师确认分期并开具处方,患者在手机或平板端完成训练,姿态识别提供动作计数、步态指标与姿势矫正反馈;照护者经微信小程序查看记录、提交自评、接收预警,照护管理员在 Web 后台随访管理。

## 范围

四大训练大类:牵伸放松、肌力维持、节律步态、姿势矫正。每条训练包含视频、节拍音频、时长与频次、禁忌提示、危险信号提示。

三端:

| 端 | 职责 |
| --- | --- |
| Android App(手机 + 平板) | 全功能;姿态识别端上实时推理;视频离线预缓存;采集条件检查;手机为主设备,平板为兼容设备 |
| 微信小程序 | 全功能;姿态识别走云端异步推理(训练视频上传,训练后出报告);无实时反馈,须在界面明示 |
| Web 后台 | 照护管理员的档案、看板、预警中心、随访台账、权限管理;康复治疗师的分期确认与处方工作台 |

App 与小程序功能清单一致;差异只在姿态识别的实现方式与实时性。

分期:按 H-Y 1–5 级建档,训练方案分 1–4 级四套;5 级仅宣教与转诊,不进训练流程。未经康复治疗师确认的分期标记为「疑似」。

## 当前状态

需求基线已裁决(见 `.agent/keep/decisions/`),演示版全链路可运行:

| 部分 | 状态 |
| --- | --- |
| 后端 `services/api` | FastAPI + SQLAlchemy,Alembic 初始迁移覆盖 16 张表;97 项测试通过 |
| Web 后台 `apps/admin-web` | 8 个业务页已接真实 API(患者、详情、分期、处方、预警、随访台账、科研、视频管理);Playwright E2E 18 条全部通过 |
| 微信小程序 / Android | 契约客户端已生成,配置指向本地后端;小程序 async 云端 CV 与 App 端上实时推理按 D3 分级实现 |
| 部署 | `services/api/Dockerfile` + `docker-compose.yml` + `deploy/`(Alembic、健康检查、自检);手册见 `docs/deployment/README.md` |
| 联调 | `scripts/smoke-api.py` 覆盖 23 项契约操作(20 个路径),含科研聚合与媒体上传/上架 |

演示版对未裁决与待临床签核内容使用 mock 并标注(D21);步态指标在完成标定前不进训练成绩(D23)。

## 验证

```powershell
# 一把跑完所有本地门禁(失败即停)
services/api/.venv/Scripts/python.exe scripts/verify_all.py
# 管理端 E2E(自动起后端与预览服务;优先用 services/api/.venv 的 python)
# 默认端口 18765/4173,被占用时用 E2E_BACKEND_PORT / E2E_PREVIEW_PORT 覆盖
cd apps/admin-web; npx playwright test
# 联调(不加 --base-url 时脚本自建独立库并拉起 uvicorn)
# 配置文件格式: {"username":"therapist","password":"<seed 输出>","bindingToken":"<seed 输出>"}
services/api/.venv/Scripts/python.exe scripts/smoke-api.py --config .agent/tmp/smoke-config.json
# 部署静态自检
services/api/.venv/Scripts/python.exe deploy/verify.py
# 容器与真实 PostgreSQL(需 Docker;先在 .agent/tmp/deployment.env 填好秘密)
docker compose --env-file .agent/tmp/deployment.env up --build -d --wait
```

## 开工

1. 读 `AGENTS.md`,按「开工前指针」加载规则、票据与术语表。
2. 工作流入口:`.agents/skills/agent-workflow/SKILL.md`。
3. 交付门禁:`git diff --check`(临时门禁,首个测试套件落地后替换)。

## 目录

| 路径 | 用途 |
| --- | --- |
| `docs/agents/` | 工作流、执行配方、票据惯例、领域文档消费规则 |
| `.agent/keep/` | 持久区:票据、决策、术语表(入版本控制) |
| `.agent/tmp/` | 临时产物(忽略) |
| `.agents/` | 本地面:个人过程记录与装配的技能(忽略) |
| `apps/` | 三端应用:Android、微信小程序、Web 后台 |
| `services/` | 服务:API(FastAPI)、云端 CV 推理(占位) |
| `packages/` | API 契约与生成客户端 |
| `docs/architecture/` | 架构与仓库结构说明 |
| `docs/delivery/` | 交付文档:用户操作手册、部署与运维手册、验收与合规说明 |

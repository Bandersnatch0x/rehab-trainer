# 仓库结构

本仓为 monorepo(决策 D15)。契约是唯一共享真源,Android 只消费生成物,不手写跨端类型。

## 目录

| 路径 | 内容 | 当前状态 |
| --- | --- | --- |
| `apps/android/` | Kotlin + Jetpack Compose,手机与平板同一 App | CameraX + MediaPipe 实时上肢计数与训练页已落地;本机无 Android SDK,**构建未验证** |
| `apps/miniprogram/` | 微信原生小程序 + TypeScript | 5 个页面接真实接口(index/patient/records/alerts/assessment),训练页保持演示并标注;行为测试 15 项、类型检查通过 |
| `apps/admin-web/` | React + Vite + TypeScript | 路由、鉴权、7 个业务页接真实 API(含随访台账);E2E 14 条全部通过 |
| `services/api/` | FastAPI + SQLAlchemy 后端 | 15 路径 / 18 操作,16 张表,Alembic 初始迁移,41 项测试通过 |
| `services/cv-worker/` | 云端 CV 异步推理 | 占位,仅 README |
| `packages/api-contract/` | OpenAPI 契约(唯一真源) | 15 路径 / 18 操作,含 `GET /prescriptions` |
| `packages/api-client-ts/` | TypeScript 生成客户端目录 | 空,仅 .gitkeep 与说明;实际生成物在各自 app 内 |
| `packages/api-client-kt/` | Kotlin 生成客户端目录 | 空,仅 .gitkeep 与说明;实际接口在 `apps/android/.../data/remote/` |
| `docs/architecture/` | 架构与仓库说明 | 本文件 |
| `.github/workflows/ci.yml` | 按路径触发的 CI | 未在真实流水线验证 |

## 包管理

- 实测 `pnpm 10.7.1` 可用,采用 pnpm workspace(`pnpm-workspace.yaml`),未使用 npm workspaces。
- 工作区成员为 `apps/*` 与 `packages/*`;`services/*` 为 Python,不纳入 workspace。
- `apps/android` 不进 workspace,由 Gradle 独立管理。
- `pnpm-lock.yaml` 已提交并在改动依赖时同步;CI 尚未切换为 `--frozen-lockfile`。

## Android 骨架

- 版本矩阵:Gradle Wrapper 8.13、Android Gradle Plugin 8.13.2、Kotlin 2.2.20、Compose BOM 2025.09.01。
- `compileSdk = 36`、`targetSdk = 36`、`minSdk = 26`,Java/Kotlin 目标 17。
- 已落地:CameraX + MediaPipe Tasks(LIVE_STREAM)实时姿态、上肢计数状态机、训练执行页双播放(D22)、`data/remote/` 契约接口。
- `debug` 构建注入 `API_BASE_URL=http://10.0.2.2:8000/v1/` 并允许明文流量;release 不含该配置。
- 未配置签名与图标;本机缺 Android SDK,`assembleDebug` 未执行。

## 小程序骨架

- 原生小程序(TypeScript),未使用 Taro 或 uni-app(决策 T8)。
- `project.config.json` 的 `appid` 为 `touristappid`,接入真实主体时替换;本地调试关闭 `urlCheck`,发布前恢复。
- 页面:`index`、`patient`、`records`、`alerts`、`assessment`、`training`。
- 请求层已具备 Bearer 注入与 401 刷新重放;`API_BASE_URL` 指向 `http://127.0.0.1:8000/v1`。
- 取数与字段投影集中在 `utils/patient-data.ts`,页面只做绑定;临床字段(分级/症状)不进入患者端页面数据(D20)。
- 行为测试 `node --test apps/miniprogram/tests/patient-data.cjs`(15 项)用 `global.wx` 桩驱动真实请求层与五个 Page。
- 本机无微信开发者工具,实际渲染与真机运行**未验证**;类型检查与行为测试可执行。

## Web 后台骨架

- Vite + React + TypeScript,react-router 路由 + 自建鉴权上下文,无第三方 UI 库。
- `src/api/` 由 `openapi-typescript` 生成 `schema.d.ts`,业务调用集中在 `operations.ts`。
- 7 个业务页已接真实 API(患者列表/详情/分期/处方/预警/随访台账);患者端 D20 文案约束只管患者端,管理端保留临床信息。
- `npx tsc --noEmit` 与 `vite build` 通过;Playwright E2E 14 条全部通过。

## 契约工作流

1. 先改 `packages/api-contract/openapi.yaml`。
2. 再生成 `packages/api-client-ts/` 与 `packages/api-client-kt/` 的客户端。
3. 三端调用点与契约在同一提交内更新,由 CI 按路径触发检查。

当前契约含 15 个路径、18 个操作,覆盖登录/绑定、患者与分期、评估、训练条目、处方(含按患者读取)、训练记录批量幂等上传、预警处置。生成命令:`apps/admin-web` 与 `apps/miniprogram` 各自的 `pnpm generate:api`。

## 验证状态(2026-11-04)

| 项 | 结果 |
| --- | --- |
| 环境 | JDK 21.0.10、Gradle 9.0.0、Node 24.19.0、pnpm 10.7.1、npm 11.17.0 均可用 |
| Android 构建 | **未验证**。仅有 `D:\platform-tools`(adb),缺少 Android SDK 的 platforms 与 build-tools,未执行 `assembleDebug` |
| Gradle Wrapper | 已生成并固定 8.13;未执行下载与构建 |
| 小程序 | JSON 文件解析通过;未安装依赖,未执行 `tsc --noEmit` |
| Web 后台 | `package.json`/`tsconfig.json` 解析通过;未安装依赖,未执行构建 |
| API 契约 | YAML 解析与顶层键(openapi/info/servers)校验通过;未做 OpenAPI 规范级校验 |
| CI | 仓库无 GitHub 远端,流水线**未验证** |

## 验证状态(2026-11-05)

| 项 | 结果 |
| --- | --- |
| 后端测试 | `pytest` 40 项通过(含处方读取、机构隔离与契约一致性) |
| 管理端 E2E | `npx playwright test` **14 passed / 0 failed**(真实后端 + 每例独立机构数据) |
| 管理端构建 | `tsc --noEmit` 退出 0;`vite build` 通过 |
| 小程序 | 行为测试 15 项通过、`tsc --noEmit` 退出 0;**实际渲染与真机未验证** |
| 联调脚本 | `scripts/smoke-api.py` 自管理模式 **18/18 契约操作**,含批量幂等与重放 |
| 部署自检 | `deploy/verify.py` 通过:16 表迁移往返、模型差异为空、PostgreSQL 离线 SQL、YAML |
| 容器 | 已在 WSL2(kali-linux2,Docker 27.5.1/Compose 2.32.4)实跑:构建、compose up(双 healthy)、`alembic 0001`、healthcheck、pg_dump/restore、18/18 联调通过;hadolint 未装 |
| Android | **未验证**:本机无 Android SDK |
| CI | **未验证**:仓库无远端流水线 |
| 独立审查 | 两轮(`cb072f8..HEAD`、`d0b612d..HEAD`):3 个 HIGH 与 19 个 MEDIUM 已修;分页已由 offset 改为 keyset,契约 schema 级校验仍待排期 |

## 后续事项

- 安装 Android SDK 后补跑 `./gradlew :app:assembleDebug`,并记录 AGP/Gradle 兼容结果。
- 容器已验证主链路;仍需 hadolint 静态检查与并发/长时压测。
- 在微信开发者工具中实跑小程序,验证 WXML 渲染、输入法与合法域名配置。
- CI 尚未接远端,接入后改为冻结锁文件并复用上述检查。

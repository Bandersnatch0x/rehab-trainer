# 仓库结构

本仓为 monorepo(决策 D15)。契约是唯一共享真源,Android 只消费生成物,不手写跨端类型。

## 目录

| 路径 | 内容 | 当前状态 |
| --- | --- | --- |
| `apps/android/` | Kotlin + Jetpack Compose,手机与平板同一 App | 最小可编译骨架,未验证构建 |
| `apps/miniprogram/` | 微信原生小程序 + TypeScript | 首页骨架与类型检查配置 |
| `apps/admin-web/` | React + Vite + TypeScript | 空壳,未安装依赖 |
| `services/api/` | 后端 API 占位 | 仅 README |
| `services/cv-worker/` | 云端 CV 异步推理占位 | 仅 README |
| `packages/api-contract/` | OpenAPI 契约 | 占位,无业务端点 |
| `packages/api-client-ts/` | TypeScript 生成客户端目录 | 空,仅 .gitkeep 与说明 |
| `packages/api-client-kt/` | Kotlin 生成客户端目录 | 空,仅 .gitkeep 与说明 |
| `docs/architecture/` | 架构与仓库说明 | 本文件 |
| `.github/workflows/ci.yml` | 按路径触发的 CI | 未在真实流水线验证 |

## 包管理

- 实测 `pnpm 10.7.1` 可用,采用 pnpm workspace(`pnpm-workspace.yaml`),未使用 npm workspaces。
- 工作区成员为 `apps/*` 与 `packages/*`;`services/*` 当前只有占位 README,不纳入 workspace。
- `apps/android` 不进 workspace,由 Gradle 独立管理。
- 依赖尚未安装,仓库暂无 `pnpm-lock.yaml`。首次安装后应提交锁文件,CI 再切换为 `--frozen-lockfile`。

## Android 骨架

- 版本矩阵:Gradle Wrapper 8.13、Android Gradle Plugin 8.13.2、Kotlin 2.2.20、Compose BOM 2025.09.01。
- `compileSdk = 36`、`targetSdk = 36`、`minSdk = 26`,Java/Kotlin 目标 17。
- 入口 `app/src/main/java/com/rehabtrainer/app/MainActivity.kt`,仅渲染占位文本。
- 未配置签名、图标、权限与任何业务能力。

## 小程序骨架

- 原生小程序(TypeScript),未使用 Taro 或 uni-app(决策 T8)。
- `project.config.json` 的 `appid` 为 `touristappid`,接入真实主体时替换。
- `tsconfig.json` 依赖 `miniprogram-api-typings`;类型检查需先安装依赖。
- 页面仅 `pages/index/index`,为占位内容。

## Web 后台骨架

- Vite + React + TypeScript,无 UI 库、路由与状态管理。
- 未执行 `pnpm install`,故未运行类型检查与生产构建。

## 契约工作流

1. 先改 `packages/api-contract/openapi.yaml`。
2. 再生成 `packages/api-client-ts/` 与 `packages/api-client-kt/` 的客户端。
3. 三端调用点与契约在同一提交内更新,由 CI 按路径触发检查。

当前契约只有 `openapi`、`info`、`servers` 占位,不包含业务端点;领域模型尚未定稿。

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

## 后续事项

- 安装 Android SDK 后补跑 `./gradlew :app:assembleDebug`,并记录 AGP/Gradle 兼容结果。
- 首次 `pnpm install` 后提交锁文件,并把 CI 改为冻结锁文件。
- 契约首个业务版本确定后,选定生成工具并补齐生成命令。
- 补充小程序 `sitemap.json`、Web 端基础样式等工程细节(不属本次骨架范围)。

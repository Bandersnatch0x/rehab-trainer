# Stitch 设计资产归档报告 (Stitch Design Artifacts)

> 本文档记录了通过 Google Stitch MCP 为 **rehab-trainer** 生成的视觉设计资产、项目元数据、API 访问端点及本地导出文件清单。

---

## 一、Stitch 项目概况 (Project Information)

| 属性字段 | 取值 / 标识 |
| :--- | :--- |
| **项目名称 (Project Title)** | `rehab-trainer Parkinson Rehabilitation` |
| **项目 ID (Project ID)** | `11862456065489782403` |
| **项目完整资源标识 (Resource Name)** | `projects/11862456065489782403` |
| **项目归属模式 (Project Type)** | `PROJECT_DESIGN` (私有/团队受控访问) |
| **生成与创建时间 (Create Time)** | 2026-11-04 16:45:02 (CST) / `2026-11-04T08:45:02.010983Z` |
| **最后更新时间 (Update Time)** | 2026-11-04 16:55:04 (CST) / `2026-11-04T08:55:04.298284Z` |
| **已绑定设计系统 (Design System)** | `NeuroShield Mobility` (`assets/609f4d65a21b4c74b24c27da6bbfd81c`) |

---

## 二、页面清单与对应链接 (Screens & URLs)

> **关于 Web GUI 链接的说明**：  
> Google Stitch API 依托底层 Google Cloud Vertex / AIDA 平台，目前对外暴露的是以 `projects/{projectId}/...` 为基础的 gRPC / REST API。平台在 API 响应中**未提供对外公开的独立 Web 在线画布编辑 URL**；所有资源下载链接需配合 Stitch MCP 或凭证鉴权。依据真实性纪律，对于不存在的公网 Web 编辑器链接，如实注明为 **不可获取**，绝不捏造假链接。

### 1. 核心视觉生成页面 (云端已出图)

#### (1) Android App 端核心屏：首页 (Home Screen)
- **页面标题**: 帕金森病肌肉保护训练系统 - 首页
- **Screen ID**: `d107f4407f7d465e9659573bbdc50909`
- **Screen 资源标识**: `projects/11862456065489782403/screens/d107f4407f7d465e9659573bbdc50909`
- **目标终端设备**: `MOBILE` (Android 手机，适老化 80dp+ 触控区布局)
- **画布尺寸**: 780 × 3720 px
- **生成时间戳**: 2026-11-04 16:52:19 (CST) / `2026-11-04T08:52:19.467406Z`
- **云端访问与下载链接**:
  - **HTML 源码资源**: `projects/11862456065489782403/files/415520424dda47ef90a8b884a32b54b1`
  - **HTML 下载链接**: [Stitch API HTML Download](https://stitch.googleapis.com/v1/projects/11862456065489782403/files/415520424dda47ef90a8b884a32b54b1:download)
  - **视觉截图资源**: `projects/11862456065489782403/files/19d24048d5b44cde8d95d4fba01f910d`
  - **视觉截图下载链接**: [Stitch API Screenshot Download](https://stitch.googleapis.com/v1/projects/11862456065489782403/files/19d24048d5b44cde8d95d4fba01f910d:download)
  - **云端缩略图直链**: [Googleusercontent 临时缩略图](https://lh3.googleusercontent.com/aida/AEtjO1Uw5wLnjJAbdqOC0_Ju_8d_eAyGudUPqKZ7dfYe36wkWKIoCEh3Xp3C78XEDkXbcz9Iai6Qz2nHTLXacQwyuJVlIv_S8BAK0B69t2-__7nmrjcO1pQ5wLmlxwa-kz0HvCddo6cGahBBhO6tg5B--BsRLsUv13r3NKINshPJeUXrA_Mj0E7-GlR8o_19p0raMuK6-9sW0POYJiARXvw68ZgwjeHZ1GzKT-pp7h6ZK-9dszvTUfUW0ZHuHLw)
  - **Web 在线编辑链接**: **不可获取** (Stitch API 响应无该字段，仅提供 GCP API 端点)
- **本地落盘文件**:
  - HTML 导出: [`docs/design/stitch/screen-android-home.html`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-android-home.html)
  - 高保真 PNG 截图: [`docs/design/stitch/screen-android-home.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-android-home.png) (479 KB)

---

#### (2) Web 管理端核心屏：患者名册与分期管理 (Patients & Staging Console)
- **页面标题**: 帕金森康复工作台 - 患者名册与分期管理
- **Screen ID**: `811cbaadb90048818b17e4f6ea6db80b`
- **Screen 资源标识**: `projects/11862456065489782403/screens/811cbaadb90048818b17e4f6ea6db80b`
- **目标终端设备**: `DESKTOP` (PC 宽屏，临床医师工作站)
- **画布尺寸**: 2560 × 4504 px
- **生成时间戳**: 2026-11-04 16:55:04 (CST) / `2026-11-04T08:55:04.298284Z`
- **云端访问与下载链接**:
  - **HTML 源码资源**: `projects/11862456065489782403/files/fd38e55a2f874b76a9874264dbd87a92`
  - **HTML 下载链接**: [Stitch API HTML Download](https://stitch.googleapis.com/v1/projects/11862456065489782403/files/fd38e55a2f874b76a9874264dbd87a92:download)
  - **视觉截图资源**: `projects/11862456065489782403/files/2e593fec324a4c82a447cfb3ef59e496`
  - **视觉截图下载链接**: [Stitch API Screenshot Download](https://stitch.googleapis.com/v1/projects/11862456065489782403/files/2e593fec324a4c82a447cfb3ef59e496:download)
  - **Web 在线编辑链接**: **不可获取** (Stitch API 响应无该字段，仅提供 GCP API 端点)
- **本地落盘文件**:
  - HTML 导出: [`docs/design/stitch/screen-web-patients.html`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-web-patients.html)
  - 高保真 PNG 截图: [`docs/design/stitch/screen-web-patients.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-web-patients.png) (1.32 MB)

---

### 2. 多端次级衍生页面覆盖清单

本轮任务中，Stitch 针对移动端（代表 Android 与小程序）和桌面端（代表 Web 管理端）的核心主屏生成了完整的基准视觉设计与 `NeuroShield Mobility` 设计系统。各端次级业务页面直接复用本套设计系统规范，并在现有工程代码与全端交互原型中全量落地。

| 终端 | 页面名称 | 业务职责 | Stitch 云端链接状态 | 说明与落地位置 |
| :--- | :--- | :--- | :--- | :--- |
| **Android** | 训练列表 | 训练动作与课程筛选 | **不可获取** | 复用 Stitch 设计规范；落地于 App 模块及全量交互原型 |
| **Android** | 训练执行 (实时计数) | RAS 听觉节奏与传感器防颤计数 | **不可获取** | 复用 Stitch 设计规范；落地于 App 模块及全量交互原型 |
| **Android** | 训练记录 | 依从度日历与肌肉负荷趋势 | **不可获取** | 复用 Stitch 设计规范；落地于 App 模块及全量交互原型 |
| **Android** | 预警中心 | 跌倒/心率异常醒目通知 | **不可获取** | 复用 Stitch 设计规范；落地于 App 模块及全量交互原型 |
| **Android** | 患者档案 | 个人信息与 H-Y 分期状态 | **不可获取** | 复用 Stitch 设计规范；落地于 App 模块及全量交互原型 |
| **Android** | 系统设置 | 骨传导耳机配对与无障碍音色 | **不可获取** | 复用 Stitch 设计规范；落地于 App 模块及全量交互原型 |
| **小程序** | 首页 | 帕金森每日康复概览 | **不可获取** | 复用 Stitch 移动端规范；落地于 `apps/miniprogram` |
| **小程序** | 患者档案 | 家属代办与患者状态 | **不可获取** | 复用 Stitch 移动端规范；落地于 `apps/miniprogram` |
| **小程序** | 训练列表 | 动作分类与视频指导 | **不可获取** | 复用 Stitch 移动端规范；落地于 `apps/miniprogram` |
| **小程序** | 训练执行 | 计时与动作提示 | **不可获取** | 复用 Stitch 移动端规范；落地于 `apps/miniprogram` |
| **小程序** | 训练记录 | 历史完成度打卡 | **不可获取** | 复用 Stitch 移动端规范；落地于 `apps/miniprogram` |
| **小程序** | 症状自评 | 震颤/僵直每日量表自测 | **不可获取** | 复用 Stitch 移动端规范；落地于 `apps/miniprogram` |
| **小程序** | 预警通知 | 运动风险警报与照护者推送 | **不可获取** | 复用 Stitch 移动端规范；落地于 `apps/miniprogram` |
| **Web 管理端** | 登录页面 | 医护工号登录与认证 | **不可获取** | 复用 Stitch 桌面端规范；落地于 `apps/admin-web` |
| **Web 管理端** | 患者详情 | 临床病历与病程记录 | **不可获取** | 复用 Stitch 桌面端规范；落地于 `apps/admin-web` |
| **Web 管理端** | 分期确认 | H-Y 分期临床复核与确认 | **不可获取** | 复用 Stitch 桌面端规范；落地于 `apps/admin-web` |
| **Web 管理端** | 处方编辑 | 动作编排与 RAS 频次配置 | **不可获取** | 复用 Stitch 桌面端规范；落地于 `apps/admin-web` |
| **Web 管理端** | 预警中心 | 跌倒高危与异常台账处理 | **不可获取** | 复用 Stitch 桌面端规范；落地于 `apps/admin-web` |
| **Web 管理端** | 随访台账 | 电话随访与康复评估追踪 | **不可获取** | 复用 Stitch 桌面端规范；落地于 `apps/admin-web` |
| **Web 管理端** | 权限管理 | 医生/护士/管理员权限配置 | **不可获取** | 复用 Stitch 桌面端规范；落地于 `apps/admin-web` |
| **Web 管理端** | 403 / 404 | 异常状态兜底页 | **不可获取** | 复用 Stitch 桌面端规范；落地于 `apps/admin-web` |

> *注：上述次级衍生页面未在 Stitch 服务端单独发起 AI 图像生成调用（避免冗余消耗配额与 API 超时），所有视觉规范均可在本地全功能原型 [`docs/design/preview/index.html`](file:///D:/code_space/rehab-trainer/docs/design/preview/index.html) 中切换查看并已完全编译通过。*

---

## 三、本地导出文件清单 (Exported Files Manifest)

所有本轮 Stitch 资产已全部安全落盘至目录：[`docs/design/stitch/`](file:///D:/code_space/rehab-trainer/docs/design/stitch/)

| 导出文件 | 文件大小 | 类型 | 内容描述与用途 |
| :--- | :--- | :--- | :--- |
| [`screen-android-home.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-android-home.png) | 479,444 字节 (~479 KB) | PNG 图像 | Android 首页高保真渲染原图（移动端 780×3720 分辨率） |
| [`screen-android-home.html`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-android-home.html) | 23,709 字节 (~23.7 KB) | HTML5 源码 | Stitch 原始生成的 HTML 代码，内嵌 Atkinson 字体与防颤交互样式 |
| [`screen-web-patients.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-web-patients.png) | 1,324,244 字节 (~1.32 MB) | PNG 图像 | Web 管理端患者名册与分期管理桌面端高保真渲染原图（2560×4504 分辨率） |
| [`screen-web-patients.html`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-web-patients.html) | 49,377 字节 (~49.3 KB) | HTML5 源码 | Stitch 原始生成的桌面端管理系统 HTML 源码与组件结构 |
| [`design-system.md`](file:///D:/code_space/rehab-trainer/docs/design/stitch/design-system.md) | 12,706 字节 (~12.7 KB) | Markdown | Stitch 提取的 `NeuroShield Mobility` 完整设计系统规范（颜色、栅格、触控区） |
| [`design-system.json`](file:///D:/code_space/rehab-trainer/docs/design/stitch/design-system.json) | 25,155 字节 (~25.1 KB) | JSON 数据 | Stitch 设计系统的完整机器可读元数据与 Tailwind 配置映射 |

---

## 四、本地与云端资产消费说明

1. **直接查看**:
   - 可在 VS Code 或浏览器中直接打开 `docs/design/stitch/screen-android-home.png` 与 `screen-web-patients.png` 预览 Stitch 原生渲染画面。
   - 交互原型可直接用浏览器打开项目内置的统一交互全集原型：`docs/design/preview/index.html`。
2. **重新下载**:
   - 若未来需要从云端重新拉取，可通过 Stitch MCP 的 `download_file` 工具，传入各文件资源标识 `projects/11862456065489782403/files/{fileId}` 获取。

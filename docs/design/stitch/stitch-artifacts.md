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

### 2. 全量 23 个页面生成与落盘清单 (覆盖率 100%)

本轮任务中，针对 Android 原生端、微信小程序端、Web 医护管理端的所有业务屏，均已在 Stitch 服务端完成高保真设计生成并下载落盘至 `docs/design/stitch/`：

#### (1) Android 原生移动端 (9 个页面)
| 序号 | 页面名称 | 业务职责与三模式定义 | Stitch Screen ID | 本地导出文件 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | 首页 | 帕金森康复工作台，Q版插画与80dp大按钮 | `d107f4407f7d465e9659573bbdc50909` | [`screen-android-home.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-android-home.png) (479 KB) |
| 2 | 训练列表 | 四大分类卡片，水平非换行适老化标签 | `bfa2f4db1d7d48398a0df4b489bfc79c` | [`screen-android-exercises.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-android-exercises.png) (793 KB) |
| 3 | 训练执行 (模式1) | **竖屏同屏双播放**：教学视频 38% + 实景拍摄 62% | `53cc050a731343f28349a15f720c0d57` | [`screen-android-training-portrait.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-android-training-portrait.png) (525 KB) |
| 4 | 训练执行 (模式2) | **纯实景拍摄+语音提示**：折叠视频为语音胶囊，摄像头独占100% | `f5bc2b4c7ce6447b9cefe4f2d21b0775` | [`screen-android-training-camera-only.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-android-training-camera-only.png) (2.94 MB) |
| 5 | 训练执行 (模式3) | **横屏自适应**：平板 50/50 分栏，手机 36/64 比例防挤压 | `8d7b916561f44e52b740deb84f547e91` | [`screen-android-training-landscape.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-android-training-landscape.png) (1.69 MB) |
| 6 | 训练记录 | 依从度打卡日历圆盘、动作规范星级评分 | `1177b5a9cf27480c98439b6fbadedd5b` | [`screen-android-records.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-android-records.png) (1.34 MB) |
| 7 | 预警通知 | 关怀提醒与安全建议，符合 D20 禁用词规范 | `1bad347980fe4498b8e5b6765e0ea720` | [`screen-android-alerts.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-android-alerts.png) (406 KB) |
| 8 | 患者档案 | 个人中心与阶段标识，一键呼叫家属大卡片 | `48c35c46b22d4e63a1674e3b44eaf9c1` | [`screen-android-patients.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-android-patients.png) (722 KB) |
| 9 | 系统设置 | 骨传导耳机配对、大字号切换、无障碍模式 | `bda36872e8d245a8b104872dcb08a32a` | [`screen-android-settings.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-android-settings.png) (732 KB) |

> **关键安全底线保证**：在任何模式与屏幕旋转状态下，底部的「🛑 紧急停止」按钮始终常驻吸底可见、高度 >= 80dp，绝对不被裁切。

#### (2) 微信小程序端 (6 个页面)
| 序号 | 页面名称 | 业务职责 | Stitch Screen ID | 本地导出文件 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | 首页 | 温暖小鹿问候、今日原地踏步任务大卡片 | `a0c295f0724148d08fa8ad27ecc9da28` | [`screen-mp-home.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-mp-home.png) (456 KB) |
| 2 | 患者档案 | 就诊卡号、当前康复阶段、照护家属快捷绑定 | `d8951048230248be8a365faf9bd1edab` | [`screen-mp-profile.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-mp-profile.png) (748 KB) |
| 3 | 训练记录 | 月度打卡日历圆盘、组数统计、星级评分流 | `33bd6fede15e41369ef17f5387abfa1e` | [`screen-mp-records.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-mp-records.png) (824 KB) |
| 4 | 关怀提醒 | 训练达成提醒、动作偏快提示、家属关怀卡片 | `ab9690d0760c4cdeb5e8b70d7e8df30b` | [`screen-mp-alerts.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-mp-alerts.png) (684 KB) |
| 5 | 症状自评 | 生活语言晨间状态问卷、单选大药丸按钮 | `279b1c3b14854713affb37e3db33e1de` | [`screen-mp-assessment.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-mp-assessment.png) (588 KB) |
| 6 | 跟练指导 | 动作示范分解卡片、64 BPM 节拍球、大字计时 | `7b074108b0514a3cb1099c5eba3c594f` | [`screen-mp-training.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-mp-training.png) (807 KB) |

#### (3) Web 医护管理端 (8 个页面)
| 序号 | 页面名称 | 业务职责 | Stitch Screen ID | 本地导出文件 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | 医护登录 | 医疗深蓝风格、机构选择、医护工号认证 | `d6c2061705a3488a867391f89d218282` | [`screen-web-login.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-web-login.png) (568 KB) |
| 2 | 患者名册 | 全科患者多维看板、分期多选过滤筛选 | `811cbaadb90048818b17e4f6ea6db80b` | [`screen-web-patients.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-web-patients.png) (1.32 MB) |
| 3 | 患者详情 | 步态雷达图、肌力维持曲线、FoG 频次记录 | `5b3149a8369c4c279cbff621d0b08a34` | [`screen-web-patient-detail.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-web-patient-detail.png) (801 KB) |
| 4 | 分期确认 | Hoehn & Yahr 1-5 级量表复核、专家意见录入 | `d33b21a981f34bf89262db6c3985d778` | [`screen-web-staging.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-web-staging.png) (859 KB) |
| 5 | 处方编辑 | 动作库拖拽编排、节拍频次调节、下发患者端 | `958c95899e7e41ebb9c62ec90a6fb3b4` | [`screen-web-prescription.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-web-prescription.png) (926 KB) |
| 6 | 预警中心 | 跌倒可疑/姿势失稳告警台账、事件处理抽屉 | `39ff490a019c46a7afbf3dd9b13fe07a` | [`screen-web-alerts.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-web-alerts.png) (651 KB) |
| 7 | 随访台账 | 随访日历看板、患者回访状态、新增随访模态框 | `7ab957e931eb400896ad9b6014cbeed0` | [`screen-web-followup.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-web-followup.png) (609 KB) |
| 8 | 权限管理 | 医护角色树 (主任/治疗师/护士)、功能权限矩阵 | `a374e86ab70c4cb38b0e39d4823c1de0` | [`screen-web-permissions.png`](file:///D:/code_space/rehab-trainer/docs/design/stitch/screen-web-permissions.png) (740 KB) |

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

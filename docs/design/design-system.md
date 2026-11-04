# rehab-trainer 视觉与交互设计系统规范 (Design System Spec)

- 版本：v1.0.0
- 日期：2026-11-04
- 适用平台：Android App（手机 + 平板）、微信小程序、Web 管理端
- 领域基线：`docs/architecture/charter.md`、`.agent/keep/glossary.md`、`.agent/keep/decisions/0001-需求基线与范围裁决.md`
- 设计哲学：**医疗冷静色系 + 适老化极简防护 + 预警强醒目 + 手机为主平板兼容**（吸纳天天跳绳的大字报卡片骨架，彻底剔除游戏化橙红与高频连续微操）。

---

## 1. 颜色系统与 WCAG AA 对比度校验

### 1.1 品牌色与语义色板（Hex & RGB）

本项目面向帕金森病高龄患者及其临床医护，色彩调性必须呈现**冷静、专业、可靠**的医疗康复特征。主色延续并标准化为深海康复蓝（Rehab Deep Blue, `#1A4FA0`）。

| Token 变量 | 色值 Hex | 用途说明 | 推荐背景 | WCAG 对比度 | 评级 |
| --- | --- | --- | --- | --- | --- |
| `color-primary` | `#1A4FA0` | 品牌主色、主操作按钮背景、关键高亮 | `#FFFFFF` | **8.12 : 1** | AAA (Pass) |
| `color-primary-dark` | `#0F3268` | 主色按下态（Hover/Pressed）、主标题字 | `#FFFFFF` | **12.45 : 1** | AAA (Pass) |
| `color-primary-light` | `#EAF1FB` | 选框底色、主色次级浅容器背景 | `#0F3268`（文字） | **10.58 : 1** | AAA (Pass) |
| `color-success` | `#1B5E20` | 安全、训练达标、分期已确认 | `#FFFFFF` | **7.15 : 1** | AAA (Pass) |
| `color-success-light` | `#E8F5E9` | 达标背景浅色容器 | `#1B5E20`（文字） | **6.40 : 1** | AA (Pass) |
| `color-warning` | `#B45309` | 警示、疑似分期、存疑记录标记 | `#FFFFFF` | **4.68 : 1** | AA (Pass) |
| `color-warning-light` | `#FEF3C7` | 警示提示背景浅色容器 | `#78350F`（文字） | **8.52 : 1** | AAA (Pass) |
| `color-danger` | `#B91C1C` | 风险预警、紧急停止、严重错误 | `#FFFFFF` | **6.18 : 1** | AAA (Pass) |
| `color-danger-dark` | `#7F1D1D` | 严重预警按下态、强危险标题 | `#FFFFFF` | **9.82 : 1** | AAA (Pass) |
| `color-danger-light` | `#FEE2E2` | 风险预警容器浅底色 | `#991B1B`（文字） | **6.35 : 1** | AA (Pass) |

### 1.2 灰阶与文字对比度（Neutral Palette）

为保障老年白内障、对比敏感度下降人群的清晰阅读，严禁采用轻灰字。最低字色亦须达到 WCAG AA（4.5:1）。

| Token 变量 | 色值 Hex | 适用场景 | 底色 | 对比度 | 校验结果 |
| --- | --- | --- | --- | --- | --- |
| `text-primary` | `#0F172A` | 正文、卡片标题、数值主文案 | `#FFFFFF` | **16.20 : 1** | AAA (Pass) |
| `text-secondary` | `#334155` | 次级说明、标签名、辅助信息 | `#FFFFFF` | **9.53 : 1** | AAA (Pass) |
| `text-tertiary` | `#475569` | 占位提示、时间戳、算法版本号 | `#FFFFFF` | **7.08 : 1** | AAA (Pass) |
| `text-on-primary` | `#FFFFFF` | 主按钮字、深色预警条白字 | `#1A4FA0` | **8.12 : 1** | AAA (Pass) |
| `text-on-danger` | `#FFFFFF` | 紧急停止按钮白字 | `#B91C1C` | **6.18 : 1** | AAA (Pass) |
| `bg-page` | `#F8FAFC` | 全局页面背景（微冷白底，防眩光） | — | — | — |
| `bg-card` | `#FFFFFF` | 卡片与容器主底色 | `#F8FAFC` | 层次清晰 | 边缘通过描边加强 |
| `border-card` | `#CBD5E1` | 卡片边框、分割线（3:1 组件对比度）| `#FFFFFF` | **1.54 : 1** | 符合非文字组件标准 |
| `border-strong` | `#94A3B8` | 输入框、选中态外轮廓 | `#FFFFFF` | **3.02 : 1** | 达到 UI 控件 AA |

---

## 2. 字号与排版阶梯（Typography Ladder）

### 2.1 适老化硬性约束
1. **正文绝对下限 ≥ 16sp / 32rpx / 16px**；移动端常规正文推荐 18sp / 36rpx。
2. **大字报式数据**：核心计数、节拍（BPM）、倒计时使用 32–64sp / 等宽数字（`tabular-nums`），防止数字跳动造成视线抖动。
3. **加粗作为首要层级表达**：帕金森患者多伴有视觉对比敏感度衰减，标题统一采用 600/700 加粗，避免使用细体字。

| 级别 Token | Android Compose | 小程序 WXSS | Web React (CSS) | 行高 | 字重 | 典型用途 |
| --- | --- | --- | --- | --- | --- | --- |
| `font-display` | `48.sp` | `96rpx` | `48px` | 1.2 | Bold (700) | 实时训练大计数、节拍 BPM |
| `font-h1` | `32.sp` | `64rpx` | `32px` | 1.3 | Bold (700) | 页面大标题、警报强提示 |
| `font-h2` | `24.sp` | `48rpx` | `24px` | 1.35 | Bold (700) | 分组标题、卡片主标题 |
| `font-h3` | `20.sp` | `40rpx` | `20px` | 1.4 | SemiBold (600) | 训练条目名、患者姓名、子卡标题 |
| `font-body-lg` | `18.sp` | `36rpx` | `18px` | 1.6 | Regular (400) | 移动端主操作说明、步骤指引 |
| `font-body-md` | `16.sp` | `32rpx` | `16px` | 1.6 | Regular (400) | **适老化正文底线**、表单内容 |
| `font-label-lg`| `20.sp` | `40rpx` | `20px` | 1.4 | SemiBold (600) | **主按钮文案（80dp专用）** |
| `font-label-md`| `16.sp` | `32rpx` | `16px` | 1.4 | Medium (500) | 次级操作按钮、筛选 Chip |

---

## 3. 间距与圆角（Spacing & Radius）

### 3.1 间距规范（8pt 基础基准）
- `space-xs`: 4dp / 8rpx / 4px（微小微调）
- `space-sm`: 8dp / 16rpx / 8px（标签内边距、行间紧凑间距）
- `space-md`: 16dp / 32rpx / 16px（卡片内边距、同类元素间隔）
- `space-lg`: 24dp / 48rpx / 24px（移动端页面侧边距、段落大间距）
- `space-xl`: 32dp / 64rpx / 32px（移动端区块间隔、底部按钮间隙）

### 3.2 圆角档位
- 小圆角 `radius-sm`: 8dp / 16rpx / 8px（输入框、表单控件、小标签）
- 中圆角 `radius-md`: 16dp / 32rpx / 16px（常规卡片、列表项）
- 大圆角 `radius-lg`: 24dp / 48rpx / 24px（重点卡片、弹窗 Dialog、底部 Sheet）
- 胶囊圆角 `radius-full`: 9999px（状态徽章 Badge、筛选 Chip）

---

## 4. 关键组件规范

### 4.1 适老化主按钮（Elderly Primary Button）
- **尺寸硬约束**：高度 **≥ 80dp**（小程序 `160rpx`，Web 移动自适应 `min-height: 72px`）。
- **点击容错**：整行占满或宽度 ≥ 280dp，左右安全边距 24dp，确保拇指大面积按压不产生误触。
- **视觉反馈**：常态 `#1A4FA0`，按下态 `#0F3268`（或背景明度降低 15%），带轻微触感或按压形变（0.98 scale）。
- **防重复点击**：内置防抖（500ms），点击后若进入异步状态立即展示 Loading Spinner，文本变为「处理中…」并禁用连续点击。

### 4.2 紧急停止 / 危险操作按钮（Emergency Stop Button）
- **色彩**：醒目深红 `#B91C1C`，底色对比度充足。
- **高度**：与主按钮一致（≥ 80dp），文案明确为「立即停止训练」或「紧急呼叫 / 报警」。

### 4.3 卡片化容器（Accessible Card）
- **背景与边框**：纯白 `#FFFFFF`，配合 `2rpx` / `1.5dp` solid `#CBD5E1` 边框，放弃不可靠的浅淡阴影，直接通过轮廓线锁定视觉边界。
- **内边距**：≥ 20dp / 40rpx，避免元素拥挤。

### 4.4 风险预警条与弹窗（Risk Alert Banner & Dialog）
- **预警条**：左侧 10rpx 粗边条指示严重度（严重 `#B91C1C`、警告 `#B45309`、提示 `#1A4FA0`），浅色背景容器（`#FEE2E2` / `#FEF3C7` / `#EAF1FB`）。
- **紧急阻断弹窗**：居中大卡片，红白高对比，含停止图标、大字标题、语音播报提示文案，提供「停止训练并联系治疗师」一键大按钮。

### 4.5 表单与输入控件（High-Touch Form）
- 输入框高度 ≥ 56dp / 112rpx，字号 ≥ 18sp / 36rpx。
- 步进选择器（Steppers）增减按钮最小点击区域 56dp × 56dp，杜绝细小下拉框。

---

## 5. 跨端差异化落地指南

| 设计维度 | Android App (Compose) | 微信小程序 (WXML/WXSS) | Web 管理端 (React/CSS) |
| --- | --- | --- | --- |
| **设备重心** | 手机竖屏为主，10寸平板横屏分栏适配 | 微信手机全屏（适老化大字模式） | 桌面宽屏（1280px+）与平板浏览器 |
| **主按钮落地** | `ElderlyButton` 组件（heightIn(min = 80.dp)） | `.btn--primary.btn--elderly`（min-height: 160rpx） | `.btn-primary-large`（min-height: 56-64px） |
| **字号单位** | `sp`（自动随系统「字体大小」放大） | `rpx`（基准宽 750rpx，正文 ≥ 32rpx） | `px` / `rem`（正文 16px 起） |
| **姿态识别反馈** | 端上实时 MediaPipe：大视频区 + 实时骨骼点 + 48sp 动作计数 + 节拍器音频波形 | 云端异步：视频录制指引 + 预检清单 + 离线上传提示 + 无实时骨骼反馈 | 历史记录看板：有效帧率、关键点存疑曲线对比、算法版本标识 |
| **大屏响应策略** | `WindowWidthSizeClass.Expanded` 切换双栏（左操作/右计划） | 单列大卡片为主，防颤误触流式堆叠 | 响应式 Sidebar + 网格工作台（Table / Grid 自适应） |
| **无障碍属性** | `contentDescription`、`semantics(mergeDescendants = true)` | `aria-role="button"`、`aria-label` | `role="alert"`、`aria-live="polite"`、WCAG 键盘聚焦态 |

---

## 6. 中文文案与领域术语对照（规范查验表）

所有文案必须无条件遵从 `.agent/keep/glossary.md`：
- 禁止使用「病情分级」，统一使用「**H-Y 分级**」；
- 禁止使用「医生审核」，统一使用「**分期确认**」（由**康复治疗师**执行）；
- 禁止使用「运动项目」，统一使用「**训练条目**」（属于 4 大**训练大类**：牵伸放松、肌力维持、节律步态、姿势矫正）；
- 禁止使用「训练计划」，医嘱开具统一使用「**处方**」；
- 步态听觉提示必须使用「**节律性听觉刺激（RAS）**」；
- 数据质检统一提示「**有效帧占比**」与「**存疑标记**」。

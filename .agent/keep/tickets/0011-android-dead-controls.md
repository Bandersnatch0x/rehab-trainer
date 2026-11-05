---
id: "0011"
type: task
status: open
assignee: null
part-of: null
blocked-by: []
created: 2026-11-05
---

# 安卓端死按钮与假开关

目标：按 `.agent/tmp/orca-specs/13-android-dead-controls.md` 消除安卓端「可点但无反应」的控件——要么真做，要么置灰并说明（对应 `REVIEW-ANDROID.md` P2-1）。

范围：仅 `apps/android/`、本票与 `.agent/tmp/`。不改 `TrainingScreen.kt`、`RehabApp.kt`、`RehabNavHost.kt`；不改契约、后端、决策记录；不新增依赖、不申请多余权限。

验收：

- 绑定照护者账号：`enabled = false` + 可见说明。
- 解除设备绑定：二次确认后清除绑定态并给出明确后续状态。
- 呼叫康复治疗师：`ACTION_DIAL` 打开拨号盘；号码来自 `BuildConfig.CONTACT_PHONE`，未配置时提示联系机构。
- 语音节拍 / 防颤振动 / 高对比度三个开关：真实生效并持久化。
- `gradlew :app:testDebugUnitTest`、`:app:assembleDebug` 通过；`git diff --check` 通过。
- 真机逐项点击截图；`.agent/tmp/ANDROID-CONTROLS.md` 记录处理方式、证据与未做项。

## Log

### 2026-11-05 建票

来源：`.agent/tmp/orca-specs/13-android-dead-controls.md`（主仓 `.agent/tmp/orca-specs/`），对应真机评审 P2-1。

### 2026-11-05 实现与真机验证

- 6 项全部处置：1 项置灰（照护者绑定，缺后端契约），5 项真做（解绑、拨号、三个开关）。
- 新增 `TherapistDialer`（纯逻辑可测）、`SettingsRuntime`、`Haptics`；`AppPreferences` 扩三项偏好。
- 高对比度顺带修正基础 `outline` 从 `#CBD5E1`（约 1.5:1）到 `#64748B`（约 4.8:1）。
- `gradlew :app:testDebugUnitTest :app:assembleDebug` 通过；新增 3 项拨号单测。
- 详情与证据：`.agent/tmp/ANDROID-CONTROLS.md`、`.agent/tmp/shots/controls/`。
- 残余：解绑后未跳转绑定入口页（规格禁止改 `RehabNavHost`），改为设置页原地状态说明。

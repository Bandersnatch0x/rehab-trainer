---
id: "0013"
type: task
status: closed
assignee: claude
part-of: null
blocked-by: []
created: 2026-11-05
---

# 安卓评审 P2 遗留项收尾（演示标注 / 旋转暂停 / 紧急停止反馈）

目标：按 `.agent/tmp/DECISIONS-NEXT.md` 第 6 条与 D29，完成三项必修：

1. P2-2：`AlertsScreen`、`ExercisesScreen`、`RecordsScreen`、`SettingsScreen` 补「演示数据」声明，覆盖页内写死的临床数值。
2. P2-4：训练页 `isPaused` 改 `rememberSaveable`，旋转重建后保持暂停且不自动续播。
3. P2-3：紧急停止先给可见反馈（Toast「训练已停止，本次未保存记录」）再退出，不做二次确认。

范围：`apps/android/app/src/main/java/com/rehabtrainer/app/ui/screens/` 下上述四页与 `TrainingScreen.kt`（仅 `isPaused`、紧急停止反馈两处）。不动 `data/offline/`、`core/training/`、契约与后端，不引入新依赖。

验收形状：

- 四页 `grep -c "演示数据"` 均不为 0，且声明随 mock 数据注释说明。
- `gradlew :app:testDebugUnitTest`（42 项不回退）与 `:app:assembleDebug` 通过。
- `git diff --check` 通过。

## Log

### 2026-11-05 开工

认领人：claude。依据评审报告 P2-2/P2-3/P2-4 与决策第 6 条。P2-5（Q 版插画）、P2-6（细节打磨）按决策接受为已知限制，不在本票范围。

### 2026-11-06 完成与决议

处理：
- P2-2：`AlertsScreen`、`ExercisesScreen`、`RecordsScreen`、`SettingsScreen` 各加页内「演示数据」声明；设置页声明只覆盖机构/治疗师 mock 卡，三个真实偏好开关不受影响；训练条目页的联系弹窗内另加一行声明；每处加 D21 注释，注明接真实 API 时随 mock 数据移除。
- P2-4：`TrainingScreen.kt:124` 的 `isPaused` 改 `rememberSaveable`；主路径（暂停中旋转且不再操作）由恢复态在首次组合前生效保证不自动续播——`onPreparedListener` 与 `update` 均读到 `true`。准备窗口内切换暂停态的边缘竞态见「已知限制」。计数清零保持现状。
- P2-3：`TrainingScreen.kt:263-267` 先 Toast「训练已停止，本次未保存记录」（`LENGTH_LONG`）再调原 `onEmergencyStop`；不做二次确认；按钮常驻与 80dp 未改动。

被否选项：
- 二次确认弹窗——安全优先，规格明确不做，且多一步确认会延后停止生效。
- 页面内状态提示——`popBackStack()` 立即销毁训练页，提示随页面消失；Toast 跨页面仍可见。
- 在 `RehabNavHost.kt` 导航层加反馈——超出本票写入边界（只允许四页 + `TrainingScreen.kt` 两处）。
- 逐值追加 `【演示数据】` 角标——页面级声明一次覆盖全部 mock 值，且设置页真实开关与 mock 机构信息混排时逐值角标更易误标真实数据。

独立审查（规格轴 + 标准轴）：无 CRITICAL/HIGH，结论「可交付」。边界内已顺带修：记录页声明点明「模型版本」、训练条目页声明补「适宜性与分期标签」、联系弹窗内补声明、引导语与声明冲突消解。

已知限制（MEDIUM，超出本票边界）：`TrainingPanes.kt:393-398` 的 `onPreparedListener` 按值捕获 `isPaused`，`update`（`:411-413`）在播放器未 Prepared 时调用 `pause()/start()` 无效；准备窗口内点暂停/继续可能与 UI 相反。主路径不受影响，待跟进票用 `rememberUpdatedState` 修正。

证据：四页 `grep -c "演示数据"` 分别为 1/2/1/2（Exercises 含弹窗声明，Settings 含注释）；`gradlew :app:testDebugUnitTest :app:assembleDebug` BUILD SUCCESSFUL（42 项单测 0 失败）；`git diff --check` 退出码 0；详见 `.agent/tmp/ANDROID-P2.md`。

未做：真机验证（`adb devices` 无设备）。“旋转保持暂停与紧急停止反馈”已由源码链路核查覆盖，待有设备补验（含上述边缘路径）。P2-5/P2-6 按决策接受为已知限制。

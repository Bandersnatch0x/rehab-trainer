---
id: "0009"
type: task
status: closed
assignee: null
part-of: null
blocked-by: []
created: 2026-11-05
---

# 训练执行页显示模式与语音提示

目标：按 `.agent/tmp/orca-specs/11-training-modes-voice.md` 补齐训练页音频（系统 TTS + 节拍音）、组状态机、D17 足部门禁真拦截、D11 分期准入，并修正显示模式在旋转后的持久化与视频释放。

范围：仅 `apps/android/`、本票与 `.agent/tmp/`。不改契约、后端与决策记录；不引入系统 TTS 以外的依赖；不改视觉设计 worker 负责的样式。

验收：

- `JAVA_HOME=E:\jdk\jdk-17.0.11`、`ANDROID_HOME=D:\Android\Sdk` 下 `gradlew :app:testDebugUnitTest` 与 `:app:assembleDebug` 通过；现有 18 项单测继续通过，新增策略与状态机单测。
- 真机（OPPO PKX110）验证双播放 / 纯拍摄+语音两种模式与旋转；`dumpsys audio` 或录音确认有真实音频输出。
- 组状态机推进、计数封顶、完成态可观察；D17 门禁阻断计数并在条件满足后自动恢复；D11 未确认分期时姿势矫正训练不可开始。
- `git diff --check` 通过。
- `.agent/tmp/TRAINING-MODES.md` 记录改动、实测证据与未做项。

## Log

### 2026-11-05 建票

来源：`.agent/tmp/orca-specs/11-training-modes-voice.md`，对应真机评审 P0-2 / P0-3 / P0-4 / P0-5。

### 2026-11-05 实现与真机验证

- 组状态机与计数封顶、D17 计数冻结与机位图示、D11 分期禁用与联系入口均已落地；TTS + 64 BPM 节拍音在 OPPO PKX110 实测有音频输出。
- 关键修复：`AndroidManifest.xml` 增加 `<queries>`，解决 Android 11+ 系统 TTS 引擎不可见导致初始化失败。
- `gradlew :app:testDebugUnitTest :app:assembleDebug` 通过；34 项单测（原 18 + 新增 16）0 失败。
- 详情与证据：`.agent/tmp/TRAINING-MODES.md`、`.agent/tmp/shots/`。
- 待独立 code-reviewer 结论后关票；D17 真人自动恢复、12 次封顶与真平板布局未在设备上实测，已记录为残余风险。

### 2026-11-05 协调者验收与关票

由协调者复核并合并（c01d467 + 合并 a853f35）。已独立复跑 Android 单测 34 项全通过；真机验证：组状态机 1/3→3/3→完成、沉浸模式隐藏菜单、纯拍摄+语音、D17 拦截生效。未验证：真人入框自动恢复、手臂动作计数封顶、真平板布局。

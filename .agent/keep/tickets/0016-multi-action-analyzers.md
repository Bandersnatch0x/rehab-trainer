---
id: "0016"
type: "feature"
status: "closed"
assignee: "claude"
part-of: null
created: 2026-11-06
---

# 多动作计数与规则法达标判定（踏步 / 微蹲）

目标：把当前只有「右臂抬举」一个计数器的实现，扩展为按训练条目分派的动作分析器；
先覆盖原地踏步与靠墙微蹲的计数 + 规则法达标判定，做到纯逻辑可单测，不依赖真机。

范围：`apps/android/` 的 `core/pose/`、`ui/screens/TrainingScreen.kt`、`TrainingPanes.kt`、
`ui/RehabApp.kt`；不改后端、契约、小程序；不引入新依赖；不改变训练页布局与安全交互。

验收：

- 纯 Kotlin `PosePoint` / `ExerciseAnalyzer` 抽象，不依赖 Android 与 MediaPipe。
- `ArmRaiseAnalyzer`：保留原有抬举计数语义，增加幅度与躯干前倾达标判定。
- `MarchAnalyzer`：左右膝角状态机计数；单次踏步按「膝屈曲峰值 + 躯干前倾」判定，
  幅度不足或躯干代偿给出问题码。
- `SquatAnalyzer`：双膝平均膝角状态机计数；判定深度（微蹲范围）、膝内扣、左右不对称、
  躯干前倾，输出问题码。
- `PoseAnalyzer` 按 `ExerciseKind` 分派；`PoseFrame.Detected` 返回总计数与最近一次
  动作判定；暂停/脚部门禁/换组时重置。
- 训练页计数标签按条目显示「踏步次数 / 抬举次数 / 蹲起次数」；最近一次判定以
  文字提示（达标 / 具体问题），姿势矫正条目如实标注计数未接入。
- JVM 单测覆盖：三种分析器的计数、阈值边界、问题码、reset；用合成关键点序列，
  不依赖真机。原有 `ArmRaiseCounterTest` 保持通过（可由新测试替代但语义不得丢失）。
- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon` 与
  `python scripts/verify_all.py` 通过。

非目标：

- 不训练机器学习模型；不做动作视频模板匹配（DTW）；不做姿势矫正计数。
- 不把规则阈值当作临床结论；阈值进配置并标注「待临床签核」（D30）。
- 不做真机准确度验证（需 T6 与设备）。

## Log

### 2026-11-06 建票

由用户指令创建：先实现多动作计数 + 规则法达标判定的可单测版本，再评估是否需要模型训练。

### 2026-11-06 实现与验证

- 新增 `PoseGeometry.kt`、`ExerciseAnalyzer.kt`、`ExerciseAnalyzers.kt`：平台无关关键点快照与三个规则分析器。
- `ArmRaiseAnalyzer`：抬举幅度 + 躯干前倾判定；`MarchAnalyzer`：左右腿踝-髋垂直距离状态机；`SquatAnalyzer`：髋-踝距离状态机 + 深度/膝内扣/左右不对称/躯干前倾。
- `PoseAnalyzer` 按 `ExerciseKind` 分派；`PoseFrame.Detected` 返回总计数与最近一次判定；暂停、脚部门禁、换组时重置。
- 训练页计数标签按条目显示「踏步次数 / 抬举次数 / 蹲起次数」，并显示「本次达标」或具体问题；姿势矫正如实标注待接入。
- 新增 `ExerciseAnalyzersTest`（17 项）；删除已被取代的 `ArmRaiseCounter` 与其 6 项旧测试。
- `gradlew :app:testDebugUnitTest :app:assembleDebug` 通过（90 项 / 13 类）；`python scripts/verify_all.py` ALL PASS。
- 未做：真机准确度验证、姿势矫正计数、DTW/模型训练；阈值标注为文献/工程预设，待专业签核。

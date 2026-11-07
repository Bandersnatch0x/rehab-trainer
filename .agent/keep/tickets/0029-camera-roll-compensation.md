---
id: "0029"
type: "bugfix"
status: "done"
assignee: "claude"
part-of: null
created: 2026-11-07
---

# 相机横滚补偿（手机斜放被误判为患者侧倾）

## 现象

手机斜放（放桌上、靠枕垫高、手持歪了）时，患者明明站直，训练页却持续提示「调整：躯干侧倾」，
正常动作被记为**不达标**：`movementErrorCount` 虚高，患者以为设备坏了。

## 根因

`PoseAnalyzer` 只用 `imageProxy.imageInfo.rotationDegrees` 做了**图像旋转**（把画面转正），
没有补偿**相机相对重力的横滚**。整副骨架因此带着恒定的倾角进入分析器，
`PoseGeometry.lateralLeanDegrees` 把机位倾斜读成躯干侧倾。

## 修复

横滚不落状态，只是一个从关键点导出的派生量加一次纯函数坐标校正：

- `PoseGeometry.cameraRollRadians`：参考线优先双眼（索引 2/5），都不可见时退化双耳（7/8），
  两者可见度门槛均为 0.5。`atan2` 的线方向折到 (-90°, 90°]，水平线返回 0，
  左右索引因镜像颠倒时也不会返回约 180° 而被上限误挡。
  死区 0.5°（噪声不值得校正），上限 20°（更可能是躺姿或极端机位，宁可不动）。
- `PoseGeometry.derotate`：以参考线两点中点为轴心，把全部关键点绕 z 轴旋转 -roll，
  `z` 与 `visibility` 原样保留。旋转是等距变换，**分析器阈值一个都不用改**。
- `PoseAnalyzer.handleResult`：在 `landmarks.map` 之后、`footVisibility` 等所有下游使用之前校正一次。

## 证据

- 新增 `CameraRollCompensationTest`（8 项，JVM 合成关键点）：
  - 缺陷复现：整体倾斜 15° 的踏步序列 → `valid == false` 且 `issues` 含 `LATERAL_LEAN`；
  - 同一序列先 `derotate` 再喂 → `valid == true` 且无 `LATERAL_LEAN`（两例共用同一副倾斜骨架，横滚是唯一变量）；
  - 10° 测量误差 ≤ 0.5°、左右索引颠倒结果一致、参考点不可见返回 null、仅双眼不可见时退化双耳、35° 返回 null、
    `derotate` 保持两点距离与 `z`/`visibility`。
- 全量 `:app:testDebugUnitTest`：24 个 suite、**175 项、0 失败**（基线 162 + 本票 8 + 票 0030 的 5）。

## 局限

- **参考线等价于「头部保持直立」假设。** 患者头相对躯干倾斜（帕金森常见的头前倾/歪头）时，
  会把整副骨架转错，抵消掉真实的躯干侧倾。20° 上限只挡极端情况，不解决这一假设。
- 双眼切双耳时轴心从眼中点换到耳中点，两条参考线角度不一致会**逐帧抖动**，未做平滑。
- `PoseAnalyzer` 的接线只有编译验证：JVM 单测测不到 MediaPipe 路径，真机复验待设备重连。

## 待专业签核

20° 上限与「完全校正」的取舍、以及头倾斜患者的例外处理，属于临床判断，当前值是工程预设。

## 调研结论（2026-11 补充）

**换 `world_landmarks` 解决不了这个问题**：
`world_landmark_projection_calculator.cc` 会按 ROI 的旋转把世界坐标转回相机坐标系，
官方也从未定义其朝向（`google-ai-edge/mediapipe` issue #3370 关闭未答）。
世界坐标与 2D 坐标一样带着机位横滚。

**专业管线的参考物是地面，不是头。** Sports2D（JOSS 2024，DOI `10.21105/joss.06849`）默认
`--correct_segment_angles_with_floor_angle true`：用地面倾角补偿相机倾斜，零解剖学假设。

**下一步（推荐）**：用 Android 重力传感器替代眼线——
`Sensor.TYPE_GRAVITY`（官方定义：「a three dimensional vector indicating the direction and
magnitude of gravity」，坐标系与加速度计相同）给出重力在相机坐标系的投影，
投影到图像平面即横滚角，**与手机怎么摆、患者头怎么歪都无关**。
眼线降级为传感器缺失时的兜底。

代价：传感器有噪声与延迟，需低通；患者碰撞桌子时的瞬时抖动要抑制。
实现要动 `TrainingScreen`（`SensorManager` 注册/注销）与 `PoseAnalyzer`（注入 roll），
纯计算部分可用 JVM 单测钉住，但**整条链路仍需真机复验**，当前设备未连，暂不实施。

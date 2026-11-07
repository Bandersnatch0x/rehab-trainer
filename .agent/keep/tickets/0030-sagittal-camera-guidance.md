---
id: "0030"
type: "feature"
status: "reverted"
assignee: "claude"
part-of: null
created: 2026-11-07
---

# 矢状面动作的机位引导（已撤回）

## 结论先行

**本票的前提是错的，实现已撤回。** 全套分析器测的都是额状面量，侧身拍会让它们静默失效；
正确的机位要求是**所有动作都正面朝向镜头**，没有按动作区分的必要。

## 原假设

票 0021 记录：坐位伸膝、踏步、蹲起深度这些「矢状面动作」从正面用 2D 关键点测不可靠，
患者做了不计数。于是按动作给出「侧身 / 45°」的站位提示。

## 为什么错：分析器实际用的量

| 分析器 | 计数信号 | 质量检查 |
|---|---|---|
| `MarchAnalyzer` | 踝-髋**垂直**距离 | 侧倾（额状面） |
| `SquatAnalyzer` | 踝-髋**垂直**跨距 | 侧倾、膝-踝**水平**偏移、髋**左右**高度差 |
| `SeatedLegRaiseAnalyzer` | 膝-髋**垂直**距离 | 侧倾（额状面） |
| `StretchAnalyzer` | 腕-肩垂直距离 ÷ **肩宽** | 肩**左右**高度差 |
| `PostureAnalyzer` | — | `\|肩中点x − 髋中点x\|`、头偏移 |

计数信号全是垂直位移：**绕垂直轴的偏航不改变 y**，所以正面拍照样有效。
质量检查全是额状面量：侧身拍时左右肢体重合，这些量被压成 0 或变成别的量。

侧身拍的具体后果：
- `PostureAnalyzer` 的侧倾 → 0，`LATERAL_LEAN` 永不触发（静默假阴性）。
- `SquatAnalyzer` 的膝外翻算的是膝-踝**水平**偏移；侧身拍时这个量是膝盖**前移**，正常蹲起会**每次误报**外翻。
- `StretchAnalyzer` 用肩宽做归一化；侧面肩宽趋近 0，被 `width < 1e-3f` 兜住后整组停止计数。
- `MarchAnalyzer` 的左右腿靠索引区分；侧身拍时远侧腿被遮挡，左右判定不可靠。

45° 更差：既不垂直矢状面也不垂直额状面，所有额状面量被 `cos45° ≈ 0.71` 等比压缩，
阈值语义整体偏移 29%，而垂直计数信号一点没受益。

## 文献与实现依据

- **Sports2D**（`davidpagnon/Sports2D`，JOSS 2024，DOI `10.21105/joss.06849`）README 原文：
  「Results are acceptable only if the persons move in the 2D plane (sagittal or frontal).
  The persons need to be filmed as parallel as possible to the motion plane.」
  —— **拍摄方向要平行于运动平面**，没有 45° 这一档。
- **Sensors 2026**（DOI `10.3390/s26196248`）用 MediaPipe 对 8 相机光电系统做视角依赖验证：
  平面内动作 ICC 0.63–0.82（RMSE 9–11°）；**离面动作 ICC ≤ 0.34（RMSE > 30°）**。
  离面分量不是优雅降级，是直接不可测。
- **Munro 2012**（J Sport Rehabil 21(1):7-11）2D 额状面投影角（FPPA）的 SEM 2.72–3.01°、
  最小可检测差 7.54–8.93° —— 膝外翻本来就测不细，不值得为它牺牲其它指标。
- **Paul 2016**（Anat Rec 299(9):1165-73）2D vs 3D：坐立转移 ICC 0.59–0.93，
  低于单腿站立/推放（0.96–0.99）——坐位动作本就有精度上限。

顺带确认（票 0029 相关）：MediaPipe 的 `world_landmarks` **不能**替代横滚补偿。
`world_landmark_projection_calculator.cc` 会按 ROI 的旋转把世界坐标转回相机坐标系，
官方也从未定义其朝向（`google-ai-edge/mediapipe` issue #3370 关闭未答）。

## 撤回内容

- `CameraView` 枚举与 `ExerciseKind.cameraView` 字段删除，`ExerciseKind` 回到无参枚举。
- 训练页 `statusText` 恢复为「距离提示 → 识别正常」。
- `CameraViewGuidanceTest.kt` 删除。

## 留下的认知

- 机位不是「每个动作的属性」——它由**分析器测什么**决定，而本套分析器统一是额状面，所以统一正面。
- 真正需要引导的是**取景**（距离/全身入镜，已有 `framingHint`）和**水平**（相机横滚，见票 0029）。
- 若将来真要支持矢状面动作（如膝关节屈曲角），那是**另一个分析器**，得同时提供侧身机位引导，
  并且不能和现有的额状面质量检查混用。

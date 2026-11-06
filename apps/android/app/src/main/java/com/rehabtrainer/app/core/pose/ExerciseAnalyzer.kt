package com.rehabtrainer.app.core.pose

/** 训练条目对应的动作分析器类型。 */
enum class ExerciseKind {
    ARM_RAISE,
    MARCH,
    SQUAT,
    POSTURE,
    STRETCH,
    SEATED_LEG_RAISE
}

/** 单次动作未达标的原因；UI 负责翻译成患者可读文案。 */
enum class RepIssue {
    INSUFFICIENT_RANGE,
    EXCESSIVE_DEPTH,
    KNEE_VALGUS,
    ASYMMETRY,
    SHOULDER_UNEVEN,
    HIP_UNEVEN,
    HEAD_OFFSET,
    LATERAL_LEAN,
    ELBOW_BENT,
    POSE_INCOMPLETE
}

data class RepResult(
    val index: Int,
    val valid: Boolean,
    val issues: Set<RepIssue>
)

data class AnalysisUpdate(
    val count: Int,
    val lastRep: RepResult?,
    val currentIssues: Set<RepIssue> = emptySet()
)

/**
 * 纯逻辑动作分析器：输入关键点快照，输出累计次数与最近一次动作判定。
 * 不依赖 Android/MediaPipe，便于用合成序列做 JVM 单测。
 */
interface ExerciseAnalyzer {
    val count: Int
    val lastRep: RepResult?

    /** 校准阶段还需完成几次动作；0 表示已进入正式计数。 */
    val calibrationRemaining: Int get() = 0

    fun update(points: List<PosePoint>, timestampMs: Long = 0L): AnalysisUpdate

    fun reset()
}

fun createExerciseAnalyzer(
    kind: ExerciseKind,
    calibrationReps: Int = 2
): ExerciseAnalyzer = when (kind) {
    ExerciseKind.ARM_RAISE -> ArmRaiseAnalyzer()
    ExerciseKind.MARCH -> MarchAnalyzer(calibrationReps = calibrationReps)
    ExerciseKind.SQUAT -> SquatAnalyzer(calibrationReps = calibrationReps)
    ExerciseKind.POSTURE -> PostureAnalyzer()
    ExerciseKind.STRETCH -> StretchAnalyzer()
    ExerciseKind.SEATED_LEG_RAISE -> SeatedLegRaiseAnalyzer(calibrationReps = calibrationReps)
}
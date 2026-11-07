package com.rehabtrainer.app.core.pose

/**
 * 机位要求：由「这个动作怎么测」决定，不是动作的固有属性。
 * [hint] 为 null 表示正面机位无需额外引导。
 */
enum class CameraView(val hint: String?) {
    FRONTAL(null),
    FORTY_FIVE("请侧转 45°：介于正侧之间"),
    SAGITTAL("请侧身站立，让镜头拍到侧面")
}

/** 训练条目对应的动作分析器类型；[cameraView] 决定训练页给患者的站位引导。 */
enum class ExerciseKind(val cameraView: CameraView) {
    ARM_RAISE(CameraView.FRONTAL),

    /** 髋屈曲在正面会被"朝向镜头"压缩，幅度测不出来。 */
    MARCH(CameraView.SAGITTAL),
    SQUAT(CameraView.FORTY_FIVE),
    POSTURE(CameraView.FRONTAL),
    STRETCH(CameraView.FRONTAL),

    /** 膝屈伸在正面 2D 里不可测。 */
    SEATED_LEG_RAISE(CameraView.SAGITTAL)
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

    /**
     * 本次使用的个人幅度：校准完成后为实测平均值，传入已知幅度时即为该值。
     * 调用方可把它按 患者+条目 存下来，下次直接沿用。不支持校准的分析器返回 null。
     */
    val calibratedAmplitude: Float? get() = null

    fun update(points: List<PosePoint>, timestampMs: Long = 0L): AnalysisUpdate

    fun reset()
}

/**
 * @param knownAmplitude 上次存下的个人幅度；有值时跳过校准直接计数。
 */
fun createExerciseAnalyzer(
    kind: ExerciseKind,
    calibrationReps: Int = 2,
    knownAmplitude: Float? = null
): ExerciseAnalyzer = when (kind) {
    ExerciseKind.ARM_RAISE -> ArmRaiseAnalyzer()
    ExerciseKind.MARCH -> MarchAnalyzer(calibrationReps = calibrationReps, knownAmplitude = knownAmplitude)
    ExerciseKind.SQUAT -> SquatAnalyzer(calibrationReps = calibrationReps, knownAmplitude = knownAmplitude)
    ExerciseKind.POSTURE -> PostureAnalyzer()
    ExerciseKind.STRETCH -> StretchAnalyzer()
    ExerciseKind.SEATED_LEG_RAISE ->
        SeatedLegRaiseAnalyzer(calibrationReps = calibrationReps, knownAmplitude = knownAmplitude)
}
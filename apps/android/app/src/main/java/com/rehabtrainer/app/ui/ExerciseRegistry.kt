package com.rehabtrainer.app.ui

import com.rehabtrainer.app.core.pose.ExerciseKind
import com.rehabtrainer.app.data.contract.ExerciseCategory
import com.rehabtrainer.app.data.session.PlanExercise
import com.rehabtrainer.app.data.session.PlanSelector

/**
 * 当前训练条目的唯一读取点。
 *
 * - 已绑定设备：由服务端当前训练方案映射得到；
 * - 未绑定/方案为空：回退到 APK 内置演示条目。
 */
object ExerciseRegistry {

    @Volatile
    private var current: List<ExerciseMeta> = DEFAULT_EXERCISES

    @Volatile
    var isPlanLoaded: Boolean = false
        private set

    val exercises: List<ExerciseMeta>
        get() = current

    fun updateFromPlan(plan: List<PlanExercise>) {
        isPlanLoaded = plan.isNotEmpty()
        current = if (plan.isEmpty()) DEFAULT_EXERCISES else plan.map(::toExerciseMeta)
    }

    fun reset() {
        isPlanLoaded = false
        current = DEFAULT_EXERCISES
    }
}

internal fun toExerciseMeta(plan: PlanExercise): ExerciseMeta = ExerciseMeta(
    code = plan.code,
    name = plan.label,
    shortName = plan.label.take(6),
    target = if (PlanSelector.hasRas(plan.category)) {
        "目标 ${plan.reps} 次 · ${plan.bpm} BPM"
    } else {
        "目标 ${plan.reps} 次 · 自主节律"
    },
    hasRas = PlanSelector.hasRas(plan.category),
    bpm = plan.bpm,
    videoTitle = "${plan.label} · 标准示范",
    prompt = "“按示范完成「${plan.label}」，保持呼吸均匀，出现不适立即停止”",
    sets = plan.sets,
    repsPerSet = plan.reps,
    requiresFullBody = PlanSelector.requiresFullBody(plan.category),
    restSeconds = plan.restSeconds,
    counterLabel = counterLabelFor(plan.category, plan.label),
    kind = kindFor(plan.category, plan.label),
    videoUri = plan.videoUri,
    watermarkText = plan.watermarkText,
    creditLine = plan.creditLine,
    riskLevel = plan.riskLevel
)

/**
 * 同一分类下动作可能不同（如「靠墙微蹲」与「坐位抬腿」都属于肌力维持），
 * 优先按条目标签识别动作模式，识别不了再回退到分类。
 */
internal fun kindFor(category: String, label: String = ""): ExerciseKind = when {
    label.contains("抬腿") || label.contains("伸膝") -> ExerciseKind.SEATED_LEG_RAISE
    label.contains("微蹲") || label.contains("下蹲") -> ExerciseKind.SQUAT
    label.contains("牵伸") || label.contains("伸展") -> ExerciseKind.STRETCH
    label.contains("步行") || label.contains("踏步") -> ExerciseKind.MARCH
    label.contains("站立") || label.contains("姿势") || label.contains("矫正") ->
        ExerciseKind.POSTURE
    category == ExerciseCategory.RHYTHMIC_GAIT.wireValue -> ExerciseKind.MARCH
    category == ExerciseCategory.STRENGTH_MAINTENANCE.wireValue -> ExerciseKind.SQUAT
    category == ExerciseCategory.POSTURAL_CORRECTION.wireValue -> ExerciseKind.POSTURE
    category == ExerciseCategory.STRETCHING_RELAXATION.wireValue -> ExerciseKind.STRETCH
    else -> ExerciseKind.ARM_RAISE
}

private fun counterLabelFor(category: String, label: String): String =
    when (kindFor(category, label)) {
    ExerciseKind.MARCH -> "踏步次数"
    ExerciseKind.SQUAT -> "蹲起次数"
    ExerciseKind.POSTURE -> "保持次数"
    ExerciseKind.STRETCH -> "伸展保持次数"
    ExerciseKind.SEATED_LEG_RAISE -> "抬腿次数"
    ExerciseKind.ARM_RAISE -> "抬举次数"
}
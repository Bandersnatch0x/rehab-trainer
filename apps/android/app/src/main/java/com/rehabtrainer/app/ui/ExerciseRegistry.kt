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
    counterLabel = counterLabelFor(plan.category),
    kind = kindFor(plan.category),
    videoUri = plan.videoUri,
    watermarkText = plan.watermarkText,
    creditLine = plan.creditLine,
    riskLevel = plan.riskLevel
)

internal fun kindFor(category: String): ExerciseKind = when (category) {
    ExerciseCategory.RHYTHMIC_GAIT.wireValue -> ExerciseKind.MARCH
    ExerciseCategory.STRENGTH_MAINTENANCE.wireValue -> ExerciseKind.SQUAT
    ExerciseCategory.POSTURAL_CORRECTION.wireValue -> ExerciseKind.POSTURE
    ExerciseCategory.STRETCHING_RELAXATION.wireValue -> ExerciseKind.STRETCH
    else -> ExerciseKind.ARM_RAISE
}

private fun counterLabelFor(category: String): String = when (kindFor(category)) {
    ExerciseKind.MARCH -> "踏步次数"
    ExerciseKind.SQUAT -> "蹲起次数"
    ExerciseKind.POSTURE -> "保持次数"
    ExerciseKind.STRETCH -> "伸展保持次数"
    ExerciseKind.ARM_RAISE -> "抬举次数"
}
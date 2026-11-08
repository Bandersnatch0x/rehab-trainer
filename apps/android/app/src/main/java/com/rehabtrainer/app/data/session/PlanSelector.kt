package com.rehabtrainer.app.data.session

import com.rehabtrainer.app.data.contract.Dosage
import com.rehabtrainer.app.data.contract.Exercise
import com.rehabtrainer.app.data.contract.ExerciseCategory
import com.rehabtrainer.app.data.contract.Prescription
import com.rehabtrainer.app.data.contract.PrescriptionStatus
import com.rehabtrainer.app.data.contract.SupportLevel

/**
 * 训练方案选择与合并（纯逻辑，便于单测）。
 *
 * 时间比较用服务器返回的 `YYYY-MM-DDTHH:MM:SS` 前缀做字典序比较：
 * 服务端存 UTC naive 时间，两端格式一致，避免引入额外日期依赖。
 */
object PlanSelector {

    /** 只返回当前有效处方；没有有效处方时不再回退到最近一份已过期/停用的处方。 */
    fun selectCurrent(prescriptions: List<Prescription>, nowIsoPrefix: String): Prescription? =
        prescriptions.sortedByDescending { it.issuedAt }.firstOrNull {
            it.status == PrescriptionStatus.ACTIVE && !it.isExpired(nowIsoPrefix)
        }

    fun mergeItems(
        prescription: Prescription?,
        exercises: Map<String, Exercise>
    ): List<PlanExercise> = prescription?.items.orEmpty().mapNotNull { item ->
        val exercise = exercises[item.exerciseCode] ?: return@mapNotNull null
        // 处方可能指定变体：剂量优先取变体默认值，再回退到动作默认值。
        val variant = item.variantCode?.let { code ->
            exercise.variants?.firstOrNull { it.code == code }
        }
        // 处方常只覆盖 repetitions；整块替换会把条目的 sets/bpm/rest 一起抹掉。
        toPlanExercise(
            exercise = exercise,
            dosage = mergeDosage(item.dosage, variant?.defaultDosage ?: exercise.defaultDosage),
            supportLevel = variant?.supportLevel
        )
    }

    private fun mergeDosage(item: Dosage?, default: Dosage?): Dosage? = when {
        item == null -> default
        default == null -> item
        else -> Dosage(
            sets = item.sets ?: default.sets,
            repetitions = item.repetitions ?: default.repetitions,
            bpm = item.bpm ?: default.bpm,
            durationMinutes = item.durationMinutes ?: default.durationMinutes,
            frequencyPerWeek = item.frequencyPerWeek ?: default.frequencyPerWeek,
            restSeconds = item.restSeconds ?: default.restSeconds
        )
    }

    fun toPlanExercise(
        exercise: Exercise,
        dosage: Dosage?,
        supportLevel: SupportLevel? = null
    ): PlanExercise = PlanExercise(
        code = exercise.code,
        label = exercise.label,
        category = exercise.category.wireValue,
        sets = dosage?.sets?.takeIf { it > 0 } ?: DEFAULT_SETS,
        reps = dosage?.repetitions?.takeIf { it > 0 } ?: DEFAULT_REPS,
        bpm = dosage?.bpm?.takeIf { it > 0 } ?: DEFAULT_BPM,
        restSeconds = dosage?.restSeconds?.takeIf { it >= 0 } ?: DEFAULT_REST_SECONDS,
        videoUri = exercise.videoUri,
        watermarkText = exercise.videoWatermarkText,
        creditLine = exercise.videoCreditLine,
        riskLevel = exercise.riskLevel?.wireValue,
        supportLevel = supportLevel?.wireValue
    )

    /** RAS 节拍只用于节律步态类条目。 */
    fun hasRas(category: String): Boolean = category == ExerciseCategory.RHYTHMIC_GAIT.wireValue

    /** 只有需要看下肢的条目才要求脚部入镜；姿势保持/牵伸只看上半身。 */
    fun requiresFullBody(category: String): Boolean = category == ExerciseCategory.RHYTHMIC_GAIT.wireValue ||
        category == ExerciseCategory.STRENGTH_MAINTENANCE.wireValue

    const val DEFAULT_SETS: Int = 2
    const val DEFAULT_REPS: Int = 10
    const val DEFAULT_BPM: Int = 100
    const val DEFAULT_REST_SECONDS: Int = 30
}

private fun Prescription.isExpired(nowIsoPrefix: String): Boolean {
    val until = validUntil?.take(19) ?: return false
    return until < nowIsoPrefix.take(19)
}
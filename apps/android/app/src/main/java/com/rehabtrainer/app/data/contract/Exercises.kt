package com.rehabtrainer.app.data.contract

import kotlinx.serialization.Serializable

/** 契约 `Dosage`。 */
@Serializable
data class Dosage(
    val sets: Int? = null,
    val repetitions: Int? = null,
    val bpm: Int? = null,
    val durationMinutes: Double? = null,
    val frequencyPerWeek: Int? = null,
    val restSeconds: Int? = null
)

/** 契约 `ExerciseVariant`。 */
@Serializable
data class ExerciseVariant(
    val code: String,
    val level: Int,
    val supportLevel: SupportLevel? = null,
    val applicableStages: List<Int>? = null,
    val defaultDosage: Dosage? = null
)

/** 契约 `Exercise`。 */
@Serializable
data class Exercise(
    val code: String,
    val label: String,
    val category: ExerciseCategory,
    val publicationStatus: PublicationStatus,
    val riskLevel: RiskLevel? = null,
    val applicableStages: List<Int>? = null,
    val bodyParts: List<String>? = null,
    val joints: List<String>? = null,
    val contraindications: List<String>? = null,
    val dangerSignals: List<String>? = null,
    val variants: List<ExerciseVariant>? = null,
    val defaultDosage: Dosage? = null,
    val videoUri: String? = null,
    val audioUri: String? = null
)

/** 契约 `ExercisePage`。 */
@Serializable
data class ExercisePage(
    val items: List<Exercise>,
    val nextCursor: String? = null
)

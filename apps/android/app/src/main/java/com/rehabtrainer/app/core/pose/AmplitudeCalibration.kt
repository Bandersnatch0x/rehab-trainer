package com.rehabtrainer.app.core.pose

import kotlin.math.max

/**
 * 单次训练内的动作幅度校准。
 *
 * 前 [requiredReps] 次完成动作只测量、不计数，取平均幅度作为该患者的个人参考：
 * - 计数进入/退出阈值按平均幅度的 35%/15% 设定（带下限），幅度小的患者也能被识别；
 * - 达标阈值取平均幅度的 50%，避免用全局常数把正常动作判为「幅度不足」。
 *
 * 说明：当前为每次训练重新校准；后续可把结果按 患者+条目 持久化。
 */
internal class AmplitudeCalibration(private val requiredReps: Int) {

    private val samples = mutableListOf<Float>()
    private var averageAmplitude: Float? = null

    val isCalibrating: Boolean
        get() = requiredReps > 0 && averageAmplitude == null

    val remaining: Int
        get() = if (!isCalibrating) 0 else (requiredReps - samples.size).coerceAtLeast(0)

    fun record(amplitude: Float): Float? {
        if (!isCalibrating) return averageAmplitude
        if (amplitude.isFinite() && amplitude > 0f) {
            samples.add(amplitude)
        }
        if (samples.size >= requiredReps) {
            averageAmplitude = samples.average().toFloat()
        }
        return averageAmplitude
    }

    fun amplitude(): Float? = averageAmplitude

    fun enterLift(): Float = max(MIN_ENTER_LIFT, (averageAmplitude ?: 0f) * 0.35f)

    fun exitLift(): Float = max(MIN_EXIT_LIFT, (averageAmplitude ?: 0f) * 0.15f)

    fun minValidLift(): Float = max(MIN_VALID_LIFT, (averageAmplitude ?: 0f) * 0.50f)

    private companion object {
        const val MIN_ENTER_LIFT = 0.04f
        const val MIN_EXIT_LIFT = 0.02f
        const val MIN_VALID_LIFT = 0.06f
    }
}
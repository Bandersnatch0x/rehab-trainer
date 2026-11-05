package com.rehabtrainer.app.core.pose

/**
 * 上肢抬举计数状态机（D2 动作计数的最小实现）。
 *
 * 信号：右腕相对肩中点的垂直偏移，除以躯干长度（肩中点→髋中点）。
 * 抬臂时手腕高于肩，偏移为负。
 *
 * 滞回阈值避免关键点抖动被重复计数：
 * - 偏移低于 [raiseThreshold] 记为「抬起」
 * - 抬起后偏移高于 [lowerThreshold] 记为「放下」，计数加一
 *
 * 阈值来自示范视频实测，患者端可能偏小，需临床数据校准（未决项 12）。
 */
class ArmRaiseCounter(
    private val raiseThreshold: Float = -0.4f,
    private val lowerThreshold: Float = -0.1f
) {
    private var isRaised = false

    @Volatile
    var count: Int = 0
        private set

    @Synchronized
    fun update(normalizedWristOffset: Float): Int {
        if (!isRaised && normalizedWristOffset < raiseThreshold) {
            isRaised = true
        } else if (isRaised && normalizedWristOffset > lowerThreshold) {
            isRaised = false
            count += 1
        }
        return count
    }

    @Synchronized
    fun reset() {
        isRaised = false
        count = 0
    }
}

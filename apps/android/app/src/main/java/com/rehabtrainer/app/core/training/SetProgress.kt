package com.rehabtrainer.app.core.training

/**
 * 训练组状态机：只允许 1/3 → 2/3 → 3/3 → 完成 单向推进。
 *
 * 计数按 [repsPerSet] 封顶；已完成的组不可重复结束，避免按钮连点把组数刷过目标。
 */
data class SetProgress(
    val totalSets: Int,
    val repsPerSet: Int,
    val completedSets: Int = 0
) {
    init {
        require(totalSets > 0) { "totalSets must be positive" }
        require(repsPerSet > 0) { "repsPerSet must be positive" }
        require(completedSets in 0..totalSets) { "completedSets out of range" }
    }

    /** 当前正在进行的组号；全部完成时停在最后一组。 */
    val currentSet: Int
        get() = completedSets.coerceAtMost(totalSets - 1) + 1

    val isComplete: Boolean
        get() = completedSets >= totalSets

    fun capReps(rawCount: Int): Int = rawCount.coerceIn(0, repsPerSet)

    /** 本组是否已达到目标次数、可以自动进入下一组（全部完成时为 false）。 */
    fun isTargetReached(rawCount: Int): Boolean = !isComplete && capReps(rawCount) >= repsPerSet

    fun finishCurrentSet(): SetProgress =
        if (isComplete) this else copy(completedSets = completedSets + 1)
}

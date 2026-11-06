package com.rehabtrainer.app.core.training

/**
 * D11 分级准入：需要治疗师确认分级的条目，在确认前一律不可开始。
 *
 * 拿不到患者分级时调用方传 `stageConfirmed = false`，按最保守处理。
 */
object TrainingAccessPolicy {

    fun canStart(requiresStageConfirmation: Boolean, stageConfirmed: Boolean): Boolean =
        !requiresStageConfirmation || stageConfirmed
}

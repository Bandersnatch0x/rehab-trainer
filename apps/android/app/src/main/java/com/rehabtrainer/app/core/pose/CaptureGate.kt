package com.rehabtrainer.app.core.pose

/**
 * 采集准入判定（D17）：脚部关键点全部可见时才允许动作计数。
 *
 * 低于阈值时暂停计数并提示调整机位，条件满足后自动恢复；
 * 上肢类条目可半身构图，不要求脚部入镜。
 */
object CaptureGate {

    /** 脚部六个关键点的最低可见度；可见度缺失按不可见处理（fail-closed）。 */
    const val FOOT_VISIBILITY_THRESHOLD: Float = 0.5f

    /** 等于阈值视为可见，与 D17 的 `>= 0.5f` 判定一致。 */
    fun isFootVisible(footVisibility: Float): Boolean =
        footVisibility >= FOOT_VISIBILITY_THRESHOLD

    /** 当前帧是否允许累计动作次数；全身类条目在脚部不可见时一律不计。 */
    fun shouldCount(requiresFullBody: Boolean, footVisibility: Float): Boolean =
        !requiresFullBody || isFootVisible(footVisibility)
}

package com.rehabtrainer.app.core.pose

/**
 * 采集准入判定（D17）：脚部关键点全部可见时才提示「姿态识别正常」。
 *
 * 阈值是端上硬性检查的一部分，放在纯逻辑层以便单测覆盖；
 * 低于阈值只提示患者调整站位，不阻断训练。
 */
object CaptureGate {

    /** 脚部六个关键点的最低可见度；可见度缺失按不可见处理（fail-closed）。 */
    const val FOOT_VISIBILITY_THRESHOLD: Float = 0.5f

    /** 等于阈值视为可见，与 D17 的 `>= 0.5f` 判定一致。 */
    fun isFootVisible(footVisibility: Float): Boolean =
        footVisibility >= FOOT_VISIBILITY_THRESHOLD
}

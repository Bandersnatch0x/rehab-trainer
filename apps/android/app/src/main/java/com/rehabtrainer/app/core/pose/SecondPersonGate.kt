package com.rehabtrainer.app.core.pose

/**
 * 多人同框门禁：`setNumPoses(2)` 只用来判定「画面里还有第二个人」。
 *
 * **为什么不做身份锁定**：只有单帧骨架时无法做身份匹配，「锁定患者」需要额外线索
 * （人脸、体型、初始位置），而在康复场景里第二个人入镜本身就意味着这次计数不可信——
 * 跟踪目标随时可能切到护理员身上，把别人的动作记进患者的处方。暂停并提示比赌一把安全。
 *
 * **为什么要可见点数门槛**：检测置信度被刻意放宽到 0.35（适老化场景光线差、机位不理想），
 * 必然出现幽灵骨架。只有 [MIN_VISIBLE_LANDMARKS] 个以上可见点的第二副骨架才认定为真人；
 * 门槛以下视为噪声，不打断训练。
 *
 * 阈值需真机标定：`0.5` 与票 0021 的可见度口径一致，`6` 是「头部+肩+髋」的量级，
 * 能在第二人被家具遮挡一半时仍然触发。两者都未在真机上验证。
 */
object SecondPersonGate {

    /** 与 [CaptureGate.FOOT_VISIBILITY_THRESHOLD] 同一口径：等于阈值算可见。 */
    const val VISIBILITY_THRESHOLD: Float = 0.5f

    /** 第二副骨架至少要有这么多可见点才认定为真人，否则视为幽灵检测。 */
    const val MIN_VISIBLE_LANDMARKS: Int = 6

    /**
     * 画面里是否存在第二个人。
     *
     * [poses] 按 MediaPipe 的显著性排序，第 0 副是当前跟踪目标；只要之后任意一副
     * 达到可见点门槛即判定为多人。**不区分谁是患者**——两人同框时无论跟到谁，
     * 这一次计数都不该继续。
     */
    fun isPresent(poses: List<List<PosePoint>>): Boolean =
        poses.size > 1 && poses.drop(1).any { pose ->
            pose.count { it.visibility >= VISIBILITY_THRESHOLD } >= MIN_VISIBLE_LANDMARKS
        }
}

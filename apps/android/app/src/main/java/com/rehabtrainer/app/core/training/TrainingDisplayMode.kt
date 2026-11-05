package com.rehabtrainer.app.core.training

/**
 * 训练执行显示模式：
 * - [DUAL]：示范视频 + 实景拍摄同屏。
 * - [CAMERA_ONLY]：纯实景拍摄，用语音与节拍音替代视频示范。
 */
enum class TrainingDisplayMode(val label: String, val icon: String) {
    DUAL("同屏示范", "🎬"),
    CAMERA_ONLY("纯拍摄+语音", "📷")
}

package com.rehabtrainer.app.core.training

/**
 * 教学视频的本地缓存解析钩子。
 *
 * 播放层（`InstructionalVideoSource`）不依赖 `data/offline`；缓存层安装后把
 * “远端地址 → 本地文件路径”的解析函数与未命中回调注册进来。未安装时两个钩子
 * 保持空实现，播放行为与缓存无关，既有单测无需 Android 环境。
 */
object CachedMediaPlayback {

    /** 命中缓存时返回本地绝对路径；未命中返回 null。 */
    @Volatile
    var resolveLocalUrl: (String) -> String? = { null }

    /** 未命中时触发后台补缓存（实现方自行做网络与空间门槛）。 */
    @Volatile
    var onPlaybackMiss: (String) -> Unit = {}
}

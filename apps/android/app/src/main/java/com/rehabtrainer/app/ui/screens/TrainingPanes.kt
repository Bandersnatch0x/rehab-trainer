package com.rehabtrainer.app.ui.screens

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import com.rehabtrainer.app.R
import com.rehabtrainer.app.core.training.InstructionalVideoSource
import com.rehabtrainer.app.core.training.SetProgress
import com.rehabtrainer.app.core.training.TrainingDisplayMode
import com.rehabtrainer.app.ui.ExerciseMeta
import com.rehabtrainer.app.ui.components.ElderlyButton
import com.rehabtrainer.app.ui.components.ElderlyButtonVariant

/**
 * 训练顶部信息与模式切换条：动作名、当前组号、显示模式，字号 >= 18sp。
 */
@Composable
internal fun TrainingHeaderBar(
    exercise: ExerciseMeta,
    currentMode: TrainingDisplayMode,
    currentSet: Int,
    totalSets: Int,
    forcedCameraOnly: Boolean,
    onModeSelect: (TrainingDisplayMode) -> Unit,
    isPhoneLandscape: Boolean,
    compactLabels: Boolean,
    showModeSelector: Boolean = true,
    showVoiceToggle: Boolean,
    voiceEnabled: Boolean,
    ttsFailed: Boolean,
    onToggleVoice: (Boolean) -> Unit,
    // DUAL 画中画下提供一个明确的「切换画面」入口，避免只靠点按小浮窗难被发现。
    showSwapButton: Boolean = false,
    onSwapMain: () -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (isPhoneLandscape) 4.dp else 8.dp)
        ) {
            if (isPhoneLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExerciseTitle(
                        exercise = exercise,
                        currentSet = currentSet,
                        totalSets = totalSets,
                        isPhoneLandscape = true,
                        compact = compactLabels,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(Modifier.width(8.dp))

                    if (showVoiceToggle) {
                        VoiceToggleChip(
                            voiceEnabled = voiceEnabled,
                            ttsFailed = ttsFailed,
                            onToggleVoice = onToggleVoice
                        )
                        Spacer(Modifier.width(8.dp))
                    }

                    if (showSwapButton) {
                        SwapMainChip(onSwapMain = onSwapMain, isPhoneLandscape = true)
                        Spacer(Modifier.width(8.dp))
                    }

                    if (showModeSelector) {
                        TrainingModeSelector(
                            currentMode = currentMode,
                            onModeSelect = onModeSelect,
                            isPhoneLandscape = true,
                            compactLabels = compactLabels
                        )
                    }
                }
            } else {
                // 竖屏宽度不足以并排显示动作名与两个模式胶囊，标题独占一行避免被挤成省略号。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExerciseTitle(
                        exercise = exercise,
                        currentSet = currentSet,
                        totalSets = totalSets,
                        isPhoneLandscape = false,
                        compact = compactLabels,
                        modifier = Modifier.weight(1f)
                    )
                    if (showSwapButton) {
                        Spacer(Modifier.width(8.dp))
                        SwapMainChip(onSwapMain = onSwapMain, isPhoneLandscape = false)
                    }
                    if (showVoiceToggle && !isPhoneLandscape) {
                        // 大字号竖屏同样要能关语音，不能只能退出训练页才能关。
                        Spacer(Modifier.width(4.dp))
                        VoiceToggleChip(
                            voiceEnabled = voiceEnabled,
                            ttsFailed = ttsFailed,
                            onToggleVoice = onToggleVoice
                        )
                    }
                    if (showModeSelector) {
                        Spacer(Modifier.width(8.dp))
                        TrainingModeSelector(
                            currentMode = currentMode,
                            onModeSelect = onModeSelect,
                            isPhoneLandscape = false,
                            compactLabels = true,
                            iconOnly = true
                        )
                    }
                }
            }

            exercise.supportLevelText?.let { level ->
                // 变体指定的支撑等级直接影响动作安全，训练中必须常显。
                Text(
                    text = "支撑等级：$level",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (forcedCameraOnly) {
                Text(
                    text = "屏幕过窄，已自动切换为纯实景模式",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ExerciseTitle(
    exercise: ExerciseMeta,
    currentSet: Int,
    totalSets: Int,
    isPhoneLandscape: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = if (compact) {
                "${exercise.shortName} · 第 $currentSet/$totalSets 组"
            } else {
                exercise.name
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!compact) {
            Text(
                text = "第 $currentSet/$totalSets 组 · ${exercise.repsPerSet} 次",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 画中画主/小切换入口；点按小浮窗同样可切换，这里给一个明确可见的按钮。 */
@Composable
private fun SwapMainChip(
    onSwapMain: () -> Unit,
    isPhoneLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSwapMain() }
            .semantics { contentDescription = "切换主画面与小窗" }
    ) {
        Text(
            text = if (isPhoneLandscape) "⇄ 切换画面" else "⇄ 切换",
            fontSize = if (isPhoneLandscape) 18.sp else 16.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

/** 手机横屏没有纵向空间放语音条，语音开关收进头部一行。 */
@Composable
private fun VoiceToggleChip(
    voiceEnabled: Boolean,
    ttsFailed: Boolean,
    onToggleVoice: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val background = when {
        ttsFailed -> Color(0xFF3A1D1D)
        voiceEnabled -> Color(0xFF006A63)
        else -> Color(0xFF334155)
    }
    Surface(
        color = background,
        shape = RoundedCornerShape(12.dp),
        modifier = if (ttsFailed) {
            modifier
        } else {
            modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggleVoice(!voiceEnabled) }
        }
    ) {
        Text(
            text = when {
                ttsFailed -> "语音不可用"
                voiceEnabled -> "🔊 语音开"
                else -> "🔇 语音关"
            },
            fontSize = 18.sp,
            color = if (ttsFailed) Color(0xFFFFB4AB) else Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun TrainingModeSelector(
    currentMode: TrainingDisplayMode,
    onModeSelect: (TrainingDisplayMode) -> Unit,
    isPhoneLandscape: Boolean,
    compactLabels: Boolean = false,
    iconOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TrainingDisplayMode.entries.forEach { mode ->
                val selected = currentMode == mode
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onModeSelect(mode) }
                        .semantics { contentDescription = mode.label }
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (isPhoneLandscape) 8.dp else 12.dp,
                            vertical = if (isPhoneLandscape) 4.dp else 6.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = mode.icon, fontSize = if (iconOnly) 22.sp else 18.sp)
                        if (!iconOnly) {
                            Text(
                                text = if (compactLabels) mode.shortLabel else mode.label,
                                fontSize = 18.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 纯拍摄模式的动作语音条：语音开关即时生效，TTS 初始化失败时明确提示。
 */
@Composable
internal fun VoiceGuidanceBanner(
    prompt: String,
    isPaused: Boolean,
    isComplete: Boolean,
    voiceEnabled: Boolean,
    ttsFailed: Boolean,
    onToggleVoice: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A),
            contentColor = Color.White
        ),
        border = BorderStroke(1.5.dp, Color(0xFF0284C7))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF0284C7), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🔊", fontSize = 20.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "动作语音提示",
                        color = Color(0xFF38BDF8),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(4.dp),
                        modifier = if (ttsFailed) {
                            Modifier
                        } else {
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onToggleVoice(!voiceEnabled) }
                        }
                    ) {
                        Text(
                            text = when {
                                ttsFailed -> "语音不可用"
                                isComplete -> "训练完成"
                                isPaused -> "已暂停"
                                voiceEnabled -> "语音 开"
                                else -> "语音 关"
                            },
                            color = when {
                                ttsFailed -> Color(0xFFFFB4AB)
                                !voiceEnabled -> Color(0xFF94A3B8)
                                else -> Color(0xFF80D5CB)
                            },
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = prompt,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (ttsFailed) {
                    Text(
                        text = "系统语音初始化失败，训练将以静音继续",
                        color = Color(0xFFFFB4AB),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** APK 内置演示视频地址：服务端未下发视频或加载失败时使用。 */
private fun demoVideoUri(context: android.content.Context): String =
    "android.resource://${context.packageName}/${R.raw.demo_exercise}"

/**
 * 教学视频播放音量。内置演示视频自带合成的低音量占位音轨（峰值约 -20 dBFS），
 * 播放器保持较低音量，既让患者听得到，又不压过语音提示与节拍音。
 * 正式讲解音轨上线后只有音量需要在这里统一调整。
 */
private const val VIDEO_AUDIO_VOLUME = 0.8f

/** 通用演示片段的醒目底色：琥珀色不易与错误红混淆，又足够抢眼。 */
private val GENERIC_DEMO_BG = Color(0xFFF59E0B)
private val GENERIC_DEMO_FG = Color(0xFF3B1D00)

/**
 * 教学视频播放区。离开组合（切到纯拍摄模式）时通过 onRelease 停止并释放播放器。
 *
 * 视频优先播放服务端下发的地址；地址缺失或播放失败时回退到 APK 内置演示视频，
 * 训练不中断。水印与署名由客户端叠加（见 [InstructionalVideoSource]），不烧录进文件。
 *
 * 播放器在 onPrepared 读取真实宽高，用 [aspectRatio] 让画面按比例居中显示，不同宽高比
 * 的素材都不会被拉伸留黑边。回退到内置通用演示片段时如实标注，避免被误认作本条动作示范。
 *
 * @param isGenericDemo 服务端未下发本条动作视频（`videoUri` 为空），播放的是通用演示片段。
 * @param compact 小浮窗形态：只保留缩小的来源标注与署名，隐藏播放状态条与进度条。
 */
@Composable
internal fun InstructionalVideoPane(
    videoTitle: String,
    isPaused: Boolean,
    isGenericDemo: Boolean,
    exerciseName: String,
    modifier: Modifier = Modifier,
    videoSource: InstructionalVideoSource = InstructionalVideoSource(),
    compact: Boolean = false
) {
    // factory 的闭包只捕获创建时的值；用 rememberUpdatedState 让 onPrepared 读到最新暂停状态，
    // 否则「准备完成」瞬间会用旧值覆盖用户的暂停/继续操作。
    val pausedNow by rememberUpdatedState(isPaused)
    val remoteUrl = videoSource.resolvedRemoteUrl
    // 远端地址播放失败会回退到内置通用演示片段，此时同样要如实标注，不能仍标「教学示范」。
    var fellBackToDemo by remember(remoteUrl) { mutableStateOf(false) }
    // 播放器真实宽高比；准备完成前铺满容器，准备完成后按比例居中，避免拉伸。
    var videoAspect by remember(remoteUrl) { mutableStateOf<Float?>(null) }

    val showsGenericDemo = isGenericDemo || fellBackToDemo

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A),
            contentColor = Color.White
        ),
        border = BorderStroke(1.5.dp, Color(0xFF334155))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 地址变化时重建播放器，避免在同一个 VideoView 上反复 setVideoURI。
            key(remoteUrl) {
                AndroidView(
                    factory = { context ->
                        val demoUri = demoVideoUri(context)
                        val initialUri = remoteUrl ?: demoUri
                        VideoView(context).apply {
                            tag = initialUri
                            setVideoURI(Uri.parse(initialUri))
                            setOnPreparedListener { player ->
                                player.isLooping = true
                                // 演示视频带占位音轨，不再静音；低音量避免惊吓患者与盖住语音。
                                player.setVolume(VIDEO_AUDIO_VOLUME, VIDEO_AUDIO_VOLUME)
                                val width = player.videoWidth
                                val height = player.videoHeight
                                if (width > 0 && height > 0) {
                                    videoAspect = width.toFloat() / height.toFloat()
                                }
                                if (!pausedNow) start()
                            }
                            setOnErrorListener { _, _, _ ->
                                // 远端不可用时回退内置演示视频；内置视频再失败就停止重试。
                                if (tag != demoUri) {
                                    tag = demoUri
                                    fellBackToDemo = true
                                    setVideoURI(Uri.parse(demoUri))
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                    },
                    update = { view ->
                        if (isPaused) view.pause() else if (!view.isPlaying) view.start()
                    },
                    onRelease = { view -> view.stopPlayback() },
                    // 按素材真实宽高比约束播放器，短边留边由外框承担，播放器本身不拉伸。
                    modifier = videoAspect
                        ?.takeIf { it > 0f }
                        ?.let { Modifier.aspectRatio(it) }
                        ?: Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (compact) 6.dp else 12.dp,
                        vertical = if (compact) 4.dp else 8.dp
                    ),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (showsGenericDemo) {
                    Surface(
                        color = GENERIC_DEMO_BG,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (compact) {
                                "🎬 通用演示 · 非本条动作"
                            } else {
                                "🎬 通用演示片段 · 非本条动作：$exerciseName"
                            },
                            fontSize = if (compact) 11.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = GENERIC_DEMO_FG,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(
                                horizontal = if (compact) 6.dp else 8.dp,
                                vertical = 2.dp
                            )
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "🎬 教学示范 · 演示",
                            fontSize = if (compact) 11.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(
                                horizontal = if (compact) 6.dp else 8.dp,
                                vertical = 2.dp
                            )
                        )
                    }
                }

                // 底部统一收拢：署名/水印 + 播放状态 + 进度条，避免大画面下状态条浮在画面中间。
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (videoSource.hasOverlay) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 署名左下、水印右下，模拟播放器叠加，兼顾版权标注可读性。
                            Text(
                                text = videoSource.creditLabel.orEmpty(),
                                fontSize = if (compact) 10.sp else 12.sp,
                                color = Color(0xB3FFFFFF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (videoSource.watermarkLabel != null) {
                                Surface(
                                    color = Color(0x73000000),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = videoSource.watermarkLabel.orEmpty(),
                                        fontSize = if (compact) 10.sp else 12.sp,
                                        color = Color(0xE0FFFFFF),
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (!compact) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isPaused) "已暂停" else "循环示范中",
                                fontSize = 18.sp,
                                color = if (isPaused) Color(0xFFFFB4AB) else Color(0xFF80D5CB),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "01:24 / 03:00",
                                fontSize = 18.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        LinearProgressIndicator(
                            progress = { 0.46f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color(0xFF334155),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 「视频通话」式训练画面布局：一个大画面铺满，另一个缩成右下角圆角小浮窗。
 *
 * [videoIsMain] 决定谁大谁小；点按小浮窗或头部「切换画面」都会调用 [onSwap]。
 * 两个 slot 的调用点固定，切换只换 Modifier，不会重建摄像头预览或播放器。
 */
@Composable
internal fun TrainingPipLayout(
    videoIsMain: Boolean,
    onSwap: () -> Unit,
    pipWidth: Dp,
    videoSlot: @Composable (modifier: Modifier, compact: Boolean) -> Unit,
    cameraSlot: @Composable (modifier: Modifier, compact: Boolean) -> Unit,
    // 视频为主画面时，计数与节拍不能随着摄像头缩成小窗而消失。
    overlaySlot: @Composable BoxScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 小浮窗固定右下角，zIndex 保证始终绘制在主画面之上。
        val pipModifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(10.dp)
            .width(pipWidth)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSwap() }
            .semantics { contentDescription = "切换主画面与小窗" }
            .zIndex(2f)

        cameraSlot(
            if (videoIsMain) pipModifier else Modifier.fillMaxSize(),
            videoIsMain
        )
        videoSlot(
            if (videoIsMain) Modifier.fillMaxSize() else pipModifier,
            !videoIsMain
        )
        overlaySlot()
    }
}

/**
 * 患者实时摄像头画面区：计数、状态、RAS 节拍开关与 D17 机位调整图示。
 */
@Composable
internal fun PatientRealtimeCameraPane(
    modifier: Modifier = Modifier,
    previewView: PreviewView,
    repCount: Int,
    repsPerSet: Int,
    // 计数在数什么由条目决定（踏步/抬举/蹲起），标签必须如实标注（P1-16）。
    counterLabel: String,
    repHint: String? = null,
    largeFont: Boolean = false,
    largeCount: Boolean = false,
    statusText: String,
    footVisible: Boolean,
    /** 画面内还有第二个人：与脚部门禁并列，都会冻结计数并告警。刻意不给默认值，逼调用方表态。 */
    multiPersonPresent: Boolean,
    requiresFullBody: Boolean,
    hasRas: Boolean,
    bpm: Int,
    metronomeEnabled: Boolean,
    onToggleMetronome: (Boolean) -> Unit,
    // 小浮窗形态：小窗只保留实时画面与必要的站位告警，计数/节拍/状态条留给主画面。
    compact: Boolean = false
) {
    // 两种门禁共用一套告警样式：脚部出框，或画面内出现第二个人。
    val gateBlocked = multiPersonPresent || (requiresFullBody && !footVisible)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(
            if (gateBlocked) 2.5.dp else 1.5.dp,
            if (gateBlocked) Color(0xFFBA1A1A) else Color(0xFF0284C7)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            if (largeFont && !compact) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 大字号是目标用户，不能为了省地方丢掉目标次数、计数标签与纠正提示。
                    CounterBadge(
                        repCount, repsPerSet, counterLabel, repHint, largeCount,
                        compact = false
                    )
                    if (hasRas) {
                        MetronomeBadge(
                            bpm, metronomeEnabled, onToggleMetronome, compact = true
                        )
                    }
                }
            } else if (!compact) {
                CounterBadge(
                    repCount = repCount,
                    repsPerSet = repsPerSet,
                    counterLabel = counterLabel,
                    repHint = repHint,
                    largeCount = largeCount,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                )
                if (hasRas) {
                    MetronomeBadge(
                        bpm = bpm,
                        enabled = metronomeEnabled,
                        onToggle = onToggleMetronome,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    )
                }
            }

            if (gateBlocked) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xE60F172A), RoundedCornerShape(16.dp))
                        .border(2.dp, Color(0xFFFFB4AB), RoundedCornerShape(16.dp))
                        .padding(
                            horizontal = if (compact) 8.dp else 16.dp,
                            vertical = if (compact) 6.dp else 12.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("🧍", fontSize = if (compact) 22.sp else 42.sp)
                    Text(
                        // 两种门禁的处置动作不同：脚部出框要后退，多人要把旁人请出画面。
                        text = if (multiPersonPresent) "请确保只有患者入镜" else "请后退站入全身框",
                        color = Color.White,
                        fontSize = if (compact) 13.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            if (statusText.isNotBlank() && !compact) {
            Surface(
                color = if (gateBlocked) Color(0xEEBA1A1A) else Color(0x990F172A),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (gateBlocked) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = if (multiPersonPresent) "画面内多人警告" else "脚部未入镜警告",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (multiPersonPresent) "⚠️ 画面内有多人，请让陪护者离开画面"
                            else "⚠️ 脚部未入镜，请后退入框",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF80D5CB), CircleShape)
                        )
                        Text(
                            text = statusText,
                            color = Color(0xFFE2E8F0),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            }
        }
    }
}

/**
 * 常驻操作底栏。紧急停止在所有布局下保持 >= 80dp 且常驻可见；
 * 竖屏用两行避免组号文案被挤压换行，手机横屏保持单行。
 */
@Composable
internal fun PersistentControlFooter(
    isPhoneLandscape: Boolean,
    isPaused: Boolean,
    largeFont: Boolean,
    setProgress: SetProgress,
    onTogglePause: () -> Unit,
    onFinishSet: () -> Unit,
    onEmergencyStop: () -> Unit
) {
    val finishText = when {
        setProgress.isComplete -> "训练完成"
        isPhoneLandscape -> "完成 ${setProgress.currentSet}/${setProgress.totalSets}"
        else -> "完成本组"
    }
    val minHeight = 80.dp

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        if (isPhoneLandscape || !largeFont) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ElderlyButton(
                    text = if (isPaused) "继续" else "暂停",
                    onClick = onTogglePause,
                    variant = ElderlyButtonVariant.Secondary,
                    minHeight = minHeight,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(0.9f)
                )
                ElderlyButton(
                    text = finishText,
                    onClick = onFinishSet,
                    enabled = !setProgress.isComplete,
                    variant = ElderlyButtonVariant.Primary,
                    minHeight = minHeight,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1.2f)
                )
                ElderlyButton(
                    text = if (isPhoneLandscape) "🛑 紧急停止" else "紧急停止",
                    onClick = onEmergencyStop,
                    variant = ElderlyButtonVariant.Danger,
                    minHeight = minHeight,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1.1f)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ElderlyButton(
                        text = if (isPaused) "继续" else "暂停",
                        onClick = onTogglePause,
                        variant = ElderlyButtonVariant.Secondary,
                        minHeight = minHeight,
                        modifier = Modifier.weight(1f)
                    )
                    ElderlyButton(
                        text = "🛑 紧急停止",
                        onClick = onEmergencyStop,
                        variant = ElderlyButtonVariant.Danger,
                        minHeight = minHeight,
                        modifier = Modifier.weight(1.4f)
                    )
                }
                ElderlyButton(
                    text = finishText,
                    onClick = onFinishSet,
                    enabled = !setProgress.isComplete,
                    variant = ElderlyButtonVariant.Primary,
                    minHeight = minHeight
                )
            }
        }
    }
}


@Composable
internal fun CounterBadge(
    repCount: Int,
    repsPerSet: Int,
    counterLabel: String,
    repHint: String?,
    largeCount: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0x990F172A),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
        modifier = modifier
    ) {
        if (compact) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "$repCount",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF38BDF8)
                )
                Text(
                    text = "次",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "$repCount",
                    fontSize = if (largeCount) 36.sp else 28.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF38BDF8)
                )
                Column {
                    Text(
                        text = "/ $repsPerSet 次",
                        fontSize = if (largeCount) 20.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = counterLabel,
                        fontSize = 14.sp,
                        color = Color(0xFFCBD5E1)
                    )
                    if (repHint != null) {
                        Text(
                            text = repHint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (repHint == "本次达标") Color(0xFF4ADE80) else Color(0xFFFBBF24)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MetronomeBadge(
    bpm: Int,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0x990F172A),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF80D5CB)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (compact) "🎵 $bpm" else "🎵 $bpm BPM",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF80D5CB)
            )
            Surface(
                color = if (enabled) Color(0xFF006A63) else Color(0xFF334155),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggle(!enabled) }
            ) {
                Text(
                    text = when {
                        compact && enabled -> "开"
                        compact -> "关"
                        enabled -> "节拍 开"
                        else -> "节拍 关"
                    },
                    fontSize = 18.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

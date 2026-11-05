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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    showVoiceToggle: Boolean,
    voiceEnabled: Boolean,
    ttsFailed: Boolean,
    onToggleVoice: (Boolean) -> Unit
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

                    TrainingModeSelector(
                        currentMode = currentMode,
                        onModeSelect = onModeSelect,
                        isPhoneLandscape = true
                    )
                }
            } else {
                // 竖屏宽度不足以并排显示动作名与两个模式胶囊，标题独占一行避免被挤成省略号。
                ExerciseTitle(
                    exercise = exercise,
                    currentSet = currentSet,
                    totalSets = totalSets,
                    isPhoneLandscape = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                TrainingModeSelector(
                    currentMode = currentMode,
                    onModeSelect = onModeSelect,
                    isPhoneLandscape = false,
                    modifier = Modifier.align(Alignment.End)
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = exercise.name,
            style = if (isPhoneLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "第 $currentSet/$totalSets 组 · ${exercise.target} 【演示数据】",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (isPhoneLandscape) 8.dp else 12.dp,
                            vertical = if (isPhoneLandscape) 4.dp else 6.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = mode.icon, fontSize = 18.sp)
                        Text(
                            text = mode.label,
                            fontSize = 18.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
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
 * 教学视频播放区。离开组合（切到纯拍摄模式）时通过 onRelease 停止并释放播放器。
 *
 * 视频优先播放服务端下发的地址；地址缺失或播放失败时回退到 APK 内置演示视频，
 * 训练不中断。水印与署名由客户端叠加（见 [InstructionalVideoSource]），不烧录进文件。
 */
@Composable
internal fun InstructionalVideoPane(
    videoTitle: String,
    modifier: Modifier = Modifier,
    isPaused: Boolean,
    videoSource: InstructionalVideoSource = InstructionalVideoSource()
) {
    // factory 的闭包只捕获创建时的值；用 rememberUpdatedState 让 onPrepared 读到最新暂停状态，
    // 否则「准备完成」瞬间会用旧值覆盖用户的暂停/继续操作。
    val pausedNow by rememberUpdatedState(isPaused)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A),
            contentColor = Color.White
        ),
        border = BorderStroke(1.5.dp, Color(0xFF334155))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 地址变化时重建播放器，避免在同一个 VideoView 上反复 setVideoURI。
            key(videoSource.resolvedRemoteUrl) {
                AndroidView(
                    factory = { context ->
                        val demoUri = demoVideoUri(context)
                        val initialUri = videoSource.resolvedRemoteUrl ?: demoUri
                        VideoView(context).apply {
                            tag = initialUri
                            setVideoURI(Uri.parse(initialUri))
                            setOnPreparedListener { player ->
                                player.isLooping = true
                                // 教学视频静音，音频通道留给动作语音与节拍音。
                                player.setVolume(0f, 0f)
                                if (!pausedNow) start()
                            }
                            setOnErrorListener { _, _, _ ->
                                // 远端不可用时回退内置演示视频；内置视频再失败就停止重试。
                                if (tag != demoUri) {
                                    tag = demoUri
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
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "🎬 教学示范",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = videoTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "动作幅度与节拍指引 【演示数据】",
                        fontSize = 18.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (videoSource.hasOverlay) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 署名左下、水印右下，模拟播放器叠加，兼顾版权标注可读性。
                        Text(
                            text = videoSource.creditLabel.orEmpty(),
                            fontSize = 12.sp,
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
                                    fontSize = 12.sp,
                                    color = Color(0xE0FFFFFF),
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

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

/**
 * 患者实时摄像头画面区：计数、状态、RAS 节拍开关与 D17 机位调整图示。
 */
@Composable
internal fun PatientRealtimeCameraPane(
    modifier: Modifier = Modifier,
    previewView: PreviewView,
    armRaiseCount: Int,
    repsPerSet: Int,
    // 计数在数什么：当前只有右臂抬举计数器，必须如实标注（P1-16）。
    counterLabel: String = "右臂抬举",
    largeCount: Boolean = false,
    statusText: String,
    footVisible: Boolean,
    requiresFullBody: Boolean,
    hasRas: Boolean,
    bpm: Int,
    metronomeEnabled: Boolean,
    onToggleMetronome: (Boolean) -> Unit
) {
    val gateBlocked = requiresFullBody && !footVisible

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

            Surface(
                color = Color(0x990F172A),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$armRaiseCount",
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
                    }
                }
            }

            if (hasRas) {
                Surface(
                    color = Color(0x990F172A),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF80D5CB)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🎵 $bpm BPM",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF80D5CB)
                        )
                        Surface(
                            color = if (metronomeEnabled) Color(0xFF006A63) else Color(0xFF334155),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onToggleMetronome(!metronomeEnabled) }
                        ) {
                            Text(
                                text = if (metronomeEnabled) "节拍 开" else "节拍 关",
                                fontSize = 18.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (gateBlocked) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xE60F172A), RoundedCornerShape(16.dp))
                        .border(2.dp, Color(0xFFFFB4AB), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("🧍", fontSize = 42.sp)
                    Text(
                        text = "请后退站入全身框",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

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
                            contentDescription = "脚部未入镜警告",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "⚠️ 脚部未入镜，请后退入框",
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

/**
 * 常驻操作底栏。紧急停止在所有布局下保持 >= 80dp 且常驻可见；
 * 竖屏用两行避免组号文案被挤压换行，手机横屏保持单行。
 */
@Composable
internal fun PersistentControlFooter(
    isPhoneLandscape: Boolean,
    isPaused: Boolean,
    setProgress: SetProgress,
    onTogglePause: () -> Unit,
    onFinishSet: () -> Unit,
    onEmergencyStop: () -> Unit
) {
    val finishText = when {
        setProgress.isComplete -> if (isPhoneLandscape) "已完成" else "训练完成 · 共 ${setProgress.totalSets} 组"
        isPhoneLandscape -> "完成 ${setProgress.currentSet}/${setProgress.totalSets}"
        else -> "完成本组 · 第 ${setProgress.currentSet}/${setProgress.totalSets} 组"
    }
    val minHeight = 80.dp

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        if (isPhoneLandscape) {
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
                    text = "🛑 紧急停止",
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

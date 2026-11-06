# 演示视频占位音频说明

## 背景

训练页的 `InstructionalVideoPane` 在服务端未下发视频（或播放失败）时回退到
`apps/android/app/src/main/res/raw/demo_exercise.mp4`。修复前该文件只有 H.264
视频流，没有音频流；播放器又在 `onPrepared` 中调用 `setVolume(0f, 0f)` 主动静音，
因此用户反馈“演示视频没有声音”。

## 当前资产

- 文件：`apps/android/app/src/main/res/raw/demo_exercise.mp4`
- 视频流：原 H.264 流原样保留（`-c:v copy`，修复前后视频流 MD5 一致）。
- 音频流：AAC-LC，单声道，44.1 kHz，64 kbps，20 秒，与视频等长。
- 音量：合成环境音峰值约 -20 dBFS（实测 max_volume -20.0 dB，
  mean_volume -30.7 dB），属于低音量背景垫，不构成临床指导。
- 内容来源：用本机 ffmpeg 合成（98/196/293.66 Hz 正弦垫音 + 低通粉噪 +
  慢速颤音 + 淡入淡出），未使用任何第三方音乐、采样或版权素材。
- 容器元数据已标注：
  `title="Demo placeholder audio (not clinical guidance)"`，
  `comment` 说明这是合成占位音轨、正式上线前需替换。

## 播放器

`TrainingPanes.kt` 的 `VIDEO_AUDIO_VOLUME = 0.8f`。播放器不再把视频静音，
音量保持较低，避免惊吓患者或压过 TTS 语音与节拍提示。若正式讲解音轨上线，
只需在 `VIDEO_AUDIO_VOLUME` 一处调整音量。

## 替换方式

这是**演示占位音频，不是临床指导**。正式素材到位后，用带讲解音的成片直接替换
`demo_exercise.mp4` 即可，播放器代码无需改动。`services/api/media/videos/` 下
是运行期上传/落盘目录（`.gitignore` 已排除），不随仓库分发；若部署环境仍在使用
旧的无音轨副本，需在部署侧单独替换。

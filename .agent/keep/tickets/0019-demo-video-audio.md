---
id: "0019"
type: "task"
status: "closed"
assignee: "claude (subagent)"
part-of: null
created: 2026-11-06
---

# 演示视频无声修复

问题：用户反馈演示视频没有声音。

定位：
- 素材 `apps/android/app/src/main/res/raw/demo_exercise.mp4` 只有 h264 视频流，无音轨；
- 播放器 `InstructionalVideoPane.onPrepared` 主动 `setVolume(0f, 0f)`，注释为“音频通道留给语音与节拍”。

修复：
- 用 ffmpeg 合成 20 秒无版权低音量环境音（AAC 64kbps，峰值 -20dB），`-c:v copy` 保留原视频流；
- 播放器音量改为 0.8，保留语音/节拍优先；
- `docs/design/demo-media.md` 说明占位音频与正式素材替换方式。

验证：ffprobe `h264,video` + `aac,audio`；APK 内资源 SHA256 一致；构建/单测通过；设备断开，真机听感未复验。

---
id: "0035"
type: "task"
status: "done"
assignee: null
part-of: null
created: 2026-11-08
---

# 未并入 main 的特性分支甄别与收敛

## 背景

2026-11-08 清理 23 个 worktree 时，`git branch --no-merged main` 列出 4 个分支仍有独有提交。
它们都只存在于本地，此前从未推送过，已于本次全部推到 origin 与 gitee 备份。

## 甄别方法

`git cherry -v main <branch>`：每行前缀 `-` 表示 main 已有**等价补丁**（按 patch-id 判定，
与提交号无关），`+` 表示真正独有。这个方法能识别「同一份改动经另一条分支重新提交进 main」
的情况，`git branch --merged` 做不到。

## 结论

| 分支 | 独有提交 | 判定 | 依据 |
| --- | --- | --- | --- |
| `agent/ui-pip-video` | 1 | **已被 main 覆盖** | `9fca652` 的等价补丁即 main 的 `db7699b`（画中画布局、`aspectRatio`、通用演示标注） |
| `agent/demo-audio-placeholder` | 1 | **已被 main 覆盖** | `460947e` 的等价补丁已在 main（演示视频占位音轨 + `docs/design/demo-media.md`） |
| `Bandersnatch0x/training-voice` | 3 | **部分独有** | 沉浸模式、D21 演示标注、`requiresStageConfirmation` 已由别的提交进 main；TTS 初始化超时等仍缺，见下 |
| `Bandersnatch0x/mp-functional-fix` | 2 | **独有** | 小程序绑定页禁用态可读性 + 票 0008 的落码记录，main 没有 |

## training-voice 仍独有的部分

`main` 在 11-06~11-08 之间大幅演进（`multiPersonPresent`、幅度校准、跨轴单位修复等），
该分支 11-05 的工作已大部分被覆盖。逐项核对后**真正缺失**的：

- `VoiceCoach.INIT_TIMEOUT_MS = 5_000L` + `TTS init timed out` 降级：TTS 引擎不回调时
  界面会一直显示「语音开」却永远无声。`isReady` 卡在初始值，没有任何超时兜底。
- `if (!usable) tts = null`：初始化失败后不释放引擎引用。
- `videoTitle` 文案「标准示范」→「动作演示」：与通用素材标注口径一致。
  注：main 已用 `isGenericDemo` + 「通用演示片段 · 非本条动作」标注处理了同一个问题，
  这条是否还需要改取决于患者端观感，属可讨论项。
- 训练准入文案简化（「该训练属于需治疗师评估的高风险条目…」→「该训练需治疗师确认后才能开始」）。

不计入：`PoseAnalyzer` 构造参数顺序调整（无功能价值，且 main 已重写该文件）。

## 待办

1. `agent/ui-pip-video`、`agent/demo-audio-placeholder`：确认后删本地分支与两个远端的分支。
2. `Bandersnatch0x/training-voice`：把 TTS 超时两项 cherry-pick 到 main 并补测试
   （`VoiceCoach` 无单测，需评估是否可测）；文案两项由产品口径决定，不做则关票。
3. `Bandersnatch0x/mp-functional-fix`：直接合并到 main，跑小程序门禁
   （`node --test apps/miniprogram/tests/patient-data.cjs`、`static_check.py`、`tsc --noEmit`）。

## 相关

- 票 0036：9 个 WIP 分支的收敛（同一批 worktree 清理的产物）

## 进展

### 2026-11-08 待办 2、3 完成

- **待办 3（`mp-functional-fix`）**：已合并进 main（`6117837`）。冲突只在票 0008 文档段
  ——两边各写了一段 11-05 记录，按「落码与验收 → 协调者验收与关票」时间序都保留。
  门禁：`node --test` 17/17、`static_check.py` 0 problem(s)、`tsc --noEmit` 通过。
- **待办 2（`training-voice` 的 TTS 超时）**：已重做进 main（`1e3f26f`），未直接
  cherry-pick——该提交还混着 D11/D17/D19/D20 等已被 main 以别的实现覆盖的改动，
  整条挑会大面积冲突。
  - 抽出 `TtsInitWatchdog`（纯逻辑）承接「回调与超时谁先到」的状态机，
    `VoiceCoach` 依赖 Android 框架无法在无 Robolectric 的单测里跑，这层可测。
  - 顺带补了一处分支里没有的：初始化失败时主动 `shutdown()` 引擎。
    只把 `tts` 置空的话 `release()` 拿不到引用，每次进训练页漏一个原生实例。
  - 单测 6 项（`TtsInitWatchdogTest`），Android 单测 190 项全通过（原 184）。
  - 文案两项（`videoTitle`、训练准入提示）未改：main 已用 `isGenericDemo` 标注
    处理了通用素材误导，措辞留给产品定，不在本次范围。
- **待办 1（删两个已覆盖分支）**：已完成。`ui-pip-video`、`demo-audio-placeholder`
  本地 + origin + gitee 三处已删；删前验过内容确实在 main（占位音轨版 mp4 含 `soun`
  atom、`setVolume(VIDEO_AUDIO_VOLUME)` 在位、画中画/`aspectRatio`/通用演示标注均在）。

### 2026-11-08 结票

4 个未并入分支全部收敛，仓库只剩 `main`。遗留的两项文案已补：`videoTitle` 5 处
「标准示范」→「动作演示」；`suitabilityTag` 现已是「需治疗师确认准入」，与目标口径一致。

---
id: "0037"
type: "feature"
status: "done"
assignee: "claude"
part-of: null
created: 2026-11-08
---

# 待上传记录退避重试（P4-5 遗留）

## 现象

服务端过载时快速返回 503（票 0026 的池隔离 + 快速失败），但客户端没跟上：

- `ApiSessionSyncService.flushPending` 失败后只 `markFailed` 就返回，**没有任何延迟**；
- `attemptCount` 在 `FileSessionUploadQueue.markFailed` 里累加，但全仓**没有任何地方读它**来算退避；
- 没有 WorkManager / 周期任务；
- `flushPending` 只在 App 启动（`MainActivity.kt:34`）和进记录页（`RecordsScreen.kt:49`）触发。

结果：503 窗口期内用户每进一次记录页就打一次服务端，退避全靠用户手速——服务端刚做的
过载保护被客户端抵消掉。

## 修复

新增 `data/offline/UploadBackoffPolicy.kt`，纯函数、无状态：

- 指数退避 `BASE × 2^(n-1)`，`BASE = 30s`，封顶 `MAX_DELAY_MS = 30min`（第 7 次起固定）；
- 位移上限 20：`Int.MAX_VALUE` 次失败若直接位移会溢出成负数，必须仍返回正的上限；
- 时钟回拨（`now < lastAttempt`）按「未到期」处理，避免疯狂重试；
- `isDue` 只读 `PendingSessionUpload` 的既有字段，不新增持久化状态，进程重启后退避进度仍在。

`ApiSessionSyncService` 在 `pending()` 之后按 `isDue` 过滤。全部在退避中时**不发请求**，
返回空成功——调用方只关心「本轮有没有传」，没有错误可提示。

注入 `nowMillis` 时钟用于测试，默认 `System::currentTimeMillis`。参数刻意放在 `upload` 之前，
既有 `ApiSessionSyncService(queue) { ... }` 的尾随 lambda 调用点无需改动。

## 验证

- 新增 8 项测试（6 项策略 + 2 项服务集成），Android 204 项通过（原 196）；
- 变异检验：去掉 `isDue` 过滤后，两个服务集成用例均失败，确认测试非空转。

## 未做

票 0026 里「其余读取路径目前只提示失败」：读路径**没有重试循环**，也就没有可退避的对象。
将来若给读路径加重试，直接复用 `UploadBackoffPolicy`。

退避参数（30s 起、30min 封顶）未做真机/真网验证，是工程取值；接入远端环境后应结合实际
503 持续时长复核。

过滤发生在 `pending(limit)` **之后**：若队列深度超过 `MAX_BATCH_SIZE`（100）且最早的 100 条
都在退避中，本轮会空转、够不到后面的可传条目。队列按入队时间 FIFO，而退避时长随失败次数
增长，所以「老记录挡路」在深度超限时是可能发生的。当前 App 单次训练只入队个位数记录，
未处理；真出现积压应改成让 `pending` 接受 `now` 并按到期过滤。

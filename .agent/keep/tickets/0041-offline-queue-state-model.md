---
id: "0041"
type: "task"
status: "pending"
assignee: "claude"
part-of: null
created: 2026-11-10
---

# 离线上传队列状态模型改造（账号归属 + 隔离区 + 重试分层）

## 背景

2026-11-10 全量 OCR 审查确认 3 条 High，均未在 `4dd942e` 修复——它们不是独立 bug，
而是同一个模型缺口的三种表现：当前队列是「无账号归属的扁平文件列表」，失败状态只有
`attemptCount`，没有隔离区、没有按条失败处理。

现有接口约定已写明正确语义，实现没有兑现：

- `SessionUploadQueue.remove` 文档：「主动丢弃记录（例如数据损坏且用户确认）」；
- `SessionUploadQueue.NON_RETRYABLE_ERROR_CODES` 文档：「命中后应移出重试队列，**留待人工处置**」；
- `SessionUploadQueue.clear` 文档：「登出/换绑时清空队列：旧患者的记录不能在新账号下重放」。

## 三条缺陷

### 1. 不可重试的拒绝记录被永久删除（High）

`ApiSessionSyncService.kt:50-54`：逐条拒绝命中不可重试错误码时，先 `markFailed` 再
`remove`。`attemptCount` / `lastError` 刚写入就被下一次 `save` 抹掉，既没有隔离区，
也没有日志或用户确认，直接与 `remove` 与 `NON_RETRYABLE_ERROR_CODES` 的文档矛盾。

后果：患者训练记录在服务端校验失败/越权后被静默丢弃，客户端无从提示，也无法人工补传。

### 2. 登出清队列与下次登录竞态（High）

`SessionRepository.kt:42` 的 `logout()` 同步清 token 后调用 `AppData.clearPending()`；
后者 `AppData.kt:75` 在 `appScope` 里异步 `queue.clear()`。若用户很快重新登录/换绑，
旧患者的待传记录可能带着新账号 token 上传，落到新账号名下。

根因是 `PendingSessionUpload`（`SessionUploadQueue.kt:7`）没有账号/患者归属字段，
队列无法在写入/读取时判断「这条属于谁」。

### 3. 批级不可重试错误卡死整个队列（High）

`ApiSessionSyncService.kt:37-40`：整批请求失败时对所有 `pending` 执行 `markFailed` 并
返回。若其中一条畸形记录导致服务端整批 422，同批合法记录也一并进入退避并无限重试，
永远传不上去。

## 验收形状

1. 队列条目带账号（或患者）归属；登出后的清理与下一次上传有明确顺序保证，旧账号记录
   不可能用新账号 token 上传。
2. 不可重试拒绝进入隔离区（quarantine），保留 `attemptCount` / `lastError` / 时间与原始记录；
   记录页能提示隔离数量，并提供人工处置入口（重试/导出/确认丢弃三选一，具体交互可再定）。
3. 批级失败按条隔离：单条畸形记录不再拖死同批合法记录；合法记录继续上传。
4. 新增测试覆盖上述三条路径；现有 Android 208 项单测保持通过。
5. 优先客户端模型改造，不改服务端 `/sessions` 契约；若确需契约配合，单独开票。

## 方案提示（不锁定实现）

- `PendingSessionUpload` 增加 `accountId`（或 `patientId`）与 `status`
  （`pending` / `quarantined` / `rejected`）；
- `logout` 改为 `suspend` 并贯穿调用点，或由队列按账号过滤而非整队清空；
- `flushPending` 对 422 做逐条提交或二分定位，把失败条目单独隔离；
- 隔离区复用现有文件持久化，避免引入数据库依赖。

## 关联

- 0037 上传退避：`attemptCount` / `lastAttemptAt` 已具备，退避策略可直接复用；
- 0024 训练记录闭环：队列与补传的原始设计；
- 0028 校准持久化、0029 相机横滚：同属 Android 本地状态可靠性，可参考其落盘与降级写法。

## Log

### 2026-11-10 建票

来源：`.agent/tmp/ocr-full-review-2026-11-10.md` §3 High 1–3（OCR#133 / #159 / #157）。
三条在 `4dd942e` 中明确未修，理由均为需要队列状态模型改造，超出单文件最小修复范围。
无替代方案：仅加日志或单独加重试都无法同时解决归属、隔离与批级失败三个问题。

---
id: "0036"
type: "task"
status: "open"
assignee: null
part-of: null
created: 2026-11-08
---

# 9 个 WIP 分支的收敛

## 背景

清理 worktree 时，9 个目录里有未提交改动。为不丢工作，删除前统一提交为
`wip: 清理 worktree 前保存工作区改动` 并保留在各自分支上；分支已推 origin 与 gitee。

这些 worktree 是同一批任务的多路并行草稿（原版 / `-claude` / `-codex2` 三方），
最终只有一路进了 main，其余是重复实现。

## 清单

| 分支 | 内容 | 判定 |
| --- | --- | --- |
| `Bandersnatch0x/offline-cache` | `MediaCacheManager` 的 `prefetchMutex` 预取互斥 + `fitsQuota` 启动淘汰修正 + 播放路径去磁盘 stat（6 文件） | **已重做进 main（`d96d4fd`）** |
| `Bandersnatch0x/api-integration` | 管理端 `vite.config` 代理、Android `build.gradle`、小程序 `project.config`、`scripts/` | 已删（main 覆盖） |
| `Bandersnatch0x/api-integration-claude` | 同上 + 后端 `config.py`/`main.py`、`.env.example` | 已删（main 覆盖） |
| `Bandersnatch0x/api-integration-codex2` | 仅 `scripts/test_smoke_api.py` | 已删（main 覆盖） |
| `Bandersnatch0x/deployment` | `docker-compose.yml`、`services/api/Dockerfile`、`deploy/`、`.env.example` | 已删（main 覆盖） |
| `Bandersnatch0x/deployment-claude` | 同上 + `alembic.ini`、`services/api/migrations/`、`.dockerignore` | 已删（main 覆盖） |
| `Bandersnatch0x/e2e-tests` | `playwright.config.ts`、`apps/admin-web/e2e/`、`package.json` | 已删（main 覆盖） |
| `Bandersnatch0x/e2e-tests-claude` | 同上 + `pnpm-lock.yaml` | 已删（main 覆盖） |
| `Bandersnatch0x/e2e-tests-codex2` | 仅票 0004 文档改动 | 已删（main 覆盖） |

## offline-cache 为什么不能直接丢

`prefetchMutex` 在整个仓库历史里搜不到（`git log --all -S "prefetchMutex"` 为空），
也不是任何分支的祖先。它做的是：

- 预取任务与会话互斥，避免同一 URL 并发下载；
- `startupPlan.fitsQuota` 为 false（pinned 自身超限）时不再做无收益的淘汰，只告警；
- 播放路径只查内存索引，不在 Compose 组合线程做磁盘 stat。

前两条是真实的行为修正，第三条是有意的性能取舍（代价：外部删文件后播放失败走内置视频回退）。
main 当前版本没有这些。

## 待办

1. ~~评审 `offline-cache` 的 3 处改动，决定是否重做进 main~~ — 已完成：3 处全部采纳，已重做进 main（`d96d4fd`）。
2. ~~其余 8 个分支确认后删除~~ — 已完成（2026-11-08）。
3. `offline-cache` 本地 + origin + gitee 分支待确认后删除（代码已与 main 等价）。

## 相关

- 票 0035：4 个未并入特性分支的甄别

## 进展

### 2026-11-08 8 个 WIP 分支已删

`git cherry` 对 8 个分支全部显示 `+`（WIP 提交的 patch-id 与 main 必然不同），
所以改用「main 缺哪些文件」逐项核对。唯一真正「main 没有」的文件只有 4 个，
且逐个评估后都不需要保留：

| main 缺失的文件 | 出自 | 结论 |
| --- | --- | --- |
| `apps/android/app/src/debug/res/xml/network_security_config.xml` | api-integration | main 的 debug 清单已用 `android:usesCleartextTraffic="true"`（更宽但够用），功能等价 |
| `services/api/scripts/prepare_smoke.py` | api-integration | 自带「仅 SQLite」守卫，覆盖不到 PostgreSQL 容器联调；而 main 文档的流程（`app.seed` 建库 → `smoke-api.py --base-url`）不依赖它 |
| `services/api/.dockerignore` | deployment-claude | **实际不生效**：Dockerfile 的 `COPY` 用的是 `services/api/...` 与 `deploy/...`，构建上下文是仓库根，Docker 只认根目录的 `.dockerignore` |
| `apps/admin-web/.env.example` | api-integration-claude | main 已提交 `.env.development`（`VITE_API_BASE_URL=/v1` 走开发代理），示例文件多余 |

其余「main 有但实现不同」的项，逐项确认 main 是更新或更完整的一版：

- **迁移**：main 在 `deploy/migrations/versions/` 有 `0001`–`0005`，分支只有单条 `0001_initial_schema`（552 行）。main 的链路已在 PostgreSQL 17.11 上验到 head。
- **E2E**：main 的 `apps/admin-web/e2e/` 有 `admin.spec.ts`（19.9K）+ `permissions.spec.ts` + `totp.spec.ts`，分支只有一个更小的 `admin-web.spec.ts`（159 行）。
- **API 接线**：main 的 `vite.config.ts` 已有 `/v1` 代理、`build.gradle.kts` 已有 debug 变体的 `API_BASE_URL`、小程序 `config.ts` 已指向 `127.0.0.1:8000/v1`。
- **CORS**：`api-integration-claude` 往 `main.py` 加了 `CORSMiddleware`，但 `docs/deployment/README.md` 明确「当前部署只提供 API 与数据库，不包含管理端静态站点」，开发期走 vite 代理，不需要跨域。
- **`training-voice`**：独有的 TTS 超时已重做进 main（`1e3f26f`，见票 0035），剩余只有文案差异（`videoTitle` 5 处「标准示范」→「动作演示」、`suitabilityTag`「疑似 · 需分期确认准入」→「疑似 · 需治疗师确认」），无测试断言，属产品口径，随分支一并删除，需要时按此记录重做。

已从本地 + origin + gitee 三处删除 9 个分支（含 `training-voice`）。
`git branch --no-merged main` 现在只剩 `offline-cache`。

### 2026-11-08 待办 1：offline-cache 评审完成，3 处改动已重做进 main

评审结论：**3 处全部采纳**。未直接 merge 分支（WIP 提交信息无意义），把同一份改动
重做为一个聚焦提交 `d96d4fd`，5 个代码/测试文件与分支逐字节一致。

1. **预取轮次互斥 + 新轮取消旧轮**：main 的 `prefetch()` 可被启动预取与处方变更并发
   触发，两个轮次会交叉执行 `applyPrescriptionPins` 与在途下载的 pinned 回写；按 URL
   的锁只保证同一 URL 不并发写，保证不了轮次语义。`prefetchMutex` 串行轮次，
   `prefetchJob.cancel()` 取消旧轮；配套让下载器在协程取消时 `call.cancel()` 并
   `ensureActive()`，把取消引发的 `IOException` 翻译为 `CancellationException`，
   旧轮不会在取消后继续写盘或回写 pinned。
2. **`startupPlan.fitsQuota` 守卫**：与 `MediaCacheEvictionPolicy` 的文档契约
   「pinned 自身超限只告警，不删除」一致。main 在 pinned 已超配额时仍执行淘汰计划、
   删光全部非 pinned 文件，删完也回不到配额内，属无收益数据删除；改为仅
   `fitsQuota` 为真时淘汰，新增 pinned 下载同理（`plan.fitsQuota == false` 时不淘汰）。
3. **播放路径去磁盘 stat**：`InstructionalVideoSource.resolvedRemoteUrl` 是 Compose
   组合期 getter，main 的 `localPlaybackUrl` 每次都做 `File.isFile`，属主线程磁盘 I/O
   且会触发 StrictMode。改为只查内存索引；代价已确认可接受：外部删文件后本次进程内
   播放失败走内置视频回退，自愈留到下次启动 `MediaCacheStore.load()` 过滤缺失文件。

同一提交还采纳了分支的配套修正（与上述 3 处同源，拆开没有意义）：

- **改名与淘汰删除同锁**：main 在锁外删除被淘汰文件，可能删到并发重新下载后刚改名的
  同名文件；改为在 `synchronized(lock)` 内完成改名、登记与删除。
- **下载前配额预判**：pinned 已占满配额时普通条目直接返回 `QUOTA_REJECTED`，不再
  整段下载后删除。
- **损坏索引保护**：索引读取/解析失败时返回空索引但**不** `cleanupOrphans`，避免
  main 的「损坏即清空缓存」；下次成功写盘后孤儿自愈。
- **索引摘要/URL 校验**：`sha256` 必须匹配 `^[0-9a-f]{64}$`、URL 必须为 http(s)，
  阻断被篡改索引经 `fileFor` 造成的路径穿越与目录外删除，并有回归测试覆盖。

验证：

- `:app:testDebugUnitTest` **25 个测试类 194 项全通过 / 0 失败**（其中
  `MediaCacheManagerTest` 18 项、`MediaCacheStoreTest` 8 项；本次新增 4 项）。
- `:app:assembleDebug` 通过。
- `git diff --check` 无输出。
- `git diff Bandersnatch0x/offline-cache -- <5 个代码/测试文件>` 为空。

分支处置：本地 + origin + gitee 的 `Bandersnatch0x/offline-cache` 可在确认后删除；
本轮未执行删除。

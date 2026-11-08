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
| `Bandersnatch0x/offline-cache` | `MediaCacheManager` 的 `prefetchMutex` 预取互斥 + `fitsQuota` 启动淘汰修正 + 播放路径去磁盘 stat（6 文件） | **独有，需评审** |
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

1. 评审 `offline-cache` 的 3 处改动，决定是否重做进 main（分支基线过旧，直接 merge 会冲突）。
2. ~~其余 8 个分支确认后删除~~ — 已完成（2026-11-08）。

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

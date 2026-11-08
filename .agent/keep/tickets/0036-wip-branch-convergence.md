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
| `Bandersnatch0x/api-integration` | 管理端 `vite.config` 代理、Android `build.gradle`、小程序 `project.config`、`scripts/` | 旧副本 |
| `Bandersnatch0x/api-integration-claude` | 同上 + 后端 `config.py`/`main.py`、`.env.example` | 旧副本 |
| `Bandersnatch0x/api-integration-codex2` | 仅 `scripts/test_smoke_api.py` | 旧副本 |
| `Bandersnatch0x/deployment` | `docker-compose.yml`、`services/api/Dockerfile`、`deploy/`、`.env.example` | 旧副本 |
| `Bandersnatch0x/deployment-claude` | 同上 + `alembic.ini`、`services/api/migrations/`、`.dockerignore` | 旧副本 |
| `Bandersnatch0x/e2e-tests` | `playwright.config.ts`、`apps/admin-web/e2e/`、`package.json` | 旧副本 |
| `Bandersnatch0x/e2e-tests-claude` | 同上 + `pnpm-lock.yaml` | 旧副本 |
| `Bandersnatch0x/e2e-tests-codex2` | 仅票 0004 文档改动 | 旧副本 |

「旧副本」的判据：对应文件在 main 上的最后提交时间晚于 worktree 内的文件修改时间
（如 `docker-compose.yml` main 是 11-07 01:31，worktree 是 11-05 00:39），
即 main 上已经是更新的一版。

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
2. 其余 8 个分支确认后删除（本地 + 两个远端），避免远端分支列表堆积。
3. 若全部处理完，`git branch --no-merged main` 应只剩票 0035 里未决的分支。

## 相关

- 票 0035：4 个未并入特性分支的甄别

---
id: "0025"
type: "infra"
status: "closed"
assignee: "claude"
part-of: null
created: 2026-11-07
---

# 迁移 0002–0005 在真实 PostgreSQL 上的复验（P4-2）

## 问题

`docs/deployment/README.md` 的容器验证表停留在只有 `0001` 的时点（`alembic current = 0001 (head)`、
PostgreSQL 表数 17），而此后新增了 `0002`–`0005` 四个迁移。`deploy/verify.py` 只覆盖 SQLite 往返
与 PostgreSQL **离线** SQL 生成，不证明这四条迁移在真实 PostgreSQL 上能建出正确结构、能降级、
降级再升级后既有数据仍在。

## 做法

新增 `deploy/verify_postgres.py`（需要服务的部分与 `verify.py` 互补，两者都要跑）：

- 拒绝在非空库上运行，避免降级删掉真实数据。
- `upgrade head` 后逐条断言 `0002`–`0005` 引入的列、检查约束、外键、索引与 JSON 列真实存在。
- 在真实 PostgreSQL 上跑 `compare_metadata`（比 SQLite 更能暴露类型/约束漂移）。
- 用 ORM 写入机构/账号/患者并读回，验证 `0005` 的 `totp_secret`、`totp_last_step` 可用。
- `downgrade 0001` → `upgrade head`：既有患者行保留，`patient.data_origin` 按 `server_default` 回填 `real`。
- `downgrade base` → `upgrade head`：证明迁移可从零重复执行。

## 结果

在 `postgres:17-bookworm`（PostgreSQL 17.11）上实跑：

| 项 | 结果 |
| --- | --- |
| `alembic upgrade head`（空库） | 通过，`current` = `0005 (head)` |
| 表数 | 19（18 张业务表 + `alembic_version`） |
| `deploy/verify_postgres.py` | PASS（结构 + 数据回读 + 往返 + 模型差异为空） |
| 迁移建库后的接口联调 | **25/25 契约操作通过**（21 条路径），`REHAB_AUTO_CREATE_SCHEMA=false` |

## 落选方案

- **只加断言到 `verify.py`**：SQLite 不支持 `ALTER ... ADD CONSTRAINT`，走的是 batch 整表重建，
  与 PostgreSQL 的常规 `ALTER` 是两条代码路径，证明不了 PostgreSQL 那条。故新建独立脚本。
- **改 `scripts/verify_all.py` 让它自带 PostgreSQL**：会让本地一键自检依赖 Docker，破坏
  "无需数据库服务即可跑通" 的现有约定。故保持独立、按需运行。

## 顺带修正的过时描述

- README 称登录限流"使用进程内状态，多副本不能共享计数"——`0005` 已改为 `login_failure` 表实现，
  该说法失效，已更正（并注明清理依赖登录时窗口裁剪，尚无独立清理任务）。
- README 称联调 18/18、表数 17、`alembic current = 0001`，均已更新为当前值。

## 局限

- 未在容器内跑（宿主机 Python 直连容器 PostgreSQL），故未复验镜像构建产物与迁移账号权限分离；
  这两项仍是 `0001` 时点的结论。
- 未做长时压测与并发验证（P4-5）。

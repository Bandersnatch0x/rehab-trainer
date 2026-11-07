---
id: "0032"
type: "feature"
status: "done"
assignee: null
part-of: null
created: 2026-11-07
---

# 拆分存活/就绪探针，探针改走应用自己的连接池

## 现象

部署手册要求把 `/openapi.json` 当存活探针、把 `deploy/healthcheck.py` 当就绪探针。两处都不对：

1. **`/openapi.json` 是公开的业务文档接口**，拿它当探针等于每 30 秒生成一次完整 schema，
   访问日志里全是噪声，而且它是否 200 与进程能否服务业务无关。
2. **健康检查脚本自己 `create_engine` 直连数据库**。它测的是「另开一条连接能不能连上」，
   不是「正在服务流量的这个进程能不能从池里拿到连接」。**连接池耗尽时，探针报健康，业务全在排队**——
   正是票 0026 修的那个故障场景，探针却看不见。
3. **存活与就绪没分开**。脚本同时探 HTTP 和数据库，数据库抖动会让编排重启一个本身没问题的进程。

## 修复

| 探针 | 路径 | 检查 | 失败语义 |
|---|---|---|---|
| 存活 | `/healthz` | 仅进程能响应，**不碰库** | 重启 |
| 就绪 | `/readyz` | 应用 `engine` 上执行 `SELECT 1` | 摘流量，不重启 |

- 两者 `include_in_schema=False`：**不进契约**。它们描述进程状态，不是客户端可依赖的业务接口；
  写进 `openapi.yaml` 会暗示调用方据此做业务决策，也白白多两个要维护的 operation。
- `deploy/healthcheck.py` 改为只探 `/readyz`，按退出码返回（200→0，503/不可达→1），
  不再自建引擎，去掉每条探针一个隐藏连接的浪费。
- 就绪失败只回 `{"status":"database_unavailable"}`，**错误细节只进日志**——
  探针可能被匿名访问，异常里常带主机名。

## 证据

- `services/api/tests/test_health.py`（8 项）：200 正常、库不可达 503、**池耗尽 503**（`PoolTimeoutError` 路径）、
  库挂时 `/healthz` 仍 200（证明存活不依赖库）、响应不含连接串/池信息、两个路径都不在 `openapi()["paths"]` 里、免鉴权。
- `scripts/test_healthcheck.py`（3 项）：200→退出码 0、503→1、连不上→1（不抛栈）。
- API 全量：**135 项、0 失败**；`deploy/verify.py` 通过。

## 决定

- **没做迁移版本断言。** 旧脚本读 `alembic_version` 确认库已迁移；现在容器 `CMD` 是
  `alembic upgrade head && exec uvicorn`，迁移失败进程根本不会起来，探针里的版本检查是重复的。
  真要防"运行期被换成旧库"，属于发布流程而非探针职责。
- **没做指标端点。** 没有 Prometheus 采集端的前提下加 `/metrics` 是凭空造接口；
  云端按 `docs/deployment/README.md` 走容器日志采集，告警应基于结构化日志字段而非拉取式指标。
  这条留在 P4-4。

## 局限

- 就绪探针占用一个池连接（`SELECT 1` 期间）。探针间隔 30 秒、耗时毫秒级，可忽略；
  但若把间隔调到秒级，需把探针连接排除在池预算外。

## 真库验证（2026-11-07 补做，并发现一个缺陷）

用 WSLC 容器跑 `postgres:16-alpine`（`127.0.0.1:55433`），从 Windows 侧 `alembic upgrade head` + seed 后起 API：

| 场景 | 结果 |
| --- | --- |
| 库正常 | `/healthz` 200（17ms）、`/readyz` 200（36ms） |
| 停掉数据库 | `/healthz` **仍 200**（10ms）、`/readyz` **503**，响应体 `{"status":"database_unavailable"}` |
| 重启数据库 | `/readyz` 自动回到 200（178ms，无需重启 API） |

### 发现并修复：建连没有超时

第一次停库时 `/readyz` **挂了 15 秒以上没返回**。原因是 `pool_timeout` 只约束「排队等池」，
**不约束建立连接本身**；WSLC 端口转发在容器停掉后仍接受 TCP 连接但不转发，
于是 psycopg 一直等到操作系统放弃（Windows 约 20 秒）。

- 新增 `REHAB_DB_CONNECT_TIMEOUT_SECONDS`（默认 5），作为 `connect_timeout` 传给驱动。
- 修复后同一场景：`/readyz` **5.1 秒返回 503**，`/healthz` 10ms 仍 200。
- 部署约束写入手册：**平台就绪探针超时必须 ≥ 该值 + 1 秒**，否则平台先超时、拿不到我们返回的 503。

这条也覆盖票 0026：过载时快速失败，但「连不上」原本仍会挂——两处超时现在都有上界。

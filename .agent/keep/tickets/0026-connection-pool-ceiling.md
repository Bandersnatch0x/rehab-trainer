---
id: "0026"
type: "infra"
status: "open"
assignee: null
part-of: null
created: 2026-11-07
---

# 连接池上限与过载行为（P4-5 发现）

## 现象

`scripts/loadtest-api.py` 对单实例 API 压测（Windows Python 3.13 + 单进程 uvicorn，PostgreSQL 17.11）：

| 并发 | RPS | p50 | 结果 |
| --- | --- | --- | --- |
| 8 | 111 | 59–79 ms | 正常 |
| 16 | 144 | 96–118 ms | 正常 |
| 32 | 133 | 218–251 ms | 吞吐不再增长，延迟翻倍 |
| 64 | 1.6 | 约 29 s | 客户端 30 s 超时，服务近乎停摆 |

## 根因

`app/core/database.py` 用 `create_engine(url)` 未显式配置连接池，取 SQLAlchemy 默认值
`pool_size=5`、`max_overflow=10`，即**最多 15 个并发数据库连接**，`pool_timeout` 默认 30 秒。

证据：并发 40 时持续采样 `pg_stat_activity`，目标库后端连接数稳定停在 15，压测结束回落到 5。
并发 64 时 p50 恰为 29 秒，正是请求在池外排队到 `pool_timeout` 后失败的表现。

因此上限约 140 RPS ≈ 15 连接 × 1/0.105 s，且**过载不降级而是挂起**：客户端要等 30 秒才拿到错误，
期间连接与线程都被占用。

## 待决策

1. **池大小**：按部署形态显式配置（副本数 × 池大小必须低于 PostgreSQL `max_connections` 并留余量），
   并评估是否需要调大 PostgreSQL 侧 `max_connections`。属部署参数，需与 P4-1 一起定。
2. **过载反馈**：把「排队到 30 秒再失败」改为快速失败。这要给契约补一个 5xx 响应
   （现有契约只声明 200/201/401/403/404/409/422/429），属契约变更，需先决定错误码与状态码，
   再改 openapi、生成物与客户端错误处理。

## 本次未做

未改动任何运行时行为：只测量并记录。凭据仍缺，未在真实多副本或生产规格机器上验证。

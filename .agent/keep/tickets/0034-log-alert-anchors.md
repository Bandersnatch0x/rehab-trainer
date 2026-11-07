---
id: "0034"
type: "feature"
status: "done"
assignee: null
part-of: null
created: 2026-11-07
---

# 日志告警锚点（P4-4 的「告警」）

## 现象

P4-4 要求「日志轮转、指标、告警」。轮转在 compose 里做了，指标和告警一直没做。告警缺的不是配置，
而是**可被稳定匹配的锚点**：失败路径的日志是自由中文，文案一改告警规则就失效，平台侧也没法可靠区分
「池耗尽」和「连不上库」。

## 决定：不造指标端点，先把日志做成可告警的

没有 Prometheus 采集端的前提下加 `/metrics` 是凭空造接口，还要处理暴露与鉴权；
而云端本来就要接容器日志采集。所以把告警建在**日志 + 探针**上：

| 信号 | 锚点 |
| --- | --- |
| 进程不可用 | `/healthz` 连续失败 |
| 不能接流量 | `/readyz` 连续失败 |
| 连接池耗尽 | `event=db_pool_exhausted` |
| 连接层故障 | `event=db_unavailable` |
| 就绪失败 | `event=db_readiness_failed` |

三条 `event=` 直接写在失败日志行首，后面保留中文说明给人看。键是契约：文案可以改，键不能改。

## 证据

- `test_pool_exhaustion_logs_a_stable_event_key`、`test_database_failure_logs_a_stable_event_key`、
  `test_readyz_failure_logs_a_stable_event_key` 三条用 `caplog` 断言锚点存在——
  有人把日志改回纯中文时测试会红。
- 部署手册新增「告警锚点」一节：锚点表 + 窗口建议，并写明阈值要按试点规模定、不要照抄。

## 决定不做的

- **指标端点**：等真有采集端（Prometheus 或云监控）再接，现在加只会多一个要维护的暴露面。
- **告警规则本身**：规则属于平台配置（日志服务/LB/编排），仓库里放不了，只能给锚点与建议窗口。

## 局限

- 锚点只是必要条件：平台侧不配规则，等于没有告警。
- 偶发 `db_pool_exhausted` 属正常削峰，窗口设太短会误报——建议值未在真实流量下标定。

## 真机/真库验证

用 WSLC 起 PostgreSQL 16 + 本地 API，把三条失败路径各触发一次，读真实日志：

| 触发 | 状态码 | 日志锚点 |
| --- | --- | --- |
| 停库后调登录 | 503 | `event=db_unavailable method=POST path=/v1/auth/login error=(psycopg.errors.ConnectionTimeout) …` |
| 停库后探就绪 | 503 | `event=db_readiness_failed error=…` |
| 64 并发压测 | 13 次 503 | **13 行** `event=db_pool_exhausted method=POST path=/v1/auth/login pool_limit=15 连接池等待超时` |

最后一行值得单独说：压测报告 13 次错误，日志正好 13 行锚点，**一一对应**——
说明锚点没有漏记也没有重复记，按它计数是可信的。

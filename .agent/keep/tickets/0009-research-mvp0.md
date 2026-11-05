---
id: "0009"
type: task
status: open
assignee: claude
part-of: null
blocked-by: []
created: 2026-11-05
---

# 科研数据底座 v0（MVP-0）

目标：按 `.agent/tmp/orca-specs/12-research-mvp0.md` 落地科研数据底座 v0，范围为用户已裁决的 N1、N2、N3、N5、N7。不做任何模型训练。

范围：只改 `services/api/`、`apps/admin-web/src/`（新增科研页）、`packages/api-contract/`、本票与 `.agent/tmp/`。不改 `apps/admin-web/src/styles.css`、小程序、Android。

硬约束：只返回聚合值且 n<5 抑制；`patient.data_origin` 默认 `real`，seed 患者标 `demo`，科研查询默认只取 `real`；D23 未验证字段进 denylist；科研查询默认限定当前账号 `org_id`。

验收：`pytest` 全绿；`npx tsc --noEmit` 通过；`npx playwright test` 现有 14 条仍通过并新增科研页冒烟；契约与实现操作集/参数/响应字段一致；两份 `d.ts` 重新生成；`git diff --check` 通过；`.agent/tmp/RESEARCH-MVP0.md` 报告。

## Log

### 2026-11-05 路由与实现

规格已给定、范围已裁决，直接进入落码。实施顺序：N1 字段与迁移 → N2 字典与校验 → N7 denylist → N3/N5 聚合端点与审计 → 契约与前端 → 验收。

### 2026-11-05 实现与自验

N1/N2/N3/N5/N7 全部落地，未做模型训练、行级导出、队列构建器与导出审批流。

- N1：`patient.data_origin` + `0002` 迁移（batch 模式，SQLite 与 PostgreSQL 兼容），seed 标 demo，两个科研端点均按 real 硬过滤（data-quality 联查患者表）。
- N2：`services/api/data/research-data-dictionary.yaml` v0.1，27 字段，9 项字典校验测试。
- N3：`/research/overview`、`/research/data-quality` 两个新端点，全部聚合、n<5 抑制；前端新增科研页，样式独立于 `styles.css`。
- N5：端点级角色校验 + `research.overview.viewed` / `research.data_quality.viewed` 审计。
- N7：`D23_PROHIBITED_FIELDS` denylist + 递归泄露断言 + 字典 `prohibited` 标记。
- 契约新增 2 路径 5 schema，两份 d.ts 已重生成。

验证：pytest 68 passed（基线 50，新增 18）；tsc 通过；playwright 15 passed（14 原有 + 1 科研冒烟）；契约测试 4 passed；alembic 升降级通过；`git diff --check` exit 0。

回退验证：移除 data-quality 的 demo 联查后新测试失败，恢复后通过。

被否选项：引入图表库（边界禁止新依赖，改纯 CSS 条形）；版本分布用抑制占位（版本键本身可能构成准标识，改为不足 5 条不列出）；科研页样式写入 `styles.css`（另一路 worker 正在改该文件，改独立 CSS）。

发现：`ruff` 在 `app/seed.py` 报既有未使用导入（`Base`、`engine`），非本次引入，未顺手改。本票保持 open，等待独立验收；未执行合并、推送或发布。详细报告见 `.agent/tmp/RESEARCH-MVP0.md`。

### 2026-11-05 安全审查跟进：小样本差额防护

背景安全审查指出两处可反推的聚合泄露，已修复并补 5 项测试。

1. `rate()` 原先只看分母。总数公开时，精确比例可反解出分子（例如存疑 2/20 = 10% 直接暴露「2」）。改为分子在 1–4 时同样抑制；分子为 0 按真值公布。
2. 分布表原先逐格抑制，未做差额防护。若只隐藏 1 格，其余之和可由总数相减得出。新增二次抑制：只隐藏 1 格且总数已公开时，再抑制计数最小的可见格。`buckets()` 与 `version_buckets()` 同步应用。总数自身被抑制时跳过，因为无从相减。

顺带修正一次抑制的边界：计数 0 不再抑制（0 不指向个体，且「零缺失」是有价值的质量信号），抑制区间收窄为 1–4。

回退验证：移除分子检查后 `test_ratio_is_suppressed_when_numerator_is_small` 失败（返回 `{'value': 0.1, 'suppressed': False}`），恢复后通过。

验证：pytest 73 passed（新增 5 项）；tsc 通过；playwright 15 passed；契约描述同步更新并重新生成两份 d.ts；`git diff --check` exit 0。

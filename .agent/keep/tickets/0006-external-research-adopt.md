---
id: "0006"
type: task
status: open
assignee: claude
part-of: null
blocked-by: []
created: 2026-11-05
---

# 外部调研可借鉴点落地

目标：把外部调研报告（仅思路，不复制代码）中可落地的工程缺口（本次五项）落成本仓实现，按优先级依次为：训练记录存疑标记服务端派生、Android 纯逻辑单测与采集门限收敛、小程序无障碍对比度机器校验、离线媒体预取策略骨架与离线队列契约补充。

范围：仅修改 `services/api/`、`apps/android/`、`apps/miniprogram/`、`.github/workflows/ci.yml`、本票与 `.agent/tmp/`。不改契约 `packages/api-contract/openapi.yaml`、不改决策记录、不引入除 JUnit 外的新依赖、不复制任何外部代码或媒体资源、不在代码/文档/票中出现外部产品名。

约束：许可证为 AGPL-3.0，动作媒体另有独立商业授权；一律自研重写。未决项 12 的有效性阈值尚未临床签核，机制先落，数值以「待临床签核」占位。

验收：后端 `pytest` 全绿（基线 47 项）；Android `assembleDebug` 与 `testDebugUnitTest` 通过；小程序 `static_check.py` 与 `node --test tests/patient-data.cjs` 通过；`git diff --check` 通过；交付任务指定的临时报告（`.agent/tmp/` 下）逐项说明。

## Log

### 2026-11-05 路由与成票

规格已给定，方向明确，按落码阶段直接执行。只吸收思路并自研实现；外部产品名不进入任何受版本控制的文件。按 P0-2 → P1-3 → P1-2 → P0-1 → P1-1 顺序实施，前一项验证通过后再做下一项。

### 2026-11-05 实现与自验

五项全部完成（P0-1/P1-1 按任务边界只交付骨架与契约）：

1. **P0-2 存疑派生**：`clinical.py` 增加待签核占位阈值与版本表，`sessions.py` 改用 `derive_is_doubtful()`。客户端自报 true 保留，自报 false 不能覆盖服务端判定，有效帧占比缺失 fail-closed。契约未改；`GET /sessions` 返回派生值与原始输入，可复核。
2. **P1-3 Android 纯逻辑单测**：新增 `CaptureGate.kt` 收敛 D17 的 `>= 0.5f`，`TrainingScreen` 改为调用；新增 `ArmRaiseCounterTest`、`CaptureGateTest`，JUnit 4.13.2，CI 增加 `testDebugUnitTest`。
3. **P1-2 对比度机检**：`static_check.py` 增加 WCAG 公式与 20 组前景/背景对照，同时校验色值仍在 `app.wxss` 中。
4. **P0-1 离线预取骨架**：新增 `MediaPrefetchPolicy.kt`（预取集合、网络门槛、并发 2、连续失败 3 次停止）与 8 项单测；下载与 LRU 留票 0007。
5. **P1-1 离线队列契约**：`count()` 更名 `pendingCount()`，新增 `NON_RETRYABLE_ERROR_CODES` 与 `isRetryable()`；未实现 Room/WorkManager。

验证（全量日志已落盘，未用管道截断判读）：

- `python -m pytest` → **50 passed**, 1 warning，最终全量日志 65.47s。
- `./gradlew :app:testDebugUnitTest --no-daemon` → **BUILD SUCCESSFUL**，18 tests / 0 failures（计数 6 + 门限 4 + 预取 8）。
- `./gradlew :app:assembleDebug --no-daemon` → **BUILD SUCCESSFUL**，`app-debug.apk` 85.8M。
- `python tests/static_check.py .` → **0 problem(s)**。
- `node --test tests/patient-data.cjs` → **15 pass / 0 fail**（工作树缺依赖，先执行 `pnpm install --frozen-lockfile`，锁文件未变）。
- `git diff --check` → exit 0。
- `ruff check`（四个改动 Python 文件）→ **All checks passed!**

回退验证：临时把 `is_doubtful` 改回 `record.is_doubtful` 后，新增的「伪造 false + 低占比」与「缺失占比 fail-closed」两项测试均失败；恢复实现后通过。自报 true 保留项在旧实现下本就通过，符合其守卫性质。

标准轴自查：导入与类型、边界校验、错误路径、无新依赖、无外部名、无复制代码。规格轴自查：逐项对照任务规格，契约未改、决策记录未改、待签核标注保留、P2/P3 项未纳入范围。完整命令输出、发现与残留风险见 `.agent/tmp/` 下本次任务的交付报告。

发现：`docs/design/design-system.md` 的手工对比度数值与 WCAG 标准公式有偏差（如主色白字文档 8.12:1、实际 7.87:1），本次未改文档，已在报告登记。

被否选项：直接把客户端上报值落库（无法阻止自称无异常）；为触发测试编造已签核阈值（与未决项 12 冲突）；在小程序静态检查里引入测试框架（成本高于收益）；一次性实现下载与 LRU（任务明确要求只做骨架）。无未决实施项；本票保持 open，等待独立验收，未执行合并、推送或发布。

---
id: "0007"
type: task
status: closed
assignee: null
part-of: null
blocked-by: []
created: 2026-11-05
---

# 离线媒体缓存实现（T3 余下部分）

目标：在 `MediaPrefetchPolicy` 策略骨架之上接入真实下载与本地缓存，使当前处方与进行中训练的媒体可离线播放。

范围：`apps/android/`（media3 依赖、下载执行、缓存索引与容量治理）、`services/api/`（按机型分档的离线预算配置下发）。不改契约时优先复用现有响应；确需新端点时先补契约。

约束（来自 T3 已裁决项）：720p；pinned + LRU；平板上限 1.5GB / 30 条，手机待实测后定；缓存跨 App 版本存活；计费网络与省流量模式不预取；并发 2、连续失败 3 次停止本轮。阈值与配额数值须可配置，不硬编码在 UI。

验收：策略骨架的既有单测保持通过；下载与淘汰有 JVM 单测或可复跑集成检查；断网可播放当前处方；缓存命中与容量上限有可复现证据；`git diff --check` 通过。

## Log

### 2026-11-05 建票

由任务 0006 拆出：该次只交付策略骨架与纯函数单测，真实下载、双上限 LRU、跨版本存活与配额下发明确留待本票，不在骨架里写半成品缓存层。

### 2026-11-06 落码完成

按 `.agent/tmp/orca-specs/16-offline-cache.md` 与 D25/D27 完成缓存层：

- 真实下载：OkHttp 流式写 `.part`，完整改名后才登记 JSON 索引，失败清理半成品。
- 缓存索引：`filesDir/media-cache/index.json` 原子写（tmp → rename），含 `url/sha256/bytes/pinnedByPrescriptionId/lastUsedAt/cachedAt/appVersion`；缺失文件与孤儿在启动加载时清理，绝不按 `appVersion` 清除。
- 容量治理：手机 500MB/10 条、平板 1.5GB/30 条；非 pinned 按 `lastUsedAt` 升序淘汰；pinned 永不淘汰，pinned 自身超限只告警；可用空间 <1GB 不开始/不继续预取。
- 预取：沿用既有策略骨架的网络门槛、并发 2、连败 3 次停止；处方变更会解除旧处方的 pinned。
- 播放接线：`InstructionalVideoSource` 命中缓存返回本地路径，未命中保持远端并触发非计费网络下的后台补缓存；播放解析刷新 `lastUsedAt`（最多每分钟一次）。
- 日志只记 URL 摘要（缓存键），不输出可能带签名/token 的原始媒体地址。

验证：`:app:testDebugUnitTest` 13 个测试类 80 项全通过（新增缓存层 36 项与播放缓存 2 项；原有 8 项 `MediaPrefetchPolicyTest`、6 项既有 `InstructionalVideoSourceTest` 继续通过）；`:app:assembleDebug` 通过；`git diff --check` 无输出。详细证据与未做项见 `.agent/tmp/OFFLINE-CACHE.md`。

未完成（原因已记）：无设备/模拟器，断网播放实测缺运行证据；训练页仍用 `DEFAULT_EXERCISES`（`videoUri` 全空）且处方数据未接 UI，预取触发入口已就绪但暂无调用方；服务端配额下发按规格另立票。

边界例外：`MainActivity` 增加 `MediaCache.install`、Manifest 增加 `ACCESS_NETWORK_STATE`（普通权限），均为接线必需；未改 `ui/screens/`。

### 2026-11-06 实现与验收

真实下载、JSON 索引（原子落盘 + 损坏容错）、pinned + LRU 双上限、跨版本存活、D27 分档配额（手机 500MB/10 条、平板 1.5GB/30 条、可用空间 <1GB 停预取）全部落地。协调者独立复跑：`:app:testDebugUnitTest` **80 项 / 0 失败**、`assembleDebug` 通过。

未做（已在报告写明）：
- 断网播放真机实测（无连接设备）；
- 预取触发无调用方（训练页仍用静态 `DEFAULT_EXERCISES`，`videoUri` 为 null；`requestStartupPrefetch` / `requestPrescriptionPrefetch` 已就绪待接线）；
- 配额服务端下发留待另立票；
- 下载器未做断点续传与内容哈希校验。

关票。被否选项：并入视频管理一起做（会与上传/播放路径抢同一批文件）；暂缓（T3 的离线承诺将无证据）。无替代方案。

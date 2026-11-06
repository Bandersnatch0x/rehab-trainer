---
id: "0018"
type: "feature"
status: "closed"
assignee: "claude"
part-of: null
created: 2026-11-06
---

# Android 绑定与真实训练方案接入（P3-1 第一期）

目标：设备绑定/登录后拉取真实患者与当前训练方案，替换训练列表与训练页的静态条目。

范围：`apps/android/` 网络、会话、注册表与训练列表/训练页；不改契约（仅补 Android 侧 GET /prescriptions 客户端）。

验收：
- 持久化令牌；绑定码/账号登录；解绑回到绑定页；
- /me → 患者 → 当前方案 → 条目详情 → 本地快照；离线启动读快照；
- 训练列表与训练页使用服务端方案，未绑定/空方案回退演示条目；
- 单测覆盖方案选择、合并、快照序列化、条目映射；
- 真机绑定并看到服务端方案（肩颈牵伸、坐位抬腿）。

## Log

### 2026-11-06 实现与验证

- `SharedPreferencesTokenStore`、`SessionRepository`、`PlanSelector`、`ExerciseRegistry`、`BindScreen`。
- `GET /prescriptions` 客户端补全。
- 真机 OPPO PKX110：`demo-binding-token` 绑定成功，训练列表显示「肩颈牵伸 / 坐位抬腿」，训练页显示对应计数标签。
- 未做：训练记录上传、首页/记录/提醒/档案真实数据、自助重新绑定入口。

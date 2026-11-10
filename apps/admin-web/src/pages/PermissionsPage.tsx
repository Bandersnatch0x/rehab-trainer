import { Fragment, useEffect, useState, type FormEvent } from "react";
import { createAccount, listAccounts, updateAccount } from "../api/operations";
import { formatServerTime } from "../api/time";
import type { Account, AccountStatus } from "../api/types";
import { isAdminRole, ROLE_LABELS, type AdminRole } from "../auth/roles";
import { useAuth } from "../auth/AuthProvider";

const STATUS_LABELS: Record<AccountStatus, string> = {
  active: "正常",
  disabled: "已停用",
};

const PAGE_LIMIT = 100;
const MAX_ACCOUNTS = 1000;

/** roles 里可能有患者/照护者等非管理端角色，列表只展示管理端的两类。 */
function roleSummary(roles: string[]): string {
  const labels = roles.filter(isAdminRole).map((role) => ROLE_LABELS[role]);
  if (labels.length > 0) {
    return labels.join("、");
  }
  // roles 非空但全是患者/照护者：不是「未分配」，而是本页不管理的账号。
  return roles.length > 0 ? "非管理端账号" : "未分配";
}

export default function PermissionsPage() {
  const { session } = useAuth();

  // 与后端 GRANTABLE_ROLES 对齐：治疗师只能授予治疗师，自造管理员等于变相自升权。
  // 服务端仍是最终裁决者（越权返回 403），这里只是不渲染越权入口。
  const grantable: AdminRole[] =
    session?.role === "care_admin" ? ["care_admin", "rehab_therapist"] : ["rehab_therapist"];
  // 只有管理端账号才可管理：空 roles 或仅含患者/照护者角色时，every() 会返回 true，
  // 必须显式要求至少有一个管理端角色。
  const canManage = (account: Account): boolean => {
    const adminRoles = account.roles.filter(isAdminRole);
    return adminRoles.length > 0 && adminRoles.every((role) => grantable.includes(role));
  };

  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [truncated, setTruncated] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  const [showCreate, setShowCreate] = useState(false);
  const [createName, setCreateName] = useState("");
  const [createUsername, setCreateUsername] = useState("");
  const [createPassword, setCreatePassword] = useState("");
  const [createRole, setCreateRole] = useState<AdminRole>(grantable[0]);
  const [createError, setCreateError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const [editStatus, setEditStatus] = useState<AccountStatus>("active");
  const [editRole, setEditRole] = useState<AdminRole | null>(grantable[0]);
  const [editOriginalRole, setEditOriginalRole] = useState<AdminRole | null>(null);
  const [editError, setEditError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setLoadFailed(false);

    async function loadAll() {
      const collected: Account[] = [];
      const seen = new Set<string>();
      let cursor: string | undefined;
      do {
        const result = await listAccounts({
          limit: PAGE_LIMIT,
          ...(cursor ? { cursor } : {}),
        });
        if (cancelled) {
          return;
        }
        if (!result.ok) {
          setError(result.error.message);
          setLoadFailed(true);
          setAccounts([]);
          setLoading(false);
          return;
        }
        collected.push(...result.data.items);
        cursor = result.data.nextCursor ?? undefined;
        if (cursor && seen.has(cursor)) {
          setError("分页游标重复，已停止加载。");
          break;
        }
        if (cursor) {
          seen.add(cursor);
        }
      } while (cursor && collected.length < MAX_ACCOUNTS);

      setAccounts(collected);
      // 达到页数上限时游标仍在，明确告知名单被截断，避免误以为已是全部。
      setTruncated(Boolean(cursor));
      setLoadFailed(false);
      setLoading(false);
    }

    void loadAll();

    return () => {
      cancelled = true;
    };
  }, [reloadToken]);

  function openEditor(account: Account) {
    const originalRole = account.roles.find(isAdminRole) ?? null;
    setEditingId(account.accountId);
    setEditName(account.displayName);
    setEditStatus(account.status);
    setEditRole(originalRole);
    setEditOriginalRole(originalRole);
    setEditError(null);
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreating(true);
    setCreateError(null);
    const result = await createAccount({
      displayName: createName,
      username: createUsername,
      password: createPassword,
      role: createRole,
    });
    setCreating(false);
    if (!result.ok) {
      setCreateError(result.error.message);
      return;
    }
    setShowCreate(false);
    setCreateName("");
    setCreateUsername("");
    setCreatePassword("");
    setCreateRole(grantable[0]);
    setReloadToken((token) => token + 1);
  }

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editingId) {
      return;
    }
    setSaving(true);
    setEditError(null);
    const result = await updateAccount(editingId, {
      displayName: editName,
      status: editStatus,
      // 只在角色确实变化时发送 role；改名/停用不应顺带重写角色绑定。
      ...(editRole && editRole !== editOriginalRole ? { role: editRole } : {}),
    });
    setSaving(false);
    if (!result.ok) {
      setEditError(result.error.message);
      return;
    }
    setEditingId(null);
    setReloadToken((token) => token + 1);
  }

  return (
    <div className="page">
      <h1>机构权限与角色管理</h1>
      <p className="page-description">
        机构内角色权限与账号分配管理。系统严格执行多机构隔离，不可查看跨机构成员（D10）。
      </p>

      <div className="notice">
        <div>
          <strong>职责矩阵权限边界（D4 约束）：</strong>
          <ul>
            <li><strong>康复治疗师</strong>：独占 H-Y 分级确认权（D11）、运动康复训练方案制定与调整权（D12）。</li>
            <li><strong>照护管理员</strong>：负责患者日常建档、风险预警跟踪、电话随访登记（D6）。</li>
          </ul>
        </div>
      </div>

      <div className="card">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
          <div>
            <div className="card-title" style={{ marginBottom: 4 }}>当前机构账号名册</div>
            <span className="hint">所属机构 ID：{session?.orgId ?? "—"}</span>
          </div>
          <button
            className="btn-primary"
            style={{ minHeight: 38, fontSize: 14 }}
            onClick={() => setShowCreate((open) => !open)}
          >
            {showCreate ? "取消开通" : "+ 分配机构账号权限"}
          </button>
        </div>

        {showCreate ? (
          <form
            aria-label="开通机构账号"
            onSubmit={handleCreate}
            style={{ display: "flex", gap: 16, alignItems: "flex-end", flexWrap: "wrap", marginBottom: 16 }}
          >
            <label>
              <span>姓名</span>
              <input
                value={createName}
                onChange={(event) => setCreateName(event.target.value)}
                required
                maxLength={128}
              />
            </label>
            <label>
              <span>登录名</span>
              <input
                value={createUsername}
                onChange={(event) => setCreateUsername(event.target.value)}
                required
                minLength={3}
                maxLength={128}
              />
            </label>
            <label>
              <span>初始密码</span>
              <input
                type="password"
                value={createPassword}
                onChange={(event) => setCreatePassword(event.target.value)}
                autoComplete="new-password"
                minLength={8}
                maxLength={128}
                required
              />
            </label>
            <label>
              <span>管理端角色</span>
              <select
                value={createRole}
                onChange={(event) => setCreateRole(event.target.value as AdminRole)}
              >
                {grantable.map((role) => (
                  <option key={role} value={role}>
                    {ROLE_LABELS[role]}
                  </option>
                ))}
              </select>
            </label>
            <button className="btn-primary" type="submit" disabled={creating}>
              开通账号
            </button>
            {createError ? (
              <p role="alert" className="notice notice--danger">
                {createError}
              </p>
            ) : null}
          </form>
        ) : null}

        <span className="hint">每个账号本轮只绑定一个管理端角色，如需调整请在「权限配置」中修改。</span>
        {truncated ? (
          <p className="hint">账号过多，仅显示前 {MAX_ACCOUNTS} 条，请缩小机构范围后再查看。</p>
        ) : null}

        {error ? (
          <div role="alert" className="notice notice--danger" style={{ display: "flex", gap: 12, alignItems: "center" }}>
            <span>机构账号加载失败：{error}</span>
            <button
              type="button"
              className="btn-secondary"
              style={{ padding: "4px 10px", minHeight: 30, fontSize: 13 }}
              onClick={() => setReloadToken((token) => token + 1)}
            >
              重试
            </button>
          </div>
        ) : null}

        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>账号编号</th>
                <th>姓名</th>
                <th>登录名</th>
                <th>绑定角色</th>
                <th>账号状态</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={7}>正在读取机构账号…</td>
                </tr>
              ) : loadFailed ? (
                <tr>
                  <td colSpan={7}>加载失败，请点击上方「重试」。</td>
                </tr>
              ) : accounts.length === 0 ? (
                <tr>
                  <td colSpan={7}>当前没有机构账号。</td>
                </tr>
              ) : (
                accounts.map((account) => (
                  <Fragment key={account.accountId}>
                    <tr>
                      <td><strong>{account.accountId}</strong></td>
                      <td><strong>{account.displayName}</strong></td>
                      <td><code>{account.username ?? "—"}</code></td>
                      <td>{roleSummary(account.roles)}</td>
                      <td>
                        <span
                          className={
                            account.status === "active" ? "badge badge--success" : "badge badge--danger"
                          }
                        >
                          {STATUS_LABELS[account.status]}
                        </span>
                      </td>
                      <td>{formatServerTime(account.createdAt)}</td>
                      <td>
                        <button
                          className="btn-secondary"
                          style={{ padding: "4px 10px", minHeight: 30, fontSize: 13 }}
                          // 治疗师看不到也改不了管理员账号：不给入口，服务端另有 403 兜底。
                          disabled={!canManage(account)}
                          title={canManage(account) ? undefined : "无权调整该账号"}
                          onClick={() =>
                            editingId === account.accountId
                              ? setEditingId(null)
                              : openEditor(account)
                          }
                        >
                          权限配置
                        </button>
                      </td>
                    </tr>
                    {editingId === account.accountId ? (
                      <tr>
                        <td colSpan={7}>
                          <form
                            aria-label="账号权限配置"
                            onSubmit={handleSave}
                            style={{ display: "flex", gap: 16, alignItems: "flex-end", flexWrap: "wrap" }}
                          >
                            <label>
                              <span>姓名</span>
                              <input
                                value={editName}
                                onChange={(event) => setEditName(event.target.value)}
                                required
                                maxLength={128}
                              />
                            </label>
                            <label>
                              <span>账号状态</span>
                              <select
                                value={editStatus}
                                onChange={(event) => setEditStatus(event.target.value as AccountStatus)}
                              >
                                <option value="active">正常</option>
                                <option value="disabled">已停用</option>
                              </select>
                            </label>
                            <label>
                              <span>角色</span>
                              <select
                                value={editRole ?? ""}
                                onChange={(event) => setEditRole(event.target.value as AdminRole)}
                              >
                                {grantable.map((role) => (
                                  <option key={role} value={role}>
                                    {ROLE_LABELS[role]}
                                  </option>
                                ))}
                              </select>
                            </label>
                            <button className="btn-primary" type="submit" disabled={saving}>
                              保存
                            </button>
                            <button
                              className="btn-secondary"
                              type="button"
                              onClick={() => setEditingId(null)}
                            >
                              取消
                            </button>
                            {editError ? (
                              <p role="alert" className="notice notice--danger">
                                {editError}
                              </p>
                            ) : null}
                          </form>
                        </td>
                      </tr>
                    ) : null}
                  </Fragment>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

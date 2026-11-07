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
  return labels.length > 0 ? labels.join("、") : "未分配";
}

export default function PermissionsPage() {
  const { session } = useAuth();

  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  const [showCreate, setShowCreate] = useState(false);
  const [createName, setCreateName] = useState("");
  const [createUsername, setCreateUsername] = useState("");
  const [createPassword, setCreatePassword] = useState("");
  const [createRole, setCreateRole] = useState<AdminRole>("care_admin");
  const [createError, setCreateError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const [editStatus, setEditStatus] = useState<AccountStatus>("active");
  const [editRole, setEditRole] = useState<AdminRole>("care_admin");
  const [editError, setEditError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

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
      setLoading(false);
    }

    void loadAll();

    return () => {
      cancelled = true;
    };
  }, [reloadToken]);

  function openEditor(account: Account) {
    setEditingId(account.accountId);
    setEditName(account.displayName);
    setEditStatus(account.status);
    setEditRole(account.roles.find(isAdminRole) ?? "care_admin");
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
    setCreateRole("care_admin");
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
      role: editRole,
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
              />
            </label>
            <label>
              <span>初始密码</span>
              <input
                type="password"
                value={createPassword}
                onChange={(event) => setCreatePassword(event.target.value)}
                required
              />
            </label>
            <label>
              <span>管理端角色</span>
              <select
                value={createRole}
                onChange={(event) => setCreateRole(event.target.value as AdminRole)}
              >
                <option value="care_admin">照护管理员</option>
                <option value="rehab_therapist">康复治疗师</option>
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

        {error ? (
          <p role="alert" className="notice notice--danger">
            机构账号加载失败：{error}
          </p>
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
                                value={editRole}
                                onChange={(event) => setEditRole(event.target.value as AdminRole)}
                              >
                                <option value="care_admin">照护管理员</option>
                                <option value="rehab_therapist">康复治疗师</option>
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

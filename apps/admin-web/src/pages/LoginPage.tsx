import { useState, type FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { login } from "../api/operations";
import { useAuth } from "../auth/AuthProvider";
import { isAdminRole } from "../auth/roles";

export default function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [totp, setTotp] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const { signIn } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setBusy(true);

    const result = await login({
      username,
      password,
      ...(totp.length > 0 ? { totp } : {}),
    });

    if (!result.ok) {
      setBusy(false);
      setError(result.error.message);
      return;
    }

    const adminRole = result.data.profile.roles.find(isAdminRole);
    if (!adminRole) {
      setBusy(false);
      // 账号本身有效，只是没有管理端角色：按 403 处理，不建立管理端会话。
      navigate("/forbidden", { replace: true });
      return;
    }

    signIn({
      accountId: result.data.profile.accountId,
      displayName: result.data.profile.displayName,
      orgId: result.data.profile.orgId,
      role: adminRole,
      accessToken: result.data.accessToken,
      refreshToken: result.data.refreshToken,
    });
    setBusy(false);
    navigate(params.get("redirect") ?? "/patients", { replace: true });
  }

  return (
    <main className="login-page">
      <div className="login-brand">
        <span className="app-brand">rehab-trainer</span>
        <span className="app-brand-badge">机构工作站</span>
      </div>
      <h1>医护人员登录</h1>
      <p className="hint">供照护管理员与康复治疗师使用机构认证账号登录。</p>
      <form onSubmit={onSubmit}>
        <label>
          账号
          <input
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            autoComplete="username"
            required
          />
        </label>
        <label>
          密码
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        <label>
          二次验证码（如已启用）
          <input
            value={totp}
            onChange={(event) => setTotp(event.target.value)}
            inputMode="numeric"
            autoComplete="one-time-code"
          />
        </label>
        {error ? (
          <p role="alert" className="hint">
            {error}
          </p>
        ) : null}
        <button type="submit" disabled={busy}>
          {busy ? "登录中…" : "登录"}
        </button>
      </form>
    </main>
  );
}

import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  confirmTotp,
  disableTotp,
  enrollTotp,
  getTotpStatus,
} from "../api/operations";
import type { TotpEnrollment } from "../api/types";

/**
 * 账号安全：本人自助启用/停用二次验证。
 *
 * 服务端把「生成密钥」与「确认启用」拆成两步——密钥在确认前不落库，
 * 所以中途放弃不会把账号锁在一个没录进认证器的密钥上。页面照这个流程走。
 */
export default function AccountSecurityPage() {
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [enrollment, setEnrollment] = useState<TotpEnrollment | null>(null);
  const [confirmCode, setConfirmCode] = useState("");
  const [password, setPassword] = useState("");
  const [disableCode, setDisableCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    const result = await getTotpStatus();
    if (!result.ok) {
      setError(result.error.message);
      return;
    }
    setEnabled(result.data.totpEnabled);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function startEnroll() {
    setBusy(true);
    setError(null);
    const result = await enrollTotp();
    setBusy(false);
    if (!result.ok) {
      setError(result.error.message);
      return;
    }
    setEnrollment(result.data);
    setConfirmCode("");
  }

  async function submitConfirm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!enrollment) {
      return;
    }
    setBusy(true);
    setError(null);
    const result = await confirmTotp({
      secret: enrollment.secret,
      code: confirmCode,
    });
    setBusy(false);
    if (!result.ok) {
      setError(result.error.message);
      return;
    }
    setEnabled(result.data.totpEnabled);
    setEnrollment(null);
    setConfirmCode("");
  }

  async function submitDisable(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    const result = await disableTotp({ password, code: disableCode });
    setBusy(false);
    if (!result.ok) {
      setError(result.error.message);
      return;
    }
    setEnabled(result.data.totpEnabled);
    setPassword("");
    setDisableCode("");
  }

  return (
    <div className="page">
      <h1>账号安全</h1>
      <p className="page-description">
        二次验证（TOTP）在口令之外再加一道认证器验证码。管理端账号能接触患者隐私数据，
        建议全部启用。
      </p>

      {error ? (
        <p role="alert" className="notice notice--danger">
          {error}
        </p>
      ) : null}

      <div className="card">
        <h2 className="card-title">二次验证</h2>

        {enabled === null ? (
          <p className="hint">加载中…</p>
        ) : enabled ? (
          <>
            <p>
              <span className="badge badge--success">已启用</span>
            </p>
            <p className="hint">
              登录时需要输入认证器生成的 6 位验证码，同一验证码不能重复使用。
              停用需要同时提供登录口令与当前验证码——只凭已登录的会话关不掉它。
            </p>
            <form
              aria-label="停用二次验证"
              onSubmit={submitDisable}
              style={{ display: "grid", gap: 12, maxWidth: 360 }}
            >
              <label>
                登录口令
                <input
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete="current-password"
                  required
                />
              </label>
              <label>
                认证器验证码
                <input
                  value={disableCode}
                  onChange={(event) => setDisableCode(event.target.value)}
                  inputMode="numeric"
                  pattern="[0-9]{6}"
                  autoComplete="one-time-code"
                  required
                />
              </label>
              <button type="submit" className="btn-secondary" disabled={busy}>
                停用二次验证
              </button>
            </form>
          </>
        ) : enrollment ? (
          <>
            <p className="hint">
              用认证器录入下面的密钥，再填入认证器显示的 6 位验证码完成启用。
              密钥在确认成功前不会写入账号——现在放弃不会影响登录。
            </p>
            <p>
              密钥：
              <code data-testid="totp-secret" style={{ userSelect: "all" }}>
                {enrollment.secret}
              </code>
            </p>
            <p className="hint" style={{ wordBreak: "break-all" }}>
              录入链接：<code>{enrollment.provisioningUri}</code>
            </p>
            <form
              aria-label="确认启用二次验证"
              onSubmit={submitConfirm}
              style={{ display: "grid", gap: 12, maxWidth: 360 }}
            >
              <label>
                认证器验证码
                <input
                  value={confirmCode}
                  onChange={(event) => setConfirmCode(event.target.value)}
                  inputMode="numeric"
                  pattern="[0-9]{6}"
                  autoComplete="one-time-code"
                  required
                />
              </label>
              <div style={{ display: "flex", gap: 12 }}>
                <button type="submit" className="btn-primary" disabled={busy}>
                  确认启用
                </button>
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => {
                    setEnrollment(null);
                    setConfirmCode("");
                  }}
                  disabled={busy}
                >
                  取消
                </button>
              </div>
            </form>
          </>
        ) : (
          <>
            <p>
              <span className="badge badge--info">未启用</span>
            </p>
            <p className="hint">
              启用后登录需额外输入认证器验证码。请先确认手机上已安装认证器应用。
            </p>
            <button
              type="button"
              className="btn-primary"
              onClick={startEnroll}
              disabled={busy}
            >
              启用二次验证
            </button>
          </>
        )}
      </div>

      <p className="hint">
        认证器丢失时无法自助恢复：需机构管理员用运维脚本清除该账号的二次验证，
        再按上面的流程重新启用。
      </p>
    </div>
  );
}

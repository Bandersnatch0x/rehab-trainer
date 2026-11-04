import { Link } from "react-router";

export default function ForbiddenPage() {
  return (
    <div className="page" style={{ maxWidth: 600, margin: "60px auto", textAlign: "center" }}>
      <div className="card" style={{ padding: "48px 32px" }}>
        <div style={{ fontSize: 56, color: "var(--color-warning)", marginBottom: 16 }}>🔒</div>
        <h1>403 · 无权访问该资源</h1>
        <p className="page-description" style={{ margin: "16px 0 24px" }}>
          当前登录账号所属角色不具备该操作权限。分期确认与处方调整仅康复治疗师可执行（D11、D12）。
        </p>
        <Link to="/patients" className="btn-primary" style={{ display: "inline-flex" }}>
          返回患者工作台
        </Link>
      </div>
    </div>
  );
}

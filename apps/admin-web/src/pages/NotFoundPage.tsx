import { Link } from "react-router";

export default function NotFoundPage() {
  return (
    <div className="page" style={{ maxWidth: 600, margin: "60px auto", textAlign: "center" }}>
      <div className="card" style={{ padding: "48px 32px" }}>
        <div style={{ fontSize: 56, color: "var(--color-primary)", marginBottom: 16 }}>🔍</div>
        <h1>404 · 页面未找到</h1>
        <p className="page-description" style={{ margin: "16px 0 24px" }}>
          请求的康复功能或档案页面不存在，请检查访问路径或返回工作台。
        </p>
        <Link to="/patients" className="btn-primary" style={{ display: "inline-flex" }}>
          返回患者工作台
        </Link>
      </div>
    </div>
  );
}

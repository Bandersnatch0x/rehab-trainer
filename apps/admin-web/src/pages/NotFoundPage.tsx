import { Link } from "react-router";

export default function NotFoundPage() {
  return (
    <main className="login-page">
      <h1>页面不存在</h1>
      <p className="page-description">请检查地址是否正确。</p>
      <p>
        <Link to="/patients">返回患者列表</Link>
      </p>
    </main>
  );
}

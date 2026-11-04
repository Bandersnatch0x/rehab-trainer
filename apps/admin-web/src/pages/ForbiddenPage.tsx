import { Link } from "react-router";

export default function ForbiddenPage() {
  return (
    <section className="page">
      <h1>无权访问</h1>
      <p className="page-description">当前账号的角色不包含此功能。</p>
      <p>
        <Link to="/patients">返回患者列表</Link>
      </p>
    </section>
  );
}

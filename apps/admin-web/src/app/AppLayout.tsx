import { NavLink, Outlet, useNavigate } from "react-router";
import { useAuth } from "../auth/AuthProvider";
import { ROLE_LABELS } from "../auth/roles";
import { NAV_ITEMS, canAccess } from "./navigation";

export function AppLayout() {
  const { session, role, signOut } = useAuth();
  const navigate = useNavigate();
  const navItems = NAV_ITEMS.filter((item) => canAccess(role, item));

  const handleSignOut = () => {
    signOut();
    navigate("/login", { replace: true });
  };

  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <div className="app-brand">
          <span>rehab-trainer</span>
          <span className="app-brand-badge">机构端</span>
        </div>
        <nav className="app-nav">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => (isActive ? "active" : undefined)}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        {/*
          多机构：机构由登录身份决定，服务端按 org 隔离数据。
          界面只读展示当前机构，不提供切换或跨机构检索入口（D10）。
        */}
        <div className="app-org">
          <div><strong>所属机构</strong></div>
          <div>{session?.orgId ?? "—"}</div>
        </div>
      </aside>
      <div className="app-content">
        <header className="app-header">
          <span className="app-user">
            {session?.displayName ?? "未登录"}
            {role ? ` (${ROLE_LABELS[role]})` : ""}
          </span>
          <button type="button" className="btn-secondary" onClick={handleSignOut}>
            退出登录
          </button>
        </header>
        <main className="app-main">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

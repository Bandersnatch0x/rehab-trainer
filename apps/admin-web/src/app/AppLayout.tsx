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
        <div className="app-brand">rehab-trainer</div>
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
        <p className="app-org">当前机构：{session?.orgId ?? "—"}</p>
      </aside>
      <div className="app-content">
        <header className="app-header">
          <span className="app-user">
            {session?.displayName ?? "未登录"}
            {role ? `（${ROLE_LABELS[role]}）` : ""}
          </span>
          <button type="button" onClick={handleSignOut}>
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

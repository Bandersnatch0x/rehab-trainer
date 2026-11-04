import { useAuth } from "../auth/AuthProvider";

interface AccountRoleBinding {
  accountId: string;
  displayName: string;
  username: string;
  role: "rehab_therapist" | "care_admin";
  roleLabel: string;
  status: "active" | "disabled";
  lastLogin: string;
}

const MOCK_ACCOUNTS: AccountRoleBinding[] = [
  {
    accountId: "ACC-TH-001",
    displayName: "李副主任医师",
    username: "therapist_dr_li",
    role: "rehab_therapist",
    roleLabel: "康复治疗师 (处方与分期签发权)",
    status: "active",
    lastLogin: "今日 08:30",
  },
  {
    accountId: "ACC-CA-002",
    displayName: "王主管护师",
    username: "admin_nurse_wang",
    role: "care_admin",
    roleLabel: "照护管理员 (患者档案与随访)",
    status: "active",
    lastLogin: "昨日 17:15",
  },
];

export default function PermissionsPage() {
  const { session } = useAuth();

  return (
    <div className="page">
      <h1>机构权限与角色管理</h1>
      <p className="page-description">
        机构内角色权限与账号分配管理。系统严格执行多机构隔离，不可查看跨机构成员（D10）。
      </p>

      <div className="notice notice--warning">
        <div>
          <strong>演示数据：</strong>
          契约当前没有机构账号列表接口，本页账号为示例，不代表真实成员；权限矩阵按决策 D4/D10/D11/D12 展示。
        </div>
      </div>

      <div className="notice">
        <div>
          <strong>职责矩阵权限边界（D4 约束）：</strong>
          <ul>
            <li><strong>康复治疗师</strong>：独占 H-Y 分期确认权（D11）、运动康复处方开具与调整权（D12）。</li>
            <li><strong>照护管理员</strong>：负责患者日常建档、风险预警跟踪、电话随访登记（D6）。</li>
          </ul>
        </div>
      </div>

      <div className="card">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
          <div>
            <div className="card-title" style={{ marginBottom: 4 }}>当前机构账号名册</div>
            <span className="hint">所属机构 ID：{session?.orgId ?? "ORG-HOSP-01"}</span>
          </div>
          <button className="btn-primary" style={{ minHeight: 38, fontSize: 14 }}>
            + 分配机构账号权限
          </button>
        </div>

        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>账号编号</th>
                <th>姓名</th>
                <th>登录名</th>
                <th>绑定角色与权限</th>
                <th>账号状态</th>
                <th>最近活跃</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {MOCK_ACCOUNTS.map((acc) => (
                <tr key={acc.accountId}>
                  <td><strong>{acc.accountId}</strong></td>
                  <td><strong>{acc.displayName}</strong></td>
                  <td><code>{acc.username}</code></td>
                  <td>
                    <span
                      className={
                        acc.role === "rehab_therapist"
                          ? "badge badge--info"
                          : "badge badge--warning"
                      }
                    >
                      {acc.roleLabel}
                    </span>
                  </td>
                  <td><span className="badge badge--success">正常</span></td>
                  <td>{acc.lastLogin}</td>
                  <td>
                    <button className="btn-secondary" style={{ padding: "4px 10px", minHeight: 30, fontSize: 13 }}>
                      权限配置
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

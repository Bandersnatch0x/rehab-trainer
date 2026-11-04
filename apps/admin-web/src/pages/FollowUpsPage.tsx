interface FollowUpRecord {
  id: string;
  patientName: string;
  patientId: string;
  date: string;
  type: string;
  content: string;
  officer: string;
  outcome: string;
}

const MOCK_FOLLOW_UPS: FollowUpRecord[] = [
  {
    id: "FU-2026-101",
    patientName: "王秀英",
    patientId: "PT-2026-085",
    date: "昨日 15:30",
    type: "跌倒风险自评异常电话随访",
    content: "患者居家自行踏步自评出现轻微晃动，已指导家属近身监护，下调踏步 BPM 至 56，并已预约下周复诊。",
    officer: "王照护主管",
    outcome: "居家安全已确认",
  },
  {
    id: "FU-2026-098",
    patientName: "张建国",
    patientId: "PT-2026-081",
    date: "2026-11-01 10:00",
    type: "分期确诊后首次训练随访",
    content: "训练完成度良好，无冻结步态发作，听觉节律引导适应性佳。",
    officer: "李康复治疗师",
    outcome: "处方执行正常",
  },
];

export default function FollowUpsPage() {
  return (
    <div className="page">
      <h1>随访台账</h1>
      <p className="page-description">
        汇总预警处置结果与患者训练依从性跟踪，供照护管理员与康复治疗师核验。
      </p>

      <div className="notice">
        <div>
          <strong>随访数据来源与协同：</strong>
          契约由预警处置记录（PATCH /alerts/&#123;id&#125;）与训练打卡数据沉淀汇总，机构内部协同留痕。
        </div>
      </div>

      <div className="table-container">
        <table>
          <thead>
            <tr>
              <th>台账编号</th>
              <th>患者</th>
              <th>随访时间</th>
              <th>随访类型</th>
              <th>随访记录摘要</th>
              <th>执行人</th>
              <th>处置结论</th>
            </tr>
          </thead>
          <tbody>
            {MOCK_FOLLOW_UPS.map((f) => (
              <tr key={f.id}>
                <td><strong>{f.id}</strong></td>
                <td><strong>{f.patientName}</strong> ({f.patientId})</td>
                <td>{f.date}</td>
                <td><span className="badge badge--info">{f.type}</span></td>
                <td style={{ maxWidth: 360 }}>{f.content}</td>
                <td>{f.officer}</td>
                <td><span className="badge badge--success">{f.outcome}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

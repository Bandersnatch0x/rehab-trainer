import { Link, useParams } from "react-router";
import { useAuth } from "../auth/AuthProvider";
import { PagePlaceholder } from "../components/PagePlaceholder";

export default function PatientDetailPage() {
  const { patientId } = useParams<{ patientId: string }>();
  const { can } = useAuth();
  const isTherapist = can("rehab_therapist");
  const encodedId = encodeURIComponent(patientId ?? "");

  return (
    <PagePlaceholder
      title="患者详情"
      description="待接入 GET /patients/{patientId}：档案、分期状态、评估与训练记录。"
    >
      <p className="hint">患者 ID：{patientId ?? "—"}</p>
      {isTherapist ? (
        <ul className="action-list">
          <li>
            <Link to={`/patients/${encodedId}/stage-confirmation`}>分期确认</Link>
          </li>
          <li>
            <Link to={`/patients/${encodedId}/prescription`}>处方编辑</Link>
          </li>
        </ul>
      ) : (
        <p className="hint">分期确认与处方开具仅由康复治疗师执行。</p>
      )}
    </PagePlaceholder>
  );
}

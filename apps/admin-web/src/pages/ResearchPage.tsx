import { useEffect, useState } from "react";
import { getResearchDataQuality, getResearchOverview } from "../api/operations";
import type {
  ResearchBucket,
  ResearchDataQuality,
  ResearchOverview,
  ResearchVersionBucket,
} from "../api/types";
import "./research.css";

const HY_LABEL: Record<string, string> = {
  "1": "H-Y 1 期",
  "2": "H-Y 2 期",
  "3": "H-Y 3 期",
  "4": "H-Y 4 期",
  "5": "H-Y 5 期",
};

const CONFIRMATION_LABEL: Record<string, string> = {
  confirmed: "治疗师已确认",
  suspected: "疑似待确认",
};

const AGE_LABEL: Record<string, string> = {
  "under-60": "60 岁以下",
  "60-69": "60–69 岁",
  "70-79": "70–79 岁",
  "80-plus": "80 岁及以上",
  unknown: "年龄未知",
};

const RATIO_LABEL: Record<string, string> = {
  missing: "未上报",
  "lt-0.5": "低于 0.5",
  "0.5-0.8": "0.5–0.8",
  "gte-0.8": "0.8 及以上",
};

const VERSION_TITLE: Record<string, string> = {
  model: "模型版本",
  algorithm: "指标算法版本",
  threshold: "阈值配置版本",
};

/** 抑制单元格统一显示为「—」，不显示 0，避免把「无数据」读成「计数为零」。 */
function cellText(cell: { count?: number | null; suppressed: boolean }): string {
  return cell.suppressed || cell.count == null ? "—" : String(cell.count);
}

function rateText(cell: { value?: number | null; suppressed: boolean }): string {
  return cell.suppressed || cell.value == null
    ? "—"
    : `${(cell.value * 100).toFixed(1)}%`;
}

function BarList({
  rows,
  labels,
}: {
  rows: ResearchBucket[];
  labels: Record<string, string>;
}) {
  const visible = rows.filter(
    (row) => !row.suppressed && row.count != null && row.count > 0,
  );
  const max = visible.reduce((peak, row) => Math.max(peak, row.count ?? 0), 0);

  if (visible.length === 0) {
    return <p className="research-suppressed">各分段样本数均为 0 或不足 5，无可展示的计数。</p>;
  }

  return (
    <ul className="research-bars">
      {visible.map((row) => (
        <li key={row.key} className="research-bar-row">
          <span>{labels[row.key] ?? row.key}</span>
          <span className="research-bar-track">
            <span
              className="research-bar-fill"
              style={{ width: `${max ? ((row.count ?? 0) / max) * 100 : 0}%` }}
            />
          </span>
          <span className="research-bar-value">{row.count}</span>
        </li>
      ))}
    </ul>
  );
}

function VersionList({
  title,
  rows,
}: {
  title: string;
  rows: ResearchVersionBucket[];
}) {
  return (
    <div className="research-versions">
      <strong>{title}</strong>
      {rows.length === 0 ? (
        <span className="research-suppressed">无样本数达到 5 条的版本。</span>
      ) : (
        rows.map((row) => (
          <span key={row.key}>
            <code>{row.key}</code> · {row.count} 条
          </span>
        ))
      )}
    </div>
  );
}

export default function ResearchPage() {
  const [overview, setOverview] = useState<ResearchOverview | null>(null);
  const [quality, setQuality] = useState<ResearchDataQuality | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    async function load() {
      const [overviewResult, qualityResult] = await Promise.all([
        getResearchOverview(),
        getResearchDataQuality(),
      ]);
      if (cancelled) {
        return;
      }
      if (!overviewResult.ok) {
        setError(overviewResult.error.message);
        setLoading(false);
        return;
      }
      if (!qualityResult.ok) {
        setError(qualityResult.error.message);
        setLoading(false);
        return;
      }
      setOverview(overviewResult.data);
      setQuality(qualityResult.data);
      setLoading(false);
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="page research-page">
      <div>
        <h1>科研数据底座</h1>
        <p className="page-description">
          本机构聚合画像与训练数据质量。只显示聚合值，样本数小于 5 的单元格一律抑制。
        </p>
      </div>

      <div className="notice">
        <div>
          <strong>数据范围：</strong>
          仅统计本机构真实数据（<code>data_origin = real</code>），演示数据不计入；
          不提供行级患者数据下载，也不提供跨机构检索（D10）。
        </div>
      </div>

      {error ? (
        <p role="alert" className="notice notice--danger">
          科研看板加载失败：{error}
        </p>
      ) : null}

      {loading ? (
        <p className="research-meta">正在读取聚合数据…</p>
      ) : (
        <div className="research-grid">
          <section className="research-card" aria-label="机构聚合画像">
            <h2>机构聚合画像</h2>
            {overview ? (
              <>
                <div className="research-metric">
                  <span>真实患者数</span>
                  <strong>{cellText(overview.patientCount)}</strong>
                </div>
                <div className="research-metric">
                  <span>抑制阈值</span>
                  <strong>n &lt; {overview.minCellCount}</strong>
                </div>
                <h3>H-Y 分布</h3>
                <BarList rows={overview.hyStageDistribution} labels={HY_LABEL} />
                <h3>分期确认状态</h3>
                <BarList
                  rows={overview.stageConfirmationDistribution}
                  labels={CONFIRMATION_LABEL}
                />
                <h3>年龄分段</h3>
                <BarList rows={overview.ageDistribution} labels={AGE_LABEL} />
                <p className="research-meta">
                  生成时间 {overview.generatedAt.slice(0, 19).replace("T", " ")} ·
                  数据来源 {overview.dataOrigin}
                </p>
              </>
            ) : null}
          </section>

          <section className="research-card" aria-label="训练数据质量">
            <h2>训练数据质量</h2>
            {quality ? (
              <>
                <div className="research-metric">
                  <span>训练记录数</span>
                  <strong>{cellText(quality.sessionRecordCount)}</strong>
                </div>
                <div className="research-metric">
                  <span>存疑比例</span>
                  <strong>{rateText(quality.doubtfulRatio)}</strong>
                </div>
                <div className="research-metric">
                  <span>有效帧占比缺失率</span>
                  <strong>{rateText(quality.missingRatio)}</strong>
                </div>
                <h3>有效帧占比分布</h3>
                <BarList
                  rows={quality.validFrameRatioDistribution}
                  labels={RATIO_LABEL}
                />
                <h3>版本分布</h3>
                <VersionList
                  title={VERSION_TITLE.model}
                  rows={quality.modelVersionDistribution}
                />
                <VersionList
                  title={VERSION_TITLE.algorithm}
                  rows={quality.metricAlgorithmVersionDistribution}
                />
                <VersionList
                  title={VERSION_TITLE.threshold}
                  rows={quality.thresholdConfigVersionDistribution}
                />
                <p className="research-meta">{quality.note}</p>
              </>
            ) : null}
          </section>
        </div>
      )}
    </div>
  );
}

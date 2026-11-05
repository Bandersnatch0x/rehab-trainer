import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  listExercises,
  listMediaAssets,
  updateMediaAsset,
  uploadMediaAsset,
  type UploadProgress,
} from "../api/operations";
import type { Exercise, MediaAsset, MediaAssetStatus } from "../api/types";
import "./media.css";

const STAGE_LABEL: Record<number, string> = {
  1: "H-Y 1 期",
  2: "H-Y 2 期",
  3: "H-Y 3 期",
  4: "H-Y 4 期",
  5: "H-Y 5 期",
};

const STATUS_LABEL: Record<MediaAssetStatus, string> = {
  draft: "草稿",
  published: "已上架",
  retired: "已下架",
};

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function formatTime(value: string | null | undefined): string {
  if (!value) return "—";
  return value.slice(0, 19).replace("T", " ");
}

function stagesText(stages: number[]): string {
  if (stages.length === 0) return "未设置（不会下发）";
  return stages.map((stage) => STAGE_LABEL[stage] ?? stage).join("、");
}

/** 水印叠加预览：水印与署名都由客户端叠加，不烧录进视频文件。 */
function PreviewBox({
  asset,
  watermarkText,
  creditLine,
  className,
}: {
  asset?: MediaAsset;
  watermarkText: string;
  creditLine: string;
  className?: string;
}) {
  return (
    <div className={className ?? "media-preview"}>
      {asset ? (
        <video
          className="media-preview-video"
          src={asset.publicUrl}
          preload="metadata"
          muted
          playsInline
        />
      ) : null}
      {creditLine ? (
        <span className="media-credit">{creditLine}</span>
      ) : null}
      {watermarkText ? (
        <span className="media-watermark">{watermarkText}</span>
      ) : null}
    </div>
  );
}

export default function MediaAssetsPage() {
  const [assets, setAssets] = useState<MediaAsset[]>([]);
  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [statusFilter, setStatusFilter] = useState<MediaAssetStatus | "">("");
  const [exerciseFilter, setExerciseFilter] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [file, setFile] = useState<File | null>(null);
  const [formExercise, setFormExercise] = useState("");
  const [formStages, setFormStages] = useState<number[]>([1, 2, 3, 4]);
  const [watermarkText, setWatermarkText] = useState("");
  const [creditLine, setCreditLine] = useState("");
  const [note, setNote] = useState("");
  const [progress, setProgress] = useState<UploadProgress | null>(null);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [formNotice, setFormNotice] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editWatermark, setEditWatermark] = useState("");
  const [editCredit, setEditCredit] = useState("");
  const [editStages, setEditStages] = useState<number[]>([]);
  const [rowError, setRowError] = useState<string | null>(null);

  const loadAssets = useCallback(async () => {
    setLoading(true);
    setError(null);
    const result = await listMediaAssets({
      ...(statusFilter ? { status: statusFilter } : {}),
      ...(exerciseFilter ? { exerciseCode: exerciseFilter } : {}),
      limit: 100,
    });
    if (!result.ok) {
      setError(result.error.message);
      setLoading(false);
      return;
    }
    setAssets(result.data.items);
    setLoading(false);
  }, [statusFilter, exerciseFilter]);

  useEffect(() => {
    void loadAssets();
  }, [loadAssets]);

  useEffect(() => {
    let cancelled = false;
    void listExercises({ limit: 100 }).then((result) => {
      if (!cancelled && result.ok) {
        setExercises(result.data.items);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const exerciseLabel = useMemo(() => {
    const map = new Map<string, string>();
    for (const exercise of exercises) {
      map.set(exercise.code, `${exercise.code} · ${exercise.label}`);
    }
    return map;
  }, [exercises]);

  const toggleFormStage = (stage: number) => {
    setFormStages((current) =>
      current.includes(stage)
        ? current.filter((value) => value !== stage)
        : [...current, stage].sort((a, b) => a - b),
    );
  };

  const handleUpload = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!file) {
      setFormError("请选择要上传的 MP4 视频文件");
      return;
    }
    setBusy(true);
    setFormError(null);
    setFormNotice(null);
    setProgress({ loaded: 0, total: file.size });

    const result = await uploadMediaAsset(
      file,
      {
        exerciseCode: formExercise || undefined,
        applicableStages: formStages,
        watermarkText: watermarkText || undefined,
        creditLine: creditLine || undefined,
        note: note || undefined,
      },
      setProgress,
    );

    setBusy(false);
    setProgress(null);
    if (!result.ok) {
      setFormError(result.error.message);
      return;
    }
    setFormNotice(
      `已上传草稿资产 ${result.data.sha256.slice(0, 12)}…，确认无误后点「上架」才会下发给患者。`,
    );
    setFile(null);
    setNote("");
    if (fileInput.current) {
      fileInput.current.value = "";
    }
    await loadAssets();
  };

  const changeStatus = async (asset: MediaAsset, next: MediaAssetStatus) => {
    const prompt =
      next === "published"
        ? `确认上架「${exerciseLabel.get(asset.exerciseCode ?? "") ?? asset.exerciseCode ?? "通用素材"}」？上架后符合分期的患者会立即看到该视频。`
        : next === "retired"
          ? "确认下架？下架后患者端不再下发该视频，文件与记录保留。"
          : "确认退回草稿？患者端将不再下发该视频。";
    if (!window.confirm(prompt)) {
      return;
    }
    setRowError(null);
    const result = await updateMediaAsset(asset.id, { status: next });
    if (!result.ok) {
      setRowError(result.error.message);
      return;
    }
    await loadAssets();
  };

  const startEdit = (asset: MediaAsset) => {
    setEditingId(asset.id);
    setEditWatermark(asset.watermarkText ?? "");
    setEditCredit(asset.creditLine ?? "");
    setEditStages(asset.applicableStages);
    setRowError(null);
  };

  const saveEdit = async (asset: MediaAsset) => {
    setRowError(null);
    const result = await updateMediaAsset(asset.id, {
      watermarkText: editWatermark,
      creditLine: editCredit,
      applicableStages: editStages,
    });
    if (!result.ok) {
      setRowError(result.error.message);
      return;
    }
    setEditingId(null);
    await loadAssets();
  };

  return (
    <div className="page media-page">
      <div>
        <h1>训练视频管理</h1>
        <p className="page-description">
          上传训练视频、设置适用 H-Y 分期与版权标注；只有「已上架」且分期匹配的视频才会下发给患者。
          水印与署名由客户端叠加显示，不烧录进视频文件。
        </p>
      </div>

      <form className="media-upload" onSubmit={handleUpload}>
        <h2>上传视频</h2>
        <div className="media-upload-grid">
          <label className="media-field">
            <span>视频文件（仅 MP4）</span>
            <input
              ref={fileInput}
              type="file"
              accept="video/mp4"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
            {file ? (
              <small>
                {file.name} · {formatSize(file.size)}
              </small>
            ) : null}
          </label>

          <label className="media-field">
            <span>关联训练条目</span>
            <select
              value={formExercise}
              onChange={(event) => setFormExercise(event.target.value)}
            >
              <option value="">通用素材（不绑定条目）</option>
              {exercises.map((exercise) => (
                <option key={exercise.code} value={exercise.code}>
                  {exercise.code} · {exercise.label}
                </option>
              ))}
            </select>
          </label>

          <label className="media-field">
            <span>水印文字</span>
            <input
              type="text"
              value={watermarkText}
              maxLength={128}
              placeholder="例如：演示素材 · 禁止转播"
              onChange={(event) => setWatermarkText(event.target.value)}
            />
          </label>

          <label className="media-field">
            <span>来源署名</span>
            <input
              type="text"
              value={creditLine}
              maxLength={255}
              placeholder="例如：素材来源：示例机构提供"
              onChange={(event) => setCreditLine(event.target.value)}
            />
          </label>

          <label className="media-field media-field--wide">
            <span>备注（内部可见）</span>
            <input
              type="text"
              value={note}
              onChange={(event) => setNote(event.target.value)}
            />
          </label>

          <fieldset className="media-field media-field--wide">
            <legend>适用 H-Y 分期</legend>
            <div className="media-stage-options">
              {[1, 2, 3, 4, 5].map((stage) => (
                <label key={stage}>
                  <input
                    type="checkbox"
                    checked={formStages.includes(stage)}
                    onChange={() => toggleFormStage(stage)}
                  />
                  {STAGE_LABEL[stage]}
                </label>
              ))}
            </div>
            {formStages.length === 0 ? (
              <small className="media-warn">
                未选择分期时上架会被拒绝：空集合表示尚未裁决，不会下发给任何患者。
              </small>
            ) : null}
          </fieldset>

          <div className="media-field media-field--wide">
            <span>叠加预览</span>
            <PreviewBox
              watermarkText={watermarkText}
              creditLine={creditLine}
              className="media-preview media-preview--form"
            />
          </div>
        </div>

        {formError ? (
          <p role="alert" className="notice notice--danger">
            {formError}
          </p>
        ) : null}
        {formNotice ? <p className="notice">{formNotice}</p> : null}

        {progress ? (
          <div className="media-progress" role="status">
            <div
              className="media-progress-bar"
              style={{
                width: `${progress.total ? Math.round((progress.loaded / progress.total) * 100) : 0}%`,
              }}
            />
            <span>
              上传中 {progress.total ? Math.round((progress.loaded / progress.total) * 100) : 0}%
            </span>
          </div>
        ) : null}

        <div className="media-actions">
          <button type="submit" className="btn-primary" disabled={busy}>
            {busy ? "上传中…" : "上传为草稿"}
          </button>
          <span className="media-hint">
            上传后为草稿，需在下方列表确认「上架」。
          </span>
        </div>
      </form>

      <section className="media-list">
        <div className="media-list-header">
          <h2>视频资产</h2>
          <div className="media-filters">
            <label>
              状态
              <select
                value={statusFilter}
                onChange={(event) =>
                  setStatusFilter(event.target.value as MediaAssetStatus | "")
                }
              >
                <option value="">全部</option>
                <option value="draft">草稿</option>
                <option value="published">已上架</option>
                <option value="retired">已下架</option>
              </select>
            </label>
            <label>
              条目
              <select
                value={exerciseFilter}
                onChange={(event) => setExerciseFilter(event.target.value)}
              >
                <option value="">全部</option>
                {exercises.map((exercise) => (
                  <option key={exercise.code} value={exercise.code}>
                    {exercise.code}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </div>

        {error ? (
          <p role="alert" className="notice notice--danger">
            加载失败：{error}
          </p>
        ) : null}
        {rowError ? (
          <p role="alert" className="notice notice--danger">
            {rowError}
          </p>
        ) : null}

        {loading ? (
          <p className="media-hint">正在读取视频资产…</p>
        ) : assets.length === 0 ? (
          <p className="media-hint">还没有符合条件的视频资产。</p>
        ) : (
          <ul className="media-grid">
            {assets.map((asset) => (
              <li key={asset.id} className="media-card">
                <PreviewBox
                  asset={asset}
                  watermarkText={asset.watermarkText ?? ""}
                  creditLine={asset.creditLine ?? ""}
                />
                <div className="media-card-body">
                  <div className="media-card-head">
                    <strong>
                      {asset.exerciseCode
                        ? (exerciseLabel.get(asset.exerciseCode) ?? asset.exerciseCode)
                        : "通用素材"}
                    </strong>
                    <span className={`media-badge media-badge--${asset.status}`}>
                      {STATUS_LABEL[asset.status]}
                    </span>
                  </div>
                  <dl className="media-meta">
                    <div>
                      <dt>适用分期</dt>
                      <dd>{stagesText(asset.applicableStages)}</dd>
                    </div>
                    <div>
                      <dt>大小</dt>
                      <dd>{formatSize(asset.sizeBytes)}</dd>
                    </div>
                    <div>
                      <dt>摘要</dt>
                      <dd>
                        <code>{asset.sha256.slice(0, 16)}…</code>
                      </dd>
                    </div>
                    <div>
                      <dt>上架时间</dt>
                      <dd>{formatTime(asset.publishedAt)}</dd>
                    </div>
                    <div>
                      <dt>下架时间</dt>
                      <dd>{formatTime(asset.retiredAt)}</dd>
                    </div>
                  </dl>

                  {editingId === asset.id ? (
                    <div className="media-edit">
                      <label>
                        水印文字
                        <input
                          type="text"
                          value={editWatermark}
                          maxLength={128}
                          onChange={(event) => setEditWatermark(event.target.value)}
                        />
                      </label>
                      <label>
                        来源署名
                        <input
                          type="text"
                          value={editCredit}
                          maxLength={255}
                          onChange={(event) => setEditCredit(event.target.value)}
                        />
                      </label>
                      <div className="media-stage-options">
                        {[1, 2, 3, 4, 5].map((stage) => (
                          <label key={stage}>
                            <input
                              type="checkbox"
                              checked={editStages.includes(stage)}
                              onChange={() =>
                                setEditStages((current) =>
                                  current.includes(stage)
                                    ? current.filter((value) => value !== stage)
                                    : [...current, stage].sort((a, b) => a - b),
                                )
                              }
                            />
                            {STAGE_LABEL[stage]}
                          </label>
                        ))}
                      </div>
                      <div className="media-actions">
                        <button
                          type="button"
                          className="btn-primary"
                          onClick={() => void saveEdit(asset)}
                        >
                          保存
                        </button>
                        <button
                          type="button"
                          className="btn-secondary"
                          onClick={() => setEditingId(null)}
                        >
                          取消
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div className="media-actions">
                      <button
                        type="button"
                        className="btn-secondary"
                        onClick={() => startEdit(asset)}
                      >
                        编辑标注
                      </button>
                      {asset.status === "published" ? (
                        <button
                          type="button"
                          className="btn-secondary"
                          onClick={() => void changeStatus(asset, "retired")}
                        >
                          下架
                        </button>
                      ) : (
                        <button
                          type="button"
                          className="btn-primary"
                          onClick={() => void changeStatus(asset, "published")}
                        >
                          上架
                        </button>
                      )}
                    </div>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

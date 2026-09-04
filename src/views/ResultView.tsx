import { useEffect, useRef, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import { api, events, type PipelineProgress } from "../api";
import type {
  Meeting,
  TranscriptSegment,
  MeetingSummary,
  StoredActionItem,
} from "../types";

function ts(secs: number): string {
  const s = Math.floor(secs % 60);
  const m = Math.floor((secs / 60) % 60);
  const h = Math.floor(secs / 3600);
  const pad = (n: number) => String(n).padStart(2, "0");
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}

const STAGE_TEXT: Record<string, string> = {
  finalizing: "Finalizando gravação…",
  transcribing: "Transcrevendo…",
  diarizing: "Separando vozes…",
  identifying: "Identificando participantes…",
  summarizing: "Gerando a ata…",
  completed: "Concluída",
  failed: "Falhou",
  cancelled: "Cancelada",
};

const PROCESSING = ["finalizing", "transcribing", "diarizing", "identifying", "summarizing"];

// Cores estáveis por cluster de voz, enquanto não há perfil identificado.
const CLUSTER_COLORS = ["#4c8dff", "#3fb950", "#d29922", "#f85149", "#a371f7", "#79c0ff"];
function clusterColor(c: number | null): string {
  return c == null ? "var(--text-dim)" : CLUSTER_COLORS[c % CLUSTER_COLORS.length];
}

export function ResultView({ meetingId, variant = "foco" }: { meetingId: string; variant?: "foco" | "painel" }) {
  const [meeting, setMeeting] = useState<Meeting | null>(null);
  const [segments, setSegments] = useState<TranscriptSegment[]>([]);
  const [summary, setSummary] = useState<MeetingSummary | null>(null);
  const [actions, setActions] = useState<StoredActionItem[]>([]);
  const [progress, setProgress] = useState<PipelineProgress | null>(null);
  const [tab, setTab] = useState<"transcript" | "summary" | "tasks">("summary");
  const pollRef = useRef<number | null>(null);

  async function refresh() {
    try {
      const m = await api.meetings.get(meetingId);
      setMeeting(m);
      setSegments(await api.meetings.segments(meetingId));
      setSummary(await api.meetings.summary(meetingId));
      setActions(await api.meetings.actions(meetingId));
      if (!PROCESSING.includes(m.stage) && pollRef.current) {
        clearInterval(pollRef.current);
        pollRef.current = null;
      }
    } catch {
      /* ignore */
    }
  }

  useEffect(() => {
    refresh();
    pollRef.current = window.setInterval(refresh, 1500);
    const un = listen<PipelineProgress>(events.pipelineProgress, (ev) => {
      if (ev.payload.meeting_id === meetingId) setProgress(ev.payload);
    });
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
      un.then((f) => f());
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [meetingId]);

  if (!meeting) return <p className="muted">Carregando…</p>;

  const processing = PROCESSING.includes(meeting.stage);

  async function retry() {
    await api.meetings.process(meetingId);
    if (!pollRef.current) pollRef.current = window.setInterval(refresh, 1500);
  }

  return (
    <section className={`result-page result-${variant}`}>
      <div className="result-heading">
        <div><div className="eyebrow">ata local</div><h1>{meeting.title}</h1>
        <p className="muted mono">
        {new Date(meeting.started_at).toLocaleString("pt-BR")} ·{" "}
        {Math.round(meeting.duration_secs / 60)} min
        </p></div>
        <div className="result-actions"><button className="secondary-button">Exportar .md</button><button className="secondary-button">Copiar ata</button></div>
      </div>

      {processing && (
        <div className="card">
          <div className="row" style={{ border: 0 }}>
            <span>{STAGE_TEXT[meeting.stage] ?? meeting.stage}</span>
            <span className="badge warn">processando</span>
          </div>
          {progress && progress.stage === meeting.stage && (
            <>
              <div className="progress">
                <div style={{ width: `${Math.round(progress.progress * 100)}%` }} />
              </div>
              <div className="muted mono" style={{ marginTop: 4 }}>
                {progress.message} · {Math.round(progress.progress * 100)}%
              </div>
            </>
          )}
        </div>
      )}

      {meeting.stage === "failed" && (
        <div className="card">
          <span className="badge fail">falhou</span>
          <p className="mono">{meeting.error}</p>
          <button className="primary" onClick={retry}>
            Tentar novamente
          </button>
        </div>
      )}

      <div className="result-tabs" role="tablist" aria-label="Conteúdo da reunião">
        {(["transcript", "summary", "tasks"] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={tab === t ? "result-tab active" : "result-tab"}
            role="tab"
            aria-selected={tab === t}
          >
            {t === "transcript" ? "Transcrição" : t === "summary" ? "Ata" : "Tarefas"}
          </button>
        ))}
      </div>

      {tab === "transcript" && (
        <div className="card">
          {segments.length === 0 && (
            <p className="muted">
              {processing
                ? "A transcrição aparecerá aqui quando ficar pronta."
                : "Sem segmentos."}
            </p>
          )}
          {segments.map((s) => (
            <div
              key={s.id}
              style={{
                display: "grid",
                gridTemplateColumns: "64px 1fr",
                gap: 12,
                padding: "6px 0",
                borderTop: "1px solid var(--border)",
              }}
            >
              <span className="muted mono" style={{ fontSize: 12 }}>
                {ts(s.start_secs)}
              </span>
              <span>
                {s.speaker_id ? (
                  <b style={{ color: clusterColor(s.cluster) }}>{s.speaker_id}: </b>
                ) : s.cluster != null ? (
                  <b style={{ color: clusterColor(s.cluster) }}>
                    Voz {s.cluster + 1}:{" "}
                  </b>
                ) : null}
                {s.text}
              </span>
            </div>
          ))}
        </div>
      )}

      {tab === "summary" && (
        <>
          {!summary && (
            <div className="card">
              <p className="muted">
                {processing
                  ? "A ata aparecerá aqui quando ficar pronta."
                  : "Sem ata gerada. Use 'Tentar novamente' para reprocessar."}
              </p>
            </div>
          )}
          {summary && (
            <>
              <div className="card">
                <h2>Resumo executivo</h2>
                <p>{summary.executive_summary}</p>
              </div>
              {summary.topics.length > 0 && (
                <div className="card">
                  <h2>Temas discutidos</h2>
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {summary.topics.map((t, i) => (
                      <li key={i}>{t}</li>
                    ))}
                  </ul>
                </div>
              )}
              {summary.decisions.length > 0 && (
                <div className="card">
                  <h2>Decisões</h2>
                  {summary.decisions.map((d, i) => (
                    <div className="row" key={i}>
                      <span>{d.text}</span>
                      {d.timestamp && (
                        <span className="muted mono">{d.timestamp}</span>
                      )}
                    </div>
                  ))}
                </div>
              )}
              {summary.pending.length > 0 && (
                <div className="card">
                  <h2>Pendências</h2>
                  {summary.pending.map((p, i) => (
                    <div className="row" key={i}>
                      <span>{p.text}</span>
                    </div>
                  ))}
                </div>
              )}
              {summary.divergences.length > 0 && (
                <div className="card">
                  <h2>Divergências</h2>
                  {summary.divergences.map((p, i) => (
                    <div className="row" key={i}>
                      <span>{p.text}</span>
                    </div>
                  ))}
                </div>
              )}
              {summary.next_steps.length > 0 && (
                <div className="card">
                  <h2>Próximos passos</h2>
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {summary.next_steps.map((t, i) => (
                      <li key={i}>{t}</li>
                    ))}
                  </ul>
                </div>
              )}
            </>
          )}
        </>
      )}
      {tab === "tasks" && (
        <div className="card">
          {actions.length === 0 && (
            <p className="muted">
              {processing ? "Extraindo tarefas…" : "Nenhuma tarefa registrada."}
            </p>
          )}
          {actions.map((a) => (
            <div className="row" key={a.id}>
              <div>
                <div>{a.description}</div>
                <div className="muted mono">
                  Responsável: {a.assignee ?? "Não informado"} · Prazo:{" "}
                  {a.due ?? "Não informado"}
                </div>
              </div>
              <span className="badge warn">{a.status}</span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

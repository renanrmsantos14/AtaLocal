import { useEffect, useState, type MouseEvent } from "react";
import { api } from "../api";
import type { Meeting } from "../types";

const STAGE_LABEL: Record<string, { text: string; cls: string }> = {
  recording: { text: "gravando", cls: "warn" },
  finalizing: { text: "finalizando", cls: "warn" },
  transcribing: { text: "transcrevendo", cls: "warn" },
  diarizing: { text: "separando vozes", cls: "warn" },
  identifying: { text: "identificando", cls: "warn" },
  summarizing: { text: "resumindo", cls: "warn" },
  completed: { text: "concluída", cls: "ok" },
  failed: { text: "falhou", cls: "fail" },
  cancelled: { text: "cancelada", cls: "fail" },
};

function fmtDur(secs: number) {
  const m = Math.round(secs / 60);
  return m < 1 ? `${Math.round(secs)}s` : `${m} min`;
}

export function MeetingsView({
  refreshKey,
  onOpen,
  variant = "foco",
}: {
  refreshKey: number;
  onOpen: (id: string) => void;
  variant?: "foco" | "painel";
}) {
  const [meetings, setMeetings] = useState<Meeting[]>([]);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    try {
      setMeetings(await api.meetings.list());
    } catch (e) {
      setError(String(e));
    }
  }

  useEffect(() => {
    load();
  }, [refreshKey]);

  async function del(e: MouseEvent, id: string) {
    e.stopPropagation();
    if (!confirm("Excluir esta reunião e seus áudios?")) return;
    await api.meetings.delete(id);
    load();
  }

  return (
    <section className={`meetings-page meetings-${variant}`}>
      <div className="page-heading page-heading-split">
        <div><div className="eyebrow">histórico local</div><h1>Reuniões</h1><p className="page-lede">Tudo que foi gravado neste computador.</p></div>
        <button className="secondary-button" onClick={load}>Atualizar</button>
      </div>
      {error && (
        <div className="error-box">
          <span className="badge fail">erro</span>
          <p className="mono">{error}</p>
        </div>
      )}
      {meetings.length === 0 && (
        <div className="empty-state"><span className="empty-orb" /><p>Nenhuma reunião ainda.</p><span>Grave sua primeira reunião para vê-la aqui.</span></div>
      )}
      <div className="meeting-list">
      {meetings.map((m) => {
        const st = STAGE_LABEL[m.stage] ?? { text: m.stage, cls: "warn" };
        return (
          <div
            className="meeting-item"
            key={m.id}
            onClick={() => onOpen(m.id)}
            style={{ cursor: "pointer" }}
          >
            <div className="meeting-main">
              <div>
                <div>{m.title}</div>
                <div className="muted mono">
                  {new Date(m.started_at).toLocaleString("pt-BR")} ·{" "}
                  {fmtDur(m.duration_secs)}
                </div>
                {m.error && (
                  <div className="mono" style={{ color: "var(--fail)", marginTop: 4 }}>
                    {m.error}
                  </div>
                )}
              </div>
              <div className="meeting-status">
                <span className={`badge ${st.cls}`}>{st.text}</span>
                <div className="meeting-arrow">→</div>
                <div>
                  <button
                    onClick={(e) => del(e, m.id)}
                    style={{
                      background: "transparent",
                      border: 0,
                      color: "var(--text-dim)",
                      cursor: "pointer",
                      fontSize: 12,
                      marginTop: 8,
                    }}
                  >
                    excluir
                  </button>
                </div>
              </div>
            </div>
          </div>
        );
      })}
      </div>
    </section>
  );
}

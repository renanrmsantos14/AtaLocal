import { useEffect, useState } from "react";
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

export function MeetingsView({ refreshKey }: { refreshKey: number }) {
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

  async function del(id: string) {
    if (!confirm("Excluir esta reunião e seus áudios?")) return;
    await api.meetings.delete(id);
    load();
  }

  return (
    <>
      <h1>Reuniões</h1>
      {error && (
        <div className="card">
          <span className="badge fail">erro</span>
          <p className="mono">{error}</p>
        </div>
      )}
      {meetings.length === 0 && (
        <p className="muted">Nenhuma reunião ainda.</p>
      )}
      {meetings.map((m) => {
        const st = STAGE_LABEL[m.stage] ?? { text: m.stage, cls: "warn" };
        return (
          <div className="card" key={m.id}>
            <div className="row" style={{ border: 0 }}>
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
              <div style={{ textAlign: "right" }}>
                <span className={`badge ${st.cls}`}>{st.text}</span>
                <div>
                  <button
                    onClick={() => del(m.id)}
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
    </>
  );
}

import { useEffect, useRef, useState } from "react";
import { api } from "../api";
import type { RecordingState, SystemDiagnostics, Meeting } from "../types";

function fmtDuration(secs: number): string {
  const s = Math.floor(secs % 60);
  const m = Math.floor((secs / 60) % 60);
  const h = Math.floor(secs / 3600);
  const pad = (n: number) => String(n).padStart(2, "0");
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}

const SIGNAL_LABEL: Record<RecordingState["signal"], { text: string; cls: string }> = {
  ok: { text: "sinal ok", cls: "ok" },
  baixo: { text: "áudio muito baixo", cls: "warn" },
  saturado: { text: "áudio saturando", cls: "fail" },
  sem_sinal: { text: "sem sinal", cls: "warn" },
};

export function RecordView({ onFinished }: { onFinished: (meetingId: string) => void }) {
  const [devices, setDevices] = useState<SystemDiagnostics["input_devices"]>([]);
  const [device, setDevice] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [rec, setRec] = useState<RecordingState | null>(null);
  const [meeting, setMeeting] = useState<Meeting | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const pollRef = useRef<number | null>(null);

  useEffect(() => {
    api.diagnostics.run().then((d) => {
      setDevices(d.input_devices);
      const def = d.input_devices.find((x) => x.is_default);
      setDevice(def?.name ?? d.input_devices[0]?.name ?? null);
    });
    api.recording.state().then(setRec).catch(() => {});
  }, []);

  function startPolling() {
    if (pollRef.current) return;
    pollRef.current = window.setInterval(async () => {
      try {
        const s = await api.recording.state();
        setRec(s);
        if (!s.recording && !s.meeting_id) stopPolling();
      } catch {
        /* ignore */
      }
    }, 200);
  }
  function stopPolling() {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }
  useEffect(() => stopPolling, []);

  async function start() {
    setError(null);
    setBusy(true);
    try {
      const m = await api.recording.start(title, device);
      setMeeting(m);
      startPolling();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }

  async function stop() {
    setBusy(true);
    setError(null);
    try {
      const id = await api.recording.stop();
      stopPolling();
      setRec(null);
      setMeeting(null);
      onFinished(id);
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }

  async function cancel() {
    setBusy(true);
    try {
      await api.recording.cancel();
      stopPolling();
      setRec(null);
      setMeeting(null);
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }

  const isRecording = !!meeting && !!rec?.recording;
  const level = rec?.level ?? 0;
  const peak = rec?.peak ?? 0;

  return (
    <>
      <h1>{isRecording ? "Gravando reunião" : "Nova reunião"}</h1>

      {error && (
        <div className="card">
          <span className="badge fail">erro</span>
          <p className="mono">{error}</p>
        </div>
      )}

      {!isRecording && (
        <div className="card">
          <h2>Antes de começar</h2>
          <label className="row" style={{ border: 0 }}>
            <span>Título (opcional)</span>
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Reunião de …"
              style={{
                background: "var(--panel-2)",
                border: "1px solid var(--border)",
                color: "var(--text)",
                borderRadius: 6,
                padding: "6px 10px",
                width: 280,
              }}
            />
          </label>
          <label className="row">
            <span>Microfone</span>
            <select
              value={device ?? ""}
              onChange={(e) => setDevice(e.target.value || null)}
              style={{
                background: "var(--panel-2)",
                border: "1px solid var(--border)",
                color: "var(--text)",
                borderRadius: 6,
                padding: "6px 10px",
                width: 280,
              }}
            >
              {devices.map((d) => (
                <option key={d.name} value={d.name}>
                  {d.name}
                  {d.is_default ? " (padrão)" : ""}
                </option>
              ))}
            </select>
          </label>
          <div style={{ marginTop: 16 }}>
            <button className="primary" onClick={start} disabled={busy || !device}>
              Iniciar reunião
            </button>
          </div>
        </div>
      )}

      {isRecording && (
        <div className="card">
          <div className="row" style={{ border: 0 }}>
            <div>
              <div style={{ fontSize: 32, fontVariantNumeric: "tabular-nums" }}>
                {fmtDuration(rec?.duration_secs ?? 0)}
              </div>
              <div className="muted">{meeting?.title}</div>
            </div>
            <span className="badge fail" style={{ alignSelf: "start" }}>
              ● REC
            </span>
          </div>

          <div style={{ marginTop: 20 }}>
            <div className="muted mono" style={{ marginBottom: 4 }}>
              nível do microfone
            </div>
            <div
              className="progress"
              style={{ height: 14, background: "var(--panel-2)" }}
            >
              <div
                style={{
                  width: `${Math.min(100, level * 140)}%`,
                  background:
                    peak >= 0.98 ? "var(--fail)" : "var(--ok)",
                  transition: "width .1s",
                }}
              />
            </div>
            {rec && (
              <div
                className={`badge ${SIGNAL_LABEL[rec.signal].cls}`}
                style={{ marginTop: 8, display: "inline-block" }}
              >
                {SIGNAL_LABEL[rec.signal].text}
              </div>
            )}
          </div>

          <div style={{ marginTop: 24, display: "flex", gap: 10 }}>
            <button className="primary" onClick={stop} disabled={busy}>
              Encerrar e processar
            </button>
            <button
              onClick={cancel}
              disabled={busy}
              style={{
                background: "transparent",
                border: "1px solid var(--border)",
                color: "var(--text-dim)",
                borderRadius: 6,
                padding: "8px 16px",
                cursor: "pointer",
              }}
            >
              Descartar
            </button>
          </div>
        </div>
      )}
    </>
  );
}

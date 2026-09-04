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

const WAVE = [14, 28, 20, 42, 31, 54, 24, 38, 17, 46, 28, 62, 34, 23, 48, 30, 68, 39, 22, 51, 30, 45, 19, 56, 36, 25, 49, 32, 64, 27, 43, 18, 37, 26];

function Waveform({ active = false }: { active?: boolean }) {
  return (
    <div className={`waveform ${active ? "waveform-active" : ""}`} aria-hidden="true">
      {WAVE.map((height, index) => <span key={index} style={{ height: `${height}%`, animationDelay: `${index * 18}ms` }} />)}
    </div>
  );
}

export function RecordView({ onFinished, variant = "foco" }: { onFinished: (meetingId: string) => void; variant?: "foco" | "painel" }) {
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

  const signal = rec ? SIGNAL_LABEL[rec.signal] : { text: "pronto para testar", cls: "ok" };

  if (isRecording) {
    return (
      <section className={`recording-stage ${variant === "painel" ? "recording-panel" : ""}`}>
        <div className="recording-status"><span className="recording-dot" /> gravando</div>
        <div className="recording-time">{fmtDuration(rec?.duration_secs ?? 0)}</div>
        <div className="recording-title">{meeting?.title}</div>
        <Waveform active />
        <div className="signal-line"><span className={`signal-dot ${signal.cls}`} />{signal.text}<span className="mono">{device}</span></div>
        <div className="recording-actions">
          <button className="primary" onClick={stop} disabled={busy}>Encerrar e gerar a ata</button>
          <button className="secondary-button" onClick={cancel} disabled={busy}>Descartar</button>
        </div>
      </section>
    );
  }

  return (
    <section className={`record-page ${variant === "foco" ? "record-page-focus" : ""}`}>
      {variant === "foco" ? (
        <div className="record-focus-content">
          <div className="eyebrow">nova reunião</div>
          <h1>Comece uma conversa.</h1>
          <p className="page-lede">Grave, transcreva e gere uma ata sem sair deste computador.</p>
          <input className="focus-title-input" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Reunião de …" aria-label="Título da reunião" />
          <div className="focus-mic-row">
            <select value={device ?? ""} onChange={(e) => setDevice(e.target.value || null)} aria-label="Microfone">
              {devices.length === 0 && <option value="">Nenhum microfone detectado</option>}
              {devices.map((d) => <option key={d.name} value={d.name}>{d.name}{d.is_default ? " — padrão" : ""}</option>)}
            </select>
            <span className={`badge ${signal.cls}`}>{signal.text}</span>
          </div>
          <button className="record-button" onClick={start} disabled={busy || !device} aria-label="Iniciar gravação">GRAVAR</button>
          <p className="privacy-note">O áudio, a transcrição e as vozes ficam apenas neste computador.</p>
        </div>
      ) : (
        <>
          <div className="page-heading">
            <div><div className="eyebrow">gravação local</div><h1>Nova reunião</h1><p className="page-lede">Confirme o microfone antes de começar.</p></div>
          </div>
          <div className="record-grid">
            <div className="card setup-card">
              <label className="form-row"><span>Título</span><input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Reunião de …" /></label>
              <label className="form-row"><span>Microfone</span><select value={device ?? ""} onChange={(e) => setDevice(e.target.value || null)}>{devices.length === 0 && <option value="">Nenhum microfone detectado</option>}{devices.map((d) => <option key={d.name} value={d.name}>{d.name}{d.is_default ? " — padrão" : ""}</option>)}</select></label>
              <div className="form-row"><span>Privacidade</span><span className="muted">100% neste computador</span></div>
            </div>
            <div className="card mic-test-card"><div className="eyebrow">teste de microfone</div><Waveform /><div className="signal-line"><span className="signal-dot ok" />{signal.text}</div><button className="primary" onClick={start} disabled={busy || !device}>Iniciar reunião</button></div>
          </div>
        </>
      )}
      {error && <div className="error-box"><span className="badge fail">erro</span><p className="mono">{error}</p></div>}
    </section>
  );
}

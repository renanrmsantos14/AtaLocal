import { useEffect, useState } from "react";
import { check } from "@tauri-apps/plugin-updater";
import { relaunch } from "@tauri-apps/plugin-process";
import { getVersion } from "@tauri-apps/api/app";
import { api } from "../api";
import type { AppSettings, WhisperOption, ModelInfo } from "../types";

type UpdateState =
  | { kind: "idle" }
  | { kind: "checking" }
  | { kind: "current" }
  | { kind: "available"; version: string; notes: string }
  | { kind: "downloading"; pct: number }
  | { kind: "ready" }
  | { kind: "error"; message: string };

export function SettingsView() {
  const [settings, setSettings] = useState<AppSettings | null>(null);
  const [version, setVersion] = useState("");
  const [upd, setUpd] = useState<UpdateState>({ kind: "idle" });
  const [whisper, setWhisper] = useState<WhisperOption[]>([]);
  const [models, setModels] = useState<ModelInfo[]>([]);

  async function loadModels() {
    try {
      setWhisper(await api.models.whisperOptions());
      setModels(await api.models.list());
    } catch {
      /* ignore */
    }
  }

  useEffect(() => {
    api.settings.get().then(setSettings).catch(() => {});
    getVersion().then(setVersion).catch(() => {});
    loadModels();
  }, []);

  const downloaded = (id: string) =>
    models.find((m) => m.id === id)?.status === "ready";

  async function downloadModel(id: string) {
    setModels((ms) =>
      ms.map((m) => (m.id === id ? { ...m, status: "downloading" } : m)),
    );
    try {
      await api.models.download(id);
    } finally {
      loadModels();
    }
  }

  async function save(patch: Partial<AppSettings>) {
    const next = await api.settings.update(patch);
    setSettings(next);
  }

  async function checkUpdate() {
    setUpd({ kind: "checking" });
    try {
      const update = await check();
      if (!update) {
        setUpd({ kind: "current" });
        return;
      }
      setUpd({
        kind: "available",
        version: update.version,
        notes: update.body ?? "",
      });

      // Guarda o handle para o download sob confirmação.
      (window as any).__pendingUpdate = update;
    } catch (e) {
      setUpd({ kind: "error", message: String(e) });
    }
  }

  async function installUpdate() {
    const update = (window as any).__pendingUpdate;
    if (!update) return;
    try {
      let total = 0;
      let got = 0;
      await update.downloadAndInstall((ev: any) => {
        if (ev.event === "Started") total = ev.data.contentLength ?? 0;
        if (ev.event === "Progress") {
          got += ev.data.chunkLength ?? 0;
          setUpd({
            kind: "downloading",
            pct: total ? Math.round((got / total) * 100) : 0,
          });
        }
        if (ev.event === "Finished") setUpd({ kind: "ready" });
      });
      setUpd({ kind: "ready" });
    } catch (e) {
      setUpd({ kind: "error", message: String(e) });
    }
  }

  if (!settings) return <p className="muted">Carregando…</p>;

  const effectiveModel =
    settings.whisper_model ||
    whisper.find((w) => w.recommended)?.id ||
    "";

  return (
    <>
      <h1>Configurações</h1>

      <div className="card">
        <h2>Modelo de transcrição</h2>
        <p className="muted" style={{ marginTop: 0 }}>
          Define a qualidade e a velocidade da transcrição. O modelo marcado como
          recomendado é o mais preciso que roda bem neste computador.
        </p>
        {whisper.map((w) => {
          const chosen = effectiveModel === w.id;
          const gb = (w.size_bytes / 1024 / 1024 / 1024).toFixed(1);
          const bars = "●".repeat(w.profile.quality) + "○".repeat(5 - w.profile.quality);
          return (
            <div
              key={w.id}
              onClick={() => downloaded(w.id) && save({ whisper_model: w.id })}
              style={{
                border: `1px solid ${chosen ? "var(--accent)" : "var(--border)"}`,
                borderRadius: 8,
                padding: 12,
                marginTop: 10,
                cursor: downloaded(w.id) ? "pointer" : "default",
                background: chosen ? "rgba(76,141,255,.08)" : "transparent",
              }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <b>{w.profile.label}</b>
                  {w.recommended && (
                    <span className="badge ok" style={{ marginLeft: 8 }}>
                      recomendado
                    </span>
                  )}
                  {chosen && (
                    <span className="badge" style={{ marginLeft: 8, background: "var(--accent)", color: "#fff" }}>
                      em uso
                    </span>
                  )}
                </div>
                <div style={{ textAlign: "right" }}>
                  {downloaded(w.id) ? (
                    !chosen && (
                      <button
                        className="primary"
                        onClick={(e) => {
                          e.stopPropagation();
                          save({ whisper_model: w.id });
                        }}
                      >
                        Usar este
                      </button>
                    )
                  ) : models.find((m) => m.id === w.id)?.status === "downloading" ? (
                    <span className="muted mono">baixando…</span>
                  ) : (
                    <button
                      className="primary"
                      onClick={(e) => {
                        e.stopPropagation();
                        downloadModel(w.id);
                      }}
                    >
                      Baixar ({gb} GB)
                    </button>
                  )}
                </div>
              </div>
              <div className="muted" style={{ fontSize: 13, marginTop: 6 }}>
                {w.profile.note}
              </div>
              <div className="muted mono" style={{ fontSize: 12, marginTop: 6 }}>
                qualidade {bars} · memória ~{(w.profile.ram_mb / 1024).toFixed(1)} GB ·
                ~{w.profile.secs_per_audio_min}s de processamento por minuto de reunião
              </div>
              {w.warning && (
                <div className="badge warn" style={{ marginTop: 6, display: "inline-block", whiteSpace: "normal" }}>
                  {w.warning}
                </div>
              )}
            </div>
          );
        })}
      </div>

      <div className="card">
        <h2>Reunião</h2>
        <label className="row" style={{ border: 0 }}>
          <span>Participantes por reunião</span>
          <input
            type="number"
            min={1}
            max={8}
            value={settings.participant_count}
            onChange={(e) =>
              save({ participant_count: Number(e.target.value) })
            }
            style={inputStyle}
          />
        </label>
        <label className="row">
          <span>Modo de baixo consumo</span>
          <input
            type="checkbox"
            checked={settings.low_power_mode}
            onChange={(e) => save({ low_power_mode: e.target.checked })}
          />
        </label>
        <label className="row">
          <span>Guardar áudio por (dias)</span>
          <input
            type="number"
            min={0}
            placeholder="sempre"
            value={settings.retention_days ?? ""}
            onChange={(e) =>
              save({
                retention_days: e.target.value ? Number(e.target.value) : null,
              })
            }
            style={inputStyle}
          />
        </label>
        <div className="row">
          <span>Pasta de dados</span>
          <span className="muted mono">{settings.data_dir}</span>
        </div>
      </div>

      <div className="card">
        <h2>Atualização</h2>
        <div className="row" style={{ border: 0 }}>
          <span>Versão instalada</span>
          <span className="mono">{version || "…"}</span>
        </div>

        <div style={{ marginTop: 12 }}>
          {(upd.kind === "idle" ||
            upd.kind === "current" ||
            upd.kind === "error") && (
            <button className="primary" onClick={checkUpdate}>
              Verificar atualização
            </button>
          )}
          {upd.kind === "checking" && (
            <span className="muted">Verificando…</span>
          )}
          {upd.kind === "current" && (
            <span className="badge ok" style={{ marginLeft: 10 }}>
              já está na versão mais recente
            </span>
          )}
          {upd.kind === "error" && (
            <div className="mono" style={{ color: "var(--fail)", marginTop: 8 }}>
              {upd.message}
            </div>
          )}
          {upd.kind === "available" && (
            <div>
              <p>
                Versão <b>{upd.version}</b> disponível.
              </p>
              {upd.notes && (
                <pre
                  className="mono"
                  style={{
                    whiteSpace: "pre-wrap",
                    background: "var(--panel-2)",
                    padding: 10,
                    borderRadius: 6,
                    fontSize: 12,
                  }}
                >
                  {upd.notes}
                </pre>
              )}
              <button className="primary" onClick={installUpdate}>
                Baixar e instalar
              </button>
              <p className="muted" style={{ fontSize: 12, marginTop: 8 }}>
                O Windows pode mostrar um aviso do SmartScreen (o instalador não é
                assinado). Clique em "Mais informações" → "Executar assim mesmo".
              </p>
            </div>
          )}
          {upd.kind === "downloading" && (
            <div>
              <div className="progress">
                <div style={{ width: `${upd.pct}%` }} />
              </div>
              <span className="muted mono">baixando… {upd.pct}%</span>
            </div>
          )}
          {upd.kind === "ready" && (
            <div>
              <span className="badge ok">atualização instalada</span>
              <button
                className="primary"
                style={{ marginLeft: 10 }}
                onClick={() => relaunch()}
              >
                Reiniciar o AtaLocal
              </button>
            </div>
          )}
        </div>
      </div>
    </>
  );
}

const inputStyle: React.CSSProperties = {
  background: "var(--panel-2)",
  border: "1px solid var(--border)",
  color: "var(--text)",
  borderRadius: 6,
  padding: "6px 10px",
  width: 120,
};

import { useEffect, useState } from "react";
import { check } from "@tauri-apps/plugin-updater";
import { relaunch } from "@tauri-apps/plugin-process";
import { getVersion } from "@tauri-apps/api/app";
import { api } from "../api";
import type { AppSettings } from "../types";

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

  useEffect(() => {
    api.settings.get().then(setSettings).catch(() => {});
    getVersion().then(setVersion).catch(() => {});
  }, []);

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

  return (
    <>
      <h1>Configurações</h1>

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

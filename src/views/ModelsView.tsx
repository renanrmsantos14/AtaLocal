import { useEffect, useState, useCallback } from "react";
import { listen } from "@tauri-apps/api/event";
import { api, events } from "../api";
import type { ModelInfo, DownloadProgress } from "../types";

function fmtBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  const u = ["KB", "MB", "GB"];
  let v = n / 1024;
  let i = 0;
  while (v >= 1024 && i < u.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v.toFixed(1)} ${u[i]}`;
}

const KIND_LABEL: Record<ModelInfo["kind"], string> = {
  whisper: "Transcrição",
  diarization: "Segmentação de vozes",
  embedding: "Impressão de voz",
  llm: "Resumo / ata",
  tool: "Programa auxiliar",
};

export function ModelsView() {
  const [models, setModels] = useState<ModelInfo[]>([]);
  const [progress, setProgress] = useState<Record<string, DownloadProgress>>({});
  const [verifying, setVerifying] = useState<Record<string, boolean>>({});
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setModels(await api.models.list());
    } catch (e) {
      setError(String(e));
    }
  }, []);

  useEffect(() => {
    refresh();
    const un = listen<DownloadProgress>(events.downloadProgress, (ev) => {
      setProgress((p) => ({ ...p, [ev.payload.model_id]: ev.payload }));
      if (ev.payload.status === "ready" || ev.payload.status === "failed") {
        refresh();
      }
    });
    return () => {
      un.then((f) => f());
    };
  }, [refresh]);

  async function download(id: string) {
    setError(null);
    try {
      await api.models.download(id);
    } catch (e) {
      setError(String(e));
    }
  }

  async function verify(id: string) {
    setError(null);
    setVerifying((current) => ({ ...current, [id]: true }));
    try {
      await api.models.verify(id);
      await refresh();
    } catch (e) {
      setError(String(e));
    } finally {
      setVerifying((current) => ({ ...current, [id]: false }));
    }
  }

  return (
    <>
      <h1>Modelos locais</h1>
      <p className="muted">
        Baixados uma única vez e verificados por checksum SHA-256. O download é
        retomável se for interrompido. Nada é enviado para fora do computador.
      </p>

      {error && (
        <div className="card">
          <span className="badge fail">erro</span>
          <p className="mono">{error}</p>
        </div>
      )}

      {models.map((m) => {
        const p = progress[m.id];
        const downloading = p?.status === "downloading" || p?.status === "verifying";
        const checking = verifying[m.id] === true;
        const done = m.downloaded_bytes || p?.downloaded_bytes || 0;
        const total = m.size_bytes || p?.total_bytes || 1;
        const pct = Math.min(100, (done / total) * 100);
        return (
          <div className="card" key={m.id}>
            <div className="row" style={{ border: 0 }}>
              <div>
                <div>
                  {KIND_LABEL[m.kind]} · <span className="mono">{m.id}</span>
                </div>
                <div className="muted mono">
                  {m.filename} · {fmtBytes(m.size_bytes)}
                </div>
              </div>
              <div style={{ textAlign: "right" }}>
                {downloading || checking ? (
                  <span className="badge warn">
                    {checking || p?.status === "verifying" ? "verificando" : "baixando"}
                  </span>
                ) : (
                  <>
                    {m.status === "ready" && <span className="badge ok">pronto</span>}
                    {m.status !== "ready" && (
                      <button className="primary" onClick={() => download(m.id)}>
                        Baixar
                      </button>
                    )}
                    <button
                      className="primary"
                      style={{ marginLeft: 8 }}
                      onClick={() => verify(m.id)}
                    >
                      Verificar
                    </button>
                  </>
                )}
              </div>
            </div>
            {(downloading || (done > 0 && m.status !== "ready")) && (
              <>
                <div className="progress">
                  <div style={{ width: `${pct}%` }} />
                </div>
                <div className="muted mono" style={{ marginTop: 4 }}>
                  {fmtBytes(done)} / {fmtBytes(total)}
                  {p?.speed ? ` · ${fmtBytes(p.speed)}/s` : ""}
                </div>
              </>
            )}
          </div>
        );
      })}
    </>
  );
}

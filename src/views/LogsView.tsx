import { useEffect, useState } from "react";
import { api } from "../api";

export function LogsView() {
  const [logs, setLogs] = useState("");
  const [info, setInfo] = useState<{ bytes: number; max_bytes: number } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function refresh() {
    setLoading(true);
    setError(null);
    try {
      const [content, details] = await Promise.all([api.logs.get(), api.logs.info()]);
      setLogs(content);
      setInfo(details);
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refresh();
  }, []);

  return (
    <>
      <h1>Logs de erro</h1>
      <p className="muted">
        Registros locais do app. Úteis para identificar falhas de áudio, modelos e
        processamento.
      </p>

      <button className="primary" onClick={refresh} disabled={loading}>
        {loading ? "Carregando…" : "Atualizar logs"}
      </button>
      {info && (
        <p className="muted mono" style={{ margin: "12px 0 0" }}>
          Espaço usado pelo log: {formatBytes(info.bytes)} de {formatBytes(info.max_bytes)}
        </p>
      )}

      {error && (
        <div className="card" style={{ marginTop: 16 }}>
          <span className="badge fail">erro</span>
          <p className="mono">{error}</p>
        </div>
      )}

      <div className="card log-panel" style={{ marginTop: 16 }}>
        {logs ? (
          <pre className="log-output">{logs}</pre>
        ) : (
          <p className="muted">Nenhum log registrado ainda.</p>
        )}
      </div>
    </>
  );
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

import { useEffect, useState } from "react";
import { api } from "../api";

export function LogsView() {
  const [logs, setLogs] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function refresh() {
    setLoading(true);
    setError(null);
    try {
      setLogs(await api.logs.get());
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

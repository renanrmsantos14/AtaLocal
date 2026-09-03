import { useEffect, useState } from "react";
import { api } from "../api";
import type { SystemDiagnostics } from "../types";

export function DiagnosticsView() {
  const [diag, setDiag] = useState<SystemDiagnostics | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function run() {
    setLoading(true);
    setError(null);
    try {
      setDiag(await api.diagnostics.run());
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    run();
  }, []);

  return (
    <>
      <h1>Diagnóstico do sistema</h1>
      <p className="muted">
        Verifica se este computador atende aos requisitos para gravar e processar
        reuniões localmente.
      </p>

      <button className="primary" onClick={run} disabled={loading}>
        {loading ? "Verificando…" : "Verificar novamente"}
      </button>

      {error && (
        <div className="card" style={{ marginTop: 16 }}>
          <span className="badge fail">erro</span>
          <p className="mono">{error}</p>
        </div>
      )}

      {diag && (
        <>
          <div className="card" style={{ marginTop: 16 }}>
            <h2>Verificações</h2>
            {diag.checks.map((c) => (
              <div className="row" key={c.id}>
                <div>
                  <div>{c.label}</div>
                  <div className="muted mono">{c.detail}</div>
                </div>
                <span className={`badge ${c.status}`}>{c.status}</span>
              </div>
            ))}
          </div>

          <div className="card">
            <h2>Hardware</h2>
            <div className="row">
              <span>Processador</span>
              <span className="mono">{diag.cpu_name}</span>
            </div>
            <div className="row">
              <span>Núcleos</span>
              <span className="mono">
                {diag.cpu_cores_physical} físicos / {diag.cpu_cores_logical} lógicos
              </span>
            </div>
            <div className="row">
              <span>Memória</span>
              <span className="mono">
                {diag.available_ram_gb.toFixed(1)} GB livres de{" "}
                {diag.total_ram_gb.toFixed(1)} GB
              </span>
            </div>
            <div className="row">
              <span>Espaço em disco (dados)</span>
              <span className="mono">{diag.data_dir_free_gb.toFixed(1)} GB livres</span>
            </div>
            <div className="row">
              <span>Sistema</span>
              <span className="mono">{diag.os_version}</span>
            </div>
          </div>

          <div className="card">
            <h2>Microfones detectados</h2>
            {diag.input_devices.length === 0 && (
              <p className="muted">Nenhum dispositivo de entrada encontrado.</p>
            )}
            {diag.input_devices.map((d) => (
              <div className="row" key={d.name}>
                <div>
                  <div>
                    {d.name} {d.is_default && <span className="badge ok">padrão</span>}
                  </div>
                  <div className="muted mono">
                    {d.default_sample_rate} Hz · {d.channels} canal(is)
                  </div>
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </>
  );
}

import { useState } from "react";
import { DiagnosticsView } from "./views/DiagnosticsView";
import { ModelsView } from "./views/ModelsView";

type Tab = "diagnostics" | "models" | "meetings";

export function App() {
  const [tab, setTab] = useState<Tab>("diagnostics");

  return (
    <div className="app">
      <nav className="sidebar">
        <h1 style={{ fontSize: 16, padding: "0 12px 12px" }}>AtaLocal</h1>
        <button
          className={tab === "diagnostics" ? "active" : ""}
          onClick={() => setTab("diagnostics")}
        >
          Diagnóstico
        </button>
        <button
          className={tab === "models" ? "active" : ""}
          onClick={() => setTab("models")}
        >
          Modelos
        </button>
        <button
          className={tab === "meetings" ? "active" : ""}
          onClick={() => setTab("meetings")}
          disabled
        >
          Reuniões
        </button>
      </nav>
      <main className="content">
        {tab === "diagnostics" && <DiagnosticsView />}
        {tab === "models" && <ModelsView />}
        {tab === "meetings" && <p className="muted">Em breve (Fase 2).</p>}
      </main>
    </div>
  );
}

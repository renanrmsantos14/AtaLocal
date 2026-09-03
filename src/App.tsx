import { useState } from "react";
import { DiagnosticsView } from "./views/DiagnosticsView";
import { ModelsView } from "./views/ModelsView";
import { RecordView } from "./views/RecordView";
import { MeetingsView } from "./views/MeetingsView";

type Tab = "record" | "meetings" | "models" | "diagnostics";

export function App() {
  const [tab, setTab] = useState<Tab>("record");
  const [meetingsRefresh, setMeetingsRefresh] = useState(0);

  const nav: [Tab, string][] = [
    ["record", "Gravar"],
    ["meetings", "Reuniões"],
    ["models", "Modelos"],
    ["diagnostics", "Diagnóstico"],
  ];

  return (
    <div className="app">
      <nav className="sidebar">
        <h1 style={{ fontSize: 16, padding: "0 12px 12px" }}>AtaLocal</h1>
        {nav.map(([id, label]) => (
          <button
            key={id}
            className={tab === id ? "active" : ""}
            onClick={() => setTab(id)}
          >
            {label}
          </button>
        ))}
      </nav>
      <main className="content">
        {tab === "record" && (
          <RecordView
            onFinished={() => {
              setMeetingsRefresh((n) => n + 1);
              setTab("meetings");
            }}
          />
        )}
        {tab === "meetings" && <MeetingsView refreshKey={meetingsRefresh} />}
        {tab === "models" && <ModelsView />}
        {tab === "diagnostics" && <DiagnosticsView />}
      </main>
    </div>
  );
}

import { useState } from "react";
import { DiagnosticsView } from "./views/DiagnosticsView";
import { ModelsView } from "./views/ModelsView";
import { RecordView } from "./views/RecordView";
import { MeetingsView } from "./views/MeetingsView";
import { ResultView } from "./views/ResultView";
import { SettingsView } from "./views/SettingsView";

type Tab = "record" | "meetings" | "models" | "diagnostics" | "settings";

export function App() {
  const [tab, setTab] = useState<Tab>("record");
  const [meetingsRefresh, setMeetingsRefresh] = useState(0);
  const [openMeeting, setOpenMeeting] = useState<string | null>(null);

  const nav: [Tab, string][] = [
    ["record", "Gravar"],
    ["meetings", "Reuniões"],
    ["models", "Modelos"],
    ["diagnostics", "Diagnóstico"],
    ["settings", "Configurações"],
  ];

  function goToMeeting(id: string) {
    setOpenMeeting(id);
    setTab("meetings");
  }

  return (
    <div className="app">
      <nav className="sidebar">
        <h1 style={{ fontSize: 16, padding: "0 12px 12px" }}>AtaLocal</h1>
        {nav.map(([id, label]) => (
          <button
            key={id}
            className={tab === id && !openMeeting ? "active" : ""}
            onClick={() => {
              setOpenMeeting(null);
              setTab(id);
            }}
          >
            {label}
          </button>
        ))}
      </nav>
      <main className="content">
        {openMeeting ? (
          <>
            <button
              onClick={() => setOpenMeeting(null)}
              style={{
                background: "transparent",
                border: 0,
                color: "var(--text-dim)",
                cursor: "pointer",
                marginBottom: 12,
              }}
            >
              ← voltar
            </button>
            <ResultView meetingId={openMeeting} />
          </>
        ) : (
          <>
            {tab === "record" && (
              <RecordView
                onFinished={(id) => {
                  setMeetingsRefresh((n) => n + 1);
                  goToMeeting(id);
                }}
              />
            )}
            {tab === "meetings" && (
              <MeetingsView refreshKey={meetingsRefresh} onOpen={goToMeeting} />
            )}
            {tab === "models" && <ModelsView />}
            {tab === "diagnostics" && <DiagnosticsView />}
            {tab === "settings" && <SettingsView />}
          </>
        )}
      </main>
    </div>
  );
}

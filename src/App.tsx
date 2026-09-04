import { useEffect, useState, type CSSProperties } from "react";
import { DiagnosticsView } from "./views/DiagnosticsView";
import { ModelsView } from "./views/ModelsView";
import { RecordView } from "./views/RecordView";
import { MeetingsView } from "./views/MeetingsView";
import { ResultView } from "./views/ResultView";
import { SettingsView } from "./views/SettingsView";
import { LogsView } from "./views/LogsView";

type Tab = "record" | "meetings" | "models" | "diagnostics" | "logs" | "settings";
type Variant = "foco" | "painel";

const NAV: Array<{ id: Tab; label: string; detail: string }> = [
  { id: "record", label: "Gravar", detail: "nova reunião" },
  { id: "meetings", label: "Reuniões", detail: "histórico local" },
  { id: "models", label: "Modelos", detail: "arquivos locais" },
  { id: "diagnostics", label: "Diagnóstico", detail: "estado do computador" },
  { id: "logs", label: "Logs", detail: "eventos do aplicativo" },
  { id: "settings", label: "Configurações", detail: "preferências" },
];

function SearchIcon() {
  return <svg viewBox="0 0 20 20" aria-hidden="true"><circle cx="8.5" cy="8.5" r="5.5" /><path d="m13 13 4 4" /></svg>;
}

function CloseIcon() {
  return <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m5 5 10 10M15 5 5 15" /></svg>;
}

export function App() {
  const [tab, setTab] = useState<Tab>("record");
  const [meetingsRefresh, setMeetingsRefresh] = useState(0);
  const [openMeeting, setOpenMeeting] = useState<string | null>(null);
  const [variant, setVariant] = useState<Variant>("foco");
  const [theme, setTheme] = useState<"dark" | "light">("dark");
  const [searchOpen, setSearchOpen] = useState(false);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setSearchOpen(true);
      }
      if (event.key === "Escape") setSearchOpen(false);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  function goToMeeting(id: string) {
    setOpenMeeting(id);
    setTab("meetings");
  }

  function goTo(tabId: Tab) {
    setOpenMeeting(null);
    setTab(tabId);
  }

  return (
    <div className={`app-shell theme-${theme} variant-${variant}`}>
      <aside className="sidebar" aria-label="Navegação principal">
        <div className="brand-lockup">
          <div className="brand-name">AtaLocal</div>
          <div className="brand-meta">100% local</div>
        </div>

        <button className="search-trigger" onClick={() => setSearchOpen(true)} aria-label="Abrir busca">
          <span>Buscar</span>
          <span className="key-hint">{navigator.platform?.includes("Mac") ? "⌘K" : "Ctrl K"}</span>
        </button>

        <nav className="nav-list">
          <span className="nav-marker" aria-hidden="true" style={{ "--nav-index": NAV.findIndex((item) => item.id === tab) } as CSSProperties & Record<"--nav-index", number>} />
          {NAV.map((item) => (
          <button
            key={item.id}
            className={`nav-item ${tab === item.id && !openMeeting ? "active" : ""}`}
            onClick={() => goTo(item.id)}
            title={item.detail}
          >
            <span className="nav-dot" aria-hidden="true" />
            <span>{item.label}</span>
          </button>
        ))}
        </nav>

        <div className="sidebar-footer">
          <div className="footer-label">variação</div>
          <div className="segmented-control" role="group" aria-label="Variação de layout">
            <button className={variant === "foco" ? "selected" : ""} onClick={() => setVariant("foco")} aria-pressed={variant === "foco"}>Foco</button>
            <button className={variant === "painel" ? "selected" : ""} onClick={() => setVariant("painel")} aria-pressed={variant === "painel"}>Painel</button>
          </div>
          <button className="footer-link" onClick={() => setSearchOpen(true)}>Primeira execução</button>
          <button className="theme-toggle" onClick={() => setTheme(theme === "dark" ? "light" : "dark")} aria-pressed={theme === "light"}>
            <span>{theme === "dark" ? "Tema escuro" : "Tema claro"}</span>
            <span className="toggle-track" aria-hidden="true"><span /></span>
          </button>
        </div>
      </aside>
      <main className="content">
        {openMeeting ? (
          <div className="route-wrap">
            <button className="back-link" onClick={() => setOpenMeeting(null)}><span aria-hidden="true">←</span> Reuniões</button>
            <ResultView meetingId={openMeeting} variant={variant} />
          </div>
        ) : (
          <div className="route-wrap">
            {tab === "record" && <RecordView onFinished={(id) => { setMeetingsRefresh((n) => n + 1); goToMeeting(id); }} variant={variant} />}
            {tab === "meetings" && <MeetingsView refreshKey={meetingsRefresh} onOpen={goToMeeting} variant={variant} />}
            {tab === "models" && <ModelsView />}
            {tab === "diagnostics" && <DiagnosticsView />}
            {tab === "logs" && <LogsView />}
            {tab === "settings" && <SettingsView />}
          </div>
        )}
      </main>

      {searchOpen && (
        <div className="search-overlay" role="presentation" onMouseDown={() => setSearchOpen(false)}>
          <section className="search-dialog" role="dialog" aria-modal="true" aria-labelledby="search-title" onMouseDown={(event) => event.stopPropagation()}>
            <div className="search-heading">
              <span className="live-dot" aria-hidden="true" />
              <label id="search-title" htmlFor="global-search">Buscar no AtaLocal</label>
              <button className="icon-button" onClick={() => setSearchOpen(false)} aria-label="Fechar busca"><CloseIcon /></button>
            </div>
            <input id="global-search" autoFocus placeholder="decisão, tarefa, pessoa ou fala" />
            <div className="search-empty">
              <SearchIcon />
              <p>Digite para buscar nas reuniões locais.</p>
              <span>Esc para fechar</span>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}

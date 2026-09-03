# Ambiente de desenvolvimento

## Toolchain instalado nesta máquina (2026-09-03)

| Ferramenta | Versão | Origem |
|---|---|---|
| Rust | 1.98.1 (rustup) | `winget install Rustlang.Rustup` |
| MSVC Build Tools | 14.44.35207 | `winget install Microsoft.VisualStudio.2022.BuildTools` (workload VCTools) |
| CMake | (Kitware) | `winget install Kitware.CMake` |
| LLVM / libclang | 22.1.8 | `winget install LLVM.LLVM` — `bindgen` do whisper-rs-sys |
| Node | 24.15.0 | já instalado |
| WebView2 Runtime | presente | Windows 11 |

`cargo`/`rustc` ficam em `%USERPROFILE%\.cargo\bin`. Se o `PATH` da sessão não
os incluir, prefixe: `$env:Path = "$env:USERPROFILE\.cargo\bin;$env:Path"`.

## Smart App Control

Estava em modo **enforced** e bloqueava a execução de todo build-script
compilado localmente (`os error 4551`) — inclusive o do próprio `tauri`.
Foi **desativado** pelo usuário em Segurança do Windows para permitir o build.

Consequência: reversível apenas reinstalando o Windows. Windows Defender,
SmartScreen, firewall e antivírus continuam ativos.

## TLS

`reqwest` usa `native-tls` (Schannel do Windows) em vez de `rustls`/`ring`.
Menos dependências e sem código C extra no build — adequado a um app Windows-only.

## Rodar

```powershell
$env:Path = "$env:USERPROFILE\.cargo\bin;$env:Path"
cd C:\Users\mendo\Desktop\AP\AtaLocal
npm run tauri dev      # janela nativa WebView2
```

Dados do app em runtime: `%APPDATA%\local\AtaLocal\data\`
(`atalocal.db`, `models/`, `recordings/`, `logs/`).

## Testes

```powershell
cd src-tauri
cargo test --test phase1_smoke
```

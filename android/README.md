# AtaLocal Mobile

Aplicativo Android independente do cliente Tauri, com alvo inicial `arm64-v8a`.

## Fundação entregue

- Kotlin + Jetpack Compose + Material 3.
- Tema inicial, tela de início e entrada para nova reunião.
- Máquina de estados do fluxo local, com testes unitários.
- Base de dependências preparada para Room/SQLite.
- Permissão de microfone declarada para a Fase 2.

## Build

Requer JDK 17, Android SDK 35 e Gradle/Android Studio. Na pasta `android`, execute:

```text
gradle test
gradle assembleDebug
```

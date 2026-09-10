# AtaLocal Mobile

Aplicativo Android independente do cliente Tauri, com alvo inicial `arm64-v8a`.

## Estado atual

- Kotlin + Jetpack Compose + Material 3.
- Interface Compose com início, gravação, processamento, transcrição, ata, modelos, configurações e diagnóstico.
- Room/SQLite, WorkManager, armazenamento privado e retomada por checkpoint.
- Gravação mono 16 kHz em segmentos WAV recuperáveis via Foreground Service.
- Whisper.cpp e Llama.cpp compilados por NDK/JNI para `arm64-v8a`.
- Download com retomada, validação de tamanho/SHA-256 e instalação atômica.
- Exportação Markdown/PDF e compartilhamento pelo menu nativo.
- Testes unitários para estados, áudio, modelos, parser factual, exportação e recuperação.

## Build

Requer JDK 17, Android SDK 35 e Gradle/Android Studio. Na pasta `android`, execute:

```text
gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Para instalar no aparelho conectado por ADB:

```text
gradlew.bat :app:installDebug
```

O release usa `keystore.properties` (não versionado) com `storeFile`, `storePassword`, `keyAlias` e `keyPassword`. Sem esse arquivo, o build local usa a assinatura debug somente para testes.

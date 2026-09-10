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

## Smoke test no aparelho

Com a depuração USB autorizada no aparelho:

```text
adb devices
gradlew.bat :app:installDebug
adb logcat -c
adb shell am start -n br.com.betinhos.atalocal/.MainActivity
```

No app:

1. Conceda microfone e notificações.
2. Em **Modelos**, instale um Whisper e o modelo LLM.
3. Crie uma reunião e grave uma frase curta.
4. Toque em finalizar. O último WAV deve ser fechado antes de a fila iniciar.
5. Confira `Transcrevendo áudio`, o segmento atual e depois a ata.
6. Feche e reabra o app durante o processamento; a fila deve continuar ou retomar.

Para investigar uma falha:

```text
adb logcat -d -v time | findstr /i "AtaLocalWhisper AtaLocalLlama WorkManager Exception"
```

Aceite físico: existe pelo menos um segmento de transcrição, o texto corresponde ao áudio, a ata não inventa nomes/prazos ausentes e Markdown/PDF/compartilhamento funcionam. Build local e CI não substituem este teste.

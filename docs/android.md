# Build Android

## O que está disponível

No Android, o app grava pelo microfone, transcreve localmente com Whisper e gera
a ata localmente com llama.cpp. A diarização e a identificação de vozes ficam
desativadas nesta etapa porque o backend atual do Sherpa é um executável Windows.

O APK do workflow é `arm64-v8a`, adequado ao Poco X5 Pro 5G, e é assinado como
build release para instalação direta. A chave privada fica somente nos secrets
do GitHub; ela não é commitada no repositório.

## Build local

Instale Android Studio com SDK Platform 35, Platform-Tools, Build-Tools 35.0.0,
NDK 27.2.12479018, JDK 17 e Rust via rustup. Depois:

```bash
npm install
npm run tauri android init -- --ci --skip-targets-install
npm run android:prepare
npm run tauri android build -- --apk --target aarch64 --split-per-abi
```

O APK fica em `src-tauri/gen/android/app/build/outputs/apk/`. Para instalar com
o uso normal, transfira o arquivo para o celular e abra-o para instalar.

Os dados e os modelos são gravados no armazenamento interno do app. Não é
necessária permissão de armazenamento externo; o primeiro uso do microfone pede
`RECORD_AUDIO`.

No Android, a interface usa o microfone padrão do aparelho. A captura usa a API
nativa `AudioRecord` do Android; o CPAL/Oboe fica restrito ao desktop. O sistema
pode não expor nomes de dispositivos selecionáveis, mas a gravação abre a rota
nativa padrão depois que a permissão é concedida.

O pedido de `RECORD_AUDIO` é feito pelo sistema na primeira abertura. Se a
permissão for negada, a reunião não é iniciada e o motivo aparece na tela e no
log local. Um marcador síncrono também é gravado antes de abrir o microfone,
para preservar o histórico mesmo se uma API nativa falhar.

Atualizações dentro do app são exclusivas do instalador Windows. No Android,
atualize baixando e instalando o novo APK da release.

## Release

Ao criar uma tag `v*`, o workflow gera o instalador Windows e anexa o APK Android
arm64 em modo release ao mesmo release. A chave privada fica fora do repositório,
nos secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_PASSWORD` e `ANDROID_KEY_ALIAS` do GitHub.

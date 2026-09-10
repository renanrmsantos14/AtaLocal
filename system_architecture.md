# Arquitetura atual

O v2 Android vive em `android/` e é independente do cliente Tauri existente.
O domínio começa em `android/app/src/main/java/br/com/betinhos/atalocal/domain`.
`MeetingStatus` é uma máquina de estados pura; persistência Room e processamento
serão adicionados em fases posteriores, sem acoplar a UI ao motor nativo.

# Skill observations

### Observation 1: Diagnostico deve separar estado persistido de processo vivo

**Status:** OPEN
**Date:** 2026-09-03
**Session context:** Diagnostico de app Tauri publicado no GitHub com pipeline de transcricao parado.
**Skill:** task-observer
**Type:** internal
**Phase/Area:** Investigacao

**Issue:** A tela exibe um estado salvo no SQLite, mas isso nao prova que a thread de processamento ainda esta viva.
**Suggested improvement:** Em diagnosticos de jobs locais, comparar estado persistido, thread de execucao e rotina de recuperacao no startup.
**Principle:** Estado exibido pela UI nao prova execucao ativa.

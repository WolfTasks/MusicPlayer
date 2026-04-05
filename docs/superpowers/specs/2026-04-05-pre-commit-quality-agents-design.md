# Pre-Commit Code Quality Agents — Design Spec

**Datum:** 2026-04-05
**Status:** Approved

---

## Überblick

Shell-basiertes Pre-Commit-Hook-System, das vor jedem `git commit` automatisch drei Qualitätsprüfungen ausführt: Formatierung (ktlint), Unit-Tests und ein inhaltliches KI-Code-Review per Claude-Subagent. Fehler blockieren den Commit; Warnungen erlauben ihn.

---

## Architektur

### Komponenten

| Datei | Zweck |
|---|---|
| `scripts/pre-commit` | Haupt-Hook-Skript — orchestriert alle drei Checks |
| `scripts/install-hooks.sh` | Einmaliges Setup: kopiert den Hook nach `.git/hooks/` |
| `scripts/review-agent-prompt.md` | System-Prompt für den Claude-Subagenten |

### Ablauf

```
git commit
  └── .git/hooks/pre-commit
        ├── 1. ktlint (./gradlew ktlintCheck)
        ├── 2. Unit Tests (./gradlew test)
        └── 3. KI-Review (claude -p scripts/review-agent-prompt.md)
```

Der Hook prüft zuerst ob Kotlin-Dateien im Staging-Bereich liegen. Sind keine vorhanden, beendet er sich sofort mit Exit 0.

---

## Schwere-Klassifizierung

### Blockiert den Commit (EXIT 1)

| Check | Kriterium |
|---|---|
| ktlint | Kompilierfehler, unresolved references |
| Gradle Tests | Jeder fehlgeschlagene Unit-Test |
| KI-Review | UI kommuniziert direkt mit Repository/DataSource (Architekturverletzung); ViewModel enthält direkte DB- oder Dateisystem-Operationen; falsche Implementierung des Weight-Algorithmus (`weight × 0.75` bei Skip, `weight × 1.1` bei vollständigem Play, Grenzen 0.1–1.0) |

### Warnung (Commit wird erlaubt)

| Check | Kriterium |
|---|---|
| ktlint | Style-Warnungen |
| KI-Review | Neuer Code ohne zugehörigen Test; fehlende Null-Safety wo sinnvoll; unklare Benennung |

---

## Hook-Skript (`scripts/pre-commit`)

```bash
#!/usr/bin/env bash
set -euo pipefail

STAGED=$(git diff --cached --name-only --diff-filter=ACM | grep '\.kt$' || true)
[ -z "$STAGED" ] && exit 0

echo "=== [1/3] ktlint ==="
./gradlew ktlintCheck --quiet || { echo "✗ ktlint fehlgeschlagen"; exit 1; }

echo "=== [2/3] Unit Tests ==="
./gradlew test --quiet || { echo "✗ Tests fehlgeschlagen"; exit 1; }

echo "=== [3/3] KI Code-Review ==="
DIFF=$(git diff --cached -- '*.kt')
RESULT=$(echo "$DIFF" | claude -p scripts/review-agent-prompt.md)

echo "$RESULT"

echo "$RESULT" | grep -q "^EXIT:1" && exit 1

echo "✓ Alle Checks bestanden"
exit 0
```

---

## KI-Review-Agent (`scripts/review-agent-prompt.md`)

Der Agent erhält den `git diff --cached` der Kotlin-Dateien über stdin. Er prüft:

1. **Architektur:** UI-Schicht → darf nur ViewModels aufrufen. ViewModels → dürfen nur Repositories aufrufen. Repositories → verwalten DataSources.
2. **Weight-Algorithmus:** Skip: `max(0.1, weight × 0.75)`. Full Play: `min(1.0, weight × 1.1)`. Default: `1.0`.
3. **Testabdeckung:** Enthält der Diff neuen Logik-Code ohne zugehörigen Test?

Ausgabe-Format:
```
FEHLER: <Beschreibung>   (blockiert)
WARNUNG: <Beschreibung>  (erlaubt Commit)
EXIT:0                   (oder EXIT:1 wenn FEHLER vorhanden)
```

---

## Setup (`scripts/install-hooks.sh`)

```bash
#!/usr/bin/env bash
cp scripts/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
echo "✓ Pre-Commit-Hook installiert"
```

Jeder Entwickler führt dieses Skript einmalig nach dem Klonen aus.

---

## Nicht im Scope

- CI/CD-Integration (GitHub Actions etc.)
- Andere IDEs ohne Claude Code CLI
- Lint-Checks für XML/Gradle-Dateien
- Android Lint (`./gradlew lint`) — zu langsam für Pre-Commit

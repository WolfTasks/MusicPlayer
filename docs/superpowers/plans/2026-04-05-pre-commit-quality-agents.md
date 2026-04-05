# Pre-Commit Quality Agents Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a shell-based pre-commit hook that runs ktlint, unit tests, and a Claude AI code review before every `git commit`, blocking on errors and warning on style issues.

**Architecture:** Three shell scripts live under `scripts/` (versioniert im Repo). The hook is activated by running `scripts/install-hooks.sh` once. The AI review agent receives the staged Kotlin diff via stdin and outputs structured FEHLER/WARNUNG lines.

**Tech Stack:** Bash, Gradle (ktlint plugin via `org.jlleitschuh.gradle.ktlint`), Claude Code CLI (`claude -p`), JUnit (existing)

---

## File Map

| File | Action | Purpose |
|---|---|---|
| `gradle/libs.versions.toml` | Modify | Add ktlint plugin version |
| `build.gradle.kts` (root) | Modify | Register ktlint plugin |
| `app/build.gradle.kts` | Modify | Apply ktlint plugin |
| `scripts/review-agent-prompt.md` | Create | System-prompt for Claude review subagent |
| `scripts/pre-commit` | Create | Main hook script |
| `scripts/install-hooks.sh` | Create | One-time setup script |

---

## Task 1: Add ktlint Plugin

ktlint is not yet in the project. We use the community Gradle plugin `org.jlleitschuh.gradle.ktlint` which adds a `ktlintCheck` task.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add ktlint version and plugin to version catalog**

Open `gradle/libs.versions.toml`. Add to `[versions]`:

```toml
ktlint = "12.1.1"
```

Add to `[plugins]`:

```toml
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint" }
```

- [ ] **Step 2: Register ktlint in root build.gradle.kts**

Open `build.gradle.kts` (project root). Add `ktlint` to the plugins block with `apply false`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
}
```

- [ ] **Step 3: Apply ktlint in app/build.gradle.kts**

Open `app/build.gradle.kts`. Add `ktlint` to the plugins block:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}
```

- [ ] **Step 4: Sync and verify ktlintCheck task exists**

Run:
```bash
./gradlew tasks --all | grep ktlint
```
Expected output includes: `ktlintCheck`, `ktlintFormat`

- [ ] **Step 5: Run ktlintCheck once to see baseline**

```bash
./gradlew ktlintCheck
```
Expected: Either passes or shows style violations. Note any violations — we fix them in the next step if they exist.

- [ ] **Step 6: Auto-fix any existing violations**

```bash
./gradlew ktlintFormat
```

- [ ] **Step 7: Run ktlintCheck again to confirm clean baseline**

```bash
./gradlew ktlintCheck
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts
git add -u   # picks up any files ktlintFormat changed
git commit -m "build: add ktlint plugin"
```

---

## Task 2: Create the AI Review Agent Prompt

The Claude subagent receives the staged Kotlin diff on stdin and must output structured lines that the hook script can parse.

**Files:**
- Create: `scripts/review-agent-prompt.md`

- [ ] **Step 1: Create the scripts/ directory and the prompt file**

Create `scripts/review-agent-prompt.md` with this exact content:

```markdown
You are a strict code reviewer for an Android Kotlin project.

You will receive a `git diff --cached` of Kotlin files on stdin.
Analyse the diff and report issues using ONLY these output formats:

- `FEHLER: <description>` — blocks the commit
- `WARNUNG: <description>` — allowed, shown as warning
- `EXIT:0` — at the very end if no FEHLER found
- `EXIT:1` — at the very end if any FEHLER found

## Project Architecture Rules (violations = FEHLER)

This project uses strict MVVM + Repository layering:

```
UI (Compose Screens)
  └── ViewModel (StateFlow)
        └── Repository
              └── DataSource / DAO
```

Report FEHLER if:
1. A `@Composable` function or `Screen` file imports or directly instantiates a `Repository`, `Dao`, or `DataSource` class.
2. A `ViewModel` file directly calls Room DAO methods, reads files from disk, or uses `MediaStore`.
3. A `Repository` file imports anything from `androidx.compose` or `androidx.lifecycle.ViewModel`.

## Weight Algorithm Rules (violations = FEHLER)

The play-weight algorithm must be exactly:
- On skip: `weight = max(0.1f, weight * 0.75f)`
- On full play (≥80% played): `weight = min(1.0f, weight * 1.1f)`
- Default weight: `1.0f`

Report FEHLER if different multipliers, bounds, or thresholds are used.

## Test Coverage Rules (violations = WARNUNG)

Report WARNUNG if:
- The diff adds a new public function in `domain/` or `data/repository/` but no corresponding test class or test method is added in the same diff.
- A new `when` branch or conditional is added without a test covering it.

## Naming / Safety Rules (violations = WARNUNG)

Report WARNUNG if:
- A nullable type (`?`) is force-unwrapped with `!!` without a preceding null-check.
- A function or variable name is a single letter (except loop indices `i`, `j`, `k`).

## Output Rules

- Output ONLY the FEHLER/WARNUNG lines and the final EXIT line.
- Do NOT output explanations, summaries, or markdown formatting.
- If there are no issues at all, output only: `EXIT:0`

Example output:
```
FEHLER: LibraryScreen.kt directly calls SongRepository.getAllSongs() — must go through LibraryViewModel
WARNUNG: WeightedShuffler.pick() added without new test in WeightedShufflerTest
EXIT:1
```
```

- [ ] **Step 2: Verify the file was created**

```bash
cat scripts/review-agent-prompt.md
```
Expected: The full prompt content is visible.

- [ ] **Step 3: Commit**

```bash
git add scripts/review-agent-prompt.md
git commit -m "feat: add AI review agent prompt"
```

---

## Task 3: Create the Pre-Commit Hook Script

**Files:**
- Create: `scripts/pre-commit`

- [ ] **Step 1: Create scripts/pre-commit**

Create `scripts/pre-commit` with this exact content:

```bash
#!/usr/bin/env bash
set -euo pipefail

# Only run if Kotlin files are staged
STAGED=$(git diff --cached --name-only --diff-filter=ACM | grep '\.kt$' || true)
if [ -z "$STAGED" ]; then
    exit 0
fi

echo ""
echo "=== [1/3] ktlint ==="
if ! ./gradlew ktlintCheck --quiet 2>&1; then
    echo "✗ ktlint fehlgeschlagen — Commit blockiert"
    exit 1
fi
echo "✓ ktlint OK"

echo ""
echo "=== [2/3] Unit Tests ==="
if ! ./gradlew test --quiet 2>&1; then
    echo "✗ Unit Tests fehlgeschlagen — Commit blockiert"
    exit 1
fi
echo "✓ Tests OK"

echo ""
echo "=== [3/3] KI Code-Review ==="
DIFF=$(git diff --cached -- '*.kt')
RESULT=$(echo "$DIFF" | claude -p scripts/review-agent-prompt.md)

echo "$RESULT"

if echo "$RESULT" | grep -q "^EXIT:1"; then
    echo ""
    echo "✗ KI-Review: FEHLER gefunden — Commit blockiert"
    exit 1
fi

echo ""
echo "✓ Alle Checks bestanden"
exit 0
```

- [ ] **Step 2: Make it executable**

```bash
chmod +x scripts/pre-commit
```

- [ ] **Step 3: Commit**

```bash
git add scripts/pre-commit
git commit -m "feat: add pre-commit hook script"
```

---

## Task 4: Create the Install Script and Activate the Hook

**Files:**
- Create: `scripts/install-hooks.sh`

- [ ] **Step 1: Create scripts/install-hooks.sh**

Create `scripts/install-hooks.sh` with this content:

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cp "$SCRIPT_DIR/pre-commit" "$REPO_ROOT/.git/hooks/pre-commit"
chmod +x "$REPO_ROOT/.git/hooks/pre-commit"

echo "✓ Pre-Commit-Hook installiert: .git/hooks/pre-commit"
echo "  Läuft automatisch vor jedem 'git commit' auf Kotlin-Dateien."
```

- [ ] **Step 2: Make it executable**

```bash
chmod +x scripts/install-hooks.sh
```

- [ ] **Step 3: Run the install script**

```bash
bash scripts/install-hooks.sh
```
Expected:
```
✓ Pre-Commit-Hook installiert: .git/hooks/pre-commit
  Läuft automatisch vor jedem 'git commit' auf Kotlin-Dateien.
```

- [ ] **Step 4: Verify the hook is in place**

```bash
ls -la .git/hooks/pre-commit
```
Expected: File exists and is executable (`-rwxr-xr-x`).

- [ ] **Step 5: Commit**

```bash
git add scripts/install-hooks.sh
git commit -m "feat: add install-hooks script"
```

Note: This commit itself will NOT trigger the hook yet because `install-hooks.sh` is a shell file, not a `.kt` file. The hook only fires on staged `.kt` files.

---

## Task 5: End-to-End Test

Verify the full pipeline works by staging a Kotlin file and running the hook manually.

**Files:** No new files — testing existing setup.

- [ ] **Step 1: Test the hook manually (dry run)**

Run the hook script directly without committing:

```bash
bash .git/hooks/pre-commit
```
Expected: Either exits cleanly (`✓ Alle Checks bestanden`) or shows specific failures. Since no `.kt` files are staged, expected output is nothing (immediate exit 0).

- [ ] **Step 2: Stage an existing Kotlin file to trigger the full pipeline**

```bash
git add app/src/main/java/com/wolf/musicplayer/domain/WeightedShuffler.kt
bash .git/hooks/pre-commit
```
Expected:
```
=== [1/3] ktlint ===
✓ ktlint OK

=== [2/3] Unit Tests ===
✓ Tests OK

=== [3/3] KI Code-Review ===
EXIT:0

✓ Alle Checks bestanden
```

- [ ] **Step 3: Unstage the test file**

```bash
git restore --staged app/src/main/java/com/wolf/musicplayer/domain/WeightedShuffler.kt
```

- [ ] **Step 4: Update CLAUDE.md to document the hook setup**

Open `CLAUDE.md` and add a section after the Build commands:

```markdown
## Pre-Commit Quality Checks

Run once after cloning to activate:
```bash
bash scripts/install-hooks.sh
```

Fires automatically on `git commit` when `.kt` files are staged. Runs ktlint → unit tests → AI code review. Errors block the commit; warnings allow it.

To bypass in an emergency: `git commit --no-verify`
```

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: document pre-commit hook setup in CLAUDE.md"
```

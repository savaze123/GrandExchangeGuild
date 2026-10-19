# AI_TRACKING.md — OSRS Grand Exchange Career Guild
> Permanent session handoff board. Every AI session MUST read this before touching code.

---

## 1. Current Architecture & Key Decisions

| Decision | Detail |
|---|---|
| **DI Strategy** | Manual DI via `AppContainer` — zero Hilt/Dagger. No annotations, no reflection. ViewModels created via factory functions called from Compose `viewModel(factory=...)`. |
| **Budget Guard** | `TokenGovernor` uses `SharedPreferences` (no DataStore overhead). Cost tracked in 1/100-cent integer units to avoid float rounding. Monthly auto-reset via YYYY-MM key. $10/month cap before Gemini fallback. |
| **Skill Matrix Grid** | `GridCells.Fixed(3)` — 3-column fixed, NOT adaptive. Per spec. |
| **Tile Interaction** | Tap-anywhere on tile = toggle (multi-select). Replaced Switch widget. `OsrsSkillTile` in `SkillCard.kt`. |
| **Level Display** | Format: `"current/max"` (e.g. `72/99`). `OSRS_MAX_LEVEL = 99`. |
| **OSRS Color Palette** | Stone `#3D2B1F`, Brown `#5C3A1E`, Gold `#FFD700`, DarkBorder `#1A0F08`, TextDim `#9E8A6A`. |
| **Active Skill Filter** | `BountyBoardViewModel` observes `SkillRepository.observeActive()` and filters `JobListing` against matched skills in `observeJobs()`. |
| **Job Score Handoff** | `TokenGovernor.isGeminiAllowed` checked before `FetchAndScoreJobsUseCase`. `recordScoringBatch(count)` called after successful Gemini run. |
| **Room Schema** | UNTOUCHED per instructions. No migrations added. |
| **No New Dependencies** | Zero new third-party deps added. All existing catalog entries only. |

---

## 2. Completed Modules (feature/di-skill-altar)

### Step 1 — TokenGovernor & DI Wiring ✅
| File | Status | Notes |
|---|---|---|
| `core/token/TokenGovernor.kt` | **CREATED** | SharedPreferences budget guard, $10/month cap, monthly rollover |
| `di/AppContainer.kt` | **MODIFIED** | Added `TokenGovernor` singleton + updated `createBountyBoardViewModel()` factory to pass `tokenGovernor` + `skillRepository` |
| `presentation/dashboard/bountyboard/BountyBoardViewModel.kt` | **MODIFIED** | Added `SkillRepository` + `TokenGovernor` params; `observeActiveSkills()` coroutine; cost recording on successful scrape |
| `presentation/dashboard/bountyboard/BountyBoardUiState.kt` | **MODIFIED** | Added `activeSkills: List<Skill>` and `isGeminiAllowed: Boolean` fields |

### Step 2 — DYNAMIC_SKILL_MATRIX UI (Skill Altar) ✅
| File | Status | Notes |
|---|---|---|
| `presentation/dashboard/skillaltar/SkillAltarScreen.kt` | **REBUILT** | Fixed 3-col grid, OSRS stone/brown palette, gold text, category chips OSRS-styled, `OsrsSkillTile` integration |
| `presentation/dashboard/skillaltar/SkillCard.kt` | **REBUILT** | Renamed composable to `OsrsSkillTile`, tap-anywhere toggle, "current/max" level badge, gold `#FFD700` active border, dark `#1A0F08` inactive border |

### Step 3 — State Updates ✅
| File | Status | Notes |
|---|---|---|
| `project_graph.json` | **UPDATED** | `DI_CONTAINER`, `TOKEN_GOVERNOR`, `DYNAMIC_SKILL_MATRIX` marked `COMPLETED`. Two new edges added. |
| `AI_TRACKING.md` | **CREATED** | This file. |

---

## 3. Known Blockers / Errors

### ⛔ BLOCKER: Gradle compilation check could not be executed

**Environment:** Asahi Linux (Fedora 44 aarch64)  
**Java Version:** OpenJDK 25.0.4.1 (Red Hat) — only Java 25 and Java 27-ea available on this machine  
**Gradle Version Attempted:** 8.14 / 8.14.6  

**Exact Error (from daemon log):**
```
java.lang.IllegalArgumentException: 25.0.4.1
    at org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse(JavaVersion.java:307)
    at org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.current(JavaVersion.java:176)
    at org.jetbrains.kotlin.cli.jvm.modules.JavaVersionUtilsKt.isAtLeastJava9(javaVersionUtils.kt:11)
```

**Root Cause:** Kotlin 2.1.0's bundled IntelliJ platform `JavaVersion` parser does not support Java 25+ version strings. The parser fails at script compilation stage before any Kotlin source files are touched.

**No gradlew exists** in the repo — the project has never been built on this machine.

### ✅ Resolution for Next Session

**Option A (Recommended):** Install Java 17 or Java 21 and set `JAVA_HOME`:
```bash
sudo dnf install java-17-openjdk-devel   # or java-21-openjdk-devel
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH=$JAVA_HOME/bin:$PATH
# Then bootstrap the wrapper:
gradle wrapper --gradle-version=8.14.6 --distribution-type=bin
./gradlew compileDebugKotlin -q
```

**Option B:** Upgrade to Kotlin 2.2+ (requires AGP version check) which fixes Java 25+ version parsing.  
**AGP used:** `8.8.0` — check compatibility before upgrading Kotlin.

**Option C:** Use Android Studio on a device/machine with Java 17/21 to do the first build + gradlew generation.

---

## 4. Next Session — Resumption Checklist

1. Install Java 17/21, re-run `./gradlew compileDebugKotlin -q`.
2. If compilation errors exist, fix them (max 2 attempts per the project rules).
3. Next modules to implement per `project_graph.json`:
   - `SCREENSHOT_OCR_HUNTER` — ML Kit OCR + screenshot picker
   - `RESUME_FORGE_PDF` — Native `PdfDocument` resume/cover-letter generator
   - `AUTO_APPLY_AGENT` — Headless WebView ATS form autofill
   - `SAFE_CLI_ENGINE` — CliDispatcher integration with the in-app CLI bar UI

---

*Last updated: 2026-10-19 by feature/di-skill-altar session.*

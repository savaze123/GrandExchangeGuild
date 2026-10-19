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

### ✅ RESOLVED: ML Kit Text Recognition Artifact Coordinate
- **Issue:** `com.google.android.gms:play-services-mlkit-text-recognition:16.0.1` failed to resolve on Google Maven.
- **Resolution:** Updated `gradle/libs.versions.toml` to `mlkitTextRecognition = "19.0.1"`. Dependency now resolves successfully.

### ✅ RESOLVED: Gradle Toolchain Compatibility
- **Issue:** Kotlin 2.1.0 IntelliJ version parser threw `IllegalArgumentException: 25.0.4.1` on OpenJDK 25.
- **Resolution:** Bootstrapped Gradle 9.8.1 wrapper supporting Java 25.

### ⛔ CURRENT BLOCKER: AAPT2 Execution on Asahi Linux (aarch64 16K Page Size)
- **Environment:** Asahi Linux (Fedora 44 aarch64, 16K kernel page size)
- **Task:** `:app:processDebugResources` (required for Kotlin compilation R-class generation)
- **Exact Error:**
```
AAPT2 aapt2-8.8.0-12006047-linux Daemon #0: Unexpected error output: [binfmt_dispatcher] Using FEX with muvm
Execution failed for task ':app:processDebugResources'.
> AAPT2 aapt2-8.8.0-12006047-linux Daemon #0: Daemon startup failed
```
- **Root Cause:**
  Google only ships x86_64 Linux binaries for `aapt2`. Because the Asahi Linux host uses a 16K page size kernel, x86_64 binaries (using jemalloc 4K pages) cannot execute natively in FEX userspace and must run via `muvm` (microVM).
  During AAPT2 daemon startup, `binfmt_dispatcher` / `muvm` emits status messages to stderr/stdout (`[binfmt_dispatcher] Using FEX with muvm`, `Failed to set the new pressure in the guest...`). Any stderr output during daemon startup causes Android Gradle Plugin to abort with `Unexpected error output: Daemon startup failed`.

---

## 4. Next Session — Resumption Checklist

1. Configure silent AAPT2 execution under `muvm` or provide an aarch64 native `aapt2` binary.
2. Re-run `./gradlew compileDebugKotlin -q`.
3. Next modules to implement per `project_graph.json`:
   - `SCREENSHOT_OCR_HUNTER` — ML Kit OCR + screenshot picker
   - `RESUME_FORGE_PDF` — Native `PdfDocument` resume/cover-letter generator
   - `AUTO_APPLY_AGENT` — Headless WebView ATS form autofill
   - `SAFE_CLI_ENGINE` — CliDispatcher integration with the in-app CLI bar UI

---

*Last updated: 2026-10-19 by feature/di-skill-altar session.*

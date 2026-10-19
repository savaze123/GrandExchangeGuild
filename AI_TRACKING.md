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

### ✅ RESOLVED: Gradle Toolchain & Java Compatibility
- **Issue:** Kotlin 2.1.0 IntelliJ version parser threw `IllegalArgumentException: 25.0.4.1` on OpenJDK 25.
- **Resolution:** Java 21 configured via `org.gradle.java.home` in `gradle.properties`.

### ✅ RESOLVED: AAPT2 Native Execution on Asahi Linux aarch64
- **Issue:** x86_64 AAPT2 binary failed under Asahi 16k page kernel.
- **Resolution:** Native ARM64 AAPT2 configured via `android.aapt2FromMavenOverride`.

### ✅ RESOLVED: Material 3 XML Theme Dependency
- **Issue:** `AAPT: error: resource style/Theme.Material3.DayNight.NoActionBar not found`.
- **Resolution:** Added `com.google.android.material:material:1.12.0` (`google-material`) to `gradle/libs.versions.toml` and `app/build.gradle.kts`.

### ✅ RESOLVED: Compilation & Resource Linking Fixes
- **Issue:** Missing mipmap launcher icons, `SupportFactory` package reference in `DatabaseModule.kt`, nullability in `BountyBoardScreen.kt`, and default ViewModel wiring in `DashboardPane.kt` / `MainActivity.kt`.
- **Resolution:** Corrected all references. `./gradlew compileDebugKotlin -q` builds cleanly with 0 errors.

---

## 4. Next Session — Resumption Checklist

1. `./gradlew compileDebugKotlin -q` is fully verified and passing.
2. Next modules to implement per `project_graph.json`:
   - `SCREENSHOT_OCR_HUNTER` — ML Kit OCR + screenshot picker
   - `RESUME_FORGE_PDF` — Native `PdfDocument` resume/cover-letter generator
   - `AUTO_APPLY_AGENT` — Headless WebView ATS form autofill
   - `SAFE_CLI_ENGINE` — CliDispatcher integration with the in-app CLI bar UI

---

*Last updated: 2026-10-19 by feature/di-skill-altar session.*

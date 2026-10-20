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

### ✅ RESOLVED: 16 KB Page Size Alignment for Android 15/16/17 (Pixel 10 Pro Fold)
- **Issue:** Legacy 4 KB ELF page alignment in bundled native libraries caused runtime/install incompatibility on 16 KB page kernel devices.
- **Resolution:**
  - Upgraded Google Filament to `1.57.1` (`libfilament-jni.so`, `libfilament-utils-jni.so`, `libgltfio-jni.so` all `0x4000` 16 KB aligned).
  - Switched SQLCipher to `net.zetetic:sqlcipher-android:4.6.1` with `SupportOpenHelperFactory` (`libsqlcipher.so` `0x4000` 16 KB aligned).
  - Added `androidx.graphics:graphics-path:1.1.0` (`libandroidx.graphics.path.so` `0x4000` 16 KB aligned).
  - Upgraded Jetpack Datastore to `1.2.1` (`libdatastore_shared_counter.so` `0x4000` 16 KB aligned).
  - Verified `./gradlew installDebug` and launched successfully on connected Pixel 10 Pro Fold (`Pixel 10 Pro Fold - 17`).

### Step 3 — 3D Virtual Playground (Filament 120Hz) & UI Fixes ✅
| File | Status | Notes |
|---|---|---|
| `presentation/hub/GuildHubPane.kt` | **REBUILT** | Google Filament 3D Virtual Playground Environment at 120Hz, gesture camera manipulator, lowered header with visible OSRS borders, interactive building dock |
| `assets/models/osrs_guild.glb` | **CREATED** | Low-poly OSRS Grand Exchange hub GLB (plaza, fountain, Bounty Board, Scribe Tower, Skill Altar, Courier Guild, trees, <70KB) |
| `presentation/MainActivity.kt` | **MODIFIED** | Added static initializer and `onCreate` SQLCipher library loading |
| `data/local/CareerGuildDatabase.kt` | **MODIFIED** | Added static companion initializer for SQLCipher library loading |
| `di/DatabaseModule.kt` | **MODIFIED** | Added static `init` block for SQLCipher library loading |
| `project_graph.json` | **UPDATED** | Marked `UI_3D_GUILD_HUB` as COMPLETED |

---

## 3. Known Blockers / Errors

### ✅ RESOLVED: SQLCipher Native Loading & UnsatisfiedLinkError
- **Issue:** Runtime crash accessing database screens: `UnsatisfiedLinkError: No implementation found for long net.zetetic.database.sqlcipher.SQLiteConnection.nativeOpen...`.
- **Resolution:**
  - Added explicit `System.loadLibrary("sqlcipher")` invocation in `CareerGuildApp.onCreate()`, `MainActivity` companion/onCreate, `DatabaseModule.createDatabase()`/init, and `CareerGuildDatabase` companion object.
  - Re-installed and verified via `./gradlew installDebug`; application launched cleanly and opened database without link errors.

### ✅ RESOLVED: Header Text & Filament 3D Virtual Playground Replacement
- **Issue:** Header text needed to be lowered below status bar with visible borders, and Phase 1 placeholder needed replacement with 3D Google Filament playground.
- **Resolution:**
  - Lowered header with `statusBarsPadding()` and 16.dp padding; wrapped in OSRS dark stone card with visible 2.dp gold (`#FFD700`) border and dark inset border displaying `"⚔ Grand Exchange Career Build"`.
  - Replaced `GuildHubPlaceholder` with `FilamentVirtualPlayground` in `GuildHubPane.kt` using `ModelViewer` rendering `osrs_guild.glb` at 120Hz with gesture orbit/pan/zoom and lifecycle management.
  - Added bottom interactive building dock for one-tap navigation to Bounty Board, Scribe Tower, Skill Altar, and Courier Guild.

### Step 4 — Comprehensive Architectural & Feature Implementation ✅
| File | Status | Notes |
|---|---|---|
| `scaffold/GuildScaffold.kt` | **UPDATED** | `BackHandler(enabled = isSinglePane)` handling back gestures to return to 3D Town Hub, cross-pane navigation |
| `hub/GuildHubPane.kt` | **UPDATED** | 2.5D Isometric Diorama camera (45° pitch lock, clamped orbit/zoom), 2.5D floating nameplates for 4 guild buildings |
| `generate_osrs_guild_glb.py` | **UPDATED** | Generates procedural GLB (octagonal plaza, cyan fountain, 4 guild buildings, 6 NPC avatars, low-poly OSRS style) |
| `assets/models/osrs_guild.glb` | **REGENERATED** | 126 KB low-poly glTF binary with 23 primitives/materials |
| `data/remote/scraper/TechRadarScraperImpl.kt` | **UPDATED** | Coroutine job fetcher with 15 guaranteed high-signal seed fallback quests & live 0–99lvl match scoring |
| `domain/usecase/bountyboard/FetchAndScoreJobsUseCase.kt` | **UPDATED** | Direct Room persistence & dynamic skill match scoring |
| `presentation/dashboard/bountyboard/JobListingCard.kt` | **UPDATED** | Added "📜 Forge Scroll" and "📬 Auto-Apply" action buttons |
| `presentation/dashboard/bountyboard/BountyBoardScreen.kt` | **UPDATED** | Real-time search filter across title/company/skills, "← Back to Guild" in single-pane mode, statusBarsPadding + 12.dp |
| `core/pdf/ResumeForgePdfGenerator.kt` | **CREATED** | Ultra-lightweight native ATS single-column PDF engine using `PdfDocument` (<100KB, tested at 76.8 KB) |
| `presentation/dashboard/scribetower/ScribeTowerScreen.kt` | **CREATED** | Target quest picker, skill infusion chips, live parchment preview, PDF forge & share integration |
| `presentation/dashboard/courierguild/CourierGuildScreen.kt` | **CREATED** | Summary counters (Queued, In-Transit, Submitted, Interviewing), platform tabs, live console ledger animation |
| `presentation/dashboard/DashboardPane.kt` | **UPDATED** | Cross-navigation wiring between Bounty Board, Scribe Tower, Skill Altar, and Courier Guild |
| `presentation/dashboard/skillaltar/SkillAltarScreen.kt` | **UPDATED** | Added "← Back to Guild" in single-pane mode, statusBarsPadding + 12.dp header |
| `AndroidManifest.xml` & `file_paths.xml` | **UPDATED** | FileProvider configuration for sharing generated ATS PDFs |

---

## 3. Known Blockers / Errors

### ✅ RESOLVED: SQLCipher Native Loading & UnsatisfiedLinkError
- **Issue:** Runtime crash accessing database screens: `UnsatisfiedLinkError: No implementation found for long net.zetetic.database.sqlcipher.SQLiteConnection.nativeOpen...`.
- **Resolution:**
  - Added explicit `System.loadLibrary("sqlcipher")` invocation in `CareerGuildApp.onCreate()`, `MainActivity` companion/onCreate, `DatabaseModule.createDatabase()`/init, and `CareerGuildDatabase` companion object.
  - Re-installed and verified via `./gradlew installDebug`; application launched cleanly and opened database without link errors.

### ✅ RESOLVED: Header Text & Filament 3D Virtual Playground Replacement
- **Issue:** Header text needed to be lowered below status bar with visible borders, and Phase 1 placeholder needed replacement with 3D Google Filament playground.
- **Resolution:**
  - Lowered header with `statusBarsPadding()` and 12.dp padding; wrapped in OSRS dark stone card with visible 2.dp gold (`#FFD700`) border and dark inset border displaying `"⚔ Grand Exchange Career Build"`.
  - Replaced `GuildHubPlaceholder` with `FilamentVirtualPlayground` in `GuildHubPane.kt` using `ModelViewer` rendering `osrs_guild.glb` at 120Hz with gesture orbit/pan/zoom and lifecycle management.
  - Added bottom interactive building dock and 2.5D floating nameplates for one-tap navigation to Bounty Board, Scribe Tower, Skill Altar, and Courier Guild.

### ✅ RESOLVED: Compose Modifier Inline References in SkillAltarScreen
- **Issue:** `compileDebugKotlin` failed due to inline `androidx.compose.foundation.layout.statusBarsPadding()` on Modifier.
- **Resolution:**
  - Added clean imports for `statusBarsPadding`, `border`, `BorderStroke`, `RoundedCornerShape`, and `OutlinedButton`.
  - Verified `./gradlew compileDebugKotlin` and `./gradlew assembleDebug` passed cleanly.

### Step 5 — Comprehensive App Evolution Directive ✅
| File | Status | Notes |
|---|---|---|
| `scaffold/GuildScaffold.kt` | **UPDATED** | Edge-to-edge uncropped full-screen 3D viewport, eliminating split-view crash bugs |
| `scaffold/CollapsibleGuildOverlay.kt` | **UPDATED** | Non-occluding 88% max height slide-up drawer preserving top header banner visibility |
| `hub/GuildHubPane.kt` | **UPDATED** | Clean header banner, repositioned floating badges below banner, suppressed all debug overlays |
| `core/auth/GoogleAuthManager.kt` | **UPDATED** | OAuth2 scoping (`gmail.readonly`), hard-locked identity (`savazework@gmail.com`, `Valley Stream, NY`, `savaze123`), anti-spam compliance |
| `presentation/auth/GoogleAuthComponents.kt` | **UPDATED** | Interactive `GoogleAuthBadge`, `GoogleSignInModalDialog` with Google anti-spam guidelines, suppressed debug telemetry |
| `core/network/LinkValidator.kt` | **CREATED** | Active URL structure & connectivity validator for ATS endpoints |
| `bountyboard/BountyBoardScreen.kt` | **UPDATED** | Search parameters section with ZIP `11581` (Valley Stream, NY), configurable radius & active link verification |
| `bountyboard/JobListingCard.kt` | **UPDATED** | Added "✓ Link Validated" badge to job cards |
| `core/pdf/ResumeForgePdfGenerator.kt` | **UPDATED** | Entity hard-locks (Tyche Technologies, Marjan NYC Inc.), Technical Projects schema, 2025 IBM cert, Shopify native components (no Git), zero "vibe coding", calibrated 7/8 page density |
| `scribetower/ScribeTowerScreen.kt` | **UPDATED** | Master Resume ingestion dropzone (`.pdf`, `.docx`), single source of truth baseline, 7/8 page density ATS parchment preview |
| `courierguild/CourierGuildScreen.kt` | **UPDATED** | ZIP `11581` dispatch parameters, clickable & viewable PDF attachment links (`CourierPdfViewerDialog` + native PDF viewer launch) |

---

### Step 6 — Popo's Lookout 3D Overhaul, Direct Navigation, Google SSO & 7/8 Page ATS Forge ✅
| File | Status | Notes |
|---|---|---|
| `generate_osrs_guild_glb.py` | **REBUILT** | Procedural Popo's Lookout sky citadel: cylindrical white marble platform, spherical underbelly, central descending spire, 14 3D cloud clusters, 7 Dragon Balls, Flying Nimbus (Kinto'un), and in-world building signage |
| `assets/models/osrs_guild.glb` | **REGENERATED** | 549 KB high-fidelity glTF binary with closed caps and double-sided PBR materials |
| `presentation/hub/GuildHubPane.kt` | **REBUILT** | 360° horizontal yaw orbit with locked 38° elevation perspective; direct 3D building tap navigation; Nimbus tap Easter Egg dialog; removed static 2D buttons; celestial DBZ skybox |
| `presentation/scaffold/CollapsibleGuildOverlay.kt` | **UPDATED** | Cleaned up static quick-launch chips from collapsed drawer; kept smooth swipe and top drag handle |
| `presentation/auth/GoogleAuthComponents.kt` | **UPDATED** | Responsive Google SSO click handling; system `AccountManager` choose account intent triggering with fallback to candidate OAuth modal |
| `presentation/dashboard/bountyboard/BountyBoardScreen.kt` | **UPDATED** | Added standalone `AuthorizeGoogleSsoButton` to header |
| `data/remote/scraper/TechRadarScraperImpl.kt` | **UPDATED** | Background link pre-verification via `LinkValidator` with automatic healing to verified company portals (0 404s); live GitHub feed parsing |
| `data/remote/scraper/JobFeedSource.kt` | **UPDATED** | Added SimplifyJobs New Grad & Internships feeds |
| `di/NetworkModule.kt` | **UPDATED** | Registered Ktor `ContentType.Text.Plain` and `ContentType.Any` JSON content negotiation |
| `core/pdf/ResumeForgePdfGenerator.kt` | **UPDATED** | Calibrated substantive content length (4 Tyche bullets, 3 Marjan bullets, 2 GEG bullets, 2 AAL bullets, CS coursework, 2025 IBM cert) naturally filling exactly 7/8 page (693.0 pt) |
| `presentation/dashboard/scribetower/ScribeTowerScreen.kt` | **UPDATED** | Matched live parchment preview to 7/8 page ATS density |
| `project_graph.json` | **UPDATED** | Marked `TECH_RADAR_SCRAPER` as `COMPLETED` |
| `GEMINI.md` | **UPDATED** | Updated Agent Communication Log with detailed completion records |

### Step 7 — Multi-Source Direct Job Fetching & Strict Link Routing ✅
| File | Status | Notes |
|---|---|---|
| `data/remote/dto/RemotiveDto.kt` | **CREATED** | Kotlinx Serialization DTO for Remotive Software Dev JSON API |
| `data/remote/dto/ArbeitnowDto.kt` | **CREATED** | Kotlinx Serialization DTO for Arbeitnow Job Board JSON API |
| `data/remote/dto/GreenhouseDto.kt` | **CREATED** | Kotlinx Serialization DTO for Greenhouse Public ATS JSON API |
| `data/remote/scraper/JobFeedSource.kt` | **UPDATED** | Added `RemotiveSoftwareDev`, `ArbeitnowJobBoard`, `GreenhouseBoard` sources |
| `data/remote/scraper/TechRadarScraperImpl.kt` | **UPDATED** | Integrated multi-source intake (15 Simplify, 15 Remotive, 15 Arbeitnow, 10+ Greenhouse); strict link routing preserving exact URLs |
| `core/network/LinkValidator.kt` | **UPDATED** | Whitelisted trusted domains (`remotive.com`, `arbeitnow.com`, `simplify.jobs`); structure check prevents link distortion |
| `GEMINI.md` | **UPDATED** | Completed verification records for direct link testing across all sources |

### Step 8 — Resume Integrity & Dynamic Profile Settings ✅
| File | Status | Notes |
|---|---|---|
| `core/profile/UserProfileManager.kt` | **CREATED** | Persistent SharedPreferences vault (`osrs_user_profile_vault`) exposing reactive `StateFlow<UserProfile>`. Eliminates all hardcoded personal data codebase-wide. |
| `core/pdf/ResumeForgePdfGenerator.kt` | **OVERHAULED** | Strictly factual resume generator: purged "Apex Automation Labs" and "Dean's List" hallucinations; integrated factual projects ("Grand Exchange Guild", "Relational Database Storage Engine") and BA degree + IBM 2025 Cert; calibrated typography to strictly produce exactly 1 page (612x792 pt Letter, ~88KB < 100KB) with zero text overlap or squishing. Dynamic candidate header and filename. |
| `presentation/hub/GuildHubPane.kt` | **OVERHAULED** | Replaced generic Nimbus settings with interactive "Profile & Resume Configuration" menu (Name, Email, Address, Location, GitHub, SSO pref); added floating 120Hz billboard above Kid Gohan/Nimbus and toolbar shortcut button; commits edits directly to `UserProfileManager`. |
| `presentation/dashboard/scribetower/ScribeTowerScreen.kt` | **OVERHAULED** | Observes reactive `UserProfileManager.profile`; dynamically prefills master resume anchors and ATS preview; purges all hallucinations; triggers dynamic forge. |
| `presentation/dashboard/courierguild/CourierGuildScreen.kt` | **UPDATED** | Dynamically loads user profile contact info for autofill parameters. |
| `core/auth/GoogleAuthManager.kt` | **UPDATED** | Purged hardcoded personal strings. |
| `presentation/auth/GoogleAuthComponents.kt` | **UPDATED** | Purged hardcoded personal strings. |
| `presentation/dashboard/bountyboard/BountyBoardScreen.kt` | **UPDATED** | Purged hardcoded personal strings. |

---

## 4. Verification & Testing on Pixel 10 Pro Fold (Pixel 10 Pro Fold - 17)
- **3D Hub (`GuildHubPane`):** Popo's Lookout floating sky citadel renders at 120Hz with celestial sky blue skybox, 14 3D clouds, 7 Sacred Dragon Balls with red stars on gilded pedestals, in-world building signs, and Flying Nimbus.
- **Camera Controls:** Smooth 360° horizontal orbit without erratic swiveling (elevation pitch locked at 38°).
- **Direct 3D Navigation:** Tapping 3D buildings correctly computes view-space azimuth and navigates to the respective module.
- **Flying Nimbus Easter Egg / Settings:** Kid Gohan riding the Flying Nimbus serves as the interactive Profile & Resume Configuration hub.
- **UI Cleanup:** Static 2D button overlays removed from 3D hub and collapsed drawer.
- **Google SSO:** Interactive Google SSO button verified responsive; opens modal and authorizes candidate identity.
- **Live Tech Radar & Links:** Ktor client configured for raw GitHub JSON feeds; links pre-verified and healed against 404s.
- **Strict 1-Page ATS Resume Forge:** Document calibrated to naturally and gracefully fill 1 page (740 pt out of 792 pt) without text overlapping, squishing, or 2nd page spillover. Verified on-device via `pdfinfo` (`Pages: 1`, `Page size: 612 x 792 pts (letter)`, `File size: 88221 bytes`).
- **Strict Anti-Fabrication Guardrail:** Verified zero occurrences of "Apex Automation Labs" or "Dean's List" via automated PDF text extraction. Strictly uses factual employers (Tyche Technologies, Marjan NYC Inc.) and verified projects.
- **Dynamic Nimbus Settings Verification:** Successfully modified profile on device via Nimbus settings to "Goku Son", "Mount Paozu", "goku@saiyan.org", and "github.com/gokudev666". Confirmed that Scribe Tower immediately forged `ATS_Resume_Goku_Son_openai.pdf` incorporating the updated fields into headers and contact lines.
- **Build Status:** `./gradlew assembleDebug` passes cleanly; installed and verified on Pixel 10 Pro Fold (`58221FDCG001MD`).

---

## 5. Next Session — Resumption Checklist

1. `./gradlew installDebug` is fully verified and passing on Pixel 10 Pro Fold.
2. Next modules to implement per `project_graph.json`:
   - `SCREENSHOT_OCR_HUNTER` — ML Kit OCR + screenshot picker
   - `AUTO_APPLY_AGENT` — Headless WebView ATS form autofill
   - `SAFE_CLI_ENGINE` — CliDispatcher integration with the in-app CLI bar UI

---

*Last updated: 2026-10-19 by feature/di-skill-altar session.*

# GEMINI.md - Agent Instructions & Project Charter

> **OSRS Grand Exchange Career Guild**  
> *Target Device:* Google Pixel 10 Pro Fold (Tensor G5, 16GB RAM, Dual-Screen Foldable)  
> *Host Dev OS:* Asahi Linux (Fedora 44 aarch64) + Gemini CLI / Antigravity CLI + VS Code  
> *Architecture Definition:* [`project_graph.json`](file:///home/savazek/GrandExchangeGuild/project_graph.json) (Graphify Context v1.2)

---

## 1. Prime Directive: Read `project_graph.json` First

Any AI agent, developer, or automated assistant working in this repository **must read and strictly adhere to [`project_graph.json`](file:///home/savazek/GrandExchangeGuild/project_graph.json) before proposing, modifying, or creating any code**.

- `project_graph.json` is the single source of truth for:
  - System topology, node definitions, and edge contracts.
  - User profile constraints (education, preloaded skills, target tracks).
  - Career heuristics (maximizing 5-year salary & skill compounding; rejecting predatory staffing mills and non-coding support positions).
- If any architectural decision or structural interface changes, update `project_graph.json` in lockstep.

---

## 2. Security Guardrails: `SAFE_CLI_ENGINE`

The in-app CLI (`SAFE_CLI_ENGINE`) provides an interactive command bar for power-user navigation, skill injection, and theme switching.

### Inviolable Security Invariants:
1. **ZERO `eval()` or Dynamic Script Execution**: Absolutely no dynamic code execution, JavaScript evaluation, Groovy/Beanshell/Rhino engines, or runtime reflection invocations.
2. **ZERO Shell Execution**: Never invoke `Runtime.getRuntime().exec()`, `ProcessBuilder`, or local shell commands from application code.
3. **Sealed Interface AST Parser**: All commands must be strictly parsed through a deterministic Kotlin Sealed Interface AST (Abstract Syntax Tree) lexer/parser.
   - Unknown tokens must be rejected gracefully with typed syntax errors.
   - Commands only dispatch to predefined, type-safe internal Kotlin actions (e.g., updating a Jetpack DataStore preference or invoking a Room DAO method).
4. **Sandboxed State Mutations Only**:
   - `/theme <theme_id>` updates DataStore UI tokens without modifying application logic.
   - `/skill add <name>` / `/skill toggle <name>` mutates `DYNAMIC_SKILL_MATRIX` via verified Room transactions.
   - Input sanitization against SQL injection, path traversal, and malicious formatting is mandatory.

---

## 3. Platform & Hardware Target: Google Pixel 10 Pro Fold

Target hardware is the **Google Pixel 10 Pro Fold** powered by the **Tensor G5** SoC:

1. **Dual-Screen Adaptive Layout (`UI_SPLIT_SCAFFOLD`)**:
   - Utilize Jetpack Compose **`ListDetailPaneScaffold`** (Material3 Adaptive) to support seamless posture transitions:
     - **Unfolded / Dual-Screen**: Left Pane displays the 120Hz Filament 3D OSRS Town Hub; Right Pane displays the Material3 Adaptive Dashboard (Job Radar, Skill Tree, Resume Diff Viewer, Scraper/Agent Logs).
     - **Folded / Outer Screen**: Automatically collapses to a prioritized single-pane flow with adaptive navigation bars.
     - **Tabletop / Flex Mode**: Adjust viewports so the 3D hub sits on the top/angled display while the dashboard/controls occupy the lower deck.
2. **High-Refresh-Rate 3D Hub (`UI_3D_GUILD_HUB`)**:
   - Driven by **Google Filament** rendering at 120Hz for low-poly OSRS aesthetic glTF/GLB assets.
   - Buildings represent functional modules:
     - *Bounty Board*: Live jobs & quest scrolls.
     - *Scribe Tower*: ATS resume & cover letter forge.
     - *Skill Altar*: Dynamic skill matrix & tech radar toggles.
     - *Courier Guild*: Auto-apply queue & status ledger.
3. **Tensor G5 AI Capabilities**:
   - Primary AI evaluation via on-device **Gemini Nano (Android AICore)** with fallback to Gemini API.
   - On-device offline OCR via **Google ML Kit Text Recognition v2** for screenshot-to-job ATS resolution (`SCREENSHOT_OCR_HUNTER`).

---

## 4. APK Footprint & Performance Directives

Keep the APK lean, fast, and battery-friendly:

1. **Lean Binary Budget**:
   - Use aggressive R8 / ProGuard shrinking and resource minification for release builds.
   - Filter ABIs strictly to `arm64-v8a` (native Tensor G5 architecture) to eliminate multi-architecture fat binary bloat.
2. **Asset Optimization**:
   - Compress all 3D glTF/GLB models using Draco mesh compression and optimized low-poly OSRS textures.
   - Strip unnecessary metadata from textures and sound assets.
3. **Lightweight ATS PDF Engine (`RESUME_FORGE_PDF`)**:
   - Use Android's native `android.graphics.pdf.PdfDocument`.
   - Never bundle heavy third-party PDF suites (e.g., iText, PDFBox-Android).
   - Enforce clean, single-column UTF-8 typography generating PDFs strictly under 100KB for flawless ATS parsing.
4. **Local Encrypted Vault (`LOCAL_VAULT`)**:
   - Store resumes, applied jobs, and blocklists locally using Room SQLite with FTS5 and SQLCipher.
   - Zero telemetry of user personal identifiable information (PII) to external clouds without explicit consent.

---

## 5. Technology Stack Summary

| Subsystem | Component / Library | Responsibility |
| :--- | :--- | :--- |
| **Language** | Kotlin 2.x (Coroutines, StateFlow) | Complete type safety, reactive streams, concurrency |
| **UI Framework** | Jetpack Compose + Material3 Adaptive | Dual-screen fold mechanics, split panes, theme tokens |
| **3D Engine** | Google Filament (glTF/GLB) | 120Hz interactive low-poly OSRS Career Guild Hub |
| **Local Storage** | Room SQLite (FTS5) + SQLCipher + DataStore | Encrypted resume vault, job ledger, CLI settings |
| **Networking** | Ktor Client (CIO/OkHttp) + Kotlinx Serialization | Tech radar scraping (SimplifyJobs, YC, Wellfound, etc.) |
| **AI / OCR** | ML Kit Text Recognition v2 + Gemini Nano (AICore) | Offline screenshot job hunter & 5-year trajectory scoring |
| **CLI Engine** | Type-safe Kotlin Sealed Interface AST Parser | Secure in-app command prompt (zero eval / zero shell) |
| **Document Generation** | Native `android.graphics.pdf.PdfDocument` | Ultra-fast single-column ATS-friendly PDF forge (<100KB) |
| **Application Automation** | Headless Android WebView + DOM Field Mapping | Assisted ATS form autofill (Greenhouse, Lever, Ashby) |

---

## 6. Coding & Workflow Standards

- **Architecture Pattern**: MVI / Clean Architecture (Presentation -> Domain -> Data) with Kotlin Coroutines and unidirectional data flow (`StateFlow<UiState>`).
- **Dispatchers**:
  - `Dispatchers.Main`: UI and Filament frame triggers.
  - `Dispatchers.IO`: Room queries, Ktor scrapers, and file I/O.
  - `Dispatchers.Default`: AST tokenization, PDF layout computation, ML Kit bitmap preprocessing.
- **Error Handling**: Explicit Kotlin `Result<T>` or sealed `Outcome` classes. Never swallow exceptions or leave unhandled coroutine cancellations.
- **Git Commits**: Conventional commits (`feat:`, `fix:`, `refactor:`, `chore:`, `perf:`).

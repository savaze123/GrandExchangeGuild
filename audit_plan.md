# TASK SPECIFICATION: GrandExchangeGuild Comprehensive Audit & ADB Testing Plan

**Target Device:** Google Pixel 10 Pro Fold (Physical Device via ADB)
**Execution Engine:** Google Antigravity (`agy`)
**Testing Scope:** Build verification, UI Automator E2E tests, Foldable state transitions, and Logcat telemetry.

## Phase 1: Environment & Static Audit
*   **Device Verification:** Execute `adb devices` to ensure the Pixel 10 Pro Fold is connected and authorized. Check device screen state (folded vs. unfolded) via `adb shell dumpsys window`.
*   **Static Code Analysis:** Run `./gradlew lintDebug` and `./gradlew assembleDebug` to verify that the recent Room DB schema changes for the `SkillAltar` and the Jetpack Compose navigation updates compile without deprecation warnings.
*   **Workspace Sync:** Read `GEMINI.md` to map the current Jetpack Compose navigation graph and Room database DAOs before initiating UI tests.

## Phase 2: Automated E2E UI Testing (ADB Driven)
Direct `agy` to use ADB UI Automator to execute the following test matrix directly on the Pixel 10 Pro Fold:
*   **Test 1 (ScribeTower & PDF Verification):** Open `ScribeTower`. Trigger the Forge Scroll PDF generation. Verify that the output strictly adheres to a maximum of 1 page. Inspect the generated document to confirm the flexbox layout bug is fixed and "Tyche Technologies" and "August" do not overlap.
*   **Test 2 (SkillAltar Deduplication):** Upload a mock resume to the `SkillAltar`. Query the Room database via ADB shell to verify that the set-union logic executed correctly, ensuring newly extracted skills were categorized without duplicating existing entries.
*   **Test 3 (3D Hub Navigation & Micro-labels):** Launch the `3D Hub` (Popo's Lookout). Verify that the floating micro-subtitles (e.g., "Job Postings" under Bounty Board) render cleanly without clutter. Simulate multi-touch X/Y delta drag events to verify camera rotation bounds and ensure stable 120Hz framerates.
*   **Test 4 (CourierGuild Pacing & Injection):** Open the `CourierGuild` dashboard. Initiate a mock ATS submission. Verify that the agent maps Name, Email, GitHub, and Location from dynamic profile settings (not hardcoded). Monitor the timeline of the DOM injection to verify that the randomized, slower-than-human throttling delays (2.5s–6.0s) are actively enforcing the pace between field inputs.

## Phase 3: Telemetry, Memory, & Foldable Transitions
*   **Continuity Testing:** While the `CourierGuild` live console is running in the foreground, prompt the user to physically fold and unfold the Pixel 10 Pro Fold. Verify that Jetpack Compose `Configuration` changes are handled gracefully without losing the Live Agent session state, WebView cookies, or the 3D camera vector.
*   **Logcat & Profiling:** Stream `adb logcat | grep -i "GrandExchangeGuild\|CourierGuild\|AndroidRuntime"` to detect silent exceptions, missing OAuth tokens, or HTTP 429 rate-limit errors from target ATS endpoints.
*   **Report Generation:** Compile all passed/failed test assertions, stack traces, and layout hierarchy dumps into a final `audit_report.md` output file.

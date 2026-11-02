# GrandExchangeGuild: Comprehensive Architecture & UI Update Plan

**Target Environment:** Google Pixel 10 Pro Fold (Android 16 / Compose 120Hz)
**Target Application:** `GrandExchangeGuild` (Kotlin, Jetpack Compose, Room DB)
**Execution:** Parse these instructions and execute the necessary code changes across the workspace.

## 1. Auto-Apply Platform Exclusions (LinkedIn Protection)
*   **Objective:** Protect the user's LinkedIn account from anti-bot bans.
*   **Action:** In `CourierAutoApplyDispatcher.kt`, inspect target URLs and job board metadata.
*   **Condition:** If the `job.source` or `applicationUrl` contains "LinkedIn" (ignore case), completely exclude it from the automated application pipeline.
*   **Fallback:** Disable the `Auto-Apply` button for these listings in the `BountyBoard`, mark them as "Manual Apply Only", and route the user to an external browser tab upon click.

## 2. 'Accounts Made by Courier' Credential Store & UI
*   **Database:** Create a `CourierAccountEntity` in Room DB to store: `companyName`, `jobTitle`, `portalUrl`, `emailUsed`, and `passwordEncrypted`. Use AndroidX Security `EncryptedSharedPreferences` or SQLCipher for password storage.
*   **Agent Logic:** Update the Courier agent's logic so that when it hits an account creation gate (e.g., TikTok Careers), it checks for existing credentials for that portal. If none exist, generate a strong password (16+ chars, alphanumeric, symbols), use the user's provided email, create the account, and save the credentials to the database.
*   **UI Integration:** In `CourierGuildScreen.kt`, add a new button labeled **"Accounts made by Courier"**. Place this button directly *above* the `APPLICATION PIPELINE` section. Clicking it must open a bottom sheet or screen displaying the saved credentials, with a toggle-visibility/copy button for the passwords.

## 3. Modular Profile Toggling & ScribeTower Filter
*   **Database Update:** Add an `isActive: Boolean = true` column to the Room entities for `Experience`, `Projects`, `Education`, and `Certifications`. Update DAOs to handle toggle mutations.
*   **UI:** Update the Profile Settings Compose UI to include a toggle switch for every section item.
*   **Resume Engine:** Refactor `ScribeTower` to filter the data (`.filter { it.isActive }`) before generating the PDF. The PDF must strictly remain 1 page long. Zero hallucination policy: do not fabricate honors, companies, or experience under any circumstances.

## 4. 3D Citadel First-Person Navigation
*   **Camera:** Update the 3D Citadel OpenGL/Filament viewport to support a toggleable first-person camera mode.
*   **Controls:** Implement a virtual analog joystick composable using `pointerInput`.
*   **Mapping:** Map the joystick's X/Y delta to the 3D camera's translation and rotation vectors (forward/backward and strafing). Ensure rendering remains stable at 120Hz.

## 5. CourierGuild UI Layout Optimization
*   **Resizing:** Reduce the maximum height and layout weights of the `CourierGuild` section in `CourierGuildScreen.kt`.
*   **Constraints:** Clamp the parent container height so it doesn't push adjacent modules off-screen on the inner folding display. Ensure all internal lists use `LazyColumn` with nested scrolling.

## 6. Execution Breakdown Prompt Injection
*   **Agent Configuration:** Inject the "Execution Breakdown" system prompt into `CourierAgentConfig.kt`.
*   **Output Schema Requirement:** The agent must output: Status, ATS Platform Detected, Mapping Log, Unmapped/Ambiguous Fields, and Failure Analysis & Upgrade Path.
*   **Telemetry:** Route these parsed logs directly to the `Live Autofill Ledger Console` inside the `Live Agent Room`.

## 7. ATS StateFlow & 'Interviewing' Status Fix
*   **Sync Logic:** Audit `CourierRepository.kt` to fix silent failures for Greenhouse/Cloudflare syncing.
*   **State Management:** Replace indefinite loading states with explicit timeout policies (15s max). Implement sealed states for: `Authenticating`, `Polling`, `RateLimited`, `AuthTokenExpired`, and `Failure`.
*   **Error Handling:** Catch HTTP 401, 403, and 429 errors and emit UI warnings instead of hanging on a static screen. Verify the Google SSO payload (Name, Email, GitHub, Location) is dynamically attached prior to POST.

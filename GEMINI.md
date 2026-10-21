# Grand Exchange Career Guild - Fix & Feature Instructions

## Role & Objective
Act as a professional Android application developer. Review and resolve the following layout bugs, broken filters, and feature requests across the app modules. Ensure the app adapts smoothly to both the unfolded wide screen and the folded narrow screen without text overlapping or scrolling getting locked.

---

## 1. Global Header & Screen Adaptation Issues

### Remove Duplicate Google Sign-In Status
* **The Problem:** The "Authorize Google SSO (savazework@gmail.com - Inbox Sync Active)" status box appears twice on the screen—once in the top header and a second time inside the active module area below the navigation tabs.
* **The Fix:** Keep the Google SSO status box in the top header only. Remove the duplicate box from inside the individual module views so it does not repeat.

### Fix Narrow Screen (Folded Mode) Layout & Scrolling
* **The Problem:** When switching the phone to the narrower screen, the app becomes unusable in several tabs:
  * In the **Courier (Ledger)** and **Bounty (Job Radar)** tabs, title text (like "Courier Guild") squishes vertically into a single-letter column (`C - o - u - r - i - e - r`), buttons overlap, and the main content disappears or locks up so you cannot scroll.
* **The Fix:** Make all containers, headers, and lists adjust cleanly to narrower screens. Ensure text wraps normally side-to-side, buttons stack or resize cleanly without overlapping, and vertical scrolling works smoothly on every tab in both screen modes.

---

## 2. Bounty Board (Job Radar)

### Show Job Location and Distance on Job Cards
* **The Problem:** Job cards (like Amazon Web Services or JPMorganChase) show the job title, company name, and source ("LinkedIn Direct"), but they do not show where the job is actually located or how far away it is.
* **The Fix:** Add the city/state location and the calculated distance from the user's zip code (e.g., "New York, NY • 15 mi away") directly onto each job listing card.

### Make the Zip Code, Radius, and Work Mode Filters Actually Filter Results
* **The Problem:** 
  1. When opening the location filter bar, the zip code box starts blank, and typing a zip code (like `11581` or `11365`) has no "Enter" or "Apply" button inside the filter bar to confirm it.
  2. Changing the zip code, picking a different search radius (`10 mi`, `25 mi`, `50 mi`, `100 mi`), or hitting the refresh button reloads the board, but the job count stays stuck at the exact same `34 quests available` with no actual change to the list.
* **The Fix:** 
  * Add a clear way to apply/submit the zip code when typing it in.
  * Connect the zip code, mile radius buttons, and work mode buttons (`All`, `Local`, `Hybrid`, `Remote`) to the actual job list so that changing your location or distance actively filters and updates the jobs shown on screen.

---

## 3. Scribe Tower (ATS Forge)

### Clean Up Repeated "7/8 Page Density" Text
* **The Problem:** The phrase `<100KB Native PDF - 7/8 Page Density` is repeated in the Scribe Tower header, the Native ATS PDF Generator box, and the green verification box, even though the preview bar at the bottom already has a green badge that says `1-Page ATS Benchmark`.
* **The Fix:** Remove the extra `7/8 Page Density` labels so the screen isn't cluttered with repeated technical stats.

### Move Technical File Details into an Info (`i`) Icon & Fix Button Alignment
* **The Problem:** Inside the "Native ATS PDF Generator" card, the long line of text (`Strictly single-column UTF-8 • <100KB • 7/8 page density`) takes up too much room. On the narrower screen, it pushes the yellow "Forge ATS PDF" button into an awkward, stretched vertical box on the right.
* **The Fix:** 
  * Hide the technical details (`Strictly single-column UTF-8 • <100KB`) inside a small clickable information circle icon (`i`), just like the one next to "GUILD MODULES" at the top of the app.
  * Fix the "Forge ATS PDF" button so it keeps a clean, normal button shape on the right side (or stacks neatly) on both wide and narrow screens.

### Check Resume Project Details Against Baseline
* **The Problem:** In the "ATS Single-Column Preview" under `TECHNICAL PROJECTS`, there is an entry for `Relational Database Storage Engine | High-Performance Systems Architecture` (C++ B+ Tree indexing) that may not match the uploaded baseline resume (`Savaze_Final_Resume.docx`) or GitHub projects.
* **The Fix:** Make sure the resume preview strictly pulls real projects and facts from the uploaded baseline resume and verified sources without inventing unrelated projects.

---

## 4. Skill Altar (Matrix)

### Allow Creating Custom Filter Categories
* **The Problem:** When adding a new skill (like `Python`) and choosing `Custom` in the Category dropdown, it just puts the skill into a generic tab literally named "Custom". There is no way to name your own category.
* **The Fix:** When a user selects `Custom` in the category dropdown, show a text box and an "Add Category" button so the user can type in and create their own custom category name, which then appears as a new filter tab along the bar (`All`, `AI & Agents`, `Automation & Web`, `Backend & Data`, plus the user's new custom category).

---

## 5. Courier Guild (Ledger)

### Prevent Duplicate Dispatching on Queued Applications
* **The Problem:** After clicking `Execute Autofill / Dispatch` on a queued job (like Stripe), the live console runs and changes the job status badge from `Queued` to `In-Transit` (updating the top counter from 1 to 2, and then 3 In-Transit), but the yellow `Execute Autofill / Dispatch` button stays active on that same card and lets you click and submit it over and over.
* **The Fix:** Once an application finishes dispatching and moves to `In-Transit` or `Submitted`, disable or change the yellow `Execute Autofill / Dispatch` button (similar to the green `Dispatched & Verified` badge on completed cards) so the same job cannot be dispatched multiple times by mistake.

### Clarify Application Status Tracking
* **The Problem:** The status counters at the top (`Queued`, `In-Transit`, `Submitted`, `Interviewing`) look nice, but it is unclear how jobs move from `In-Transit` to `Submitted` or `Interviewing` or what triggers those updates.
* **The Fix:** Make sure status changes accurately reflect real application progress (or allow manual status updates / clear inbox sync rules) so the user knows exactly how a job moves through each stage.

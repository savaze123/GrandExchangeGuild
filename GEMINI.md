# Grand Exchange Application - Targeted Directives: Resume Integrity & Dynamic Profile Settings

You are an expert AI development agent. Your task is to enforce strict factual boundaries on the ATS resume generator and overhaul the user profile settings to eliminate hardcoded data.

## Part 1: Scribe Tower Resume Integrity (CRITICAL)
* **Strict Anti-Fabrication Guardrail:** The resume generator must *strictly* use factual user data. You are explicitly forbidden from hallucinating or fabricating job experiences, academic honors, or metrics.
* **Remove Specific Hallucinations:** Immediately strip any code or data injecting "Apex Automation Labs" (AAL) or fabricated academic achievements like the "Dean's List". 
* **Data Sourcing:** The resume content must be strictly derived from the user's master resume, verified skills, and their linked GitHub repository.
* **1-Page Strict Limit & Natural Spacing:** The generated PDF must *always* be exactly 1 page long—no more, no less. Adjust the typography gracefully so the factual content naturally fills the page without text overlapping, squishing, or spilling onto a second page.

## Part 2: Dynamic Profile & Flying Nimbus Settings
* **Eliminate Hardcoded Personal Data:** Remove all hardcoded instances of the user's name, email, address, location, GitHub link, and Google SSO credentials from the codebase.
* **Overhaul Nimbus Settings:** Remove the previously requested generic settings from the Kid Gohan/Flying Nimbus menu.
* **Profile Configuration UI:** Rebuild the Nimbus settings into a "Profile & Resume Configuration" menu. Provide input fields for the user to manually enter and save their Name, Email, Address, Location, GitHub, and SSO preferences.
* **Resume Prefill Override:** Ensure the Scribe Tower pulls directly from these Nimbus settings to prefill the resume headers and contact info. This menu must serve as the definitive fallback source of truth if the master resume transcription fails to parse those details correctly.

## Part 3: On-Device ADB Verification
* **Resume Testing:** Compile and deploy the build. Use `adb shell` to generate a resume via the Scribe Tower. Download the generated PDF and verify that it is exactly 1 page, formatting is clean, and absolutely zero fabricated data is present.
* **Settings Testing:** Verify that modifying a field in the Nimbus settings directly updates the corresponding data generated in the next Scribe Tower resume.

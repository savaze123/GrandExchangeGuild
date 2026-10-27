# GrandExchangeGuild - Agent Architecture and Token-Saving Map

## 1. Operational Rules
- No broad scans. Do not run recursive file dumps or read whole directories. Use the file index below to open only the exact file needed.
- Use line-range reads on large files: GuildHubPane.kt, TechRadarScraperImpl.kt, CourierGuildScreen.kt, ScribeTowerScreen.kt.
- Do not run ./gradlew unless the task says to verify compilation or run a named test.
- Make surgical edits. Modify only the composable or function the task names. Do not rename or delete existing files, classes, public functions, or DAO methods. Add overloads instead of changing signatures.
- Database changes need an explicit Room Migration. Never use fallbackToDestructiveMigration.
- Secrets (API keys, tokens) live in local.properties and BuildConfig. Never commit them.

## 2. Hard Invariants
- Dynamic profile only: never hardcode personal data (name, email, address, location, GitHub username). Read it through UserProfileManager.getInstance(context).profile.
- Resume content is data-driven. Ground truth = the user's uploaded base resume (BaseResume) + public GitHub data. Never fabricate or remove employers, titles, dates, degrees, certifications, projects, metrics, or technologies. Tailoring may only select, reorder, and lightly reword existing base bullets. Output is strictly 1 page (pageHeight = 792), single-column UTF-8, under 100KB, native Android PdfDocument.
- Resume previews (ScribeTowerScreen, CourierGuildScreen) and the PDF render from the same model, never from separate hardcoded text.
- Safe CLI (core/cli/): no eval() or shell execution, and an exhaustive when over the sealed CliCommand.
- Job links shown to the user must be verified direct links to a specific posting, and every job must carry its description. Never pad the job list with fake or duplicated entries.

## 3. File Index (app/src/main/java/com/osrs/careerguild/)
- Root and DI: CareerGuildApp.kt, di/AppContainer.kt, di/DatabaseModule.kt, di/NetworkModule.kt, di/LocalAppContainer.kt
- core/auth/GoogleAuthManager.kt: OAuth2 state and Gmail read-only scope
- core/profile/UserProfileManager.kt: reactive StateFlow of UserProfile
- core/location/JobLocationUtils.kt: Haversine distance, ZIP coordinates, radius and mode filters
- core/network/LinkValidator.kt: direct ATS link verification
- core/pdf/ResumeForgePdfGenerator.kt: native 1-page PDF generator
- core/token/TokenGovernor.kt: Gemini API budget guard
- core/vision/AntiGravityScreenShareManager.kt: viewport telemetry
- core/cli/: CliCommand.kt, CliParser.kt, CliDispatcher.kt, CliResult.kt
- data/local/CareerGuildDatabase.kt: SQLCipher Room DB (JobDao.kt, SkillDao.kt, BlockedCompanyDao.kt)
- data/remote/scraper/: JobFeedSource.kt, GeoFilter.kt, TechRadarScraperImpl.kt (large)
- data/repository/: JobRepositoryImpl.kt, SkillRepositoryImpl.kt
- domain/model/: GuildBuilding.kt, JobListing.kt, Skill.kt
- domain/scorer/: TrajectoryScorer.kt, GeminiTrajectoryScorer.kt
- domain/usecase/bountyboard/: FetchAndScoreJobsUseCase.kt, BlockCompanyUseCase.kt, MarkAppliedUseCase.kt
- domain/usecase/skillmatrix/: AddSkillUseCase.kt, RemoveSkillUseCase.kt, ToggleSkillUseCase.kt, SeedDefaultSkillsUseCase.kt
- presentation/: MainActivity.kt, scaffold/GuildScaffold.kt, scaffold/CollapsibleGuildOverlay.kt
- presentation/hub/: GuildHubPane.kt (large), GuildHubPlaceholder.kt
- presentation/dashboard/: DashboardPane.kt
- presentation/dashboard/bountyboard/: BountyBoardScreen.kt, BountyBoardViewModel.kt, BountyBoardUiState.kt, JobListingCard.kt
- presentation/dashboard/scribetower/ScribeTowerScreen.kt (large): resume upload and ATS preview
- presentation/dashboard/skillaltar/: SkillAltarScreen.kt, SkillAltarViewModel.kt, SkillAltarUiState.kt, SkillCard.kt
- presentation/dashboard/courierguild/CourierGuildScreen.kt (large): auto-apply ledger and PDF dialog
- core/resume/: BaseResume.kt, ResumeTextExtractor.kt, ResumeStructurer.kt, BaseResumeRepository.kt
- core/github/GitHubProfileFetcher.kt
- domain/resume/: ResumeTailor.kt, ResumeVerifier.kt, TailoredResume.kt
- core/network/PoliteHttpClient.kt
- data/remote/scraper/sources/: GreenhouseSource.kt, LeverSource.kt, AshbySource.kt, AdzunaSource.kt, RemotiveSource.kt, ArbeitnowSource.kt, CompanyBoards.kt
- core/diagnostics/JobSourceDiagnostics.kt
- presentation/auth/GoogleAuthComponents.kt, common/InfoTooltip.kt, theme/CareerGuildTheme.kt

## 4. Planned Files (agent: move each line into section 3 once the file exists)

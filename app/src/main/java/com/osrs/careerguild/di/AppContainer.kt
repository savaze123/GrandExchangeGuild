package com.osrs.careerguild.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.osrs.careerguild.BuildConfig
import com.osrs.careerguild.core.cli.CliDispatcher
import com.osrs.careerguild.core.cli.CliParser
import com.osrs.careerguild.core.resume.BaseResumeRepository
import com.osrs.careerguild.core.resume.BaseResumeRepositoryImpl
import com.osrs.careerguild.core.resume.ResumeStructurer
import com.osrs.careerguild.core.resume.ResumeTextExtractor
import com.osrs.careerguild.core.token.TokenGovernor
import com.osrs.careerguild.data.remote.scraper.TechRadarScraperImpl
import com.osrs.careerguild.data.repository.JobRepositoryImpl
import com.osrs.careerguild.data.repository.SkillRepositoryImpl
import com.osrs.careerguild.domain.scorer.GeminiTrajectoryScorer
import com.osrs.careerguild.domain.usecase.bountyboard.BlockCompanyUseCase
import com.osrs.careerguild.domain.usecase.bountyboard.FetchAndScoreJobsUseCase
import com.osrs.careerguild.domain.usecase.bountyboard.MarkAppliedUseCase
import com.osrs.careerguild.domain.usecase.skillmatrix.AddSkillUseCase
import com.osrs.careerguild.domain.usecase.skillmatrix.RemoveSkillUseCase
import com.osrs.careerguild.domain.usecase.skillmatrix.SeedDefaultSkillsUseCase
import com.osrs.careerguild.domain.usecase.skillmatrix.ToggleSkillUseCase
import com.osrs.careerguild.presentation.dashboard.bountyboard.BountyBoardViewModel
import com.osrs.careerguild.presentation.dashboard.skillaltar.SkillAltarViewModel

private val Context.dataStore by preferencesDataStore(name = "career_guild_prefs")

/**
 * Manual dependency injection container — the single source of truth for all
 * singleton instances across the app.
 *
 * Lifecycle: created once in [CareerGuildApp.onCreate], held for the process
 * lifetime, accessed via [LocalAppContainer] in Compose.
 *
 * Construction order (bottom → top of dependency tree):
 *   Database → DAOs → Repositories → Use Cases → Dispatcher → ViewModels
 *
 * No reflection, no annotation processing, no code generation.
 * ViewModels are created lazily via factory functions called by Compose's
 * `viewModel(factory = ...)` — they live in the Activity's ViewModelStore.
 */
class AppContainer(context: Context) {

    // ---------------------------------------------------------------------------
    // Infrastructure
    // ---------------------------------------------------------------------------

    private val appContext  = context.applicationContext
    private val db          = DatabaseModule.createDatabase(appContext)
    private val httpClient  = NetworkModule.createHttpClient()
    private val dataStore   = appContext.dataStore

    // ---------------------------------------------------------------------------
    // TokenGovernor — SharedPreferences budget guard for TRAJECTORY_SCORER
    // ---------------------------------------------------------------------------

    val tokenGovernor = TokenGovernor(appContext)

    // ---------------------------------------------------------------------------
    // DAOs
    // ---------------------------------------------------------------------------

    private val skillDao          = db.skillDao()
    private val jobDao            = db.jobDao()
    private val blockedCompanyDao = db.blockedCompanyDao()
    private val baseResumeDao     = db.baseResumeDao()
    val gitHubRepoDao             = db.gitHubRepoDao()

    // ---------------------------------------------------------------------------
    // Repositories (domain interfaces)
    // ---------------------------------------------------------------------------

    val skillRepository      = SkillRepositoryImpl(skillDao)
    val jobRepository        = JobRepositoryImpl(jobDao, blockedCompanyDao)
    val baseResumeRepository : BaseResumeRepository = BaseResumeRepositoryImpl(baseResumeDao)
    val resumeTextExtractor  = ResumeTextExtractor(appContext)
    val resumeStructurer     = ResumeStructurer(tokenGovernor)
    val resumeTailor         = com.osrs.careerguild.domain.resume.ResumeTailor(tokenGovernor)

    // ---------------------------------------------------------------------------
    // Remote / Scraper
    // ---------------------------------------------------------------------------

    private val techRadarScraper = TechRadarScraperImpl(httpClient, blockedCompanyDao)

    // ---------------------------------------------------------------------------
    // AI Scorer (TRAJECTORY_SCORER)
    // ---------------------------------------------------------------------------

    private val trajectoryScorer = GeminiTrajectoryScorer(
        apiKey = BuildConfig.GEMINI_API_KEY,
    )

    // ---------------------------------------------------------------------------
    // Skill Matrix use cases
    // ---------------------------------------------------------------------------

    val addSkillUseCase           = AddSkillUseCase(skillRepository)
    val toggleSkillUseCase        = ToggleSkillUseCase(skillRepository)
    val removeSkillUseCase        = RemoveSkillUseCase(skillRepository)
    val seedDefaultSkillsUseCase  = SeedDefaultSkillsUseCase(skillRepository)

    // ---------------------------------------------------------------------------
    // Bounty Board use cases
    // ---------------------------------------------------------------------------

    private val fetchAndScoreJobsUseCase = FetchAndScoreJobsUseCase(
        scraper         = techRadarScraper,
        scorer          = trajectoryScorer,
        jobRepository   = jobRepository,
        skillRepository = skillRepository,
    )
    private val blockCompanyUseCase = BlockCompanyUseCase(jobRepository)
    private val markAppliedUseCase  = MarkAppliedUseCase(jobRepository)

    // ---------------------------------------------------------------------------
    // SAFE_CLI_ENGINE dispatcher
    // ---------------------------------------------------------------------------

    val cliParser     = CliParser
    val cliDispatcher = CliDispatcher(
        addSkill    = addSkillUseCase,
        toggleSkill = toggleSkillUseCase,
        removeSkill = removeSkillUseCase,
        dataStore   = dataStore,
    )

    // ---------------------------------------------------------------------------
    // ViewModel factories — called by Compose's viewModel(factory = ...)
    // ---------------------------------------------------------------------------

    fun createBountyBoardViewModel() = BountyBoardViewModel(
        jobRepository     = jobRepository,
        skillRepository   = skillRepository,
        fetchAndScoreJobs = fetchAndScoreJobsUseCase,
        blockCompany      = blockCompanyUseCase,
        markApplied       = markAppliedUseCase,
        tokenGovernor     = tokenGovernor,
    )

    fun createSkillAltarViewModel() = SkillAltarViewModel(
        skillRepository       = skillRepository,
        addSkill              = addSkillUseCase,
        toggleSkill           = toggleSkillUseCase,
        removeSkill           = removeSkillUseCase,
        seedDefaultSkills     = seedDefaultSkillsUseCase,
    )
}

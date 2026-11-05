package com.osrs.careerguild

import android.app.Application
import kotlinx.coroutines.launch
import com.google.android.filament.utils.Utils as FilamentUtils
import com.osrs.careerguild.di.AppContainer

/**
 * Application entry point.
 *
 * Initialises:
 *  1. Google Filament native engine (UI_3D_GUILD_HUB) — must happen on main thread.
 *  2. [AppContainer] — manual DI singleton that wires the full dependency graph.
 *
 * [appContainer] is accessed by [MainActivity] to provide [LocalAppContainer]
 * to the Compose tree.
 */
class CareerGuildApp : Application() {

    /** Singleton DI container — lazily safe, but Application.onCreate is always main-thread. */
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // Load SQLCipher native library before Room/AppContainer database creation
        System.loadLibrary("sqlcipher")

        // Filament must be init'd before any Engine instance is created (main thread only)
        FilamentUtils.init()
        com.google.android.filament.gltfio.Gltfio.init()

        // Wire the full dependency graph
        appContainer = AppContainer(this)

        // Task 1 / Task 4: Ingest any pending Forge results on app start safely on Dispatchers.IO
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val pending = appContainer.forgeOutbox.listPendingTaskIds()
                val results = appContainer.forgeInbox.readResults(pending)
                for (item in results) {
                    if (item.result.status == com.osrs.careerguild.core.forge.ForgeResultStatus.SUBMITTED) {
                        val jobId = item.result.taskId.removePrefix("forge_").substringBefore("_")
                        appContainer.markAppliedUseCase(jobId)
                    }
                    appContainer.forgeOutbox.deleteTaskFolder(item.result.taskId)
                    appContainer.forgeInbox.deleteResultFile(item.resultFile)
                }
            } catch (t: Throwable) {
                android.util.Log.w("CareerGuildApp", "Forge startup ingest warning: ${t.message}")
            }
        }
    }
}

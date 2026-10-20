package com.osrs.careerguild

import android.app.Application
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
    }
}

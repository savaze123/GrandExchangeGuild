package com.osrs.careerguild.core.profile

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dynamic User Profile & Resume Configuration entity.
 * Eliminates all hardcoded personal data and acts as the single source of truth
 * for candidate identity, contact information, and SSO preferences.
 */
data class UserProfile(
    val name: String = "",
    val email: String = "",
    val address: String = "",
    val location: String = "",
    val github: String = "",
    val ssoPreferred: Boolean = false,
    val githubUsername: String = "",
) {
    val effectiveGithubUsername: String
        get() = if (githubUsername.isNotBlank()) {
            githubUsername.trim()
        } else {
            github.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("github.com/")
                .removeSuffix("/")
        }
}

/**
 * UserProfileManager — Thread-safe persistent profile manager backed by SharedPreferences.
 * Provides a reactive StateFlow so that modifications in the Kid Gohan / Flying Nimbus
 * settings menu immediately propagate to Scribe Tower, Courier Guild, and PDF generation.
 */
class UserProfileManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("osrs_user_profile_vault", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(loadProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private fun loadProfile(): UserProfile {
        return UserProfile(
            name = prefs.getString(KEY_NAME, "") ?: "",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            address = prefs.getString(KEY_ADDRESS, "") ?: "",
            location = prefs.getString(KEY_LOCATION, "") ?: "",
            github = prefs.getString(KEY_GITHUB, "") ?: "",
            ssoPreferred = prefs.getBoolean(KEY_SSO_PREF, false),
            githubUsername = prefs.getString(KEY_GITHUB_USERNAME, "") ?: "",
        )
    }

    fun saveProfile(profile: UserProfile) {
        prefs.edit()
            .putString(KEY_NAME, profile.name.trim())
            .putString(KEY_EMAIL, profile.email.trim())
            .putString(KEY_ADDRESS, profile.address.trim())
            .putString(KEY_LOCATION, profile.location.trim())
            .putString(KEY_GITHUB, profile.github.trim())
            .putString(KEY_GITHUB_USERNAME, profile.githubUsername.trim())
            .putBoolean(KEY_SSO_PREF, profile.ssoPreferred)
            .apply()
        _profile.value = profile
    }

    companion object {
        private const val KEY_NAME = "profile_name"
        private const val KEY_EMAIL = "profile_email"
        private const val KEY_ADDRESS = "profile_address"
        private const val KEY_LOCATION = "profile_location"
        private const val KEY_GITHUB = "profile_github"
        private const val KEY_GITHUB_USERNAME = "profile_github_username"
        private const val KEY_SSO_PREF = "profile_sso_pref"

        @Volatile
        private var instance: UserProfileManager? = null

        fun getInstance(context: Context): UserProfileManager =
            instance ?: synchronized(this) {
                instance ?: UserProfileManager(context.applicationContext).also { instance = it }
            }
    }
}

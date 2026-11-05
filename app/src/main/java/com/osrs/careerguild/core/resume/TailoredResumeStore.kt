package com.osrs.careerguild.core.resume

import android.content.Context
import com.osrs.careerguild.domain.resume.TailoredResume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Stores and retrieves verified [TailoredResume] objects keyed by jobId.
 * Persists to disk under tailored_resumes/ and maintains an in-memory cache.
 */
class TailoredResumeStore(
    private val context: Context? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    },
    private val baseDirOverride: File? = null,
) {
    private val memoryCache = ConcurrentHashMap<String, TailoredResume>()

    fun getStorageDir(): File {
        val base = baseDirOverride
            ?: context?.getExternalFilesDir("tailored_resumes")
            ?: context?.filesDir?.let { File(it, "tailored_resumes") }
            ?: File("build/tailored_resumes")
        if (!base.exists()) {
            base.mkdirs()
        }
        return base
    }

    suspend fun saveTailoredResume(jobId: String, tailoredResume: TailoredResume): File = withContext(Dispatchers.IO) {
        memoryCache[jobId] = tailoredResume
        val dir = getStorageDir()
        val safeId = jobId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(dir, "tailored_${safeId}.json")
        file.writeText(json.encodeToString(tailoredResume), Charsets.UTF_8)
        file
    }

    suspend fun getTailoredResume(jobId: String): TailoredResume? = withContext(Dispatchers.IO) {
        memoryCache[jobId]?.let { return@withContext it }
        val dir = getStorageDir()
        val safeId = jobId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(dir, "tailored_${safeId}.json")
        if (file.exists()) {
            runCatching {
                val resume = json.decodeFromString<TailoredResume>(file.readText(Charsets.UTF_8))
                memoryCache[jobId] = resume
                resume
            }.getOrNull()
        } else {
            null
        }
    }

    fun hasTailoredResume(jobId: String): Boolean {
        if (memoryCache.containsKey(jobId)) return true
        val dir = getStorageDir()
        val safeId = jobId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(dir, "tailored_${safeId}.json").exists()
    }
}

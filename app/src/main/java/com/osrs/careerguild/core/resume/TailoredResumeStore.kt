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
    private val rejectSyntheticFixtures: Boolean = true,
) {
    private val memoryCache = ConcurrentHashMap<String, TailoredResume>()

    private fun isSyntheticContaminated(raw: String): Boolean {
        if (!rejectSyntheticFixtures) return false
        val lower = raw.lowercase()
        return lower.contains("@synthetic.invalid") ||
            lower.contains("syntheticcloud") ||
            lower.contains("synthetic state university") ||
            lower.contains("alexmercer-synth")
    }

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
        memoryCache[jobId]?.let { cached ->
            val encoded = runCatching { json.encodeToString(cached) }.getOrDefault("")
            if (isSyntheticContaminated(encoded)) {
                memoryCache.remove(jobId)
            } else {
                return@withContext cached
            }
        }
        val dir = getStorageDir()
        val safeId = jobId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(dir, "tailored_${safeId}.json")
        if (file.exists()) {
            runCatching {
                val raw = file.readText(Charsets.UTF_8)
                if (isSyntheticContaminated(raw)) {
                    file.delete()
                    return@runCatching null
                }
                val resume = json.decodeFromString<TailoredResume>(raw)
                memoryCache[jobId] = resume
                resume
            }.getOrNull()
        } else {
            null
        }
    }

    fun deleteTailoredResume(jobId: String) {
        memoryCache.remove(jobId)
        val dir = getStorageDir()
        val safeId = jobId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        File(dir, "tailored_${safeId}.json").delete()
    }

    fun hasTailoredResume(jobId: String): Boolean {
        if (memoryCache.containsKey(jobId)) return true
        val dir = getStorageDir()
        val safeId = jobId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(dir, "tailored_${safeId}.json").exists()
    }
}

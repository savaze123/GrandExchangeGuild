package com.osrs.careerguild.core.resume

import com.osrs.careerguild.data.local.dao.BaseResumeDao
import com.osrs.careerguild.data.local.entity.BaseResumeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository interface managing candidate BaseResume ground truth.
 */
interface BaseResumeRepository {
    fun observeBaseResume(): Flow<BaseResume?>
    fun observeRawText(): Flow<String?>
    suspend fun getBaseResume(): BaseResume?
    suspend fun getRawText(): String?
    suspend fun saveConfirmedResume(rawText: String, resume: BaseResume)
    suspend fun clear()
}

/**
 * Concrete implementation storing confirmed BaseResume and raw extracted text
 * in encrypted SQLCipher Room storage.
 */
class BaseResumeRepositoryImpl(
    private val dao: BaseResumeDao,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true },
    private val rejectSyntheticFixtures: Boolean = true,
) : BaseResumeRepository {

    private fun isSyntheticContaminated(text: String?): Boolean {
        if (!rejectSyntheticFixtures || text.isNullOrBlank()) return false
        val lower = text.lowercase()
        return lower.contains("@synthetic.invalid") ||
            lower.contains("syntheticcloud") ||
            lower.contains("synthetic state university") ||
            lower.contains("alexmercer-synth")
    }

    override fun observeBaseResume(): Flow<BaseResume?> {
        return dao.observeBaseResume().map { entity ->
            if (entity == null) return@map null
            if (isSyntheticContaminated(entity.confirmedJson) || isSyntheticContaminated(entity.rawText)) {
                null
            } else {
                entity.confirmedJson.let { rawJson ->
                    runCatching { json.decodeFromString<BaseResume>(rawJson) }.getOrNull()
                }
            }
        }
    }

    override fun observeRawText(): Flow<String?> {
        return dao.observeBaseResume().map { entity ->
            if (entity == null) return@map null
            if (isSyntheticContaminated(entity.rawText) || isSyntheticContaminated(entity.confirmedJson)) {
                null
            } else {
                entity.rawText
            }
        }
    }

    override suspend fun getBaseResume(): BaseResume? {
        val entity = dao.getBaseResume() ?: return null
        if (isSyntheticContaminated(entity.confirmedJson) || isSyntheticContaminated(entity.rawText)) {
            dao.deleteBaseResume()
            return null
        }
        return runCatching { json.decodeFromString<BaseResume>(entity.confirmedJson) }.getOrNull()
    }

    override suspend fun getRawText(): String? {
        val entity = dao.getBaseResume() ?: return null
        if (isSyntheticContaminated(entity.rawText) || isSyntheticContaminated(entity.confirmedJson)) {
            dao.deleteBaseResume()
            return null
        }
        return entity.rawText
    }

    override suspend fun saveConfirmedResume(rawText: String, resume: BaseResume) {
        val jsonStr = json.encodeToString(resume)
        val entity = BaseResumeEntity(
            id = "primary",
            rawText = rawText,
            confirmedJson = jsonStr,
            updatedAt = System.currentTimeMillis(),
        )
        dao.saveBaseResume(entity)
    }

    override suspend fun clear() {
        dao.deleteBaseResume()
    }
}

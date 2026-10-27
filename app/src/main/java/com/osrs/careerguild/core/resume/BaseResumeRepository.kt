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
) : BaseResumeRepository {

    override fun observeBaseResume(): Flow<BaseResume?> {
        return dao.observeBaseResume().map { entity ->
            entity?.confirmedJson?.let { rawJson ->
                runCatching { json.decodeFromString<BaseResume>(rawJson) }.getOrNull()
            }
        }
    }

    override fun observeRawText(): Flow<String?> {
        return dao.observeBaseResume().map { it?.rawText }
    }

    override suspend fun getBaseResume(): BaseResume? {
        val entity = dao.getBaseResume() ?: return null
        return runCatching { json.decodeFromString<BaseResume>(entity.confirmedJson) }.getOrNull()
    }

    override suspend fun getRawText(): String? {
        return dao.getBaseResume()?.rawText
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

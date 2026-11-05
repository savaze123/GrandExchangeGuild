package com.osrs.careerguild.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.osrs.careerguild.data.local.dao.ApplicantAnswerDao
import com.osrs.careerguild.data.local.dao.BaseResumeDao
import com.osrs.careerguild.data.local.dao.BlockedCompanyDao
import com.osrs.careerguild.data.local.dao.CourierAccountDao
import com.osrs.careerguild.data.local.dao.CourierTaskDao
import com.osrs.careerguild.data.local.dao.FieldMappingCacheDao
import com.osrs.careerguild.data.local.dao.GitHubRepoCacheDao
import com.osrs.careerguild.data.local.dao.GitHubRepoDao
import com.osrs.careerguild.data.local.dao.JobDao
import com.osrs.careerguild.data.local.dao.SkillDao
import com.osrs.careerguild.data.local.entity.ApplicantAnswerEntity
import com.osrs.careerguild.data.local.entity.BaseResumeEntity
import com.osrs.careerguild.data.local.entity.BlockedCompanyEntity
import com.osrs.careerguild.data.local.entity.CourierAccountEntity
import com.osrs.careerguild.data.local.entity.CourierTaskEntity
import com.osrs.careerguild.data.local.entity.FieldMappingCacheEntity
import com.osrs.careerguild.data.local.entity.GitHubRepoCacheEntity
import com.osrs.careerguild.data.local.entity.GitHubRepoEntity
import com.osrs.careerguild.data.local.entity.JobEntity
import com.osrs.careerguild.data.local.entity.SkillEntity

/**
 * LOCAL_VAULT — Room database encrypted with SQLCipher.
 *
 * Instantiated at runtime via:
 * ```kotlin
 * Room.databaseBuilder(context, CareerGuildDatabase::class.java, "career_guild.db")
 *     .openHelperFactory(SupportFactory(passphrase))
 *     .build()
 * ```
 * where `passphrase` is retrieved from Android Keystore, never hard-coded.
 *
 * exportSchema = true so migration SQL files are generated into
 * `app/schemas/` and can be code-reviewed and committed.
 *
 * Node: LOCAL_VAULT
 */
@Database(
    entities = [
        SkillEntity::class,
        JobEntity::class,
        BlockedCompanyEntity::class,
        BaseResumeEntity::class,
        GitHubRepoEntity::class,
        CourierAccountEntity::class,
        ApplicantAnswerEntity::class,
        CourierTaskEntity::class,
        GitHubRepoCacheEntity::class,
        FieldMappingCacheEntity::class,
    ],
    version      = 6,
    exportSchema = true,
)
abstract class CareerGuildDatabase : RoomDatabase() {

    abstract fun skillDao(): SkillDao
    abstract fun jobDao(): JobDao
    abstract fun blockedCompanyDao(): BlockedCompanyDao
    abstract fun baseResumeDao(): BaseResumeDao
    abstract fun gitHubRepoDao(): GitHubRepoDao
    abstract fun courierAccountDao(): CourierAccountDao
    abstract fun applicantAnswerDao(): ApplicantAnswerDao
    abstract fun courierTaskDao(): CourierTaskDao
    abstract fun gitHubRepoCacheDao(): GitHubRepoCacheDao
    abstract fun fieldMappingCacheDao(): FieldMappingCacheDao

    companion object {
        init {
            try {
                System.loadLibrary("sqlcipher")
            } catch (t: Throwable) {
                android.util.Log.w("CareerGuildDB", "SQLCipher loadLibrary warning: ${t.message}")
            }
        }
    }
}

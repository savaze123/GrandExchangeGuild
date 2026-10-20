package com.osrs.careerguild.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.osrs.careerguild.data.local.dao.BlockedCompanyDao
import com.osrs.careerguild.data.local.dao.JobDao
import com.osrs.careerguild.data.local.dao.SkillDao
import com.osrs.careerguild.data.local.entity.BlockedCompanyEntity
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
    ],
    version      = 1,
    exportSchema = true,
)
abstract class CareerGuildDatabase : RoomDatabase() {

    abstract fun skillDao(): SkillDao
    abstract fun jobDao(): JobDao
    abstract fun blockedCompanyDao(): BlockedCompanyDao

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

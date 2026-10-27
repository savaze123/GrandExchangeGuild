package com.osrs.careerguild.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.osrs.careerguild.data.local.entity.BaseResumeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for encrypted BaseResume storage in SQLCipher Room.
 */
@Dao
interface BaseResumeDao {

    @Query("SELECT * FROM base_resumes WHERE id = :id LIMIT 1")
    fun observeBaseResume(id: String = "primary"): Flow<BaseResumeEntity?>

    @Query("SELECT * FROM base_resumes WHERE id = :id LIMIT 1")
    suspend fun getBaseResume(id: String = "primary"): BaseResumeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBaseResume(entity: BaseResumeEntity)

    @Query("DELETE FROM base_resumes WHERE id = :id")
    suspend fun deleteBaseResume(id: String = "primary")
}

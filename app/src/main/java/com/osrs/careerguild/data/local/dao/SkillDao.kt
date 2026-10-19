package com.osrs.careerguild.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.osrs.careerguild.data.local.entity.SkillEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for DYNAMIC_SKILL_MATRIX operations.
 *
 * All queries are parameterised — no string interpolation into SQL.
 * Dispatcher: [Dispatchers.IO]
 *
 * Node: LOCAL_VAULT (DYNAMIC_SKILL_MATRIX storage)
 */
@Dao
interface SkillDao {

    @Query("SELECT * FROM skills ORDER BY category ASC, name ASC")
    fun observeAll(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills WHERE is_active = 1 ORDER BY name ASC")
    fun observeActive(): Flow<List<SkillEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(skill: SkillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(skills: List<SkillEntity>)

    @Query("UPDATE skills SET is_active = NOT is_active WHERE name = :name")
    suspend fun toggleActive(name: String)

    @Query("DELETE FROM skills WHERE name = :name")
    suspend fun delete(name: String)
}

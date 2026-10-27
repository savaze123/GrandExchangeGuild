package com.osrs.careerguild.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.osrs.careerguild.data.local.entity.GitHubRepoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GitHubRepoDao {

    @Query("SELECT * FROM github_repos ORDER BY pushed_at DESC")
    fun observeAll(): Flow<List<GitHubRepoEntity>>

    @Query("SELECT * FROM github_repos ORDER BY pushed_at DESC")
    suspend fun getAll(): List<GitHubRepoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(repos: List<GitHubRepoEntity>)

    @Query("DELETE FROM github_repos")
    suspend fun clear()
}

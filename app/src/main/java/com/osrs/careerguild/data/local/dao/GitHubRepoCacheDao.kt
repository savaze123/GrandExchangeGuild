package com.osrs.careerguild.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.osrs.careerguild.data.local.entity.GitHubRepoCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GitHubRepoCacheDao {

    @Query("SELECT * FROM github_repo_cache ORDER BY pushed_at DESC")
    fun observeAll(): Flow<List<GitHubRepoCacheEntity>>

    @Query("SELECT * FROM github_repo_cache ORDER BY pushed_at DESC")
    suspend fun getAll(): List<GitHubRepoCacheEntity>

    @Query("SELECT * FROM github_repo_cache WHERE LOWER(username) = LOWER(:username) ORDER BY pushed_at DESC")
    suspend fun getByUsername(username: String): List<GitHubRepoCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(repos: List<GitHubRepoCacheEntity>)

    @Query("DELETE FROM github_repo_cache WHERE LOWER(username) = LOWER(:username)")
    suspend fun deleteByUsername(username: String)

    @Query("DELETE FROM github_repo_cache")
    suspend fun clear()
}

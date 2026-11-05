package com.osrs.careerguild.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.osrs.careerguild.data.local.entity.CourierTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourierTaskDao {

    @Query("SELECT * FROM courier_task ORDER BY created_at DESC")
    fun observeAll(): Flow<List<CourierTaskEntity>>

    @Query("SELECT * FROM courier_task ORDER BY created_at DESC")
    suspend fun getAll(): List<CourierTaskEntity>

    @Query("SELECT * FROM courier_task WHERE task_id = :taskId LIMIT 1")
    suspend fun getById(taskId: String): CourierTaskEntity?

    @Query("SELECT * FROM courier_task WHERE task_id = :taskId LIMIT 1")
    fun observeById(taskId: String): Flow<CourierTaskEntity?>

    @Query("SELECT * FROM courier_task WHERE job_id = :jobId ORDER BY created_at DESC")
    suspend fun getByJobId(jobId: String): List<CourierTaskEntity>

    @Query("SELECT COUNT(*) FROM courier_task WHERE job_id = :jobId AND status NOT IN ('CANCELLED', 'FAILED')")
    suspend fun countOpenOrSubmittedForJob(jobId: String): Int

    @Query(
        "SELECT * FROM courier_task " +
            "WHERE status = 'QUEUED' " +
            "OR (status = 'SNOOZED' AND snoozed_until IS NOT NULL AND snoozed_until <= :nowMs) " +
            "ORDER BY created_at ASC"
    )
    suspend fun getRunnableTasks(nowMs: Long = System.currentTimeMillis()): List<CourierTaskEntity>

    @Query(
        "SELECT COUNT(*) FROM courier_task " +
            "WHERE status = 'SUBMITTED' AND updated_at >= :sinceMs"
    )
    suspend fun countSubmittedSince(sinceMs: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: CourierTaskEntity)

    @Update
    suspend fun update(task: CourierTaskEntity)

    @Query(
        "UPDATE courier_task " +
            "SET status = :status, step = :step, current_url = :currentUrl, " +
            "filled_fields_json = :filledFieldsJson, updated_at = :updatedAt " +
            "WHERE task_id = :taskId"
    )
    suspend fun checkpointStep(
        taskId: String,
        status: String,
        step: String,
        currentUrl: String,
        filledFieldsJson: String,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE courier_task " +
            "SET status = :status, blocked_reason = :blockedReason, " +
            "blocked_fields_json = :blockedFieldsJson, current_url = :currentUrl, " +
            "last_error = :lastError, updated_at = :updatedAt " +
            "WHERE task_id = :taskId"
    )
    suspend fun updateStatusAndBlock(
        taskId: String,
        status: String,
        blockedReason: String?,
        blockedFieldsJson: String = "[]",
        currentUrl: String = "",
        lastError: String? = null,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE courier_task " +
            "SET status = 'SNOOZED', snoozed_until = :snoozedUntil, updated_at = :updatedAt " +
            "WHERE task_id = :taskId"
    )
    suspend fun markSnoozed(
        taskId: String,
        snoozedUntil: Long,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM courier_task WHERE task_id = :taskId")
    suspend fun deleteById(taskId: String)
}

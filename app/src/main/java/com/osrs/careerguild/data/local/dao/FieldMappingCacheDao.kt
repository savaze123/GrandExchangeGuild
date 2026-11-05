package com.osrs.careerguild.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.osrs.careerguild.data.local.entity.FieldMappingCacheEntity

@Dao
interface FieldMappingCacheDao {

    @Query(
        "SELECT * FROM field_mapping_cache " +
            "WHERE LOWER(host) = LOWER(:host) AND normalized_label = :normalizedLabel " +
            "LIMIT 1"
    )
    suspend fun findMapping(host: String, normalizedLabel: String): FieldMappingCacheEntity?

    @Query("SELECT * FROM field_mapping_cache WHERE LOWER(host) = LOWER(:host)")
    suspend fun getMappingsForHost(host: String): List<FieldMappingCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMapping(entity: FieldMappingCacheEntity)

    @Query("DELETE FROM field_mapping_cache")
    suspend fun clear()
}

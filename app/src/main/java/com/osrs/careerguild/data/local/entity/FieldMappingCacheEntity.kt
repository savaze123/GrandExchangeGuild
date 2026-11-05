package com.osrs.careerguild.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Room table `field_mapping_cache` mapping `(host, normalized_label)` to a canonical payload key.
 * Caches ONLY payload keys (e.g. `first_name`, `linkedin_url`, `work_authorization`),
 * NEVER free-text answers or personal values.
 */
@Entity(
    tableName = "field_mapping_cache",
    primaryKeys = ["host", "normalized_label"],
)
data class FieldMappingCacheEntity(
    @ColumnInfo(name = "host")
    val host: String,

    @ColumnInfo(name = "normalized_label")
    val normalizedLabel: String,

    @ColumnInfo(name = "payload_key")
    val payloadKey: String,

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
)

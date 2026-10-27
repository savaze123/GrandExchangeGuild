package com.osrs.careerguild.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached public GitHub repository entity for candidate portfolio context.
 * Cache TTL: 6 hours.
 */
@Entity(tableName = "github_repos")
data class GitHubRepoEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "html_url")
    val htmlUrl: String,

    @ColumnInfo(name = "topics")
    val topics: String, // comma-separated

    @ColumnInfo(name = "languages")
    val languages: String, // comma-separated

    @ColumnInfo(name = "pushed_at")
    val pushedAt: String?,

    @ColumnInfo(name = "readme_excerpt")
    val readmeExcerpt: String?,

    @ColumnInfo(name = "etag")
    val etag: String?,

    @ColumnInfo(name = "cached_at")
    val cachedAt: Long = System.currentTimeMillis(),
)

fun GitHubRepoEntity.toDomain() = com.osrs.careerguild.core.github.GitHubRepo(
    id = id,
    name = name,
    description = description,
    htmlUrl = htmlUrl,
    topics = if (topics.isBlank()) emptyList() else topics.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    languages = if (languages.isBlank()) emptyList() else languages.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    pushedAt = pushedAt,
    readmeExcerpt = readmeExcerpt,
)

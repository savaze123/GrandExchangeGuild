package com.osrs.careerguild.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.osrs.careerguild.core.github.GitHubRepo

/**
 * Room table `github_repo_cache` storing REST-fetched non-fork, non-archived repositories
 * with README excerpts (up to 1500 chars for the 8 most recent) and ETag metadata.
 */
@Entity(tableName = "github_repo_cache")
data class GitHubRepoCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "repo_id")
    val repoId: Long,

    @ColumnInfo(name = "username", defaultValue = "''")
    val username: String,

    @ColumnInfo(name = "name", defaultValue = "''")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "html_url", defaultValue = "''")
    val htmlUrl: String,

    @ColumnInfo(name = "topics", defaultValue = "''")
    val topics: String,

    @ColumnInfo(name = "languages", defaultValue = "''")
    val languages: String,

    @ColumnInfo(name = "pushed_at")
    val pushedAt: String?,

    @ColumnInfo(name = "readme_excerpt")
    val readmeExcerpt: String?,

    @ColumnInfo(name = "etag")
    val etag: String?,

    @ColumnInfo(name = "cached_at", defaultValue = "0")
    val cachedAt: Long = System.currentTimeMillis(),
) {
    fun toDomain(): GitHubRepo = GitHubRepo(
        id = repoId,
        name = name,
        description = description,
        htmlUrl = htmlUrl,
        topics = if (topics.isBlank()) emptyList() else topics.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        languages = if (languages.isBlank()) emptyList() else languages.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        pushedAt = pushedAt,
        readmeExcerpt = readmeExcerpt,
    )
}

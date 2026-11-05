package com.osrs.careerguild.core.forge

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class ForgeInbox(
    private val context: Context? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = false // strict decoding per prompt
        isLenient = false
    },
    private val baseDirOverride: File? = null,
) {
    private val tag = "ForgeInbox"

    private fun logWarning(msg: String) {
        try {
            android.util.Log.w(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    fun getInboxDir(): File {
        val base = baseDirOverride
            ?: context?.getExternalFilesDir("forge_inbox")
            ?: context?.filesDir?.let { File(it, "forge_inbox") }
            ?: File("build/forge_inbox")
        if (!base.exists()) {
            base.mkdirs()
        }
        return base
    }

    data class ReceivedResult(
        val result: ForgeResult,
        val resultFile: File,
    )

    /**
     * Reads all .json files in forge_inbox, strictly parses them as [ForgeResult],
     * validates the schema matches "forge_result.v1", and verifies that [knownTaskIds] contains the task_id.
     * Any result whose task_id is unknown or whose schema is wrong is ignored.
     */
    suspend fun readResults(knownTaskIds: Set<String>): List<ReceivedResult> = withContext(Dispatchers.IO) {
        val inbox = getInboxDir()
        val jsonFiles = inbox.listFiles()?.filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
            ?: emptyList()

        val results = mutableListOf<ReceivedResult>()

        for (file in jsonFiles) {
            try {
                val content = file.readText(Charsets.UTF_8)
                val parsed = json.decodeFromString<ForgeResult>(content)

                if (parsed.schema != ForgeResult.SCHEMA_V1) {
                    logWarning("Ignoring result file ${file.name}: invalid schema '${parsed.schema}', expected '${ForgeResult.SCHEMA_V1}'")
                    continue
                }

                if (parsed.taskId !in knownTaskIds) {
                    logWarning("Ignoring result file ${file.name}: unknown task_id '${parsed.taskId}'")
                    continue
                }

                results.add(ReceivedResult(result = parsed, resultFile = file))
            } catch (e: Exception) {
                logWarning("Ignoring malformed result in file ${file.name}: ${e.message}")
            }
        }

        results
    }

    suspend fun deleteResultFile(file: File): Boolean = withContext(Dispatchers.IO) {
        if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
}

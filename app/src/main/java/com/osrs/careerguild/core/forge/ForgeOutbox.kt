package com.osrs.careerguild.core.forge

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ForgeOutbox(
    private val context: Context? = null,
    private val json: Json = Json {
        encodeDefaults = true
        prettyPrint = true
    },
    private val baseDirOverride: File? = null,
) {
    fun getOutboxDir(): File {
        val base = baseDirOverride
            ?: context?.getExternalFilesDir("forge_outbox")
            ?: context?.filesDir?.let { File(it, "forge_outbox") }
            ?: File("build/forge_outbox")
        if (!base.exists()) {
            base.mkdirs()
        }
        return base
    }

    suspend fun writeTask(task: ForgeTask, pdfSourceFile: File): File = withContext(Dispatchers.IO) {
        val outbox = getOutboxDir()
        val taskDir = File(outbox, task.taskId)
        if (!taskDir.exists()) {
            taskDir.mkdirs()
        }

        val taskJsonFile = File(taskDir, "task.json")
        val jsonString = json.encodeToString(task)
        taskJsonFile.writeText(jsonString, Charsets.UTF_8)

        val targetPdf = File(taskDir, "resume.pdf")
        pdfSourceFile.copyTo(targetPdf, overwrite = true)

        taskJsonFile
    }

    suspend fun listPendingTaskIds(): Set<String> = withContext(Dispatchers.IO) {
        val outbox = getOutboxDir()
        val dirs = outbox.listFiles { file -> file.isDirectory } ?: emptyArray()
        dirs.map { it.name }.toSet()
    }

    suspend fun deleteTaskFolder(taskId: String): Boolean = withContext(Dispatchers.IO) {
        val outbox = getOutboxDir()
        val taskDir = File(outbox, taskId)
        if (taskDir.exists()) {
            taskDir.deleteRecursively()
        } else {
            false
        }
    }
}

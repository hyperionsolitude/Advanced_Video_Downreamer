package com.downloadmanager.app.storage

import android.content.SharedPreferences
import com.downloadmanager.app.model.DownloadCheckpoint

object CheckpointStorage {
    private const val KEY_CHECKPOINTS = "download_checkpoints"
    private const val FIELD_SEPARATOR = "|"
    private const val CHECKPOINT_FIELDS = 5

    fun saveCheckpoints(
        prefs: SharedPreferences,
        checkpoints: Map<String, DownloadCheckpoint>,
    ) {
        val checkpointsJson = checkpoints.values.joinToString(FIELD_SEPARATOR) { checkpoint ->
            listOf(
                checkpoint.url,
                checkpoint.fileName,
                checkpoint.downloadedBytes.toString(),
                checkpoint.totalBytes.toString(),
                checkpoint.timestamp.toString()
            ).joinToString(FIELD_SEPARATOR)
        }
        prefs.edit().putString(KEY_CHECKPOINTS, checkpointsJson).apply()
    }

    fun restoreCheckpoints(prefs: SharedPreferences): MutableMap<String, DownloadCheckpoint> {
        val restored = mutableMapOf<String, DownloadCheckpoint>()
        val checkpointsJson = prefs.getString(KEY_CHECKPOINTS, "") ?: ""
        if (checkpointsJson.isEmpty()) return restored

        val parts = checkpointsJson.split(FIELD_SEPARATOR).filter { it.isNotEmpty() }
        for (index in parts.indices step CHECKPOINT_FIELDS) {
            if (index + CHECKPOINT_FIELDS - 1 >= parts.size) break
            try {
                val checkpoint = DownloadCheckpoint(
                    url = parts[index],
                    fileName = parts[index + 1],
                    downloadedBytes = parts[index + 2].toLong(),
                    totalBytes = parts[index + 3].toLong(),
                    timestamp = parts[index + 4].toLong()
                )
                restored[checkpoint.url] = checkpoint
            } catch (_: NumberFormatException) {
                // Skip malformed records
            }
        }
        return restored
    }
}

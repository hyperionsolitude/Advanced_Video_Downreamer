package com.downloadmanager.app.helpers

import com.downloadmanager.app.adapter.FileAdapter
import com.downloadmanager.app.model.DownloadCheckpoint
import com.downloadmanager.app.model.DownloadFile
import com.downloadmanager.app.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DownloadRunnerConfig(
    val context: android.content.Context,
    val downloadScope: CoroutineScope,
    val downloadCheckpoints: MutableMap<String, DownloadCheckpoint>,
    val activeDownloads: MutableSet<String>,
    val saveCheckpoints: () -> Unit,
    val downloadFileHandler: DownloadFileHandler,
    val showSnackbar: (String) -> Unit,
)

class DownloadRunner(
    private val config: DownloadRunnerConfig,
) {
    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_BASE_MS = 1_500L
    }

    fun launchDownload(file: DownloadFile, adapter: FileAdapter?) {
        adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.STARTED)
        ensureCheckpointExists(file)
        config.saveCheckpoints()
        config.downloadScope.launch { runDownloadWithRetries(file, adapter) }
    }

    private fun ensureCheckpointExists(file: DownloadFile) {
        if (config.downloadCheckpoints[file.url] != null) return
        val checkpoint = DownloadCheckpoint(
            url = file.url,
            fileName = file.name.ifEmpty { file.url.substringAfterLast("/") },
            downloadedBytes = 0L,
            totalBytes = file.getExpectedSize() ?: 0L
        )
        config.downloadCheckpoints[file.url] = checkpoint
    }

    private suspend fun runDownloadWithRetries(
        file: DownloadFile,
        adapter: FileAdapter?,
    ) {
        var attempt = 0
        while (attempt <= MAX_RETRY_ATTEMPTS) {
            try {
                config.downloadFileHandler.downloadFile(file)
                return
            } catch (e: SecurityException) {
                handleNonFatalError(file, adapter, e)
            } catch (e: java.io.IOException) {
                handleNonFatalError(file, adapter, e)
            } catch (e: IllegalArgumentException) {
                handleNonFatalError(file, adapter, e)
            }

            // Exponential backoff before retrying
            attempt += 1
            if (attempt <= MAX_RETRY_ATTEMPTS) {
                val delayMs = RETRY_DELAY_BASE_MS * attempt
                kotlinx.coroutines.delay(delayMs)
            } else {
                // Out of retries – keep checkpoint for resume on next start and mark paused
                withContext(Dispatchers.Main) {
                    adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.PAUSED)
                    adapter?.flushProgressUpdates()
                }
                config.saveCheckpoints()
                return
            }
        }
    }

    private suspend fun handleNonFatalError(
        file: DownloadFile,
        adapter: FileAdapter?,
        throwable: Throwable,
    ) {
        Logger.w(
            "DownloadRunner",
            "Transient error for ${file.name}: ${throwable.message}",
            throwable
        )
        // Do not surface transient retry to UI; keep state as-is and continue
        withContext(Dispatchers.Main) { adapter?.flushProgressUpdates() }
        // Keep checkpoint and active download to allow seamless retry
        config.saveCheckpoints()
    }
}

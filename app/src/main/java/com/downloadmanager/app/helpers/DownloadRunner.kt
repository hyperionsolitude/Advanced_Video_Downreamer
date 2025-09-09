package com.downloadmanager.app.helpers

import android.content.Context
import com.downloadmanager.app.adapter.FileAdapter
import com.downloadmanager.app.model.DownloadCheckpoint
import com.downloadmanager.app.model.DownloadFile
import com.downloadmanager.app.utils.ErrorHandler
import com.downloadmanager.app.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DownloadRunnerConfig(
    val context: Context,
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
        private const val PERCENT_100 = 100
    }

    fun launchDownload(file: DownloadFile, adapter: FileAdapter?) {
        adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.STARTED)
        ensureCheckpointExists(file)
        config.saveCheckpoints()
        config.downloadScope.launch { runDownloadWithErrorHandling(file, adapter) }
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

    private suspend fun runDownloadWithErrorHandling(
        file: DownloadFile,
        adapter: FileAdapter?,
    ) {
        try {
            config.downloadFileHandler.downloadFile(file)
        } catch (e: SecurityException) {
            handleDownloadError(file, adapter, e)
        } catch (e: java.io.IOException) {
            handleDownloadError(file, adapter, e)
        } catch (e: IllegalArgumentException) {
            handleDownloadError(file, adapter, e)
        }
    }

    private suspend fun handleDownloadError(
        file: DownloadFile,
        adapter: FileAdapter?,
        throwable: Throwable,
    ) {
        Logger.e(
            "DownloadRunner",
            "Error downloading ${file.name}: ${throwable.message}",
            throwable
        )
        val errorMessage = ErrorHandler.getUserFriendlyMessage(throwable, config.context)
        withContext(Dispatchers.Main) {
            adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.FAILED)
            adapter?.updateProgressOnly(file.url, PERCENT_100)
            adapter?.flushProgressUpdates()
            config.activeDownloads.remove(file.url)
            config.downloadCheckpoints.remove(file.url)
            config.saveCheckpoints()
            config.showSnackbar("Error downloading ${file.name}: $errorMessage")
        }
    }
}

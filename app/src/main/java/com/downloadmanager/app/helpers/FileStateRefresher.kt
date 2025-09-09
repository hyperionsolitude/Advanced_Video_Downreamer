package com.downloadmanager.app.helpers

import com.downloadmanager.app.adapter.FileAdapter
import com.downloadmanager.app.model.DownloadCheckpoint
import com.downloadmanager.app.model.DownloadFile
import com.downloadmanager.app.model.FileUtils
import com.downloadmanager.app.viewmodel.MainViewModel

class FileStateRefresher(
    private val viewModel: MainViewModel,
    private val downloadCheckpoints: MutableMap<String, DownloadCheckpoint>,
    private val activeDownloads: MutableSet<String>,
) {
    companion object {
        private const val PERCENT_100 = 100
    }

    fun refreshFileStates(adapter: FileAdapter?) {
        val files = viewModel.currentFiles.value ?: emptyList()
        files.forEach { file -> updateSingleFileState(adapter, file) }
        adapter?.flushProgressUpdates()
    }

    private fun updateSingleFileState(adapter: FileAdapter?, file: DownloadFile) {
        val fileName = file.name.ifEmpty { file.url.substringAfterLast("/") }
        val localFile = FileUtils.getLocalFile(
            viewModel.currentStorageDir.value,
            fileName,
            file.subfolder
        )
        if (!localFile.exists()) return

        if (file.isCompletelyDownloaded()) {
            adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.COMPLETE)
            adapter?.updateProgressOnly(file.url, PERCENT_100)
            downloadCheckpoints.remove(file.url)
            return
        }

        if (file.isPartiallyDownloaded()) {
            val partialSize = file.getPartialDownloadSize()
            val expectedSize = file.getExpectedSize()
            if (expectedSize != null && expectedSize > 0) {
                val progress = ((partialSize * PERCENT_100) / expectedSize).toInt()

                // Check if this download is currently active
                if (activeDownloads.contains(file.url)) {
                    adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.DOWNLOADING)
                } else {
                    // Not active, so this is a paused/partial download
                    adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.PAUSED)
                }
                adapter?.updateProgressOnly(file.url, progress)
            }
        }
    }
}

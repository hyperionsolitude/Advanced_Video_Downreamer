package com.downloadmanager.app.helpers

import android.content.Context
import com.downloadmanager.app.adapter.FileAdapter
import com.downloadmanager.app.model.DownloadCheckpoint
import com.downloadmanager.app.model.DownloadFile
import com.downloadmanager.app.model.FileUtils
import com.downloadmanager.app.utils.Logger
import com.downloadmanager.app.viewmodel.MainViewModel
import java.io.File
import java.net.URL
import java.net.URLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadFileHandler(
    private val context: Context,
    private val viewModel: MainViewModel,
    private val downloadCheckpoints: MutableMap<String, DownloadCheckpoint>,
    private val updateProgress: (String, Int) -> Unit,
    private val finalizeDownload: (DownloadFile) -> Unit,
) {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 30000
        private const val READ_TIMEOUT_MS = 60000
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; SM-G975F) " +
            "AppleWebKit/537.36"
        private const val PERCENT_100 = 100
        private const val BUFFER_SIZE = 8192
    }

    suspend fun downloadFile(file: DownloadFile) {
        val outputFile = prepareOutputFile(file) ?: return
        val startByte = getStartByte(file)
        val connection = openConnection(file.url, startByte)
        val totalSize = calculateTotalSize(connection.contentLength, startByte)

        Logger.d(
            "DownloadDebug",
            "Content length: ${connection.contentLength}, Start byte: $startByte, " +
                "Total size: $totalSize"
        )

        performDownload(connection, outputFile, file.url, startByte, totalSize)
        updateUIOnCompletion(file)
        finalizeDownload(file)
    }

    private fun getStartByte(file: DownloadFile): Long {
        val checkpoint = downloadCheckpoints[file.url]
        return when {
            checkpoint != null -> checkpoint.downloadedBytes
            file.isPartiallyDownloaded() -> file.getPartialDownloadSize()
            else -> 0L
        }
    }

    private fun calculateTotalSize(contentLength: Int, startByte: Long): Long {
        return if (contentLength > 0) contentLength + startByte else -1L
    }

    private suspend fun performDownload(
        connection: URLConnection,
        outputFile: File,
        fileUrl: String,
        startByte: Long,
        totalSize: Long,
    ) {
        connection.inputStream.use { inputStream ->
            java.io.FileOutputStream(outputFile, true).use { outputStream ->
                processDownloadStream(inputStream, outputStream, fileUrl, startByte, totalSize)
            }
        }
    }

    private suspend fun processDownloadStream(
        inputStream: java.io.InputStream,
        outputStream: java.io.OutputStream,
        fileUrl: String,
        startByte: Long,
        totalSize: Long,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var totalBytesRead = 0L
        var bytesRead: Int

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead

            if (totalSize > 0) {
                val progress = ((startByte + totalBytesRead) * PERCENT_100 / totalSize).toInt()
                updateProgress(fileUrl, progress)
            }
        }

        Logger.d(
            "DownloadDebug",
            "Download completed successfully. Total bytes read: $totalBytesRead " +
                "(resumed from: $startByte)"
        )
    }

    private suspend fun updateUIOnCompletion(file: DownloadFile) {
        withContext(Dispatchers.Main) {
            val adapter = (context as? androidx.appcompat.app.AppCompatActivity)
                ?.findViewById<androidx.recyclerview.widget.RecyclerView>(
                    com.downloadmanager.app.R.id.recyclerViewFiles
                )
                ?.adapter as? FileAdapter
            adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.COMPLETE)
            adapter?.updateProgressOnly(file.url, PERCENT_100)
            adapter?.flushProgressUpdates()
        }
    }

    private fun prepareOutputFile(file: DownloadFile): File? {
        val downloadsDir = FileUtils.getDownloadDir(
            viewModel.currentStorageDir.value,
            file.subfolder
        )
        FileUtils.ensureDirExists(downloadsDir)
        val rawName = file.name.ifEmpty { file.url.substringAfterLast("/") }
        val fileName = FileUtils.sanitizeFileName(rawName)
        val outputFile = FileUtils.getLocalFile(
            viewModel.currentStorageDir.value,
            fileName,
            file.subfolder
        )

        if (file.isCompletelyDownloaded()) {
            Logger.d("DownloadDebug", "File already completely downloaded: ${file.name}")
            return null
        }

        if (outputFile.exists() && !file.isCompletelyDownloaded()) {
            Logger.d("DownloadDebug", "File partially downloaded, will resume: ${file.name}")
        }

        return outputFile
    }

    private fun openConnection(urlStr: String, startByte: Long): URLConnection {
        val connection = URL(urlStr).openConnection()
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Connection", "close")
        connection.setRequestProperty("Accept-Encoding", "identity")

        if (startByte > 0) {
            connection.setRequestProperty("Range", "bytes=$startByte-")
            Logger.d("DownloadDebug", "Setting Range header: bytes=$startByte-")
        } else {
            connection.setRequestProperty("Range", "bytes=0-")
        }

        return connection
    }
}

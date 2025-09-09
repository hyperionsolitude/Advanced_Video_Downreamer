package com.downloadmanager.app

import java.net.URLConnection

class DownloadController(
    private val bufferSizeProvider: () -> Int,
) {
    private companion object {
        const val BUFFER_SIZE = 8192
        const val PROGRESS_THROTTLE_MS = 150L
        const val PERCENT_100 = 100
        const val ONE_MB: Long = 1024 * 1024
    }
    fun performDownload(
        connection: URLConnection,
        outputFile: java.io.File,
        onProgress: (Int) -> Unit,
    ) {
        performDownload(connection, outputFile, onProgress, 0L)
    }

    fun performDownload(
        connection: URLConnection,
        outputFile: java.io.File,
        onProgress: (Int) -> Unit,
        startByte: Long,
    ) {
        val inputStream = connection.getInputStream()
        val outputStream = java.io.FileOutputStream(outputFile, startByte > 0)
        val buffer = ByteArray(bufferSizeProvider())
        var bytesRead: Int
        var totalBytesRead = startByte
        val contentLength = connection.contentLength
        val totalSize = if (contentLength > 0) contentLength + startByte else -1L

        android.util.Log.d(
            "DownloadDebug",
            "Content length: $contentLength, Start byte: $startByte, Total size: $totalSize"
        )

        try {
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (totalSize > 0) {
                    val progress = ((totalBytesRead * PERCENT_100) / totalSize).toInt()
                    onProgress(progress)
                }
                // Flush periodically to ensure data is written
                if (totalBytesRead % ONE_MB == 0L) { // Every 1MB
                    outputStream.flush()
                }
            }
            outputStream.flush() // Final flush
            android.util.Log.d(
                "DownloadDebug",
                "Download completed successfully. Total bytes read: $totalBytesRead " +
                    "(resumed from: $startByte)"
            )
        } catch (e: java.io.IOException) {
            // handle IO error
            android.util.Log.e("DownloadDebug", "Download failed: ${e.message}")
            throw e // Re-throw to allow retry logic
        } finally {
            inputStream.use { it.close() }
            outputStream.use { it.close() }
        }
    }
}

package com.downloadmanager.app.model

data class DownloadCheckpoint(
    val url: String,
    val fileName: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val timestamp: Long = System.currentTimeMillis(),
) {

    fun isComplete(): Boolean = downloadedBytes >= totalBytes && totalBytes > 0

    fun getProgress(): Float = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
}

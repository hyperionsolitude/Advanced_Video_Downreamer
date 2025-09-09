package com.downloadmanager.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.downloadmanager.app.model.DownloadFile
import java.io.File

class PlaylistCreator(
    private val context: Context,
    private val sdCardUri: Uri?,
) {
    data class Result(val uri: Uri?, val file: File?)

    fun create(files: List<DownloadFile>, downloadsDir: File): Result? {
        val isSdCard = downloadsDir.absolutePath.startsWith("/storage/") &&
            !downloadsDir.absolutePath.startsWith("/storage/emulated/0/")

        return if (isSdCard && sdCardUri != null) {
            // Try SD card first, but fall back to internal if it fails
            createOnSdCard(files) ?: createOnInternal(files, downloadsDir)
        } else {
            // Use internal storage
            createOnInternal(files, downloadsDir)
        }
    }

    private fun buildPlaylistContent(files: List<DownloadFile>): String {
        val builder = StringBuilder()
        builder.append("#EXTM3U\n")
        val sortedFiles = files.sortedWith(compareBy({ !it.isCompletelyDownloaded() }, { it.name }))
        sortedFiles.forEach { file ->
            val (title, location) = if (file.isCompletelyDownloaded()) {
                val localPath = file.getLocalPath()
                val localFile = localPath?.let { File(it) }
                val displayTitle = localFile?.name ?: file.name
                val loc = "file://$localPath"
                displayTitle to loc
            } else {
                // Derive a cleaner title from URL last segment (URL-decoded)
                val last = file.url.substringAfterLast('/')
                val decoded = try {
                    java.net.URLDecoder.decode(last, "UTF-8")
                } catch (_: Exception) { last }
                val displayTitle = if (file.name.isNotBlank()) file.name else decoded
                displayTitle to file.url
            }
            builder.append("#EXTINF:-1,${title}\n")
            builder.append("#EXTVLCOPT:meta-title=${title}\n")
            builder.append("#EXTVLCOPT:input-title-format=${title}\n")
            builder.append("#EXTVLCOPT:start-time=0\n")
            builder.append("$location\n\n")
        }
        return builder.toString()
    }

    private fun createOnSdCard(files: List<DownloadFile>): Result? {
        var result: Result? = null
        if (sdCardUri != null) {
            val baseUri = sdCardUri
            val basePath = DocumentsContract.getTreeDocumentId(baseUri)
            val playlistName = "playlist_${System.currentTimeMillis()}.m3u"
            val playlistContent = buildPlaylistContent(files)
            val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
                baseUri,
                basePath
            )
            val docUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocumentUri,
                "audio/x-mpegurl",
                playlistName
            )
            if (docUri != null) {
                context.contentResolver.openOutputStream(docUri)?.use {
                    it.write(playlistContent.toByteArray())
                }
                result = Result(docUri, null)
            }
        }
        return result
    }

    private fun createOnInternal(files: List<DownloadFile>, downloadsDir: File): Result {
        val targetDir: File = (
            files.firstOrNull { it.isCompletelyDownloaded() }?.getLocalPath()
                ?.let { lp -> File(lp).parentFile }
                ?: downloadsDir
            )
        if (!targetDir.exists()) targetDir.mkdirs()
        val playlistFile = File(targetDir, "playlist_${System.currentTimeMillis()}.m3u")
        val content = buildPlaylistContent(files)
        playlistFile.writeText(content)
        val uri = FileProvider.getUriForFile(
            context,
            "${com.downloadmanager.app.BuildConfig.APPLICATION_ID}.fileprovider",
            playlistFile
        )
        return Result(uri, playlistFile)
    }
}

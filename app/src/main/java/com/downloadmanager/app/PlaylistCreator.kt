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
    companion object {
        private const val MIN_FILENAME_LENGTH = 3
        private const val HASHCODE_LENGTH = 4
    }

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

        android.util.Log.d("PlaylistDebug", "Building playlist with ${sortedFiles.size} files")
        sortedFiles.forEachIndexed { index, file ->
            val (title, location) = getFileTitleAndLocation(file)
            android.util.Log.d(
                "PlaylistDebug",
                "File $index: title='$title', location='$location', " +
                    "downloaded=${file.isCompletelyDownloaded()}"
            )
            appendFileToPlaylist(builder, title, location)
        }

        val content = builder.toString()
        android.util.Log.d("PlaylistDebug", "Playlist content:\n$content")
        return content
    }

    private fun getFileTitleAndLocation(file: DownloadFile): Pair<String, String> {
        val displayTitle = getUnifiedFileTitle(file)
        val location = if (file.isCompletelyDownloaded()) {
            val localPath = file.getLocalPath()
            "file://$localPath"
        } else {
            file.url
        }
        return displayTitle to location
    }

    private fun getUnifiedFileTitle(file: DownloadFile): String {
        // Use the same naming logic for both downloaded and network files
        val prefix = if (file.isCompletelyDownloaded()) "Local" else "Network"

        // First try to use the file name from the fetched list
        if (file.name.isNotBlank() &&
            !file.name.matches(Regex("^\\d+\\s*\\[.*\\]$")) &&
            file.name.length > MIN_FILENAME_LENGTH
        ) {
            android.util.Log.d("PlaylistDebug", "Using file name: '${file.name}'")
            return "$prefix - ${file.name}"
        }

        // If file name is not good, try to extract from URL
        val extractedTitle = extractTitleFromUrl(file.url)
        android.util.Log.d("PlaylistDebug", "Using extracted title: '$extractedTitle'")
        return "$prefix - $extractedTitle"
    }

    private fun extractTitleFromUrl(url: String): String {
        val last = url.substringAfterLast('/')
        val decoded = try {
            java.net.URLDecoder.decode(last, "UTF-8")
        } catch (_: Exception) { last }

        // Try to extract a better name from the URL path
        val pathSegments = url.split('/').filter { it.isNotBlank() }
        val potentialNames = pathSegments.filter { segment ->
            segment.contains('.') &&
                segment.length > MIN_FILENAME_LENGTH &&
                !segment.matches(Regex("^\\d+\\s*\\[.*\\]$"))
        }

        val bestName = when {
            potentialNames.isNotEmpty() -> potentialNames.last()
            !decoded.matches(Regex("^\\d+\\s*\\[.*\\]$")) &&
                decoded.length > MIN_FILENAME_LENGTH -> decoded
            else -> null
        }

        return bestName ?: "Network Stream ${url.hashCode().toString().takeLast(HASHCODE_LENGTH)}"
    }

    private fun appendFileToPlaylist(builder: StringBuilder, title: String, location: String) {
        builder.append("#EXTINF:-1,${title}\n")
        builder.append("#EXTVLCOPT:meta-title=${title}\n")
        builder.append("#EXTVLCOPT:input-title-format=${title}\n")
        builder.append("#EXTVLCOPT:start-time=0\n")
        builder.append("$location\n\n")
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

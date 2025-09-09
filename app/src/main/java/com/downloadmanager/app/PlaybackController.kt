package com.downloadmanager.app

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.downloadmanager.app.adapter.FileAdapter
import com.downloadmanager.app.model.DownloadFile
import java.io.File

class PlaybackController(
    private val activity: MainActivity,
) {
    fun streamSelectedFiles() {
        val selected = activity.viewModel.currentFiles.value?.filter {
            activity.viewModel.selectedFiles.value?.contains(it.url) == true
        } ?: emptyList()
        if (selected.isEmpty()) return
        if (selected.size == 1) {
            streamFileWithPriority(selected.first())
        } else {
            createAndStreamPlaylist(selected)
        }
        activity.viewModel.clearSelection()
        val adapter = activity.recyclerViewFiles.adapter as? FileAdapter
        adapter?.clearSelection()
        activity.textViewSelectedCount.text = "Selected: 0"
        activity.updateActionButtons()
        activity.updateSelectedSizeInfo()
    }

    fun streamFileWithPriority(file: DownloadFile) {
        val streamer = Streamer(
            activity = activity,
            isOnSdCard = { f -> isOnSdCard(f) },
            getSdCardContentUri = { f -> getSdCardContentUri(f) }
        )
        streamer.streamFile(file)
    }

    @Suppress("LongMethod", "TooGenericExceptionCaught")
    fun createAndStreamPlaylist(files: List<DownloadFile>) {
        if (!activity.isStorageAvailable()) {
            activity.showSnackbar("Storage not available. Please check your storage or SD card.")
            return
        }
        val downloadsDir = activity.viewModel.getDownloadDir()
        val creator = PlaylistCreator(activity, activity.sdCardUri)
        val result = creator.create(files, downloadsDir)
        if (result?.uri == null) {
            activity.showSnackbar("Failed to create playlist file.")
            return
        }
        activity.lastPlaylistUri = result.uri
        activity.lastPlaylistFile = result.file
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(result.uri, "audio/x-mpegurl")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_TITLE, "Playlist")
        // Attach ClipData with the playlist URI and grant to VLC
        intent.clipData = android.content.ClipData.newUri(
            activity.contentResolver,
            "Playlist",
            result.uri
        )
        val vlcPackages = listOf("org.videolan.vlc", "com.huawei.vlc")
        var launched = false
        for (pkg in vlcPackages) {
            try {
                activity.grantUriPermission(
                    pkg,
                    result.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
            intent.setPackage(pkg)
            try {
                activity.startActivity(intent)
                launched = true
                break
            } catch (_: android.content.ActivityNotFoundException) {
            } catch (_: SecurityException) {}
        }
        if (!launched) {
            intent.setPackage(null)
            try {
                activity.startActivity(
                    Intent.createChooser(intent, "Open playlist with")
                )
            } catch (e: android.content.ActivityNotFoundException) {
                com.downloadmanager.app.utils.Logger.w(
                    "MainActivity",
                    "No app found to open playlist: ${e.message}"
                )
                activity.showSnackbar("No app found to open the playlist")
            } catch (e: SecurityException) {
                com.downloadmanager.app.utils.Logger.w(
                    "MainActivity",
                    "Permission denied to open playlist: ${e.message}"
                )
                activity.showSnackbar("Permission denied to open playlist")
            }
        }
    }

    private fun isOnSdCard(file: File): Boolean {
        val path = file.absolutePath
        return path.startsWith("/storage/") && !path.startsWith("/storage/emulated/0/")
    }

    @Suppress("ReturnCount")
    private fun getSdCardContentUri(file: File): Uri? {
        val base = activity.sdCardUri ?: return null

        try {
            val basePath = DocumentsContract.getTreeDocumentId(base)
            val filePath = file.absolutePath

            // Handle different SD card path formats
            val baseFolder = when {
                basePath.startsWith("primary:") ->
                    "/storage/emulated/0/" + basePath.removePrefix("primary:")
                basePath.contains(":") ->
                    "/storage/" + basePath.substringBefore(":") + "/" + basePath.substringAfter(":")
                else -> "/storage/" + basePath
            }.trimEnd('/')

            // Check if file is within the selected folder
            if (!filePath.startsWith(baseFolder)) {
                android.util.Log.d(
                    "StreamDebug",
                    "File not in selected folder. File: $filePath, Base: $baseFolder"
                )
                return null
            }

            val relPath = filePath.removePrefix(baseFolder).trimStart('/')
            val docId = if (relPath.isEmpty()) basePath else "$basePath/$relPath"
            val result = DocumentsContract.buildDocumentUriUsingTree(base, docId)

            android.util.Log.d(
                "StreamDebug",
                "Created SD card content URI: $result for file: $filePath"
            )
            return result
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("StreamDebug", "Illegal argument for SD URI: ${e.message}")
            return null
        } catch (e: SecurityException) {
            android.util.Log.e("StreamDebug", "Security exception for SD URI: ${e.message}")
            android.util.Log.e(
                "StreamDebug",
                "Failed to create SD card content URI for file: ${file.absolutePath}",
                e
            )
            return null
        }
    }
}

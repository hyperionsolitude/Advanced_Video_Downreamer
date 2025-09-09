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
    companion object {
        private const val PLAYLIST_LAUNCH_DELAY_MS = 500L
        private const val MAX_RETRY_ATTEMPTS = 2
        private const val RETRY_DELAY_MULTIPLIER_MS = 1000L
    }
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
        adapter?.clearProgressAndStatus()
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

        // Clean up any existing playlist before creating a new one
        android.util.Log.d(
            "PlaybackDebug",
            "Cleaning up any existing playlist before creating new one"
        )
        activity.cleanupLastPlaylist()

        android.util.Log.d("PlaybackDebug", "Creating playlist with ${files.size} files")
        val downloadsDir = activity.viewModel.getDownloadDir()
        val creator = PlaylistCreator(activity, activity.sdCardUri)
        val result = creator.create(files, downloadsDir)
        if (result?.uri == null) {
            activity.showSnackbar("Failed to create playlist file.")
            return
        }

        android.util.Log.d("PlaybackDebug", "Playlist created successfully: ${result.uri}")
        activity.lastPlaylistUri = result.uri
        activity.lastPlaylistFile = result.file

        // Add a delay to ensure the playlist file is fully written and try multiple times
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            launchPlaylistIntentWithRetry(result.uri, 0)
        }, PLAYLIST_LAUNCH_DELAY_MS)
    }

    private fun launchPlaylistIntentWithRetry(uri: Uri, retryCount: Int) {
        android.util.Log.d(
            "PlaybackDebug",
            "Attempting to launch playlist (attempt ${retryCount + 1})"
        )

        val intent = createPlaylistIntent(uri)
        val vlcPackages = listOf("org.videolan.vlc", "com.huawei.vlc")

        val success = tryLaunchWithVlcPackages(intent, uri, vlcPackages)

        if (!success && retryCount < MAX_RETRY_ATTEMPTS) {
            // Retry after a longer delay
            val delayMs = (retryCount + 1) * RETRY_DELAY_MULTIPLIER_MS
            android.util.Log.d("PlaybackDebug", "Launch failed, retrying in ${delayMs}ms")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                launchPlaylistIntentWithRetry(uri, retryCount + 1)
            }, delayMs)
        } else if (!success) {
            android.util.Log.d("PlaybackDebug", "All VLC attempts failed, trying with chooser")
            launchWithChooser(intent)
        }
    }

    private fun createPlaylistIntent(uri: Uri): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "audio/x-mpegurl")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_TITLE, "Playlist")

        // Attach ClipData with the playlist URI and grant to VLC
        intent.clipData = android.content.ClipData.newUri(
            activity.contentResolver,
            "Playlist",
            uri
        )
        return intent
    }

    private fun tryLaunchWithVlcPackages(
        intent: Intent,
        uri: Uri,
        packages: List<String>,
    ): Boolean {
        for (pkg in packages) {
            if (tryLaunchWithPackage(intent, uri, pkg)) {
                return true
            }
        }
        return false
    }

    private fun tryLaunchWithPackage(intent: Intent, uri: Uri, pkg: String): Boolean {
        try {
            // Grant permission first
            activity.grantUriPermission(
                pkg,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            android.util.Log.d("PlaybackDebug", "Granted permission to $pkg")

            // Set package and launch
            intent.setPackage(pkg)
            activity.startActivity(intent)
            android.util.Log.d("PlaybackDebug", "Successfully launched with $pkg")
            return true
        } catch (e: android.content.ActivityNotFoundException) {
            android.util.Log.d("PlaybackDebug", "Activity not found for $pkg: ${e.message}")
        } catch (e: SecurityException) {
            android.util.Log.w(
                "PlaybackDebug",
                "Security exception with $pkg: ${e.message}"
            )
        } catch (e: IllegalStateException) {
            android.util.Log.w(
                "PlaybackDebug",
                "Illegal state with $pkg: ${e.message}"
            )
        } catch (e: UnsupportedOperationException) {
            android.util.Log.w(
                "PlaybackDebug",
                "Unsupported operation with $pkg: ${e.message}"
            )
        }
        return false
    }

    private fun launchWithChooser(intent: Intent) {
        intent.setPackage(null)
        try {
            activity.startActivity(
                Intent.createChooser(intent, "Open playlist with")
            )
            android.util.Log.d("PlaybackDebug", "Launched with chooser")
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

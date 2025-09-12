package com.downloadmanager.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.downloadmanager.app.model.DownloadFile
import java.io.File

class Streamer(
    private val activity: android.app.Activity,
    private val isOnSdCard: (File) -> Boolean,
    private val getSdCardContentUri: (File) -> Uri?,
) {

    private fun getMimeTypeForUrl(url: String): String {
        return when (url.substringAfterLast(".", "").lowercase()) {
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "wmv" -> "video/x-ms-wmv"
            "flv" -> "video/x-flv"
            "webm" -> "video/webm"
            "m4v" -> "video/x-m4v"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            else -> "video/mp4" // Default to video
        }
    }
    fun streamFile(file: DownloadFile) {
        if (!isStorageAvailable()) {
            show("Storage not available. Please check your storage or SD card.")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
        val mimeType = getMimeTypeForUrl(file.url)
        if (file.isCompletelyDownloaded()) {
            streamLocalOrNetwork(intent, file, mimeType)
        } else {
            streamFromNetwork(intent, file, mimeType)
        }
    }

    private fun isStorageAvailable(): Boolean {
        val dir = (activity as? MainActivity)?.viewModel?.getDownloadDir()
        return dir?.exists() == true && dir.canWrite()
    }

    private fun streamLocalOrNetwork(
        intent: Intent,
        file: DownloadFile,
        mimeType: String,
    ) {
        val localPath = file.getLocalPath()
        val localFile = if (localPath != null) File(localPath) else null

        if (localFile == null || !localFile.exists() || localFile.length() == 0L) {
            intent.setDataAndType(Uri.parse(file.url), mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra(Intent.EXTRA_TITLE, file.name)
            show("Local file missing, streaming from network")
            streamWithPreferredPlayers(intent)
            return
        }
        if (localFile.isDirectory) {
            show("Cannot stream a directory. Please select a file.")
            return
        }
        if (isOnSdCard(localFile)) {
            streamFromSdCard(intent, file, localFile, mimeType)
        } else {
            streamFromInternal(intent, file, localFile, mimeType)
        }
    }

    private fun streamFromNetwork(intent: Intent, file: DownloadFile, mimeType: String) {
        try {
            val parentDir = File(activity.cacheDir, "network_playlists")
            if (!parentDir.exists()) parentDir.mkdirs()
            try {
                parentDir.listFiles()?.filter { f ->
                    f.isFile && f.extension.equals("m3u", true)
                }?.forEach { it.delete() }
            } catch (_: Exception) {
            }

            val safeTitle = file.name.ifBlank { "Network Stream" }
            val playlistFile = File(
                parentDir,
                "network_single_${System.currentTimeMillis()}.m3u"
            )
            val builder = StringBuilder()
            builder.append("#EXTM3U\n")
            builder.append("#EXTINF:-1,$safeTitle\n")
            builder.append("#EXTVLCOPT:meta-title=$safeTitle\n")
            builder.append("#EXTVLCOPT:input-title-format=$safeTitle\n")
            builder.append("#EXTVLCOPT:start-time=0\n")
            builder.append("#EXTVLCOPT:network-caching=5000\n")
            builder.append("${file.url}\n")
            playlistFile.writeText(builder.toString())

            val playlistUri = FileProvider.getUriForFile(
                activity,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                playlistFile
            )
            val playlistIntent = Intent(Intent.ACTION_VIEW)
            playlistIntent.setDataAndType(playlistUri, "audio/x-mpegurl")
            playlistIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            playlistIntent.putExtra(Intent.EXTRA_TITLE, safeTitle)
            streamWithPreferredPlayers(playlistIntent)
        } catch (_: Exception) {
            intent.setDataAndType(Uri.parse(file.url), mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra(Intent.EXTRA_TITLE, file.name)
            streamWithPreferredPlayers(intent)
        }
    }

    private fun streamFromSdCard(
        intent: Intent,
        file: DownloadFile,
        localFile: File,
        mimeType: String,
    ) {
        val contentUri = getSdCardContentUri(localFile)
        if (contentUri == null) {
            streamFromInternal(intent, file, localFile, mimeType)
            return
        }
        intent.setDataAndType(contentUri, mimeType)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_TITLE, file.name)
        streamWithPreferredPlayers(intent)
    }

    @Suppress("LongMethod", "NestedBlockDepth")
    private fun streamFromInternal(
        intent: Intent,
        file: DownloadFile,
        localFile: File,
        mimeType: String,
    ) {
        try {
            val parentDir = localFile.parentFile ?: activity.cacheDir
            if (!parentDir.exists()) parentDir.mkdirs()
            try {
                parentDir.listFiles()?.filter { f ->
                    f.isFile &&
                        f.name.startsWith(
                            localFile.nameWithoutExtension + "_single"
                        ) &&
                        f.extension.equals("m3u", true)
                }?.forEach { it.delete() }
            } catch (_: java.io.IOException) {
            } catch (_: SecurityException) {
            }
            val playlistFile =
                File(
                    parentDir,
                    "${localFile.nameWithoutExtension}_single_" +
                        "${System.currentTimeMillis()}.m3u"
                )
            val title = localFile.name
            val builder = StringBuilder()
            builder.append("#EXTM3U\n")
            builder.append("#EXTINF:-1,$title\n")
            builder.append("#EXTVLCOPT:meta-title=$title\n")
            builder.append("#EXTVLCOPT:input-title-format=$title\n")
            builder.append("#EXTVLCOPT:start-time=0\n")
            builder.append(
                "file://${localFile.absolutePath}\n"
            )
            playlistFile.writeText(builder.toString())
            val playlistUri = FileProvider.getUriForFile(
                activity,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                playlistFile
            )
            val playlistIntent = Intent(Intent.ACTION_VIEW)
            playlistIntent.setDataAndType(playlistUri, "audio/x-mpegurl")
            playlistIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            playlistIntent.putExtra(Intent.EXTRA_TITLE, title)
            streamWithPreferredPlayers(playlistIntent)
            return
        } catch (_: Exception) {
        }
        try {
            val cleanFileName = file.name.replace("[", "").replace("]", "").replace("%", "")
            val tempDir = File(activity.cacheDir, "streaming")
            if (!tempDir.exists()) tempDir.mkdirs()
            val tempFile = File(tempDir, cleanFileName)
            if (tempFile.exists()) tempFile.delete()
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf("ln", "-s", localFile.absolutePath, tempFile.absolutePath)
                )
                process.waitFor()
                if (process.exitValue() != 0) {
                    localFile.copyTo(tempFile, overwrite = true)
                }
            } catch (_: Exception) {
                localFile.copyTo(tempFile, overwrite = true)
            }
            val localUri = FileProvider.getUriForFile(
                activity,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                tempFile
            )
            intent.setDataAndType(localUri, mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra(Intent.EXTRA_TITLE, cleanFileName)
            streamWithPreferredPlayers(intent)
        } catch (_: Exception) {
            try {
                val localUri = FileProvider.getUriForFile(
                    activity,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    localFile
                )
                intent.setDataAndType(localUri, mimeType)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.putExtra(Intent.EXTRA_TITLE, file.name)
                streamWithPreferredPlayers(intent)
            } catch (_: Exception) {
                try {
                    val localUri = FileProvider.getUriForFile(
                        activity,
                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                        localFile
                    )
                    intent.setDataAndType(localUri, mimeType)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val actualFileName = localFile.name
                    intent.putExtra(Intent.EXTRA_TITLE, actualFileName)
                    intent.putExtra(Intent.EXTRA_SUBJECT, actualFileName)
                    intent.putExtra("android.intent.extra.TEXT", actualFileName)
                    streamWithPreferredPlayers(intent)
                } catch (_: IllegalArgumentException) {
                    try {
                        val directUri = android.net.Uri.fromFile(localFile)
                        intent.setDataAndType(directUri, mimeType)
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        val actualFileName = localFile.name
                        intent.putExtra(Intent.EXTRA_TITLE, actualFileName)
                        streamWithPreferredPlayers(intent)
                    } catch (_: Exception) {
                        show("Cannot access this file")
                    }
                }
            }
        }
    }

    private fun streamWithPreferredPlayers(intent: Intent) {
        val vlcPackages = listOf("org.videolan.vlc", "com.huawei.vlc")
        if (!tryLaunchPackages(intent, vlcPackages)) {
            launchChooser(intent)
        }
    }

    private fun tryLaunchPackages(intent: Intent, packages: List<String>): Boolean {
        for (pkg in packages) {
            intent.setPackage(pkg)
            try {
                activity.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.e("StreamDebug", "App not found for package $pkg", e)
            } catch (e: SecurityException) {
                Log.e("StreamDebug", "Security exception for package $pkg", e)
            } catch (e: IllegalArgumentException) {
                Log.e("StreamDebug", "Invalid argument for package $pkg", e)
            }
        }
        return false
    }

    private fun launchChooser(intent: Intent) {
        intent.setPackage(null)
        try {
            activity.startActivity(
                Intent.createChooser(intent, "Open with")
            )
        } catch (_: Exception) {
            show("No app found to open this file")
        }
    }

    private fun show(text: String) {
        activity.runOnUiThread {
            // Add a small delay to ensure RecyclerView has finished layout
            activity.findViewById<
                androidx.recyclerview.widget.RecyclerView
                >(R.id.recyclerViewFiles)?.post {
                (activity as? MainActivity)?.showSnackbar(text)
            }
        }
    }
}

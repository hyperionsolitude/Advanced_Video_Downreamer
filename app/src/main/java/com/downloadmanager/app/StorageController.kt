package com.downloadmanager.app

import android.content.Context
import java.io.File

class StorageController(private val context: Context) {
    fun getDefaultStorageDir(): File {
        val externalDirs = context.getExternalFilesDirs(null)
        if (externalDirs.size > 1 && externalDirs[1] != null) {
            val sdRoot = File(externalDirs[1].absolutePath)
            val sdRootPath = sdRoot.absolutePath.substringBefore("/Android/")
            val sdDownloadManager = File(sdRootPath, "Download/DownloadManager")
            if (sdDownloadManager.exists() || sdDownloadManager.mkdirs()) {
                return sdDownloadManager
            }
        }
        return File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            ),
            "DownloadManager"
        )
    }

    fun getAvailableStorageDirs(): List<Pair<String, File>> {
        val dirs = mutableListOf<Pair<String, File>>()
        val internal = File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            ),
            "DownloadManager"
        )
        dirs.add(Pair("Internal Storage", internal))
        val externalDirs = context.getExternalFilesDirs(null)
        if (externalDirs.size > 1 && externalDirs[1] != null) {
            val sdRoot = File(externalDirs[1].absolutePath)
            val sdRootPath = sdRoot.absolutePath.substringBefore("/Android/")
            val sdDownloadManager = File(sdRootPath, "Download/DownloadManager")
            dirs.add(Pair("SD Card", sdDownloadManager))
        }
        return dirs
    }

    fun getDownloadDir(base: File, subfolder: String?): File {
        return if (subfolder.isNullOrEmpty()) base else File(base, subfolder)
    }
}

package com.downloadmanager.app.model

import android.content.Context
import java.io.File
import android.os.Environment

object FileUtils {
    fun getDownloadDir(baseDir: File?, subfolder: String? = null): File {
        val dir = baseDir ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DownloadManager")
        return if (subfolder.isNullOrEmpty()) dir else File(dir, subfolder)
    }

    fun getLocalFile(baseDir: File?, fileName: String, subfolder: String?): File {
        val dir = getDownloadDir(baseDir, subfolder)
        return File(dir, fileName)
    }

    fun fileExists(baseDir: File?, fileName: String, subfolder: String?): Boolean {
        val file = getLocalFile(baseDir, fileName, subfolder)
        return file.exists() && file.length() > 0
    }
    
    fun isFileComplete(baseDir: File?, fileName: String, subfolder: String?, expectedSize: Long? = null): Boolean {
        val file = getLocalFile(baseDir, fileName, subfolder)
        if (!file.exists() || file.length() == 0L) return false
        
        // If we have expected size, check if file is complete
        if (expectedSize != null && expectedSize > 0) {
            return file.length() >= expectedSize
        }
        
        // For files without expected size, consider them complete if they exist and have content
        return file.length() > 0
    }

    fun deleteFileIfZeroLength(baseDir: File?, fileName: String, subfolder: String?): Boolean {
        val file = getLocalFile(baseDir, fileName, subfolder)
        return if (file.exists() && file.length() == 0L) file.delete() else false
    }

    fun safeDelete(file: File): Boolean {
        return try {
            if (file.exists()) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            } else true
        } catch (e: Exception) {
            false
        }
    }

    fun ensureDirExists(dir: File): Boolean {
        return if (!dir.exists()) dir.mkdirs() else dir.isDirectory
    }
} 
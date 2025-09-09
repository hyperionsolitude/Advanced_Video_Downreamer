package com.downloadmanager.app.model

import android.content.Context
import android.os.Environment
import java.io.File
import kotlin.math.abs

object FileUtils {
    private const val ONE_MB: Long = 1024 * 1024
    private const val TOLERANCE_PERCENT = 1
    private const val ZERO_LONG = 0L
    private const val ONE_HUNDRED = 100

    fun getDownloadDir(baseDir: File?, subfolder: String? = null): File {
        val dir = baseDir ?: File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "DownloadManager"
        )
        return if (subfolder.isNullOrEmpty()) dir else File(dir, subfolder)
    }

    fun getLocalFile(baseDir: File?, fileName: String, subfolder: String?): File {
        val dir = getDownloadDir(baseDir, subfolder)
        return File(dir, fileName)
    }

    fun fileExists(baseDir: File?, fileName: String, subfolder: String?): Boolean {
        val file = getLocalFile(baseDir, fileName, subfolder)
        return file.exists() && file.length() > ZERO_LONG
    }

    @Suppress("ReturnCount")
    fun isFileComplete(
        baseDir: File?,
        fileName: String,
        subfolder: String?,
        expectedSize: Long? = null,
    ): Boolean {
        val file = getLocalFile(baseDir, fileName, subfolder)
        val existsAndNotEmpty = file.exists() && file.length() != ZERO_LONG
        if (!existsAndNotEmpty) return false

        val actualSize = file.length()
        // If we don't know expected size, treat existing non-empty file as complete
        val expected = expectedSize
        if (expected == null) return true
        // Allow for small differences (up to 1% or 1MB, whichever is smaller)
        val tolerance = minOf((expected * TOLERANCE_PERCENT / ONE_HUNDRED), ONE_MB)
        val isWithinTolerance = abs(expected - actualSize) <= tolerance
        return isWithinTolerance
    }

    fun deleteFileIfZeroLength(baseDir: File?, fileName: String, subfolder: String?): Boolean {
        val file = getLocalFile(baseDir, fileName, subfolder)
        return if (file.exists() && file.length() == ZERO_LONG) file.delete() else false
    }

    fun safeDelete(file: File): Boolean {
        return try {
            if (!file.exists()) return true
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun ensureDirExists(dir: File): Boolean {
        return if (!dir.exists()) dir.mkdirs() else dir.isDirectory
    }

    fun sanitizeFileName(name: String): String {
        // Replace characters that are problematic on common filesystems
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\n|\r|\t"), " ")
            .trim()
    }

    fun getAppExternalDownloadDir(context: Context, subfolder: String? = null): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        return if (subfolder.isNullOrEmpty()) base else File(base, subfolder)
    }

    /**
     * Check if a file is partially downloaded (exists but not complete)
     */
    fun isFilePartiallyDownloaded(
        baseDir: File?,
        fileName: String,
        subfolder: String?,
        expectedSize: Long? = null,
    ): Boolean {
        val file = getLocalFile(baseDir, fileName, subfolder)
        val existsAndNotEmpty = file.exists() && file.length() > ZERO_LONG
        val actualSize = file.length()
        val hasExpected = expectedSize != null
        val expected = expectedSize ?: 0L
        // Single-return: partial if exists, has expected size, and is smaller than expected
        return existsAndNotEmpty && hasExpected && actualSize < expected
    }

    /**
     * Get the current size of a partially downloaded file
     */
    fun getPartialFileSize(
        baseDir: File?,
        fileName: String,
        subfolder: String?,
    ): Long {
        val file = getLocalFile(baseDir, fileName, subfolder)
        return if (file.exists()) file.length() else 0L
    }
}

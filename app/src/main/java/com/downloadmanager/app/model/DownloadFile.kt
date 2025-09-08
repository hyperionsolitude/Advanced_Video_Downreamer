package com.downloadmanager.app.model

import android.os.Parcel
import android.os.Parcelable
import java.io.File

data class DownloadFile(
    val name: String,
    val url: String,
    val size: String,
    val type: String,
    val subfolder: String = "",
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(url)
        parcel.writeString(size)
        parcel.writeString(type)
        parcel.writeString(subfolder)
    }

    override fun describeContents(): Int {
        return 0
    }

    /**
     * Check if this file is downloaded locally
     */
    fun isDownloaded(): Boolean {
        val fileName = name.ifEmpty { url.substringAfterLast("/") }
        return FileUtils.fileExists(downloadDir, fileName, subfolder)
    }

    /**
     * Check if this file is completely downloaded (not partial)
     */
    fun isCompletelyDownloaded(): Boolean {
        val fileName = name.ifEmpty { url.substringAfterLast("/") }
        val expectedSize = parseFileSizeToBytes()
        return FileUtils.isFileComplete(downloadDir, fileName, subfolder, expectedSize)
    }

    /**
     * Parse the file size string to bytes
     */
    private fun parseFileSizeToBytes(): Long? {
        val s = size.trim().lowercase()
        return when {
            s.endsWith("gb") -> (
                (s.removeSuffix("gb").trim().toDoubleOrNull() ?: 0.0) * 1024 * 1024 * 1024
                ).toLong()
            s.endsWith("mb") -> (
                (s.removeSuffix("mb").trim().toDoubleOrNull() ?: 0.0) * 1024 * 1024
                ).toLong()
            s.endsWith("kb") -> (
                (s.removeSuffix("kb").trim().toDoubleOrNull() ?: 0.0) * 1024
                ).toLong()
            s.endsWith("b") -> (s.removeSuffix("b").trim().toDoubleOrNull() ?: 0.0).toLong()
            s == "unknown size" -> null
            else -> null
        }
    }

    /**
     * Get the local file path if downloaded, null otherwise
     */
    fun getLocalPath(): String? {
        if (!isDownloaded()) return null
        val fileName = name.ifEmpty { url.substringAfterLast("/") }
        return FileUtils.getLocalFile(downloadDir, fileName, subfolder).absolutePath
    }

    companion object CREATOR : Parcelable.Creator<DownloadFile> {
        var downloadDir: File? = null
        fun setDownloadDirectory(dir: File) {
            downloadDir = dir
        }
        override fun createFromParcel(parcel: Parcel): DownloadFile {
            return DownloadFile(parcel)
        }
        override fun newArray(size: Int): Array<DownloadFile?> {
            return arrayOfNulls(size)
        }
    }
}

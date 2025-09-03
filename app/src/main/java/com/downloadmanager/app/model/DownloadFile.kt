package com.downloadmanager.app.model

import android.os.Parcel
import android.os.Parcelable
import java.io.File
import android.os.Environment
import com.downloadmanager.app.model.FileUtils

data class DownloadFile(
    val name: String,
    val url: String,
    val size: String,
    val type: String,
    val subfolder: String = ""
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
     * Get the local file path if downloaded, null otherwise
     */
    fun getLocalPath(): String? {
        if (!isDownloaded()) return null
        val fileName = name.ifEmpty { url.substringAfterLast("/") }
        return FileUtils.getLocalFile(downloadDir, fileName, subfolder).absolutePath
    }

    companion object CREATOR : Parcelable.Creator<DownloadFile> {
        var downloadDir: File? = null
        fun setDownloadDirectory(dir: File) { downloadDir = dir }
        override fun createFromParcel(parcel: Parcel): DownloadFile {
            return DownloadFile(parcel)
        }
        override fun newArray(size: Int): Array<DownloadFile?> {
            return arrayOfNulls(size)
        }
    }
} 
package com.downloadmanager.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.downloadmanager.app.model.DownloadFile
import java.io.File

class MainViewModel : ViewModel() {
    private val _currentFiles = MutableLiveData<List<DownloadFile>>(emptyList())
    val currentFiles: LiveData<List<DownloadFile>> = _currentFiles

    private val _selectedFiles = MutableLiveData<Set<String>>(emptySet())
    val selectedFiles: LiveData<Set<String>> = _selectedFiles

    private val _currentStorageDir = MutableLiveData<File?>(null)
    val currentStorageDir: LiveData<File?> = _currentStorageDir

    fun setFiles(files: List<DownloadFile>) {
        _currentFiles.value = files
    }

    fun selectFile(url: String) {
        _selectedFiles.value = _selectedFiles.value?.plus(url) ?: setOf(url)
    }

    fun deselectFile(url: String) {
        _selectedFiles.value = _selectedFiles.value?.minus(url) ?: emptySet()
    }

    fun setSelectedFiles(urls: Set<String>) {
        _selectedFiles.value = urls
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
    }

    fun setCurrentStorageDir(dir: File?) {
        _currentStorageDir.value = dir
    }

    fun getDownloadDir(subfolder: String? = null): File {
        val baseDir = _currentStorageDir.value ?: File("/storage/emulated/0/Download/DownloadManager")
        return if (subfolder.isNullOrEmpty()) baseDir else File(baseDir, subfolder)
    }
} 
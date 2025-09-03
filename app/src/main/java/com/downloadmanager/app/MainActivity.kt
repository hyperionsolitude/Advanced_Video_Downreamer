package com.downloadmanager.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.os.Handler
import android.os.Looper
import android.view.View
import android.util.Log
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.Executors
import com.downloadmanager.app.adapter.FileAdapter
import com.downloadmanager.app.model.DownloadFile
import androidx.core.content.FileProvider
import com.downloadmanager.app.BuildConfig
import androidx.lifecycle.lifecycleScope
import com.downloadmanager.app.model.FileUtils
import com.google.android.material.snackbar.Snackbar
import androidx.activity.result.contract.ActivityResultContracts
import com.downloadmanager.app.databinding.ActivityMainBinding
import androidx.activity.viewModels
import com.downloadmanager.app.viewmodel.MainViewModel
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.content.SharedPreferences

class MainActivity : AppCompatActivity() {
    
    private lateinit var editTextUrl: EditText

    private lateinit var buttonFetch: Button
    private lateinit var textViewStorageInfo: TextView
    private lateinit var buttonChangeStorage: Button
    private lateinit var editTextFilter: EditText
    private lateinit var textViewFileCount: TextView
    private lateinit var textViewSelectedCount: TextView
    private lateinit var buttonSelectAll: Button
    private lateinit var buttonDeselectAll: Button
    private lateinit var buttonDownload: Button
    private lateinit var buttonStream: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerViewFiles: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var textViewSelectedSize: TextView
    private lateinit var buttonInvertSelection: com.google.android.material.button.MaterialButton
    private lateinit var buttonSelectNone: com.google.android.material.button.MaterialButton
    
    private val PERMISSION_REQUEST_CODE = 123
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )
    
    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                showSnackbar("All permissions granted!")
            } else {
                showSnackbar("Some permissions were denied")
            }
        }

    private lateinit var binding: ActivityMainBinding
    private val PREFS_NAME = "download_manager_prefs"
    private val KEY_STORAGE_PATH = "storage_path"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val KEY_SD_URI = "sd_card_uri"
    private lateinit var prefs: SharedPreferences
    private var sdCardUri: Uri? = null
    
    // Performance optimization: Throttle UI updates
    private val progressUpdateHandler = Handler(Looper.getMainLooper())
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            // Batch update all progress bars
            val adapter = recyclerViewFiles.adapter as? FileAdapter
            adapter?.notifyDataSetChanged()
        }
    }
    private var lastProgressUpdate = 0L
    private val PROGRESS_UPDATE_INTERVAL = 500L // Optimized to 500ms for better performance
    private val progressUpdateQueue = mutableMapOf<String, Int>() // Queue progress updates
    private val openDocumentTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                prefs.edit().putString(KEY_SD_URI, uri.toString()).apply()
                sdCardUri = uri
                showSnackbar("SD card access granted. Please try streaming again.")
            } else {
                showSnackbar("SD card access not granted.")
            }
        }

    // Store the last playlist file/URI for cleanup
    private var lastPlaylistFile: File? = null
    private var lastPlaylistUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        sdCardUri = prefs.getString(KEY_SD_URI, null)?.let { Uri.parse(it) }
        
        initializeViews()
        setupClickListeners()
        observeViewModel()
        requestPermissionsIfNeeded()
        loadStorageDir()
        updateStorageInfo()
    }
    
    override fun onResume() {
        super.onResume()
        // Delete any zero-length files and partial downloads for all current files
        for (file in viewModel.currentFiles.value ?: emptyList()) {
            val fileName = file.name.ifEmpty { file.url.substringAfterLast("/") }
            FileUtils.deleteFileIfZeroLength(viewModel.currentStorageDir.value, fileName, file.subfolder)
            // Also clean up partial downloads (files that exist but are not complete)
            if (file.isDownloaded() && !file.isCompletelyDownloaded()) {
                val localFile = FileUtils.getLocalFile(viewModel.currentStorageDir.value, fileName, file.subfolder)
                if (localFile.exists()) {
                    FileUtils.safeDelete(localFile)
                }
            }
        }
        // Clean up last playlist file if it exists
        cleanupLastPlaylist()
        val adapter = recyclerViewFiles.adapter as? FileAdapter
        adapter?.updateFiles(viewModel.currentFiles.value ?: emptyList())
        updateActionButtons()
        updateSelectedSizeInfo()
    }

    private fun cleanupLastPlaylist() {
        try {
            if (lastPlaylistUri != null) {
                // SAF playlist (SD card)
                contentResolver.delete(lastPlaylistUri!!, null, null)
                lastPlaylistUri = null
            }
            if (lastPlaylistFile != null) {
                // Internal storage playlist
                lastPlaylistFile!!.delete()
                lastPlaylistFile = null
            }
        } catch (e: Exception) {
            Log.e("Playlist", "Error cleaning up playlist: ${e.message}", e)
        }
    }
    
    private fun initializeViews() {
        // Replace all findViewById with binding references
        // Example: editTextUrl = binding.editTextUrl
        editTextUrl = binding.editTextUrl

        buttonFetch = binding.buttonFetch
        textViewStorageInfo = binding.textViewStorageInfo
        buttonChangeStorage = binding.buttonChangeStorage
        editTextFilter = binding.editTextFilter
        textViewFileCount = binding.textViewFileCount
        textViewSelectedCount = binding.textViewSelectedCount
        buttonSelectAll = binding.buttonSelectAll
        buttonDeselectAll = binding.buttonDeselectAll
        buttonDownload = binding.buttonDownload
        buttonStream = binding.buttonStream
        progressBar = binding.progressBar
        recyclerViewFiles = binding.recyclerViewFiles
        swipeRefresh = binding.swipeRefresh
        textViewSelectedSize = binding.textViewSelectedSize
        buttonInvertSelection = binding.buttonInvertSelection
        buttonSelectNone = binding.buttonSelectNone
        
        // Setup RecyclerView
        recyclerViewFiles.layoutManager = LinearLayoutManager(this)
        val adapter = FileAdapter { file, isSelected ->
            handleFileSelection(file, isSelected)
        }
        recyclerViewFiles.adapter = adapter
        adapter.attachRecyclerView(recyclerViewFiles)
        
        // Setup SwipeRefreshLayout
        swipeRefresh.setOnRefreshListener {
            val url = editTextUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                fetchFiles(url)
        } else {
                swipeRefresh.isRefreshing = false
            }
        }
        
        // Setup filter
        editTextFilter.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                filterFiles(s.toString())
            }
        })
    }

    private fun setupClickListeners() {
        buttonFetch.setOnClickListener {
            val url = editTextUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                fetchFiles(url)
            } else {
                showSnackbar("Please enter a URL")
            }
        }

        buttonDownload.setOnClickListener {
            if (viewModel.selectedFiles.value?.isNotEmpty() == true) {
                startDownloadForSelectedFiles()
            } else {
                showSnackbar("Please select files to download")
            }
        }

        buttonStream.setOnClickListener {
            if (viewModel.selectedFiles.value?.isNotEmpty() == true) {
                streamSelectedFiles()
            } else {
                showSnackbar("Please select files to stream")
            }
        }

        buttonSelectAll.setOnClickListener {
            selectAllFiles()
        }
        
        buttonDeselectAll.setOnClickListener {
            deselectAllFiles()
        }
        
        buttonInvertSelection.setOnClickListener {
            invertSelection()
        }
        buttonSelectNone.setOnClickListener {
            deselectAllFiles()
        }
        
        buttonChangeStorage.setOnClickListener {
            showStorageSelectionDialog()
        }
    }
    
    private fun fetchFiles(url: String) {
        if (!isNetworkAvailable()) {
            showSnackbar("No network connection. Please check your internet.")
            swipeRefresh.isRefreshing = false
            return
        }
        progressBar.visibility = View.VISIBLE
        buttonFetch.isEnabled = false
        swipeRefresh.isRefreshing = true
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val files = when {
                    url.endsWith("/") || isDirectoryUrl(url) -> {
                        // Directory listing
                        fetchFilesFromDirectory(url)
                    }
                    isDirectFileUrl(url) -> {
                        // Single file
                        listOf(createDownloadFileFromUrl(url))
                    }
                    else -> {
                        // Try to parse as HTML page
                        fetchFilesFromHtmlPage(url)
                    }
                }
                
                val filteredFiles = files
                
                // Delete any zero-length files and partial downloads for all fetched files
                for (file in filteredFiles) {
                    val fileName = file.name.ifEmpty { file.url.substringAfterLast("/") }
                    FileUtils.deleteFileIfZeroLength(viewModel.currentStorageDir.value, fileName, file.subfolder)
                    // Also clean up partial downloads (files that exist but are not complete)
                    if (file.isDownloaded() && !file.isCompletelyDownloaded()) {
                        val localFile = FileUtils.getLocalFile(viewModel.currentStorageDir.value, fileName, file.subfolder)
                        if (localFile.exists()) {
                            FileUtils.safeDelete(localFile)
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    viewModel.setFiles(filteredFiles)
                    
                    textViewFileCount.text = "Files: ${viewModel.currentFiles.value?.size ?: 0}"
                    textViewSelectedCount.text = "Selected: 0"
                    
                    progressBar.visibility = View.GONE
                    buttonFetch.isEnabled = true
                    swipeRefresh.isRefreshing = false
                    
                    showSnackbar("Fetched ${filteredFiles.size} files")
                }
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching files: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    buttonFetch.isEnabled = true
                    swipeRefresh.isRefreshing = false
                    showSnackbar("Error fetching files: ${e.message}")
                }
            }
        }
    }
    
    private fun fetchFilesFromDirectory(url: String): List<DownloadFile> {
        val files = mutableListOf<DownloadFile>()
        val subfolder = getSubfolderNameFromUrl()
        try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Android) Advanced Video Downreamer")
                .timeout(8000) // Reduced timeout for faster response
                .maxBodySize(0) // No body size limit
                .get()
            val links = doc.select("a[href]")
            for (link in links) {
                val href = link.attr("href")
                if (isValidFileUrl(href)) {
                    val fullUrl = if (href.startsWith("http")) href else "$url$href"
                    val fileName = link.text().ifEmpty { href.substringAfterLast("/") }
                    val fileType = getFileType(href)
                    val fileSize = getFileSize(fullUrl)
                    files.add(DownloadFile(fileName, fullUrl, fileSize, fileType, subfolder))
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error parsing directory: ${e.message}", e)
        }
        return files
    }

    private fun fetchFilesFromHtmlPage(url: String): List<DownloadFile> {
        val files = mutableListOf<DownloadFile>()
        val subfolder = getSubfolderNameFromUrl()
        try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Android) Advanced Video Downreamer")
                .timeout(8000) // Reduced timeout for faster response
                .maxBodySize(0) // No body size limit
                .get()
            val fileSelectors = listOf(
                "a[href*='.mp4']", "a[href*='.mkv']", "a[href*='.avi']", "a[href*='.mov']",
                "a[href*='.mp3']", "a[href*='.wav']", "a[href*='.flac']", "a[href*='.aac']",
                "a[href*='.pdf']", "a[href*='.zip']", "a[href*='.rar']"
            )
            for (selector in fileSelectors) {
                val links = doc.select(selector)
                for (link in links) {
                    val href = link.attr("href")
                    if (isValidFileUrl(href)) {
                        val fullUrl = if (href.startsWith("http")) href else "$url$href"
                        val fileName = link.text().ifEmpty { href.substringAfterLast("/") }
                        val fileType = getFileType(href)
                        val fileSize = getFileSize(fullUrl)
                        files.add(DownloadFile(fileName, fullUrl, fileSize, fileType, subfolder))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error parsing HTML page: ${e.message}", e)
        }
        return files
    }

    private fun createDownloadFileFromUrl(url: String): DownloadFile {
        val fileName = url.substringAfterLast("/")
        val fileType = getFileType(url)
        val fileSize = getFileSize(url)
        val subfolder = getSubfolderNameFromUrl()
        return DownloadFile(fileName, url, fileSize, fileType, subfolder)
    }
    
    private fun isValidFileUrl(url: String): Boolean {
        val fileExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm",
                                   ".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a",
                                   ".pdf", ".zip", ".rar", ".7z", ".txt", ".doc", ".docx")
        return fileExtensions.any { url.lowercase().contains(it) }
    }
    
    private fun isDirectFileUrl(url: String): Boolean {
        return isValidFileUrl(url) && !url.endsWith("/")
    }
    
    private fun isDirectoryUrl(url: String): Boolean {
        return url.endsWith("/") || !url.contains(".")
    }
    
    private fun getFileType(url: String): String {
        val extension = url.substringAfterLast(".").lowercase()
        return when (extension) {
            "mp3", "wav", "flac", "aac", "ogg", "m4a" -> "audio"
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm" -> "video"
            "pdf" -> "document"
            "zip", "rar", "7z" -> "archive"
            else -> "file"
        }
    }
    
    private fun getFileSize(url: String): String {
        return try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val contentLength = connection.contentLength
            if (contentLength > 0) {
                formatFileSize(contentLength.toLong())
            } else {
                "Unknown size"
            }
        } catch (e: Exception) {
            "Unknown size"
        }
    }
    
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }
    
    private fun handleFileSelection(file: DownloadFile, isSelected: Boolean) {
        if (isSelected) {
            viewModel.selectFile(file.url)
        } else {
            viewModel.deselectFile(file.url)
        }
        textViewSelectedCount.text = "Selected: ${viewModel.selectedFiles.value?.size ?: 0}"
        updateActionButtons()
        updateSelectedSizeInfo()
    }
    
    private fun selectAllFiles() {
        viewModel.setSelectedFiles(viewModel.currentFiles.value?.map { it.url }?.toSet() ?: emptySet())
        val adapter = recyclerViewFiles.adapter as? FileAdapter
        adapter?.selectAll()
        textViewSelectedCount.text = "Selected: ${viewModel.selectedFiles.value?.size ?: 0}"
        updateActionButtons()
        updateSelectedSizeInfo()
    }
    
    private fun deselectAllFiles() {
        viewModel.clearSelection()
        val adapter = recyclerViewFiles.adapter as? FileAdapter
        adapter?.clearSelection()
        textViewSelectedCount.text = "Selected: 0"
        updateActionButtons()
        updateSelectedSizeInfo()
    }

    private fun invertSelection() {
        val adapter = recyclerViewFiles.adapter as? FileAdapter ?: return
        val allFiles = viewModel.currentFiles.value ?: emptyList()
        val newSelected = mutableSetOf<String>()
        for (file in allFiles) {
            if (!(viewModel.selectedFiles.value?.contains(file.url) ?: false)) {
                newSelected.add(file.url)
            }
        }
        viewModel.setSelectedFiles(newSelected)
        adapter.updateFiles(allFiles)
        textViewSelectedCount.text = "Selected: ${viewModel.selectedFiles.value?.size ?: 0}"
        updateActionButtons()
        updateSelectedSizeInfo()
    }
    
    private fun filterFiles(query: String) {
        if (query.isEmpty()) {
            val adapter = recyclerViewFiles.adapter as? FileAdapter
            adapter?.updateFiles(viewModel.currentFiles.value ?: emptyList())
        } else {
            val filteredFiles = viewModel.currentFiles.value?.filter { file ->
                file.name.contains(query, ignoreCase = true) ||
                file.type.contains(query, ignoreCase = true) ||
                file.size.contains(query, ignoreCase = true)
            } ?: emptyList()
            val adapter = recyclerViewFiles.adapter as? FileAdapter
            adapter?.updateFiles(filteredFiles)
        }
    }
    
    private fun updateActionButtons() {
        val hasSelection = viewModel.selectedFiles.value?.isNotEmpty() == true
        buttonDownload.isEnabled = hasSelection
        buttonStream.isEnabled = hasSelection
    }
    
    private fun updateSelectedSizeInfo() {
        val selectedFileList = viewModel.currentFiles.value?.filter { viewModel.selectedFiles.value?.contains(it.url) == true } ?: emptyList()
        val totalBytes = selectedFileList.sumOf { parseFileSizeToBytes(it.size) }
        textViewSelectedSize.text = "Total size: ${formatFileSize(totalBytes)}"
        val freeSpace = viewModel.getDownloadDir().freeSpace
        if (totalBytes > 0 && totalBytes > freeSpace) {
            textViewSelectedSize.setTextColor(android.graphics.Color.RED)
            textViewSelectedSize.text = "Total size: ${formatFileSize(totalBytes)} (Not enough space!)"
        } else {
            textViewSelectedSize.setTextColor(android.graphics.Color.WHITE)
        }
    }

    private fun parseFileSizeToBytes(sizeStr: String): Long {
        val s = sizeStr.trim().lowercase()
        return when {
            s.endsWith("gb") -> ((s.removeSuffix("gb").trim().toDoubleOrNull() ?: 0.0) * 1024 * 1024 * 1024).toLong()
            s.endsWith("mb") -> ((s.removeSuffix("mb").trim().toDoubleOrNull() ?: 0.0) * 1024 * 1024).toLong()
            s.endsWith("kb") -> ((s.removeSuffix("kb").trim().toDoubleOrNull() ?: 0.0) * 1024).toLong()
            s.endsWith("b") -> (s.removeSuffix("b").trim().toDoubleOrNull() ?: 0.0).toLong()
            else -> 0L
        }
    }

    private fun startDownloadForSelectedFiles() {
        if (!isStorageAvailable()) {
            showSnackbar("Storage not available. Please check your storage or SD card.")
            return
        }
        if (!isNetworkAvailable()) {
            showSnackbar("No network connection. Please check your internet.")
            return
        }
        val filesToDownload = viewModel.currentFiles.value?.filter { viewModel.selectedFiles.value?.contains(it.url) == true && !it.isCompletelyDownloaded() } ?: emptyList()
        if (filesToDownload.isEmpty()) {
            showSnackbar("All selected files are already downloaded.")
            return
        }
        for (file in filesToDownload) {
            // Mark download as started
            val adapter = recyclerViewFiles.adapter as? FileAdapter
            adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.STARTED)
            
            // Start each download in parallel
            lifecycleScope.launch {
                try {
                    downloadFile(file)
                } catch (e: Exception) {
                    adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.FAILED)
                    adapter?.updateProgressOnly(file.url, -1)
                    adapter?.flushProgressUpdates()
                    showSnackbar("Error downloading ${file.name}: ${e.message}")
                }
            }
        }
    }

    private suspend fun downloadFile(file: DownloadFile) = withContext(Dispatchers.IO) {
        if (!isStorageAvailable()) {
            runOnUiThread { showSnackbar("Storage not available. Please check your storage or SD card.") }
            return@withContext
        }
        if (!isNetworkAvailable()) {
            runOnUiThread { showSnackbar("No network connection. Please check your internet.") }
            return@withContext
        }
        val downloadsDir = FileUtils.getDownloadDir(viewModel.currentStorageDir.value, file.subfolder)
        FileUtils.ensureDirExists(downloadsDir)
        val fileName = file.name.ifEmpty { file.url.substringAfterLast("/") }
        val outputFile = FileUtils.getLocalFile(viewModel.currentStorageDir.value, fileName, file.subfolder)
        Log.d("DownloadDebug", "Preparing to download: ${outputFile.absolutePath}")
        Log.d("DownloadDebug", "Exists: ${outputFile.exists()}, IsFile: ${outputFile.isFile}, IsDir: ${outputFile.isDirectory}")
        if (outputFile.exists()) {
            FileUtils.safeDelete(outputFile)
        }
        if (outputFile.exists()) {
            runOnUiThread {
                showSnackbar("Cannot download: File or directory still exists after deletion. Please check storage or restart device.")
            }
            Log.e("DownloadDebug", "File still exists after deletion: ${outputFile.absolutePath}")
            return@withContext
        }
        try {
            // Initialize progress bar immediately when download starts
            runOnUiThread {
                val adapter = recyclerViewFiles.adapter as? FileAdapter
                adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.DOWNLOADING)
                adapter?.updateDownloadProgress(file.url, 0)
            }
            
            val connection = URL(file.url).openConnection()
            connection.connectTimeout = 15000 // Reduced timeout for faster failure detection
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Advanced Video Downreamer")
            val inputStream = connection.getInputStream()
            val outputStream = FileOutputStream(outputFile, false)
            val buffer = ByteArray(32768) // Doubled buffer size for better performance
            var bytesRead: Int
            var totalBytesRead = 0L
            val contentLength = connection.contentLength
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (contentLength > 0) {
                    val progress = ((totalBytesRead * 100) / contentLength).toInt()
                    // Update progress in 5% increments for better performance
                    val roundedProgress = (progress / 5) * 5
                    
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastProgressUpdate > PROGRESS_UPDATE_INTERVAL) {
                        // Update progress immediately for better responsiveness
                        runOnUiThread {
                            val adapter = recyclerViewFiles.adapter as? FileAdapter
                            adapter?.updateDownloadProgress(file.url, roundedProgress)
                        }
                        lastProgressUpdate = currentTime
                    } else {
                        // Queue for batch update
                        progressUpdateQueue[file.url] = roundedProgress
                    }
                }
            }
            // Ensure proper resource cleanup
            inputStream.use { it.close() }
            outputStream.use { it.close() }
            // Make the file world-readable for other apps (e.g., VLC)
            outputFile.setReadable(true, false)
            runOnUiThread {
                val adapter = recyclerViewFiles.adapter as? FileAdapter
                adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.COMPLETE)
                adapter?.updateProgressOnly(file.url, 100)
                adapter?.flushProgressUpdates()
                showSnackbar("Downloaded: ${file.name}")
            }
        } catch (e: Exception) {
            runOnUiThread {
                val adapter = recyclerViewFiles.adapter as? FileAdapter
                adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.FAILED)
                adapter?.updateProgressOnly(file.url, -1)
                adapter?.flushProgressUpdates()
                showSnackbar("Error downloading ${file.name}: ${e.message}")
            }
            Log.e("DownloadDebug", "Error downloading file: ${e.message}", e)
        }
    }
    
    private fun streamSelectedFiles() {
        // Only use the current selection, do not modify selection state
        val selectedFileList = viewModel.currentFiles.value?.filter { viewModel.selectedFiles.value?.contains(it.url) == true } ?: emptyList()
        if (selectedFileList.isNotEmpty()) {
            if (selectedFileList.size == 1) {
                // Single file - use priority streaming
                val file = selectedFileList.first()
                streamFileWithPriority(file)
            } else {
                // Multiple files - create playlist
                createAndStreamPlaylist(selectedFileList)
            }
            // Clear selection after streaming
            viewModel.clearSelection()
            val adapter = recyclerViewFiles.adapter as? FileAdapter
            adapter?.clearSelection()
            textViewSelectedCount.text = "Selected: 0"
            updateActionButtons()
            updateSelectedSizeInfo()
        }
    }

    private fun streamFileWithPriority(file: DownloadFile) {
        if (!isStorageAvailable()) {
            showSnackbar("Storage not available. Please check your storage or SD card.")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
        val mimeType = getMimeType(file.url)
        if (file.isCompletelyDownloaded()) {
            val localPath = file.getLocalPath()
            val localFile = if (localPath != null) File(localPath) else null
            if (localFile == null || !localFile.exists() || localFile.length() == 0L) {
                intent.setDataAndType(android.net.Uri.parse(file.url), mimeType)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.putExtra(Intent.EXTRA_TITLE, file.name)
                showSnackbar("Local file missing, streaming from network")
            } else if (localFile.isDirectory) {
                Log.e("StreamDebug", "Selected file is a directory: ${localFile.absolutePath}")
                showSnackbar("Cannot stream a directory. Please select a file.")
                return
            } else if (isOnSdCard(localFile)) {
                // SD card file: use SAF
                if (sdCardUri == null) {
                    showSnackbar("SD card access required. Please select the SD card root.")
                    openDocumentTreeLauncher.launch(null)
                    return
                }
                val contentUri = getSdCardContentUri(localFile)
                if (contentUri == null) {
                    showSnackbar("Could not get SD card content URI. Please reselect SD card.")
                    openDocumentTreeLauncher.launch(null)
                    return
                }
                Log.d("StreamDebug", "Streaming SD file: ${localFile.absolutePath}, uri: $contentUri, mimeType: $mimeType")
                intent.setDataAndType(contentUri, mimeType)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.putExtra(Intent.EXTRA_TITLE, file.name)
                val vlcPackages = listOf("org.videolan.vlc", "com.huawei.vlc")
                var launched = false
                for (pkg in vlcPackages) {
                    intent.setPackage(pkg)
                    try {
                        startActivity(intent)
                        launched = true
                        break
                    } catch (e: Exception) {
                        Log.e("StreamDebug", "Error launching intent for package $pkg", e)
                    }
                }
                if (!launched) {
                    intent.setPackage(null)
                    try {
                        startActivity(Intent.createChooser(intent, "Open with"))
                    } catch (e: Exception) {
                        Log.e("StreamDebug", "No app found to open this file", e)
                        showSnackbar("No app found to open this file")
                    }
                }
                return
            } else {
                // Internal storage: use FileProvider
                try {
                    val localUri = FileProvider.getUriForFile(
                        this,
                        "${com.downloadmanager.app.BuildConfig.APPLICATION_ID}.fileprovider",
                        localFile
                    )
                    Log.d("StreamDebug", "Streaming local file: ${localFile.absolutePath}, uri: $localUri, mimeType: $mimeType")
                    intent.setDataAndType(localUri, mimeType)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.putExtra(Intent.EXTRA_TITLE, file.name)
                    val vlcPackages = listOf("org.videolan.vlc", "com.huawei.vlc")
                    var launched = false
                    for (pkg in vlcPackages) {
                        intent.setPackage(pkg)
                        try {
                            startActivity(intent)
                            launched = true
                            break
                        } catch (e: Exception) {
                            Log.e("StreamDebug", "Error launching intent for package $pkg", e)
                        }
                    }
                    if (!launched) {
                        intent.setPackage(null)
                        try {
                            startActivity(Intent.createChooser(intent, "Open with"))
                        } catch (e: Exception) {
                            Log.e("StreamDebug", "No app found to open this file", e)
                            showSnackbar("No app found to open this file")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("StreamDebug", "Error getting URI for file: ${localFile.absolutePath}", e)
                    showSnackbar("Error streaming local file: ${e.message}")
                }
                return
            }
        } else {
            intent.setDataAndType(android.net.Uri.parse(file.url), mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra(Intent.EXTRA_TITLE, file.name)
            val vlcPackages = listOf("org.videolan.vlc", "com.huawei.vlc")
            var launched = false
            for (pkg in vlcPackages) {
                intent.setPackage(pkg)
                try {
                    startActivity(intent)
                    launched = true
                    break
                } catch (e: Exception) {
                    Log.e("StreamDebug", "Error launching intent for package $pkg", e)
                }
            }
            if (!launched) {
                intent.setPackage(null)
                try {
                    startActivity(Intent.createChooser(intent, "Open with"))
                } catch (e: Exception) {
                    Log.e("StreamDebug", "No app found to open this file", e)
                    showSnackbar("No app found to open this file")
                }
            }
            return
        }
    }

    private fun createAndStreamPlaylist(files: List<DownloadFile>) {
        if (!isStorageAvailable()) {
            showSnackbar("Storage not available. Please check your storage or SD card.")
            return
        }
        try {
            val downloadsDir = viewModel.getDownloadDir()
            val isSdCard = downloadsDir.absolutePath.startsWith("/storage/") && !downloadsDir.absolutePath.startsWith("/storage/emulated/0/")
            val playlistFile: File
            var playlistUri: Uri? = null
            if (isSdCard) {
                // Use SAF to create playlist file on SD card
                if (sdCardUri == null) {
                    showSnackbar("SD card access required. Please select the SD card folder.")
                    openDocumentTreeLauncher.launch(null)
                    return
                }
                val baseUri = sdCardUri!!
                val basePath = DocumentsContract.getTreeDocumentId(baseUri)
                val playlistName = "playlist_${System.currentTimeMillis()}.m3u"
                val playlistContent = buildPlaylistContent(files)
                // Correctly get the parent document URI for the folder
                val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(baseUri, basePath)
                val docUri = DocumentsContract.createDocument(contentResolver, parentDocumentUri, "audio/x-mpegurl", playlistName)
                if (docUri != null) {
                    contentResolver.openOutputStream(docUri)?.use { it.write(playlistContent.toByteArray()) }
                    playlistUri = docUri
                    lastPlaylistUri = docUri // Track for cleanup
                    lastPlaylistFile = null
                } else {
                    showSnackbar("Failed to create playlist file on SD card.")
                    return
                }
            } else {
                // Internal storage: use FileProvider
                playlistFile = createPlaylistFile(files)
                playlistUri = FileProvider.getUriForFile(
                    this,
                    "${com.downloadmanager.app.BuildConfig.APPLICATION_ID}.fileprovider",
                    playlistFile
                )
                lastPlaylistFile = playlistFile // Track for cleanup
                lastPlaylistUri = null
            }
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(playlistUri, "audio/x-mpegurl")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val vlcPackages = listOf("org.videolan.vlc", "com.huawei.vlc")
            var launched = false
            for (pkg in vlcPackages) {
                intent.setPackage(pkg)
                try {
                    startActivity(intent)
                    launched = true
                    break
                } catch (e: Exception) {
                    // Try next package
                }
            }
            if (!launched) {
                intent.setPackage(null)
                try {
                    startActivity(Intent.createChooser(intent, "Open playlist with"))
                    val localFiles = files.count { it.isCompletelyDownloaded() }
                    val networkFiles = files.size - localFiles
                    val message = "Opening playlist with ${files.size} files\n" +
                        "\uD83D\uDCC1 Local: $localFiles | \uD83C\uDF10 Network: $networkFiles"
                    showSnackbar(message)
                } catch (e: Exception) {
                    showSnackbar("No app found to open playlist. Try VLC or MX Player")
                }
            }
        } catch (e: Exception) {
            Log.e("Playlist", "Error creating playlist: ${e.message}", e)
            showSnackbar("Error creating playlist: ${e.message}")
        }
    }

    private fun createPlaylistFile(files: List<DownloadFile>): File {
        val downloadsDir = viewModel.getDownloadDir()
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val playlistFile = File(downloadsDir, "playlist_${System.currentTimeMillis()}.m3u")
        val playlistContent = StringBuilder()
        playlistContent.append("#EXTM3U\n")
        playlistContent.append("# Playlist created by Download Manager\n")
        playlistContent.append("# Priority: Local files first, then network files\n\n")
        val sortedFiles = files.sortedWith(compareBy({ !it.isCompletelyDownloaded() }, { it.name }))
        sortedFiles.forEach { file ->
            if (file.isCompletelyDownloaded()) {
                val localPath = file.getLocalPath()
                playlistContent.append("#EXTINF:-1,${file.name} (LOCAL)\n")
                playlistContent.append("file://$localPath\n\n")
            } else {
                playlistContent.append("#EXTINF:-1,${file.name} (NETWORK)\n")
                playlistContent.append("${file.url}\n\n")
            }
        }
        playlistFile.writeText(playlistContent.toString())
        return playlistFile
    }

    private fun buildPlaylistContent(files: List<DownloadFile>): String {
        val playlistContent = StringBuilder()
        playlistContent.append("#EXTM3U\n")
        playlistContent.append("# Playlist created by Download Manager\n")
        playlistContent.append("# Priority: Local files first, then network files\n\n")
        val sortedFiles = files.sortedWith(compareBy({ !it.isCompletelyDownloaded() }, { it.name }))
        sortedFiles.forEach { file ->
            if (file.isCompletelyDownloaded()) {
                val localPath = file.getLocalPath()
                playlistContent.append("#EXTINF:-1,${file.name} (LOCAL)\n")
                playlistContent.append("file://$localPath\n\n")
            } else {
                playlistContent.append("#EXTINF:-1,${file.name} (NETWORK)\n")
                playlistContent.append("${file.url}\n\n")
            }
        }
        return playlistContent.toString()
    }
    
    private fun getMimeType(url: String): String {
        val extension = url.substringAfterLast(".").lowercase()
        return when (extension) {
            "mp4", "mkv", "avi", "mov" -> "video/*"
            "mp3", "wav", "flac" -> "audio/*"
            "pdf" -> "application/pdf"
            else -> "*/*"
        }
    }
    
    private fun loadStorageDir() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedPath = prefs.getString(KEY_STORAGE_PATH, null)
        viewModel.setCurrentStorageDir(if (savedPath != null) File(savedPath) else getDefaultStorageDir())
        com.downloadmanager.app.model.DownloadFile.setDownloadDirectory(viewModel.getDownloadDir())
    }

    private fun saveStorageDir(dir: File) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(KEY_STORAGE_PATH, dir.absolutePath).apply()
        viewModel.setCurrentStorageDir(dir)
        com.downloadmanager.app.model.DownloadFile.setDownloadDirectory(viewModel.getDownloadDir())
        updateStorageInfo()
    }

    private fun getDefaultStorageDir(): File {
        // Prefer SD card public Download/DownloadManager if available
        val externalDirs = getExternalFilesDirs(null)
        if (externalDirs.size > 1 && externalDirs[1] != null) {
            // Get SD card root
            val sdRoot = File(externalDirs[1].absolutePath)
            // Go up to /storage/XXXX-XXXX
            val sdRootPath = sdRoot.absolutePath.substringBefore("/Android/")
            val sdDownloadManager = File(sdRootPath, "Download/DownloadManager")
            if (sdDownloadManager.exists() || sdDownloadManager.mkdirs()) {
                return sdDownloadManager
            }
        }
        // Fallback to internal storage public Download/DownloadManager
        return File(android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS), "DownloadManager")
    }

    private fun getAvailableStorageDirs(): List<Pair<String, File>> {
        val dirs = mutableListOf<Pair<String, File>>()
        // Internal storage
        val internal = File(android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS), "DownloadManager")
        dirs.add(Pair("Internal Storage", internal))
        // SD card (if available)
        val externalDirs = getExternalFilesDirs(null)
        if (externalDirs.size > 1 && externalDirs[1] != null) {
            val sdRoot = File(externalDirs[1].absolutePath)
            val sdRootPath = sdRoot.absolutePath.substringBefore("/Android/")
            val sdDownloadManager = File(sdRootPath, "Download/DownloadManager")
            dirs.add(Pair("SD Card", sdDownloadManager))
        }
        return dirs
    }

    private fun getDownloadDir(subfolder: String? = null): File {
        val baseDir = viewModel.currentStorageDir.value ?: getDefaultStorageDir()
        return if (subfolder.isNullOrEmpty()) baseDir else File(baseDir, subfolder)
    }

    private fun updateStorageInfo() {
        val dir = viewModel.getDownloadDir()
        if (!dir.exists()) dir.mkdirs()
        val freeSpace = dir.freeSpace
        val totalSpace = dir.totalSpace
        val freeSpaceFormatted = formatFileSize(freeSpace)
        val totalSpaceFormatted = formatFileSize(totalSpace)
        textViewStorageInfo.text = "${dir.absolutePath}\nFree: $freeSpaceFormatted\nTotal: $totalSpaceFormatted"
    }

    private fun showStorageSelectionDialog() {
        val dirs = getAvailableStorageDirs()
        val names = dirs.map { it.first }.toTypedArray()
        val current = dirs.indexOfFirst { it.second.absolutePath == viewModel.getDownloadDir().absolutePath }
        android.app.AlertDialog.Builder(this)
            .setTitle("Select Storage Location")
            .setSingleChoiceItems(names, current) { dialog, which ->
                val selectedDir = dirs[which].second
                saveStorageDir(selectedDir)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestPermissionsIfNeeded() {
        val permissionsToRequest = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToRequest.isNotEmpty()) {
            // Show rationale if needed
            val showRationale = permissionsToRequest.any {
                ActivityCompat.shouldShowRequestPermissionRationale(this, it)
            }
            if (showRationale) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Permissions Required")
                    .setMessage("This app needs storage permissions to download and manage files.")
                    .setPositiveButton("OK") { _, _ ->
                        requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up handlers to prevent memory leaks
        progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun showSnackbar(message: String, long: Boolean = false) {
        val rootView = findViewById<View>(android.R.id.content)
        Snackbar.make(rootView, message, if (long) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT).show()
    }

    private fun isStorageAvailable(): Boolean {
        val dir = viewModel.getDownloadDir()
        return dir.exists() && dir.canWrite()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isOnSdCard(file: File): Boolean {
        val path = file.absolutePath
        return path.startsWith("/storage/") && !path.startsWith("/storage/emulated/0/")
    }

    private fun getSdCardContentUri(file: File): Uri? {
        val baseUri = sdCardUri ?: return null
        val basePath = DocumentsContract.getTreeDocumentId(baseUri)
        val filePath = file.absolutePath
        // Find the absolute path of the selected folder
        val baseFolder = when {
            basePath.startsWith("primary:") ->
                "/storage/emulated/0/" + basePath.removePrefix("primary:")
            basePath.contains(":") ->
                "/storage/" + basePath.substringBefore(":") + "/" + basePath.substringAfter(":")
            else -> "/storage/" + basePath
        }.trimEnd('/')
        // Check if file is within the selected folder
        if (!filePath.startsWith(baseFolder)) return null
        val relPath = filePath.removePrefix(baseFolder).trimStart('/')
        val docId = if (relPath.isEmpty()) basePath else "$basePath/$relPath"
        return DocumentsContract.buildDocumentUriUsingTree(baseUri, docId)
    }

    // Helper to get the subfolder name from the current URL
    private fun getSubfolderNameFromUrl(): String {
        val url = editTextUrl.text.toString().trim()
        if (url.isEmpty()) return "Downloads"
        val sanitized = url.trimEnd('/').substringAfterLast('/')
        // Remove any invalid filesystem characters
        return sanitized.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    private fun observeViewModel() {
        viewModel.currentFiles.observe(this) { files ->
            val adapter = recyclerViewFiles.adapter as? FileAdapter
            adapter?.updateFiles(files)
            textViewFileCount.text = "Files: ${files.size}"
            updateSelectedSizeInfo()
        }
        viewModel.selectedFiles.observe(this) { selected ->
            textViewSelectedCount.text = "Selected: ${selected.size}"
            updateActionButtons()
            updateSelectedSizeInfo()
        }
    }
} 
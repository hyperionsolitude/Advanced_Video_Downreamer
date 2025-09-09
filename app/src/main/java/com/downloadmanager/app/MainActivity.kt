package com.downloadmanager.app

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.downloadmanager.app.adapter.FileAdapter
import com.downloadmanager.app.databinding.ActivityMainBinding
import com.downloadmanager.app.model.DownloadFile
import com.downloadmanager.app.model.FileUtils
import com.downloadmanager.app.utils.ErrorHandler
import com.downloadmanager.app.utils.Logger
import com.downloadmanager.app.utils.MemoryManager
import com.downloadmanager.app.viewmodel.MainViewModel
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.net.ConnectException
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val USER_AGENT = "Mozilla/5.0 (Android) Advanced Video Downreamer"
        private const val CONNECT_TIMEOUT_SHORT_MS = 3_000
        private const val READ_TIMEOUT_SHORT_MS = 3_000
        private const val BYTES_IN_KILOBYTE = 1_024
        private const val BYTES_IN_MEGABYTE = 1_024 * 1_024
        private const val BYTES_IN_GIGABYTE = 1_024 * 1_024 * 1_024
        private const val DEFAULT_PERMISSION_REQUEST_CODE = 123
        private const val DEFAULT_PROGRESS_UPDATE_INTERVAL_MS = 200L
        private const val JSOUP_TIMEOUT_MS = 8_000
        private const val PERCENT_0 = 0
        private const val PERCENT_2 = 2
        private const val PERCENT_100 = 100
        private const val RETRY_DELAY_MS = 2000L
        private const val MAX_RETRIES = 3
    }

    private lateinit var editTextUrl: EditText

    private lateinit var buttonFetch: Button
    private lateinit var textViewStorageInfo: TextView
    private lateinit var buttonChangeStorage: Button
    private lateinit var editTextFilter: EditText
    private lateinit var textViewFileCount: TextView
    internal lateinit var textViewSelectedCount: TextView
    private lateinit var buttonSelectAll: Button
    private lateinit var buttonDeselectAll: Button
    internal lateinit var buttonDownload: Button
    internal lateinit var buttonStream: Button
    private lateinit var progressBar: ProgressBar
    internal lateinit var recyclerViewFiles: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    internal lateinit var textViewSelectedSize: TextView
    private lateinit var buttonInvertSelection: com.google.android.material.button.MaterialButton
    private lateinit var buttonSelectNone: com.google.android.material.button.MaterialButton

    @Suppress("UnusedPrivateProperty")
    private val permissionRequestCode = DEFAULT_PERMISSION_REQUEST_CODE
    private val requiredPermissions = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    internal val viewModel: MainViewModel by viewModels()

    private val requestPermissionsLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                showSnackbar("All permissions granted!")
            } else {
                showSnackbar("Some permissions were denied")
            }
        }

    internal lateinit var binding: ActivityMainBinding
    private val prefsName = "download_manager_prefs"
    private val keyStoragePath = "storage_path"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val keySdUri = "sd_card_uri"
    private lateinit var prefs: SharedPreferences
    internal var sdCardUri: Uri? = null

    // Delegates
    private val fileFetcher = FileFetcher(
        userAgent = USER_AGENT,
        jsoupTimeoutMs = JSOUP_TIMEOUT_MS,
        getSubfolderName = { getSubfolderNameFromUrl() },
        getFileType = { href -> getFileType(href) },
        getFileSize = { url -> getFileSize(url) }
    )
    private val storageController by lazy { StorageController(this) }

    // Performance optimization: Throttle UI updates
    private val progressUpdateHandler = Handler(Looper.getMainLooper())
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            // Batch update all progress bars
            val adapter = recyclerViewFiles.adapter as? FileAdapter
            adapter?.flushProgressUpdates()
        }
    }
    private var lastProgressUpdate = 0L
    private val progressUpdateInterval = DEFAULT_PROGRESS_UPDATE_INTERVAL_MS // Optimized
    private val progressUpdateQueue = mutableMapOf<String, Int>() // Queue progress updates

    // Performance optimization: Coroutine scope for downloads
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Performance optimization: Memory-aware caches
    private val fileTypeCache = MemoryManager.MemoryAwareCache<String, String>()
    private val fileSizeCache = MemoryManager.MemoryAwareCache<String, String>()

    // Memory management
    private val memoryManager = MemoryManager
    // OpenDocumentTree launcher is no longer needed after delegating playback

    // Store the last playlist file/URI for cleanup
    internal var lastPlaylistFile: File? = null
    internal var lastPlaylistUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.d("MainActivity", "onCreate started")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        sdCardUri = prefs.getString(keySdUri, null)?.let { Uri.parse(it) }

        Logger.d("MainActivity", "Memory usage: ${memoryManager.getMemoryUsagePercentage(this)}%")
        Logger.d(
            "MainActivity",
            "Available memory: ${memoryManager.getAvailableMemory(this) / BYTES_IN_MEGABYTE}MB"
        )

        initializeViews()
        setupClickListeners()
        observeViewModel()
        requestPermissionsIfNeeded()
        loadStorageDir()
        updateStorageInfo()

        Logger.d("MainActivity", "onCreate completed")
    }

    override fun onResume() {
        super.onResume()

        // Memory management: Clear caches if low memory
        if (memoryManager.shouldClearCache(this)) {
            fileTypeCache.clear()
            fileSizeCache.clear()
            Logger.d("MemoryManager", "Cleared caches due to low memory")
        }

        // Delete any zero-length files and partial downloads for all current files
        for (file in viewModel.currentFiles.value ?: emptyList()) {
            val fileName = file.name.ifEmpty { file.url.substringAfterLast("/") }
            FileUtils.deleteFileIfZeroLength(
                viewModel.currentStorageDir.value,
                fileName,
                file.subfolder
            )
            // Also clean up partial downloads (files that exist but are not complete)
            if (file.isDownloaded() && !file.isCompletelyDownloaded()) {
                val localFile = FileUtils.getLocalFile(
                    viewModel.currentStorageDir.value,
                    fileName,
                    file.subfolder
                )
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
        } catch (e: SecurityException) {
            Log.e("Playlist", "Security error cleaning up playlist: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            Log.e("Playlist", "Invalid argument cleaning up playlist: ${e.message}", e)
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
        val adapter = FileAdapter(
            onFileSelected = { file, isSelected ->
                handleFileSelection(file, isSelected)
            },
            getSelectedFiles = { viewModel.selectedFiles.value ?: emptySet() }
        )
        recyclerViewFiles.adapter = adapter
        // No longer needed; adapter updates via notifyItemChanged

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
        editTextFilter.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) { /* no-op */ }
                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) { /* no-op */ }
                override fun afterTextChanged(s: android.text.Editable?) {
                    filterFiles(s.toString())
                }
            }
        )
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

    internal fun fetchFiles(url: String) {
        Logger.d("MainActivity", "fetchFiles started for URL: $url")

        if (!ErrorHandler.isNetworkAvailable(this)) {
            val errorMessage = ErrorHandler.getUserFriendlyMessage(
                ConnectException("No network connection"),
                this
            )
            Logger.w("MainActivity", "No network connection available")
            showSnackbar(errorMessage)
            swipeRefresh.isRefreshing = false
            return
        }

        Logger.d("MainActivity", "Network available, starting file fetch")
        progressBar.visibility = View.VISIBLE
        buttonFetch.isEnabled = false
        swipeRefresh.isRefreshing = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val files = determineFilesForUrl(url)
                cleanupFetchedFiles(files)
                withContext(Dispatchers.Main) {
                    updateUiAfterFetch(files)
                }
            } catch (e: java.io.IOException) {
                handleFetchError(e)
            } catch (e: IllegalArgumentException) {
                handleFetchError(e)
            }
        }
    }

    private fun determineFilesForUrl(url: String): List<DownloadFile> {
        return when {
            url.endsWith("/") || isDirectoryUrl(url) -> {
                fileFetcher.fetchFromDirectory(url)
            }
            isDirectFileUrl(url) -> {
                listOf(createDownloadFileFromUrl(url))
            }
            else -> {
                fileFetcher.fetchFromHtml(url)
            }
        }
    }

    private suspend fun handleFetchError(e: Exception) {
        Logger.e("MainActivity", "Error fetching files: ${e.message}", e)
        val errorMessage = ErrorHandler.getUserFriendlyMessage(e, this@MainActivity)
        val suggestion = ErrorHandler.getSuggestedAction(e, this@MainActivity)
        withContext(Dispatchers.Main) {
            progressBar.visibility = View.GONE
            buttonFetch.isEnabled = true
            swipeRefresh.isRefreshing = false
            showSnackbar("$errorMessage\n$suggestion", true)
        }
    }

    private fun cleanupFetchedFiles(files: List<DownloadFile>) {
        for (file in files) {
            val fileName = file.name.ifEmpty { file.url.substringAfterLast("/") }
            FileUtils.deleteFileIfZeroLength(
                viewModel.currentStorageDir.value,
                fileName,
                file.subfolder
            )
            if (file.isDownloaded() && !file.isCompletelyDownloaded()) {
                val localFile = FileUtils.getLocalFile(
                    viewModel.currentStorageDir.value,
                    fileName,
                    file.subfolder
                )
                if (localFile.exists()) {
                    FileUtils.safeDelete(localFile)
                }
            }
        }
    }

    private fun updateUiAfterFetch(files: List<DownloadFile>) {
        Logger.d("MainActivity", "Successfully fetched ${files.size} files")
        viewModel.setFiles(files)
        textViewFileCount.text = "Files: ${viewModel.currentFiles.value?.size ?: 0}"
        textViewSelectedCount.text = "Selected: 0"
        progressBar.visibility = View.GONE
        buttonFetch.isEnabled = true
        swipeRefresh.isRefreshing = false
        showSnackbar("Fetched ${files.size} files")
    }

    // delegated to FileFetcher

    // delegated to FileFetcher

    // delegated to FileFetcher

    private fun createDownloadFileFromUrl(url: String): DownloadFile {
        val fileName = url.substringAfterLast("/")
        val fileType = getFileType(url)
        val fileSize = getFileSize(url)
        val subfolder = getSubfolderNameFromUrl()
        return DownloadFile(fileName, url, fileSize, fileType, subfolder)
    }

    private fun isValidFileUrl(url: String): Boolean {
        val fileExtensions = listOf(
            ".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm",
            ".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a",
            ".pdf", ".zip", ".rar", ".7z", ".txt", ".doc", ".docx"
        )
        return fileExtensions.any { url.lowercase().contains(it) }
    }

    private fun isDirectFileUrl(url: String): Boolean {
        return isValidFileUrl(url) && !url.endsWith("/")
    }

    private fun isDirectoryUrl(url: String): Boolean {
        return url.endsWith("/") || !url.contains(".")
    }

    private fun getFileType(url: String): String {
        return fileTypeCache.get(url) ?: run {
            val extension = url.substringAfterLast(".").lowercase()
            val fileType = when (extension) {
                "mp3", "wav", "flac", "aac", "ogg", "m4a" -> "audio"
                "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm" -> "video"
                "pdf" -> "document"
                "zip", "rar", "7z" -> "archive"
                else -> "file"
            }
            fileTypeCache.put(url, fileType)
            fileType
        }
    }

    private fun getFileSize(url: String): String {
        return fileSizeCache.get(url) ?: run {
            val fileSize = try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = CONNECT_TIMEOUT_SHORT_MS // Reduced timeout
                connection.readTimeout = READ_TIMEOUT_SHORT_MS
                connection.setRequestProperty(
                    "User-Agent",
                    USER_AGENT
                )
                val contentLength = connection.contentLength
                if (contentLength > 0) {
                    formatFileSize(contentLength.toLong())
                } else {
                    "Unknown size"
                }
            } catch (e: java.io.IOException) {
                Logger.w("MainActivity", "IO error getting file size for $url: ${e.message}")
                "Unknown size"
            } catch (e: SecurityException) {
                Logger.w("MainActivity", "Security error getting file size for $url: ${e.message}")
                "Unknown size"
            }
            fileSizeCache.put(url, fileSize)
            fileSize
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < BYTES_IN_KILOBYTE -> "$size B"
            size < BYTES_IN_MEGABYTE ->
                String.format(Locale.US, "%.1f KB", size / BYTES_IN_KILOBYTE.toDouble())
            size < BYTES_IN_GIGABYTE ->
                String.format(
                    Locale.US,
                    "%.1f MB",
                    size / BYTES_IN_MEGABYTE.toDouble()
                )
            else ->
                String.format(
                    Locale.US,
                    "%.2f GB",
                    size / BYTES_IN_GIGABYTE.toDouble()
                )
        }
    }

    private val selectionController by lazy { SelectionController(this) }
    private fun handleFileSelection(file: DownloadFile, isSelected: Boolean) {
        selectionController.handleFileSelection(file, isSelected)
    }

    private fun selectAllFiles() { selectionController.selectAllFiles() }

    private fun deselectAllFiles() { selectionController.deselectAllFiles() }

    private fun invertSelection() { selectionController.invertSelection() }

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

    internal fun updateActionButtons() { selectionController.updateActionButtons() }

    internal fun updateSelectedSizeInfo() { selectionController.updateSelectedSizeInfo() }

    // moved to SelectionController

    private fun validatePreconditionsForDownloads(): Boolean {
        val hasStorage = ErrorHandler.isStorageAvailable(viewModel.getDownloadDir().absolutePath)
        val hasNetwork = ErrorHandler.isNetworkAvailable(this)

        if (!hasStorage) {
            val errorMessage = ErrorHandler.getUserFriendlyMessage(
                SecurityException("Storage not available"),
                this
            )
            Logger.w("MainActivity", "Storage not available")
            showSnackbar(errorMessage)
        } else if (!hasNetwork) {
            val errorMessage = ErrorHandler.getUserFriendlyMessage(
                ConnectException("No network connection"),
                this
            )
            Logger.w("MainActivity", "No network connection")
            showSnackbar(errorMessage)
        }

        return hasStorage && hasNetwork
    }

    private suspend fun validateDownloadPreconditions(): Boolean {
        val hasStorage = ErrorHandler.isStorageAvailable(viewModel.getDownloadDir().absolutePath)
        val hasNetwork = ErrorHandler.isNetworkAvailable(this@MainActivity)

        if (!hasStorage) {
            val errorMessage = ErrorHandler.getUserFriendlyMessage(
                SecurityException("Storage not available"),
                this@MainActivity
            )
            runOnUiThread { showSnackbar(errorMessage) }
        } else if (!hasNetwork) {
            val errorMessage = ErrorHandler.getUserFriendlyMessage(
                ConnectException("No network connection"),
                this@MainActivity
            )
            runOnUiThread { showSnackbar(errorMessage) }
        }

        return hasStorage && hasNetwork
    }

    private fun filesPendingDownload(): List<DownloadFile> {
        return viewModel.currentFiles.value?.filter {
            viewModel.selectedFiles.value?.contains(it.url) == true && !it.isCompletelyDownloaded()
        } ?: emptyList()
    }

    private fun launchDownload(file: DownloadFile) {
        val adapter = recyclerViewFiles.adapter as? FileAdapter
        adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.STARTED)
        downloadScope.launch {
            try {
                downloadFile(file)
            } catch (e: SecurityException) {
                Logger.e(
                    "MainActivity",
                    "Security error downloading ${file.name}: ${e.message}",
                    e
                )
                val errorMessage = ErrorHandler.getUserFriendlyMessage(e, this@MainActivity)
                withContext(Dispatchers.Main) {
                    adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.FAILED)
                    adapter?.updateProgressOnly(file.url, PERCENT_100)
                    adapter?.flushProgressUpdates()
                    showSnackbar("Error downloading ${file.name}: $errorMessage")
                }
            } catch (e: java.io.IOException) {
                Logger.e(
                    "MainActivity",
                    "IO error downloading ${file.name}: ${e.message}",
                    e
                )
                val errorMessage = ErrorHandler.getUserFriendlyMessage(e, this@MainActivity)
                withContext(Dispatchers.Main) {
                    adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.FAILED)
                    adapter?.updateProgressOnly(file.url, PERCENT_100)
                    adapter?.flushProgressUpdates()
                    showSnackbar("Error downloading ${file.name}: $errorMessage")
                }
            } catch (e: IllegalArgumentException) {
                Logger.e(
                    "MainActivity",
                    "Invalid argument downloading ${file.name}: ${e.message}",
                    e
                )
                val errorMessage = ErrorHandler.getUserFriendlyMessage(e, this@MainActivity)
                withContext(Dispatchers.Main) {
                    adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.FAILED)
                    adapter?.updateProgressOnly(file.url, PERCENT_100)
                    adapter?.flushProgressUpdates()
                    showSnackbar("Error downloading ${file.name}: $errorMessage")
                }
            }
        }
    }

    internal fun startDownloadForSelectedFiles() {
        Logger.d("MainActivity", "startDownloadForSelectedFiles started")
        if (!validatePreconditionsForDownloads()) {
            return
        }
        val filesToDownload = filesPendingDownload()
        if (filesToDownload.isEmpty()) {
            Logger.i("MainActivity", "All selected files are already downloaded")
            showSnackbar("All selected files are already downloaded.")
            return
        }
        Logger.d("MainActivity", "Files to download: ${filesToDownload.size}")
        for (file in filesToDownload) {
            launchDownload(file)
        }
    }

    private fun prepareOutputFile(file: DownloadFile): java.io.File? {
        val downloadsDir = FileUtils.getDownloadDir(
            viewModel.currentStorageDir.value,
            file.subfolder
        )
        FileUtils.ensureDirExists(downloadsDir)
        val rawName = file.name.ifEmpty { file.url.substringAfterLast("/") }
        val fileName = FileUtils.sanitizeFileName(rawName)
        val outputFile = FileUtils.getLocalFile(
            viewModel.currentStorageDir.value,
            fileName,
            file.subfolder
        )
        Logger.d("DownloadDebug", "Preparing to download: ${outputFile.absolutePath}")
        Logger.d(
            "DownloadDebug",
            "Exists: ${outputFile.exists()}, IsFile: ${outputFile.isFile}, " +
                "IsDir: ${outputFile.isDirectory}"
        )
        if (outputFile.exists()) {
            FileUtils.safeDelete(outputFile)
        }
        return if (outputFile.exists()) null else outputFile
    }

    private fun openConnection(urlStr: String): java.net.URLConnection {
        val connection = java.net.URL(urlStr).openConnection()
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Connection", "close") // Use close instead of keep-alive
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("Range", "bytes=0-") // Support range requests
        return connection
    }

    private fun updateProgress(fileUrl: String, roundedProgress: Int) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProgressUpdate > progressUpdateInterval) {
            runOnUiThread {
                val adapter = recyclerViewFiles.adapter as? FileAdapter
                adapter?.updateDownloadProgress(fileUrl, roundedProgress)
            }
            lastProgressUpdate = currentTime
        } else {
            progressUpdateQueue[fileUrl] = roundedProgress
        }
    }

    private fun finalizeDownload(file: DownloadFile) {
        runOnUiThread {
            val adapter = recyclerViewFiles.adapter as? FileAdapter
            adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.COMPLETE)
            adapter?.updateProgressOnly(file.url, PERCENT_100)
            adapter?.flushProgressUpdates()
            showSnackbar("Downloaded: ${file.name}")
        }
    }

    private val downloadController = DownloadController(
        bufferSizeProvider = { memoryManager.getRecommendedBufferSize(this@MainActivity) }
    )

    @Suppress("LongMethod")
    private suspend fun downloadFile(file: DownloadFile) = withContext(Dispatchers.IO) {
        if (!validateDownloadPreconditions()) return@withContext

        val outputFile = prepareOutputFile(file)
        if (outputFile == null) {
            runOnUiThread {
                showSnackbar(
                    "Cannot download: File or directory still exists after deletion. " +
                        "Please check storage or restart device."
                )
            }
            Logger.e(
                "DownloadDebug",
                "File still exists after deletion for ${file.url}"
            )
            return@withContext
        }

        try {
            runOnUiThread {
                val adapter = recyclerViewFiles.adapter as? FileAdapter
                adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.DOWNLOADING)
                adapter?.updateDownloadProgress(file.url, PERCENT_0)
            }

            Logger.d("DownloadDebug", "Starting download to: ${outputFile.absolutePath}")

            // Try download with retry logic
            var retryCount = 0
            val maxRetries = MAX_RETRIES
            var success = false

            while (retryCount < maxRetries && !success) {
                try {
                    val connection = openConnection(file.url)
                    downloadController.performDownload(
                        connection = connection,
                        outputFile = outputFile,
                        onProgress = { progress ->
                            val rounded = (progress / PERCENT_2) * PERCENT_2
                            updateProgress(file.url, rounded)
                        }
                    )
                    success = true
                    Logger.d(
                        "DownloadDebug",
                        "Download completed successfully on attempt ${retryCount + 1}"
                    )
                } catch (e: java.io.IOException) {
                    retryCount++
                    Logger.w("DownloadDebug", "Download attempt $retryCount failed: ${e.message}")
                    if (retryCount < maxRetries) {
                        Logger.d("DownloadDebug", "Retrying download in 2 seconds...")
                        kotlinx.coroutines.delay(RETRY_DELAY_MS) // Wait 2 seconds before retry
                    } else {
                        throw e
                    }
                }
            }

            val completedMsg = "Download completed. Exists=${outputFile.exists()} " +
                "Size=${outputFile.length()}"
            Logger.d("DownloadDebug", completedMsg)
            finalizeDownload(file)
        } catch (e: java.io.IOException) {
            runOnUiThread {
                val adapter = recyclerViewFiles.adapter as? FileAdapter
                adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.FAILED)
                adapter?.updateProgressOnly(file.url, PERCENT_100)
                adapter?.flushProgressUpdates()
                showSnackbar("I/O error downloading ${file.name}: ${e.message}")
            }
            Logger.e(
                "DownloadDebug",
                "Error downloading file: ${e.message}",
                e
            )
        } catch (e: SecurityException) {
            runOnUiThread {
                val adapter = recyclerViewFiles.adapter as? FileAdapter
                adapter?.setDownloadStatus(file.url, FileAdapter.DownloadStatus.FAILED)
                adapter?.updateProgressOnly(file.url, PERCENT_100)
                adapter?.flushProgressUpdates()
                showSnackbar("Security error downloading ${file.name}: ${e.message}")
            }
            Logger.e("DownloadDebug", "Security error downloading file: ${e.message}", e)
        }
    }

    private val playbackController by lazy { PlaybackController(this) }
    internal fun streamSelectedFiles() { playbackController.streamSelectedFiles() }

    private fun streamFileWithPriority(file: DownloadFile) {
        playbackController.streamFileWithPriority(file)
    }

    private fun createAndStreamPlaylist(files: List<DownloadFile>) {
        playbackController.createAndStreamPlaylist(files)
    }

    private fun loadStorageDir() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val savedPath = prefs.getString(keyStoragePath, null)
        viewModel.setCurrentStorageDir(
            if (savedPath != null) File(savedPath) else getDefaultStorageDir()
        )
        com.downloadmanager.app.model.DownloadFile.setDownloadDirectory(viewModel.getDownloadDir())
    }

    private fun saveStorageDir(dir: File) {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        prefs.edit().putString(keyStoragePath, dir.absolutePath).apply()
        viewModel.setCurrentStorageDir(dir)
        com.downloadmanager.app.model.DownloadFile.setDownloadDirectory(viewModel.getDownloadDir())
        updateStorageInfo()
    }

    private fun getDefaultStorageDir(): File {
        return storageController.getDefaultStorageDir()
    }

    private fun getAvailableStorageDirs(): List<Pair<String, File>> {
        return storageController.getAvailableStorageDirs()
    }

    private fun getDownloadDir(subfolder: String? = null): File {
        val baseDir = viewModel.currentStorageDir.value ?: getDefaultStorageDir()
        return storageController.getDownloadDir(baseDir, subfolder)
    }

    private fun updateStorageInfo() {
        val dir = viewModel.getDownloadDir()
        if (!dir.exists()) dir.mkdirs()
        val freeSpace = dir.freeSpace
        val totalSpace = dir.totalSpace
        val freeSpaceFormatted = formatFileSize(freeSpace)
        val totalSpaceFormatted = formatFileSize(totalSpace)
        textViewStorageInfo.text =
            "${dir.absolutePath}\nFree: $freeSpaceFormatted\nTotal: $totalSpaceFormatted"
    }

    internal fun showStorageSelectionDialog() {
        val dirs = getAvailableStorageDirs()
        val names = dirs.map { it.first }.toTypedArray()
        val current = dirs.indexOfFirst {
            it.second.absolutePath == viewModel.getDownloadDir().absolutePath
        }
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
        val permissionsToRequest = requiredPermissions.filter {
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

        // Cancel all download coroutines
        downloadScope.cancel()

        // Clear caches to free memory
        fileTypeCache.clear()
        fileSizeCache.clear()
        progressUpdateQueue.clear()
    }

    internal fun showSnackbar(message: String, long: Boolean = false) {
        val rootView = findViewById<View>(android.R.id.content)
        Snackbar.make(
            rootView,
            message,
            if (long) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT
        ).show()
    }

    internal fun isStorageAvailable(): Boolean {
        val dir = viewModel.getDownloadDir()
        return dir.exists() && dir.canWrite()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            cm.activeNetwork
        } else {
            null
        }
        val capabilities = if (network != null) cm.getNetworkCapabilities(network) else null
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    // moved to Streamer usage; no longer needed here

    // moved to Streamer usage; no longer needed here

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
            // Notify adapter to update checkbox states
            val adapter = recyclerViewFiles.adapter as? FileAdapter
            adapter?.notifyDataSetChanged()
        }
    }
}

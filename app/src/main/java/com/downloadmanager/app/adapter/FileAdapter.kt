package com.downloadmanager.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.downloadmanager.app.R
import com.downloadmanager.app.model.DownloadFile
import com.google.android.material.progressindicator.LinearProgressIndicator

private fun setupSelectionUi(
    holder: FileAdapter.FileViewHolder,
    getSelectedFiles: () -> Set<String>,
    file: DownloadFile,
) {
    // Don't set isSelected to prevent purple flashing
    // Only the checkbox will indicate selection state
    // Parameters are kept for consistency with the interface
    // Suppress unused parameter warnings as they're part of the interface
    @Suppress("UNUSED_PARAMETER")
    val unused = holder to getSelectedFiles to file
}

private fun bindFileTexts(holder: FileAdapter.FileViewHolder, file: DownloadFile) {
    val statusText = if (file.isCompletelyDownloaded()) {
        "\uD83D\uDCC1 LOCAL"
    } else {
        "\uD83C\uDF10 NETWORK"
    }
    holder.textViewName.text = "$statusText - ${file.name}"
    val itemContext = holder.itemView.context
    if (file.isCompletelyDownloaded()) {
        holder.textViewName.setTextColor(
            ContextCompat.getColor(itemContext, R.color.status_local)
        )
    } else {
        holder.textViewName.setTextColor(
            ContextCompat.getColor(itemContext, R.color.status_network)
        )
    }
    holder.textViewUrl.text = file.url
    holder.textViewSize.text = file.size
    holder.textViewType.text = file.type
}

private fun setupCheckbox(
    holder: FileAdapter.FileViewHolder,
    getSelectedFiles: () -> Set<String>,
    file: DownloadFile,
    onFileSelected: (DownloadFile, Boolean) -> Unit,
) {
    val isSelected = getSelectedFiles().contains(file.url)

    // Temporarily disable the listener to prevent cascade effects during rebinding
    holder.checkBox.setOnCheckedChangeListener(null)
    holder.checkBox.isChecked = isSelected
    // Set checkbox state without logging

    // Set the listener after setting the checked state
    holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
        // Checkbox state changed
        // Defer the UI update to avoid RecyclerView layout conflicts
        holder.itemView.post {
            onFileSelected(file, isChecked)
        }
    }
}

private fun setupItemClick(
    holder: FileAdapter.FileViewHolder,
    file: DownloadFile,
    getSelectedFiles: () -> Set<String>,
    onFileSelected: (DownloadFile, Boolean) -> Unit,
) {
    holder.itemView.setOnClickListener {
        val currentState = getSelectedFiles().contains(file.url)
        val newState = !currentState
        // Item clicked
        onFileSelected(file, newState)
    }
}

private fun bindProgress(
    holder: FileAdapter.FileViewHolder,
    file: DownloadFile,
    downloadProgress: Map<String, Int>,
    percentMin: Int,
    percentMax: Int,
) {
    val progress = downloadProgress[file.url] ?: 0
    if (progress in percentMin..percentMax) {
        holder.progressBar.visibility = View.VISIBLE
        holder.progressBar.progress = progress
        val itemContext = holder.itemView.context
        val colorRes = if (progress == FileAdapter.PROGRESS_ERROR_VALUE) {
            R.color.progress_bar_error
        } else {
            R.color.progress_bar_fill
        }
        holder.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(itemContext, colorRes)
        )
    } else {
        holder.progressBar.visibility = View.GONE
    }
}

private fun bindStatus(
    holder: FileAdapter.FileViewHolder,
    file: DownloadFile,
    downloadStatus: Map<String, FileAdapter.DownloadStatus>,
) {
    val status = downloadStatus[file.url] ?: FileAdapter.DownloadStatus.PENDING
    when (status) {
        FileAdapter.DownloadStatus.PENDING -> {
            holder.textViewStatus.visibility = View.GONE
        }
        FileAdapter.DownloadStatus.STARTED -> {
            holder.textViewStatus.visibility = View.VISIBLE
            holder.textViewStatus.text = "Starting..."
            holder.textViewStatus.setTextColor(
                ContextCompat.getColor(holder.itemView.context, R.color.status_started)
            )
        }
        FileAdapter.DownloadStatus.DOWNLOADING -> {
            holder.textViewStatus.visibility = View.VISIBLE
            holder.textViewStatus.text = "Downloading"
            holder.textViewStatus.setTextColor(
                ContextCompat.getColor(holder.itemView.context, R.color.status_started)
            )
        }
        FileAdapter.DownloadStatus.COMPLETE -> {
            holder.textViewStatus.visibility = View.VISIBLE
            holder.textViewStatus.text = "Complete"
            holder.textViewStatus.setTextColor(
                ContextCompat.getColor(holder.itemView.context, R.color.status_complete)
            )
        }
        FileAdapter.DownloadStatus.FAILED -> {
            holder.textViewStatus.visibility = View.VISIBLE
            holder.textViewStatus.text = "Failed"
            holder.textViewStatus.setTextColor(
                ContextCompat.getColor(holder.itemView.context, R.color.status_failed)
            )
        }
    }
}

@Suppress("TooManyFunctions")
class FileAdapter(
    private val onFileSelected: (DownloadFile, Boolean) -> Unit,
    private val getSelectedFiles: () -> Set<String>,
) : ListAdapter<DownloadFile, FileAdapter.FileViewHolder>(DiffCallback) {
    private var recyclerView: androidx.recyclerview.widget.RecyclerView? = null

    companion object DiffCallback : DiffUtil.ItemCallback<DownloadFile>() {
        private const val PROGRESS_THROTTLE_MS = 150L
        private const val PERCENT_MIN = 0
        private const val PERCENT_MAX = 100
        private const val UPDATE_THROTTLE_MS = 100L
        const val PROGRESS_ERROR_VALUE = -1

        override fun areItemsTheSame(oldItem: DownloadFile, newItem: DownloadFile): Boolean {
            return oldItem.url == newItem.url
        }

        override fun areContentsTheSame(oldItem: DownloadFile, newItem: DownloadFile): Boolean {
            return oldItem == newItem
        }
    }
    private val downloadProgress = mutableMapOf<String, Int>() // url -> progress
    private val lastProgressUpdate = mutableMapOf<String, Long>() // url -> timestamp

    // Update UI at most every 150ms per item for better performance
    private val progressUpdateThrottle = PROGRESS_THROTTLE_MS
    private val progressUpdatePending = mutableSetOf<String>() // Track which items need UI updates

    // Download status tracking
    enum class DownloadStatus {
        PENDING, // Selected but not started
        STARTED, // Download has started
        DOWNLOADING, // Currently downloading
        COMPLETE, // Download completed
        FAILED, // Download failed
    }
    private val downloadStatus = mutableMapOf<String, DownloadStatus>() // url -> status

    class FileViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(
        itemView
    ) {
        val checkBox: CheckBox = itemView.findViewById(R.id.checkBoxFile)
        val textViewName: TextView = itemView.findViewById(R.id.textViewFileName)
        val textViewUrl: TextView = itemView.findViewById(R.id.textViewFileUrl)
        val textViewSize: TextView = itemView.findViewById(R.id.textViewFileSize)
        val textViewType: TextView = itemView.findViewById(R.id.textViewFileType)
        val textViewStatus: TextView = itemView.findViewById(R.id.textViewDownloadStatus)
        val progressBar: LinearProgressIndicator = itemView.findViewById(
            R.id.progressBarDownload
        )
    }

    override fun onAttachedToRecyclerView(recyclerView: androidx.recyclerview.widget.RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(
        recyclerView: androidx.recyclerview.widget.RecyclerView,
    ) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = getItem(position)
        setupSelectionUi(holder, getSelectedFiles, file)
        bindFileTexts(holder, file)
        setupCheckbox(holder, getSelectedFiles, file, onFileSelected)
        setupItemClick(holder, file, getSelectedFiles, onFileSelected)
        bindProgress(holder, file, downloadProgress, PERCENT_MIN, PERCENT_MAX)
        bindStatus(holder, file, downloadStatus)
    }

    fun updateFiles(newFiles: List<DownloadFile>) {
        submitList(newFiles)
    }

    fun clearProgressAndStatus() {
        downloadProgress.clear()
        downloadStatus.clear()
        lastProgressUpdate.clear()
        progressUpdatePending.clear()
        notifyDataSetChanged()
    }

    private var lastUpdateTime = 0L
    private val updateThrottleMs = UPDATE_THROTTLE_MS // Throttle updates to max once per 100ms

    fun updateSelectionState() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < updateThrottleMs.toLong()) {
            return // Skip update if too soon
        }
        lastUpdateTime = currentTime

        updateVisibleCheckboxes()
    }

    private fun updateVisibleCheckboxes() {
        val layoutManager = recyclerView?.layoutManager
        if (layoutManager is androidx.recyclerview.widget.LinearLayoutManager) {
            val firstVisible = layoutManager.findFirstVisibleItemPosition()
            val lastVisible = layoutManager.findLastVisibleItemPosition()
            if (firstVisible != -1 && lastVisible != -1) {
                for (i in firstVisible..lastVisible) {
                    updateCheckboxForPosition(i)
                }
            }
        }
    }

    private fun updateCheckboxForPosition(position: Int) {
        val holder = recyclerView?.findViewHolderForAdapterPosition(position)
            as? FileViewHolder
        holder?.let { viewHolder ->
            val file = getItem(position)
            val isSelected = getSelectedFiles().contains(file.url)
            updateCheckboxState(viewHolder, isSelected)
        }
    }

    private fun updateCheckboxState(holder: FileViewHolder, isSelected: Boolean) {
        // Only update if the state actually changed to prevent unnecessary updates
        if (holder.checkBox.isChecked != isSelected) {
            // Temporarily disable listener to prevent cascade effects
            holder.checkBox.setOnCheckedChangeListener(null)

            // Simple state change without animation to prevent flashing
            holder.checkBox.isChecked = isSelected

            // Re-enable listener after setting state
            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                // Checkbox changed
                holder.itemView.post {
                    onFileSelected(getItem(holder.adapterPosition), isChecked)
                }
            }
        }
    }

    fun updateDownloadProgress(url: String, progress: Int) {
        val currentTime = System.currentTimeMillis()
        val lastUpdate = lastProgressUpdate[url] ?: 0L

        downloadProgress[url] = progress

        if (currentTime - lastUpdate > progressUpdateThrottle) {
            updateProgressBarOnly(url, progress)
            lastProgressUpdate[url] = currentTime
        } else {
            progressUpdatePending.add(url)
        }
    }

    private fun updateProgressBarOnly(url: String, progress: Int) {
        recyclerView?.let { rv ->
            updateProgressBarForVisibleItems(rv, url, progress)
        }
    }

    private fun updateProgressBarForVisibleItems(
        recyclerView: androidx.recyclerview.widget.RecyclerView,
        url: String,
        progress: Int,
    ) {
        val layoutManager = recyclerView.layoutManager
        if (layoutManager is androidx.recyclerview.widget.LinearLayoutManager) {
            val firstVisible = layoutManager.findFirstVisibleItemPosition()
            val lastVisible = layoutManager.findLastVisibleItemPosition()
            if (firstVisible != -1 && lastVisible != -1) {
                findAndUpdateProgressBar(recyclerView, url, progress, firstVisible, lastVisible)
            }
        }
    }

    private fun findAndUpdateProgressBar(
        recyclerView: androidx.recyclerview.widget.RecyclerView,
        url: String,
        progress: Int,
        firstVisible: Int,
        lastVisible: Int,
    ) {
        for (i in firstVisible..lastVisible) {
            val file = getItem(i)
            if (file.url == url) {
                val holder = recyclerView.findViewHolderForAdapterPosition(i) as? FileViewHolder
                holder?.let { viewHolder ->
                    updateProgressBarDirectly(viewHolder, progress)
                }
                break
            }
        }
    }

    private fun updateProgressBarDirectly(holder: FileViewHolder, progress: Int) {
        if (progress in PERCENT_MIN..PERCENT_MAX) {
            showProgressBar(holder, progress)
        } else {
            hideProgressBar(holder)
        }
    }

    private fun showProgressBar(holder: FileViewHolder, progress: Int) {
        holder.progressBar.visibility = View.VISIBLE
        holder.progressBar.progress = progress
        val itemContext = holder.itemView.context
        val colorRes = if (progress == PROGRESS_ERROR_VALUE) {
            R.color.progress_bar_error
        } else {
            R.color.progress_bar_fill
        }
        holder.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(itemContext, colorRes)
        )
    }

    private fun hideProgressBar(holder: FileViewHolder) {
        holder.progressBar.visibility = View.GONE
    }

    fun setDownloadStatus(url: String, status: DownloadStatus) {
        downloadStatus[url] = status
        val index = currentList.indexOfFirst { it.url == url }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    fun updateProgressOnly(url: String, progress: Int) {
        downloadProgress[url] = progress
        progressUpdatePending.add(url)
    }

    fun flushProgressUpdates() {
        progressUpdatePending.forEach { url ->
            val progress = downloadProgress[url] ?: 0
            updateProgressBarOnly(url, progress)
        }
        progressUpdatePending.clear()
    }
}

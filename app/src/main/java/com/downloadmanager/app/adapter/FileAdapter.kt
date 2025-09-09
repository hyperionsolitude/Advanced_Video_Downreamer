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
    selectedFiles: Set<String>,
    file: DownloadFile,
) {
    holder.itemView.isSelected = selectedFiles.contains(file.url)
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
    selectedFiles: MutableSet<String>,
    file: DownloadFile,
    onFileSelected: (DownloadFile, Boolean) -> Unit,
) {
    holder.checkBox.isChecked = selectedFiles.contains(file.url)
    holder.checkBox.setOnCheckedChangeListener(null)
    holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            selectedFiles.add(file.url)
        } else {
            selectedFiles.remove(file.url)
        }
        // Defer the UI update to avoid RecyclerView layout conflicts
        holder.itemView.post {
            onFileSelected(file, isChecked)
        }
    }
}

private fun setupItemClick(holder: FileAdapter.FileViewHolder) {
    holder.itemView.setOnClickListener {
        // Defer the checkbox toggle to avoid RecyclerView layout conflicts
        holder.itemView.post {
            holder.checkBox.isChecked = !holder.checkBox.isChecked
        }
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
        if (progress == -1) {
            holder.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(
                    itemContext,
                    com.downloadmanager.app.R.color.progress_fill_error
                )
            )
        } else {
            holder.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(itemContext, com.downloadmanager.app.R.color.progress_fill)
            )
        }
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

class FileAdapter(
    private val onFileSelected: (DownloadFile, Boolean) -> Unit,
) : ListAdapter<DownloadFile, FileAdapter.FileViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<DownloadFile>() {
        private const val PROGRESS_THROTTLE_MS = 150L
        private const val PERCENT_MIN = 0
        private const val PERCENT_MAX = 100

        override fun areItemsTheSame(oldItem: DownloadFile, newItem: DownloadFile): Boolean {
            return oldItem.url == newItem.url
        }

        override fun areContentsTheSame(oldItem: DownloadFile, newItem: DownloadFile): Boolean {
            return oldItem == newItem
        }
    }
    private val selectedFiles = mutableSetOf<String>()
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = getItem(position)
        setupSelectionUi(holder, selectedFiles, file)
        bindFileTexts(holder, file)
        setupCheckbox(holder, selectedFiles, file, onFileSelected)
        setupItemClick(holder)
        bindProgress(holder, file, downloadProgress, PERCENT_MIN, PERCENT_MAX)
        bindStatus(holder, file, downloadStatus)
    }

    fun updateFiles(newFiles: List<DownloadFile>) {
        submitList(newFiles)
    }

    fun selectAll() {
        selectedFiles.clear()
        currentList.forEach { selectedFiles.add(it.url) }
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedFiles.clear()
        downloadProgress.clear()
        downloadStatus.clear()
        lastProgressUpdate.clear()
        progressUpdatePending.clear()
        notifyDataSetChanged()
    }

    fun getSelectedFiles(): Set<String> = selectedFiles.toSet()

    fun updateDownloadProgress(url: String, progress: Int) {
        val currentTime = System.currentTimeMillis()
        val lastUpdate = lastProgressUpdate[url] ?: 0L

        downloadProgress[url] = progress

        if (currentTime - lastUpdate > progressUpdateThrottle) {
            val index = currentList.indexOfFirst { it.url == url }
            if (index != -1) {
                notifyItemChanged(index)
                lastProgressUpdate[url] = currentTime
            }
        } else {
            progressUpdatePending.add(url)
        }
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
            val index = currentList.indexOfFirst { it.url == url }
            if (index != -1) {
                notifyItemChanged(index)
            }
        }
        progressUpdatePending.clear()
    }
}

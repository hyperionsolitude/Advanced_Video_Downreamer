package com.downloadmanager.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.downloadmanager.app.R
import com.downloadmanager.app.model.DownloadFile
import com.google.android.material.progressindicator.LinearProgressIndicator

class FileAdapter(
    private val onFileSelected: (DownloadFile, Boolean) -> Unit,
) : ListAdapter<DownloadFile, FileAdapter.FileViewHolder>(DiffCallback) {

    private val selectedFiles = mutableSetOf<String>()
    private val downloadProgress = mutableMapOf<String, Int>() // url -> progress
    private val lastProgressUpdate = mutableMapOf<String, Long>() // url -> timestamp

    // Update UI at most every 150ms per item for better performance
    private val PROGRESS_UPDATE_THROTTLE = 150L
    private val progressUpdatePending = mutableSetOf<String>() // Track which items need UI updates
    private var recyclerView: androidx.recyclerview.widget.RecyclerView? = null

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
        // Highlight selected item
        holder.itemView.isSelected = selectedFiles.contains(file.url)
        // Show download status in the name with proper colors
        val statusText = if (file.isCompletelyDownloaded()) {
            "\uD83D\uDCC1 LOCAL"
        } else {
            "\uD83C\uDF10 NETWORK"
        }
        holder.textViewName.text = "$statusText - ${file.name}"
        // Set text color based on download status
        val itemContext = holder.itemView.context
        if (file.isCompletelyDownloaded()) {
            holder.textViewName.setTextColor(
                itemContext.getColor(R.color.status_local)
            )
        } else {
            holder.textViewName.setTextColor(
                itemContext.getColor(R.color.status_network)
            )
        }
        holder.textViewUrl.text = file.url
        holder.textViewSize.text = file.size
        holder.textViewType.text = file.type
        // Set checkbox state
        holder.checkBox.isChecked = selectedFiles.contains(file.url)
        // Handle checkbox clicks
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedFiles.add(file.url)
            } else {
                selectedFiles.remove(file.url)
            }
            onFileSelected(file, isChecked)
        }
        // Handle item clicks
        holder.itemView.setOnClickListener {
            holder.checkBox.isChecked = !holder.checkBox.isChecked
        }
        // Show download progress if available with consistent colors
        val progress = downloadProgress[file.url] ?: 0
        if (progress >= 0 && progress <= 100) {
            holder.progressBar.visibility = View.VISIBLE
            // Always update progress for better responsiveness
            holder.progressBar.progress = progress

            // Use consistent color to prevent flashing - only change for errors
            if (progress == -1) {
                holder.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(
                    itemContext.getColor(com.downloadmanager.app.R.color.progress_fill_error)
                )
            } else {
                // Use consistent green color for all normal progress states
                holder.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(
                    itemContext.getColor(com.downloadmanager.app.R.color.progress_fill)
                )
            }
        } else {
            holder.progressBar.visibility = View.GONE
        }

        // Show download status
        val status = downloadStatus[file.url] ?: DownloadStatus.PENDING
        when (status) {
            DownloadStatus.PENDING -> {
                holder.textViewStatus.visibility = View.GONE
            }
            DownloadStatus.STARTED -> {
                holder.textViewStatus.visibility = View.VISIBLE
                holder.textViewStatus.text = "Starting..."
                holder.textViewStatus.setTextColor(itemContext.getColor(R.color.status_started))
            }
            DownloadStatus.DOWNLOADING -> {
                holder.textViewStatus.visibility = View.VISIBLE
                holder.textViewStatus.text = "Downloading"
                holder.textViewStatus.setTextColor(itemContext.getColor(R.color.status_started))
            }
            DownloadStatus.COMPLETE -> {
                holder.textViewStatus.visibility = View.VISIBLE
                holder.textViewStatus.text = "Complete"
                holder.textViewStatus.setTextColor(itemContext.getColor(R.color.status_complete))
            }
            DownloadStatus.FAILED -> {
                holder.textViewStatus.visibility = View.VISIBLE
                holder.textViewStatus.text = "Failed"
                holder.textViewStatus.setTextColor(itemContext.getColor(R.color.status_failed))
            }
        }
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

        // Update UI more frequently for better responsiveness
        if (currentTime - lastUpdate > PROGRESS_UPDATE_THROTTLE) {
            val index = currentList.indexOfFirst { it.url == url }
            if (index != -1) {
                // Only update the progress bar, not the entire item
                updateProgressBarOnly(index, progress)
                lastProgressUpdate[url] = currentTime
            }
        } else {
            // Queue for batch update
            progressUpdatePending.add(url)
        }
    }

    private fun updateProgressBarOnly(position: Int, progress: Int) {
        // Find the view holder for this position
        val viewHolder = recyclerView?.findViewHolderForAdapterPosition(position) as? FileViewHolder
        viewHolder?.let { holder ->
            // Only update the progress bar
            if (progress >= 0 && progress <= 100) {
                holder.progressBar.visibility = View.VISIBLE
                holder.progressBar.progress = progress

                // Use consistent color to prevent flashing - only change for errors
                if (progress == -1) {
                    holder.progressBar.progressTintList =
                        android.content.res.ColorStateList.valueOf(
                            holder.itemView.context.getColor(
                                R.color.progress_fill_error
                            )
                        )
                } else {
                    // Use consistent green color for all normal progress states
                    holder.progressBar.progressTintList =
                        android.content.res.ColorStateList.valueOf(
                            holder.itemView.context.getColor(
                                R.color.progress_fill
                            )
                        )
                }
            } else {
                holder.progressBar.visibility = View.GONE
            }
        }
    }

    fun attachRecyclerView(rv: androidx.recyclerview.widget.RecyclerView) {
        recyclerView = rv
    }

    fun setDownloadStatus(url: String, status: DownloadStatus) {
        downloadStatus[url] = status
        val index = currentList.indexOfFirst { it.url == url }
        if (index != -1) {
            updateStatusDisplay(index, status)
        }
    }

    private fun updateStatusDisplay(position: Int, status: DownloadStatus) {
        val viewHolder = recyclerView?.findViewHolderForAdapterPosition(position) as? FileViewHolder
        viewHolder?.let { holder ->
            when (status) {
                DownloadStatus.PENDING -> {
                    holder.textViewStatus.visibility = View.GONE
                }
                DownloadStatus.STARTED -> {
                    holder.textViewStatus.visibility = View.VISIBLE
                    holder.textViewStatus.text = "Starting..."
                    holder.textViewStatus.setTextColor(
                        holder.itemView.context.getColor(R.color.status_started)
                    )
                }
                DownloadStatus.DOWNLOADING -> {
                    holder.textViewStatus.visibility = View.VISIBLE
                    holder.textViewStatus.text = "Downloading"
                    holder.textViewStatus.setTextColor(
                        holder.itemView.context.getColor(R.color.status_started)
                    )
                }
                DownloadStatus.COMPLETE -> {
                    holder.textViewStatus.visibility = View.VISIBLE
                    holder.textViewStatus.text = "Complete"
                    holder.textViewStatus.setTextColor(
                        holder.itemView.context.getColor(R.color.status_complete)
                    )
                }
                DownloadStatus.FAILED -> {
                    holder.textViewStatus.visibility = View.VISIBLE
                    holder.textViewStatus.text = "Failed"
                    holder.textViewStatus.setTextColor(
                        holder.itemView.context.getColor(R.color.status_failed)
                    )
                }
            }
        }
    }

    fun updateProgressOnly(url: String, progress: Int) {
        // Update progress without triggering UI refresh
        downloadProgress[url] = progress
        progressUpdatePending.add(url)
    }

    fun flushProgressUpdates() {
        // Update all pending progress items at once
        progressUpdatePending.forEach { url ->
            val index = currentList.indexOfFirst { it.url == url }
            if (index != -1) {
                notifyItemChanged(index)
            }
        }
        progressUpdatePending.clear()
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DownloadFile>() {
        override fun areItemsTheSame(oldItem: DownloadFile, newItem: DownloadFile): Boolean {
            return oldItem.url == newItem.url
        }
        override fun areContentsTheSame(oldItem: DownloadFile, newItem: DownloadFile): Boolean {
            return oldItem == newItem
        }
    }
}

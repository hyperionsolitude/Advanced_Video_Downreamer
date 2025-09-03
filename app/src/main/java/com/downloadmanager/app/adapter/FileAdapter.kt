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
import android.widget.ProgressBar

class FileAdapter(
    private val onFileSelected: (DownloadFile, Boolean) -> Unit
) : ListAdapter<DownloadFile, FileAdapter.FileViewHolder>(DiffCallback) {

    private val selectedFiles = mutableSetOf<String>()
    private val downloadProgress = mutableMapOf<String, Int>() // url -> progress
    private val lastProgressUpdate = mutableMapOf<String, Long>() // url -> timestamp
    private val PROGRESS_UPDATE_THROTTLE = 500L // Update UI every 500ms max per item
    private val progressUpdatePending = mutableSetOf<String>() // Track which items need UI updates

    class FileViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val checkBox: CheckBox = itemView.findViewById(R.id.checkBoxFile)
        val textViewName: TextView = itemView.findViewById(R.id.textViewFileName)
        val textViewUrl: TextView = itemView.findViewById(R.id.textViewFileUrl)
        val textViewSize: TextView = itemView.findViewById(R.id.textViewFileSize)
        val textViewType: TextView = itemView.findViewById(R.id.textViewFileType)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progressBarDownload)
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
        val statusText = if (file.isCompletelyDownloaded()) "\uD83D\uDCC1 LOCAL" else "\uD83C\uDF10 NETWORK"
        holder.textViewName.text = "$statusText - ${file.name}"
        // Set text color based on download status
        val itemContext = holder.itemView.context
        if (file.isCompletelyDownloaded()) {
            holder.textViewName.setTextColor(itemContext.getColor(com.downloadmanager.app.R.color.status_local))
        } else {
            holder.textViewName.setTextColor(itemContext.getColor(com.downloadmanager.app.R.color.status_network))
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
        // Show download progress if available with anti-flashing measures
        val progress = downloadProgress[file.url] ?: 0
        if (progress in 1..99) {
            holder.progressBar.visibility = View.VISIBLE
            // Only update progress if it's significantly different to prevent flashing
            val currentProgress = holder.progressBar.progress
            if (kotlin.math.abs(currentProgress - progress) >= 2) {
                holder.progressBar.progress = progress
            }
            holder.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(
                itemContext.getColor(com.downloadmanager.app.R.color.progress_fill_downloading)
            )
        } else if (progress == 100) {
            holder.progressBar.visibility = View.VISIBLE
            holder.progressBar.progress = progress
            holder.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(
                itemContext.getColor(com.downloadmanager.app.R.color.progress_fill_complete)
            )
        } else if (progress == -1) {
            holder.progressBar.visibility = View.VISIBLE
            holder.progressBar.progress = 0
            holder.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(
                itemContext.getColor(com.downloadmanager.app.R.color.progress_fill_error)
            )
        } else {
            holder.progressBar.visibility = View.GONE
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
        notifyDataSetChanged()
    }

    fun getSelectedFiles(): Set<String> = selectedFiles.toSet()

    fun updateDownloadProgress(url: String, progress: Int) {
        val currentTime = System.currentTimeMillis()
        val lastUpdate = lastProgressUpdate[url] ?: 0L
        
        downloadProgress[url] = progress
        
        // Only update UI if enough time has passed to prevent flashing
        if (currentTime - lastUpdate > PROGRESS_UPDATE_THROTTLE) {
            val index = currentList.indexOfFirst { it.url == url }
            if (index != -1) {
                notifyItemChanged(index)
                lastProgressUpdate[url] = currentTime
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
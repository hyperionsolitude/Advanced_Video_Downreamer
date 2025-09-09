package com.downloadmanager.app

import com.downloadmanager.app.model.DownloadFile
import java.util.Locale

class SelectionController(
    private val main: MainActivity,
) {
    private companion object {
        private const val BYTES_IN_KILOBYTE = 1024L
        private const val BYTES_IN_MEGABYTE = 1024L * 1024L
        private const val BYTES_IN_GIGABYTE = 1024L * 1024L * 1024L
    }

    fun handleFileSelection(file: DownloadFile, isSelected: Boolean) {
        val selected = main.viewModel.selectedFiles.value?.toMutableSet() ?: mutableSetOf()
        android.util.Log.d(
            "SelectionController",
            "handleFileSelection: ${file.name}, isSelected: $isSelected, " +
                "current selected: ${selected.size}"
        )
        if (isSelected) {
            selected.add(file.url)
        } else {
            selected.remove(file.url)
        }
        android.util.Log.d("SelectionController", "After update: selected count: ${selected.size}")
        main.viewModel.setSelectedFiles(selected)
        updateButtonsAndSize()
    }

    fun selectAllFiles() {
        val urls = main.viewModel.currentFiles.value?.map { it.url }?.toSet() ?: emptySet()
        main.viewModel.setSelectedFiles(urls.toMutableSet())
        updateButtonsAndSize()
    }

    fun deselectAllFiles() {
        main.viewModel.setSelectedFiles(mutableSetOf())
        updateButtonsAndSize()
    }

    fun invertSelection() {
        val current = main.viewModel.selectedFiles.value ?: mutableSetOf()
        val allUrls = main.viewModel.currentFiles.value?.map { it.url } ?: emptyList()
        val newSel = mutableSetOf<String>()
        for (url in allUrls) {
            if (!current.contains(url)) newSel.add(url)
        }
        main.viewModel.setSelectedFiles(newSel)
        updateButtonsAndSize()
    }

    fun updateButtonsAndSize() {
        updateActionButtons()
        updateSelectedSizeInfo()
    }

    fun updateActionButtons() {
        val selected = main.viewModel.selectedFiles.value?.size ?: 0
        main.buttonDownload.isEnabled = selected > 0
        main.buttonStream.isEnabled = selected > 0
        main.textViewSelectedCount.text = "Selected: $selected"

        // Don't update the adapter here to prevent loops
        // The adapter will update when items are clicked directly
    }

    fun updateSelectedSizeInfo() {
        val selected = main.viewModel.selectedFiles.value ?: emptySet()
        val files = main.viewModel.currentFiles.value ?: emptyList()
        var totalBytes = 0L
        files.forEach { f ->
            if (selected.contains(f.url)) {
                totalBytes += parseFileSizeToBytes(f.size)
            }
        }
        val info = "Total size: ${formatFileSize(totalBytes)}"
        main.textViewSelectedSize.text = info
        val freeSpace = main.viewModel.getDownloadDir().freeSpace
        if (totalBytes > 0 && totalBytes > freeSpace) {
            main.textViewSelectedSize.setTextColor(android.graphics.Color.RED)
            main.textViewSelectedSize.text =
                "Total size: ${formatFileSize(totalBytes)} (Not enough space!)"
        } else {
            main.textViewSelectedSize.setTextColor(android.graphics.Color.WHITE)
        }
    }

    private fun parseFileSizeToBytes(sizeStr: String): Long {
        val s = sizeStr.trim().lowercase()
        return when {
            s.endsWith("gb") -> (
                (s.removeSuffix("gb").trim().toDoubleOrNull() ?: 0.0) *
                    BYTES_IN_GIGABYTE
                ).toLong()
            s.endsWith("mb") -> (
                (s.removeSuffix("mb").trim().toDoubleOrNull() ?: 0.0) *
                    BYTES_IN_MEGABYTE
                ).toLong()
            s.endsWith("kb") -> (
                (s.removeSuffix("kb").trim().toDoubleOrNull() ?: 0.0) *
                    BYTES_IN_KILOBYTE
                ).toLong()
            s.endsWith("b") -> (
                s.removeSuffix("b").trim().toDoubleOrNull() ?: 0.0
                ).toLong()
            else -> 0L
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
}

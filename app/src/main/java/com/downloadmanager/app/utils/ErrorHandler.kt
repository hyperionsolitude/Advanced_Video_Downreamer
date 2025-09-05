package com.downloadmanager.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import com.downloadmanager.app.R
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Centralized error handling utility
 * Provides user-friendly error messages and error categorization
 */
object ErrorHandler {
    
    /**
     * Error categories for better error handling
     */
    enum class ErrorCategory {
        NETWORK,
        STORAGE,
        PERMISSION,
        FILE_SYSTEM,
        UNKNOWN
    }
    
    /**
     * Get user-friendly error message based on exception
     */
    fun getUserFriendlyMessage(throwable: Throwable, context: Context): String {
        return when (throwable) {
            is ConnectException -> context.getString(R.string.error_network_connection)
            is SocketTimeoutException -> context.getString(R.string.error_network_timeout)
            is UnknownHostException -> context.getString(R.string.error_network_host)
            is SecurityException -> context.getString(R.string.error_permission_denied)
            is java.io.FileNotFoundException -> context.getString(R.string.error_file_not_found)
            is java.io.IOException -> context.getString(R.string.error_io_exception)
            else -> context.getString(R.string.error_unknown, throwable.message ?: "Unknown error")
        }
    }
    
    /**
     * Get error category for logging and analytics
     */
    fun getErrorCategory(throwable: Throwable): ErrorCategory {
        return when (throwable) {
            is ConnectException, is SocketTimeoutException, is UnknownHostException -> ErrorCategory.NETWORK
            is SecurityException -> ErrorCategory.PERMISSION
            is java.io.FileNotFoundException, is java.io.IOException -> ErrorCategory.FILE_SYSTEM
            else -> ErrorCategory.UNKNOWN
        }
    }
    
    /**
     * Check if error is recoverable
     */
    fun isRecoverableError(throwable: Throwable): Boolean {
        return when (throwable) {
            is ConnectException, is SocketTimeoutException, is UnknownHostException -> true
            is SecurityException -> false
            is java.io.FileNotFoundException -> false
            is java.io.IOException -> true
            else -> false
        }
    }
    
    /**
     * Get suggested action for error
     */
    fun getSuggestedAction(throwable: Throwable, context: Context): String {
        return when (throwable) {
            is ConnectException, is SocketTimeoutException, is UnknownHostException -> 
                context.getString(R.string.suggestion_check_network)
            is SecurityException -> 
                context.getString(R.string.suggestion_check_permissions)
            is java.io.FileNotFoundException -> 
                context.getString(R.string.suggestion_check_storage)
            is java.io.IOException -> 
                context.getString(R.string.suggestion_retry)
            else -> 
                context.getString(R.string.suggestion_contact_support)
        }
    }
    
    /**
     * Check network connectivity
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    /**
     * Check storage availability
     */
    fun isStorageAvailable(context: Context, path: String): Boolean {
        return try {
            val file = File(path)
            file.exists() && file.canWrite()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get available storage space
     */
    fun getAvailableStorageSpace(path: String): Long {
        return try {
            val stat = StatFs(path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Check if there's enough space for download
     */
    fun hasEnoughSpace(path: String, requiredBytes: Long): Boolean {
        return getAvailableStorageSpace(path) >= requiredBytes
    }
    
    /**
     * Get memory usage percentage (unused parameter for future use)
     */
    fun getMemoryUsagePercentage(context: Context): Float {
        return 0f // Placeholder for future implementation
    }
}

package com.downloadmanager.app.utils

import android.app.ActivityManager
import android.content.Context
import java.lang.ref.WeakReference

/**
 * Memory management utility for optimizing app performance
 */
object MemoryManager {

    private const val BYTES_IN_KILOBYTE = 1024
    private const val BYTES_IN_MEGABYTE = 1024 * 1024
    private const val MAX_MEMORY_CACHE_SIZE_BYTES = 50 * BYTES_IN_MEGABYTE // 50MB
    private const val LOW_MEMORY_THRESHOLD_BYTES = 100 * BYTES_IN_MEGABYTE // 100MB
    private const val LOW_AVAILABLE_MEMORY_BYTES = 200 * BYTES_IN_MEGABYTE
    private const val MED_AVAILABLE_MEMORY_BYTES = 500 * BYTES_IN_MEGABYTE
    private const val BUFFER_LOW_BYTES = 8 * BYTES_IN_KILOBYTE
    private const val BUFFER_MED_BYTES = 16 * BYTES_IN_KILOBYTE
    private const val BUFFER_HIGH_BYTES = 32 * BYTES_IN_KILOBYTE
    private const val PERCENT_80 = 80f
    private const val PERCENT_100 = 100f
    private const val THREAD_POOL_SMALL = 1
    private const val THREAD_POOL_MEDIUM_MAX = 2
    private const val THREAD_POOL_LARGE_MAX = 4

    /**
     * Check if device is low on memory
     */
    fun isLowMemory(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem < LOW_MEMORY_THRESHOLD_BYTES
    }

    /**
     * Get available memory in bytes
     */
    fun getAvailableMemory(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem
    }

    /**
     * Get total memory in bytes
     */
    fun getTotalMemory(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem
    }

    /**
     * Get memory usage percentage
     */
    fun getMemoryUsagePercentage(context: Context): Float {
        val total = getTotalMemory(context)
        val available = getAvailableMemory(context)
        return ((total - available).toFloat() / total.toFloat()) * PERCENT_100
    }

    /**
     * Force garbage collection
     */
    fun forceGarbageCollection() {
        // No-op: Avoid explicit GC calls
    }

    /**
     * Check if memory cache should be cleared
     */
    fun shouldClearCache(context: Context): Boolean {
        return isLowMemory(context) || getMemoryUsagePercentage(context) > PERCENT_80
    }

    /**
     * Get recommended buffer size based on available memory
     */
    fun getRecommendedBufferSize(context: Context): Int {
        val availableMemory = getAvailableMemory(context)
        return when {
            availableMemory < LOW_AVAILABLE_MEMORY_BYTES -> BUFFER_LOW_BYTES
            availableMemory < MED_AVAILABLE_MEMORY_BYTES -> BUFFER_MED_BYTES
            else -> BUFFER_HIGH_BYTES
        }
    }

    /**
     * Get recommended thread pool size based on available memory
     */
    fun getRecommendedThreadPoolSize(context: Context): Int {
        val availableMemory = getAvailableMemory(context)
        val cores = Runtime.getRuntime().availableProcessors()
        return when {
            availableMemory < LOW_AVAILABLE_MEMORY_BYTES -> THREAD_POOL_SMALL
            availableMemory < MED_AVAILABLE_MEMORY_BYTES -> cores.coerceAtMost(
                THREAD_POOL_MEDIUM_MAX
            )
            else -> cores.coerceAtMost(THREAD_POOL_LARGE_MAX)
        }
    }

    /**
     * Memory-aware cache implementation
     */
    class MemoryAwareCache<K, V> {
        private val cache = mutableMapOf<K, V>()
        private val accessOrder = mutableListOf<K>()

        fun put(key: K, value: V) {
            // Remove oldest entries if cache is too large
            while (cache.size > MAX_MEMORY_CACHE_SIZE_BYTES / BYTES_IN_KILOBYTE) { // Rough estimate
                val oldestKey = accessOrder.removeFirstOrNull()
                if (oldestKey != null) {
                    cache.remove(oldestKey)
                }
            }

            cache[key] = value
            accessOrder.remove(key) // Remove if already exists
            accessOrder.add(key) // Add to end
        }

        fun get(key: K): V? {
            val value = cache[key]
            if (value != null) {
                // Move to end (most recently used)
                accessOrder.remove(key)
                accessOrder.add(key)
            }
            return value
        }

        fun clear() {
            cache.clear()
            accessOrder.clear()
        }

        fun size(): Int = cache.size
    }

    /**
     * Weak reference pool for preventing memory leaks
     */
    class WeakReferencePool<T> {
        private val pool = mutableListOf<WeakReference<T>>()

        fun add(item: T) {
            pool.add(WeakReference(item))
        }

        fun clear() {
            pool.clear()
        }

        fun getAliveItems(): List<T> {
            val aliveItems = mutableListOf<T>()
            val iterator = pool.iterator()
            while (iterator.hasNext()) {
                val ref = iterator.next()
                val item = ref.get()
                if (item != null) {
                    aliveItems.add(item)
                } else {
                    iterator.remove() // Remove dead references
                }
            }
            return aliveItems
        }
    }
}

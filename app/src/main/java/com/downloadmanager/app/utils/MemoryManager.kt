package com.downloadmanager.app.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import java.lang.ref.WeakReference

/**
 * Memory management utility for optimizing app performance
 */
object MemoryManager {
    
    private const val MAX_MEMORY_CACHE_SIZE = 50 * 1024 * 1024 // 50MB
    private const val LOW_MEMORY_THRESHOLD = 100 * 1024 * 1024 // 100MB
    
    /**
     * Check if device is low on memory
     */
    fun isLowMemory(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem < LOW_MEMORY_THRESHOLD
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
        return ((total - available).toFloat() / total.toFloat()) * 100f
    }
    
    /**
     * Force garbage collection
     */
    fun forceGarbageCollection() {
        System.gc()
        System.runFinalization()
        System.gc()
    }
    
    /**
     * Check if memory cache should be cleared
     */
    fun shouldClearCache(context: Context): Boolean {
        return isLowMemory(context) || getMemoryUsagePercentage(context) > 80f
    }
    
    /**
     * Get recommended buffer size based on available memory
     */
    fun getRecommendedBufferSize(context: Context): Int {
        val availableMemory = getAvailableMemory(context)
        return when {
            availableMemory < 200 * 1024 * 1024 -> 8192 // 8KB for low memory
            availableMemory < 500 * 1024 * 1024 -> 16384 // 16KB for medium memory
            else -> 32768 // 32KB for high memory
        }
    }
    
    /**
     * Get recommended thread pool size based on available memory
     */
    fun getRecommendedThreadPoolSize(context: Context): Int {
        val availableMemory = getAvailableMemory(context)
        val cores = Runtime.getRuntime().availableProcessors()
        return when {
            availableMemory < 200 * 1024 * 1024 -> 1 // Single thread for low memory
            availableMemory < 500 * 1024 * 1024 -> cores.coerceAtMost(2) // Max 2 threads
            else -> cores.coerceAtMost(4) // Max 4 threads
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
            while (cache.size > MAX_MEMORY_CACHE_SIZE / 1024) { // Rough estimate
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

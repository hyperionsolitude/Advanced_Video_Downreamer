package com.downloadmanager.app.utils

import android.util.Log
import com.downloadmanager.app.BuildConfig

/**
 * Centralized logging utility for the app
 * Automatically handles debug/release builds
 */
object Logger {
    private const val TAG_PREFIX = "AdvancedVideoDownreamer"
    
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.d("$TAG_PREFIX:$tag", message, throwable)
            } else {
                Log.d("$TAG_PREFIX:$tag", message)
            }
        }
    }
    
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.i("$TAG_PREFIX:$tag", message, throwable)
            } else {
                Log.i("$TAG_PREFIX:$tag", message)
            }
        }
    }
    
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.w("$TAG_PREFIX:$tag", message, throwable)
            } else {
                Log.w("$TAG_PREFIX:$tag", message)
            }
        }
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.e("$TAG_PREFIX:$tag", message, throwable)
            } else {
                Log.e("$TAG_PREFIX:$tag", message)
            }
        }
    }
    
    fun v(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.v("$TAG_PREFIX:$tag", message, throwable)
            } else {
                Log.v("$TAG_PREFIX:$tag", message)
            }
        }
    }
}

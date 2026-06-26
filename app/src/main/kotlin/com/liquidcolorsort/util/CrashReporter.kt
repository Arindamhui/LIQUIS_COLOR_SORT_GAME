package com.liquidcolorsort.util

import android.util.Log

/**
 * Stub CrashReporter to log exceptions locally during development.
 *
 * When Firebase Crashlytics is added to the project, replace this stub's
 * implementation with:
 * `FirebaseCrashlytics.getInstance().recordException(throwable)`
 */
object CrashReporter {
    private const val TAG = "CrashReporter"

    /** Logs an exception to Logcat (and records it to Crashlytics in production). */
    fun logException(throwable: Throwable) {
        Log.e(TAG, "Caught exception: ${throwable.message}", throwable)
        // TODO production setup:
        // FirebaseCrashlytics.getInstance().recordException(throwable)
    }

    /** Logs a non-fatal warning message. */
    fun logWarning(message: String) {
        Log.w(TAG, "Warning: $message")
        // TODO production setup:
        // FirebaseCrashlytics.getInstance().log("Warning: $message")
    }
}

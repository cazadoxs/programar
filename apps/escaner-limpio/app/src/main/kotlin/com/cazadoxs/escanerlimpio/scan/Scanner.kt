package com.cazadoxs.escanerlimpio.scan

import android.app.Activity
import android.content.IntentSender
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import kotlinx.coroutines.tasks.await

/**
 * Google's on-device document scanner (edge detection, perspective fix,
 * filters, finger and stain removal). It runs inside Google Play services,
 * so the app needs no camera permission.
 */
object Scanner {
    private val options = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(50)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build()

    /** Returns the scanner's intent, or null when this phone can't run it. */
    suspend fun intentSender(activity: Activity): IntentSender? =
        runCatching { GmsDocumentScanning.getClient(options).getStartScanIntent(activity).await() }.getOrNull()
}

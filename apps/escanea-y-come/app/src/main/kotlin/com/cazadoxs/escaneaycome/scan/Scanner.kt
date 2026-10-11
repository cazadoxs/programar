package com.cazadoxs.escaneaycome.scan

import android.content.Context
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface ScanOutcome {
    data class Code(val value: String) : ScanOutcome
    data object Cancelled : ScanOutcome
    /** Scanner module missing or Play services unavailable: offer manual entry. */
    data class Failed(val message: String) : ScanOutcome
}

/**
 * Google's code scanner: the camera UI runs inside Play services, so the app never
 * holds the CAMERA permission and never sees camera frames.
 */
object Scanner {
    suspend fun scan(context: Context): ScanOutcome = suspendCancellableCoroutine { cont ->
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            )
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue
                if (cont.isActive) cont.resume(if (raw.isNullOrBlank()) ScanOutcome.Cancelled else ScanOutcome.Code(raw))
            }
            .addOnCanceledListener { if (cont.isActive) cont.resume(ScanOutcome.Cancelled) }
            .addOnFailureListener { e ->
                val msg = if (e is MlKitException && e.errorCode == MlKitException.UNAVAILABLE) {
                    "El escáner se está descargando en Google Play. Inténtalo en unos segundos o escribe el código."
                } else {
                    "No se pudo abrir el escáner. Puedes escribir el código a mano."
                }
                if (cont.isActive) cont.resume(ScanOutcome.Failed(msg))
            }
    }
}

package app.apunta.models

/**
 * Whisper models offered in the app. Files come from the whisper.cpp model
 * repository on Hugging Face; the SHA-1 values are the ones published by
 * whisper.cpp (models/README.md) and every download is checked against them.
 */
data class WhisperModel(
    val id: String,
    val fileName: String,
    val sha1: String,
    val sizeBytes: Long,
    val pro: Boolean,
    /** Total RAM (GB) under which the model is not recommended. */
    val minRamGb: Int,
    /** 1..5 for the UI bars. */
    val speed: Int,
    val accuracy: Int,
) {
    val url: String get() = "$BASE_URL/$fileName"

    companion object {
        const val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"
    }
}

object ModelCatalog {
    val all = listOf(
        WhisperModel("tiny", "ggml-tiny.bin", "bd577a113a864445d4c299885e0cb97d4ba92b5f", 77_691_713, pro = false, minRamGb = 2, speed = 5, accuracy = 2),
        WhisperModel("base", "ggml-base.bin", "465707469ff3a37a2b9b8d8f89f2f99de7299dac", 147_951_465, pro = false, minRamGb = 3, speed = 4, accuracy = 3),
        WhisperModel("small", "ggml-small.bin", "55356645c2b361a969dfd0ef2c5a50d530afd8d5", 487_601_967, pro = true, minRamGb = 4, speed = 2, accuracy = 4),
        WhisperModel("turbo", "ggml-large-v3-turbo-q5_0.bin", "e050f7970618a659205450ad97eb95a18d69c9ee", 574_041_195, pro = true, minRamGb = 6, speed = 1, accuracy = 5),
    )

    const val CUSTOM_ID = "custom"

    fun byId(id: String): WhisperModel? = all.firstOrNull { it.id == id }

    /** Best free model for the phone's RAM, used during onboarding. */
    fun recommended(totalRamGb: Double): WhisperModel =
        if (totalRamGb < 3.0) byId("tiny")!! else byId("base")!!
}

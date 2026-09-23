package dev.pixelforge.mobile.model

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

data class ModelInfo(val name: String, val license: String, val source: String)

class ModelStore(private val context: Context) {
    val directory = File(context.filesDir, "model")
    private val required = listOf(
        "text_encoder/model.onnx", "unet/model.onnx", "vae_encoder/model.onnx",
        "vae_decoder/model.onnx", "tokenizer/vocab.json", "tokenizer/merges.txt", "model.json"
    )

    fun isReady(): Boolean = required.all { File(directory, it).isFile }

    fun info(): ModelInfo? = runCatching {
        val json = JSONObject(File(directory, "model.json").readText())
        ModelInfo(json.getString("name"), json.getString("license"), json.getString("source"))
    }.getOrNull()

    suspend fun import(uri: Uri, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "model-import-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open model pack" }
                ZipInputStream(input.buffered()).use { zip ->
                    var count = 0
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val target = File(staging, entry.name).canonicalFile
                        require(target.path.startsWith(staging.canonicalPath + File.separator)) { "Unsafe archive path" }
                        if (entry.isDirectory) target.mkdirs() else {
                            target.parentFile?.mkdirs()
                            target.outputStream().buffered().use { zip.copyTo(it) }
                        }
                        count++
                        progress((count.coerceAtMost(12)) / 14f)
                    }
                }
            }
            require(required.all { File(staging, it).isFile }) { "Model pack is incomplete" }
            val json = JSONObject(File(staging, "model.json").readText())
            require(json.optInt("format", 0) == 1) { "Unsupported model pack format" }
            require(json.optString("license").isNotBlank()) { "Model license is missing" }
            if (directory.exists()) directory.deleteRecursively()
            require(staging.renameTo(directory)) { "Could not install model pack" }
            progress(1f)
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }
}

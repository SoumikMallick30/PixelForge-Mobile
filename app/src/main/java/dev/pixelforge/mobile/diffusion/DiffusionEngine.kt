package dev.pixelforge.mobile.diffusion

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.util.EnumSet
import kotlin.coroutines.coroutineContext
import kotlin.math.*
import kotlin.random.Random

class DiffusionEngine(private val context: Context, private val modelDir: File) : Closeable {
    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(4); setInterOpNumThreads(1); setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        // NNAPI uses the S23's available accelerators where the graph permits it,
        // with ONNX Runtime's CPU provider retained as a compatibility fallback.
        runCatching { addNnapi(EnumSet.noneOf(NNAPIFlags::class.java)) }
    }
    private val text = env.createSession(File(modelDir, "text_encoder/model.onnx").path, options)
    private val unet = env.createSession(File(modelDir, "unet/model.onnx").path, options)
    private val encoder = env.createSession(File(modelDir, "vae_encoder/model.onnx").path, options)
    private val decoder = env.createSession(File(modelDir, "vae_decoder/model.onnx").path, options)
    private val tokenizer = ClipTokenizer(modelDir)
    private val size = 512
    private val latentSize = size / 8
    private val steps = 20
    private val guidance = 7.0f

    suspend fun generate(prompt: String, reference: Uri?, progress: (Float, String) -> Unit): Bitmap = withContext(Dispatchers.Default) {
        progress(.03f, "Understanding your prompt…")
        val positive = embed("photorealistic, highly detailed photograph, $prompt")
        val negative = embed("painting, illustration, cartoon, anime, 3d render, blurry, distorted, low quality")
        val scheduler = DdimScheduler(steps)
        var start = 0
        var latent = if (reference == null) randomLatent() else {
            progress(.08f, "Reading reference photo…")
            val source = context.contentResolver.openInputStream(reference).use { BitmapFactory.decodeStream(it) }
            val encoded = encodeBitmap(source)
            start = (steps * .35f).toInt()
            scheduler.addNoise(encoded, randomLatent(), scheduler.timesteps[start])
        }
        for (i in start until steps) {
            coroutineContext.ensureActive()
            val t = scheduler.timesteps[i]
            val unconditional = predict(latent, t, negative)
            val conditional = predict(latent, t, positive)
            val noise = FloatArray(latent.size) { j -> unconditional[j] + guidance * (conditional[j] - unconditional[j]) }
            latent = scheduler.step(noise, t, latent)
            progress(.12f + .82f * ((i - start + 1f) / (steps - start)), "Creating photo • ${i - start + 1}/${steps - start}")
        }
        progress(.96f, "Finishing…")
        decode(latent)
    }

    private fun embed(prompt: String): FloatArray {
        OnnxTensor.createTensor(env, arrayOf(tokenizer.encode(prompt))).use { input ->
            text.run(mapOf(text.inputNames.first() to input)).use { result -> return floats(result[0] as OnnxTensor) }
        }
    }

    private fun predict(latent: FloatArray, timestep: Int, embedding: FloatArray): FloatArray {
        OnnxTensor.createTensor(env, FloatBuffer.wrap(latent), longArrayOf(1, 4, latentSize.toLong(), latentSize.toLong())).use { sample ->
            OnnxTensor.createTensor(env, floatArrayOf(timestep.toFloat())).use { time ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(embedding), longArrayOf(1, 77, embedding.size.toLong() / 77)).use { hidden ->
                    val inputs = mapInputs(unet, sample, time, hidden)
                    unet.run(inputs).use { return floats(it[0] as OnnxTensor) }
                }
            }
        }
    }

    private fun mapInputs(session: OrtSession, sample: OnnxTensor, timestep: OnnxTensor, hidden: OnnxTensor): Map<String, OnnxTensor> =
        session.inputNames.associateWith { name -> when { name.contains("time") -> timestep; name.contains("encoder") || name.contains("hidden") -> hidden; else -> sample } }

    private fun encodeBitmap(source: Bitmap): FloatArray {
        val bitmap = Bitmap.createScaledBitmap(source, size, size, true)
        val data = FloatArray(3 * size * size)
        for (y in 0 until size) for (x in 0 until size) {
            val c = bitmap.getPixel(x, y); val p = y * size + x
            data[p] = ((c shr 16 and 255) / 127.5f - 1f); data[size * size + p] = ((c shr 8 and 255) / 127.5f - 1f); data[2 * size * size + p] = ((c and 255) / 127.5f - 1f)
        }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, size.toLong(), size.toLong())).use { tensor ->
            encoder.run(mapOf(encoder.inputNames.first() to tensor)).use { result ->
                val output = floats(result[0] as OnnxTensor)
                val latent = if (output.size >= 8 * latentSize * latentSize) FloatArray(4 * latentSize * latentSize) { output[it] } else output
                return FloatArray(latent.size) { latent[it] * .18215f }
            }
        }
    }

    private fun decode(latent: FloatArray): Bitmap {
        val scaled = FloatArray(latent.size) { latent[it] / .18215f }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(scaled), longArrayOf(1, 4, latentSize.toLong(), latentSize.toLong())).use { tensor ->
            decoder.run(mapOf(decoder.inputNames.first() to tensor)).use { result ->
                val pixels = floats(result[0] as OnnxTensor); val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val colors = IntArray(size * size)
                for (p in colors.indices) {
                    val r = (((pixels[p] / 2 + .5f).coerceIn(0f, 1f)) * 255).toInt()
                    val g = (((pixels[size * size + p] / 2 + .5f).coerceIn(0f, 1f)) * 255).toInt()
                    val b = (((pixels[2 * size * size + p] / 2 + .5f).coerceIn(0f, 1f)) * 255).toInt()
                    colors[p] = (255 shl 24) or (r shl 16) or (g shl 8) or b
                }
                bitmap.setPixels(colors, 0, size, 0, 0, size, size); return bitmap
            }
        }
    }

    private fun randomLatent() = FloatArray(4 * latentSize * latentSize) { gaussian() }
    private fun gaussian(): Float { val u = Random.nextFloat().coerceAtLeast(1e-7f); return (sqrt(-2.0 * ln(u.toDouble())) * cos(2 * Math.PI * Random.nextFloat())).toFloat() }
    private fun floats(tensor: OnnxTensor): FloatArray { val b = tensor.floatBuffer; val out = FloatArray(b.remaining()); b.get(out); return out }

    override fun close() { text.close(); unet.close(); encoder.close(); decoder.close(); options.close() }
}

internal class DdimScheduler(private val count: Int) {
    private val train = 1000
    private val alpha = FloatArray(train)
    val timesteps = IntArray(count) { train - 1 - it * (train / count) }
    init { var product = 1.0; for (i in 0 until train) { val beta = .00085 + (.012 - .00085) * i / (train - 1); product *= 1.0 - beta; alpha[i] = product.toFloat() } }
    fun addNoise(original: FloatArray, noise: FloatArray, t: Int) = FloatArray(original.size) { sqrt(alpha[t]) * original[it] + sqrt(1f - alpha[t]) * noise[it] }
    fun step(noise: FloatArray, t: Int, sample: FloatArray): FloatArray {
        val prev = (t - train / count).coerceAtLeast(0); val a = alpha[t]; val ap = alpha[prev]
        return FloatArray(sample.size) { i ->
            val predicted = (sample[i] - sqrt(1f - a) * noise[i]) / sqrt(a)
            sqrt(ap) * predicted + sqrt(1f - ap) * noise[i]
        }
    }
}

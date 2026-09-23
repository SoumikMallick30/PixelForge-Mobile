package dev.pixelforge.mobile.diffusion

import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Minimal CLIP byte-pair tokenizer compatible with Stable Diffusion v1 model packs. */
class ClipTokenizer(modelDir: File) {
    private val encoder: Map<String, Int>
    private val ranks: Map<Pair<String, String>, Int>
    private val cache = ConcurrentHashMap<String, List<String>>()
    private val byteEncoder = bytesToUnicode()
    private val pattern = Regex("<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]+|[^\\s\\p{L}\\p{N}]+", RegexOption.IGNORE_CASE)

    init {
        val json = JSONObject(File(modelDir, "tokenizer/vocab.json").readText())
        encoder = json.keys().asSequence().associateWith { json.getInt(it) }
        ranks = File(modelDir, "tokenizer/merges.txt").readLines().drop(1).mapNotNull { line ->
            val p = line.trim().split(" "); if (p.size == 2) p[0] to p[1] else null
        }.withIndex().associate { it.value to it.index }
    }

    fun encode(text: String): LongArray {
        val tokens = mutableListOf(encoder["<|startoftext|>"] ?: 49406)
        pattern.findAll(text.lowercase()).forEach { match ->
            val transformed = match.value.encodeToByteArray().joinToString("") { byteEncoder[it.toInt() and 0xff].toString() }
            bpe(transformed).forEach { encoder[it]?.let(tokens::add) }
        }
        val end = encoder["<|endoftext|>"] ?: 49407
        tokens += end
        while (tokens.size < 77) tokens += end
        return tokens.take(77).map(Int::toLong).toLongArray()
    }

    private fun bpe(token: String): List<String> = cache.getOrPut(token) {
        var word = token.mapIndexed { i, c -> if (i == token.lastIndex) "$c</w>" else c.toString() }
        while (word.size > 1) {
            val pairs = word.zipWithNext()
            val best = pairs.minByOrNull { ranks[it] ?: Int.MAX_VALUE } ?: break
            if (best !in ranks) break
            val merged = mutableListOf<String>(); var i = 0
            while (i < word.size) {
                if (i < word.lastIndex && word[i] == best.first && word[i + 1] == best.second) { merged += word[i] + word[i + 1]; i += 2 }
                else { merged += word[i]; i++ }
            }
            word = merged
        }
        word
    }

    private fun bytesToUnicode(): Map<Int, Char> {
        val base = (33..126).toMutableList().apply { addAll(161..172); addAll(174..255) }
        val chars = base.toMutableList(); var extra = 0
        for (b in 0..255) if (b !in base) { base += b; chars += (256 + extra++) }
        return base.zip(chars.map(Int::toChar)).toMap()
    }
}

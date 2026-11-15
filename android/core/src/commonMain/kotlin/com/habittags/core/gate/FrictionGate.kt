package com.habittags.core.gate

import kotlin.random.Random

object FrictionGate {
    const val TARGET_WORDS = 100
    const val MAX_INSERT = 3

    fun passage(random: Random, targetWords: Int = TARGET_WORDS): String {
        val picked = mutableListOf<String>()
        var words = 0
        for (sentence in GATE_SENTENCES.shuffled(random)) {
            if (words >= targetWords) break
            picked += sentence
            words += sentence.split(" ").size
        }
        return picked.joinToString(" ")
    }

    /** Rejects bulk inserts (paste, dictation, suggestion taps); only typing a few characters at a time counts. */
    fun acceptEdit(before: String, after: String): Boolean = after.length - before.length <= MAX_INSERT

    fun progress(typed: String, target: String): Int =
        normalize(typed).zip(normalize(target)).takeWhile { (a, b) -> a == b }.size

    fun isComplete(typed: String, target: String): Boolean = normalize(typed) == normalize(target)

    /** Characters of the target typed correctly so far, counting a just-typed trailing space. */
    fun matchedLength(typed: String, target: String): Int =
        normalizeTyping(typed).zip(normalize(target)).takeWhile { (a, b) -> a == b }.size

    fun hasTypo(typed: String, target: String): Boolean = normalizeTyping(typed).length > matchedLength(typed, target)

    /** Whole words finished: each followed by a typed space, or the last word once the passage is complete. */
    fun wordsTyped(typed: String, target: String): Int {
        val t = normalize(target)
        val m = matchedLength(typed, target)
        return t.take(m).count { it == ' ' } + if (m == t.length && m > 0) 1 else 0
    }

    fun wordCount(target: String): Int = normalize(target).split(' ').count { it.isNotEmpty() }

    private fun normalize(s: String) = s.lowercase().trim().replace(Regex("\\s+"), " ")

    private fun normalizeTyping(s: String) = s.lowercase().replace(Regex("\\s+"), " ").trimStart()
}

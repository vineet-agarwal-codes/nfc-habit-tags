package com.habittags.core.gate

import kotlin.random.Random

object FrictionGate {
    const val TARGET_WORDS = 100
    const val MAX_INSERT = 3

    private val sentences: List<String> by lazy {
        FrictionGate::class.java.getResourceAsStream("/gate_sentences.txt")!!
            .bufferedReader().readLines().map(String::trim).filter(String::isNotEmpty)
    }

    fun passage(random: Random, targetWords: Int = TARGET_WORDS): String {
        val picked = mutableListOf<String>()
        var words = 0
        for (sentence in sentences.shuffled(random)) {
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

    private fun normalize(s: String) = s.lowercase().trim().replace(Regex("\\s+"), " ")
}

package com.example.emrtdreader.utils

import android.util.Log

enum class MrzFormat { TD3, TD1 }

data class MrzCandidate(
    val line1: String,
    val line2: String,
    val line3: String? = null,
    val score: Int,
    val format: MrzFormat
) {
    fun toResult() = MrzResult(line1, line2, line3, format)
}

data class MrzResult(
    val line1: String,
    val line2: String,
    val line3: String?,
    val format: MrzFormat
)

object MrzNormalizer {

    fun normalize(rawOcrText: String): MrzResult? {
        val td3 = findTd3Candidates(rawOcrText)
        val td1 = findTd1Candidates(rawOcrText)

        val all = td3 + td1

        return all
            .maxByOrNull { it.score }
            ?.takeIf { it.score >= 3 } // At least 3 of 4 checksums must match
            ?.toResult()
    }

    private fun findTd3Candidates(text: String): List<MrzCandidate> {
        val clean = normalizeRawText(text)
        val results = mutableListOf<MrzCandidate>()
        for (i in 0..clean.length - 88) {
            val l1 = clean.substring(i, i + 44)
            val l2 = clean.substring(i + 44, i + 88)
            val normalized = normalizeTd3Lines(l1, l2)
            val scored = scoreTd3(normalized)
            if (scored.score > 0) results += scored
        }
        return results
    }

    private fun findTd1Candidates(text: String): List<MrzCandidate> {
        val clean = normalizeRawText(text)
        val results = mutableListOf<MrzCandidate>()
        for (i in 0..clean.length - 90) {
            val l1 = clean.substring(i, i + 30)
            val l2 = clean.substring(i + 30, i + 60)
            val l3 = clean.substring(i + 60, i + 90)
            val normalized = normalizeTd1Lines(l1, l2, l3)
            val scored = scoreTd1(normalized)
            if (scored.score > 0) results += scored
        }
        return results
    }

    private fun normalizeRawText(text: String): String =
        text.uppercase()
            .replace(" ", "")
            .replace("\n", "")
            .filter { it in MRZ_CHARSET }

    private val MRZ_CHARSET = ('A'..'Z') + ('0'..'9') + '<'

    private val OCR_DIGIT_FIX = mapOf(
        'O' to '0', 'Q' to '0', 'D' to '0',
        'I' to '1', 'L' to '1',
        'Z' to '2', 'S' to '5',
        'B' to '8', 'G' to '6', 'T' to '7'
    )

    private fun normalizeTd3Lines(l1: String, l2: String): Pair<String, String> {
        return normalizeLine(l1, numeric = false, length = 44) to
               normalizeLine(l2, numeric = true, length = 44)
    }

    private fun normalizeTd1Lines(l1: String, l2: String, l3: String): Triple<String, String, String> =
        Triple(
            normalizeLine(l1, numeric = false, length = 30),
            normalizeLine(l2, numeric = true, length = 30),
            normalizeLine(l3, numeric = false, length = 30)
        )

    private fun normalizeLine(line: String, numeric: Boolean, length: Int): String {
        val sb = StringBuilder()
        for (c in line) {
            sb.append(
                when {
                    c in MRZ_CHARSET -> c
                    numeric && c in OCR_DIGIT_FIX -> OCR_DIGIT_FIX[c]
                    else -> '<'
                }
            )
        }
        return sb.toString().padEnd(length, '<').take(length)
    }

    private val WEIGHTS = intArrayOf(7, 3, 1)

    private fun checksum(data: String): Int {
        var sum = 0
        for (i in data.indices) {
            val v = when (val c = data[i]) {
                in '0'..'9' -> c.digitToInt()
                in 'A'..'Z' -> c.code - 'A'.code + 10
                '<' -> 0
                else -> 0
            }
            sum += v * WEIGHTS[i % 3]
        }
        return sum % 10
    }

    private fun scoreTd3(lines: Pair<String, String>): MrzCandidate {
        val (l1, l2) = lines
        var score = 0
        if (checksum(l2.substring(0, 9)) == digit(l2[9])) score++
        if (checksum(l2.substring(13, 19)) == digit(l2[19])) score++
        if (checksum(l2.substring(21, 27)) == digit(l2[27])) score++
        val composite = l2.substring(0, 10) + l2.substring(13, 20) + l2.substring(21, 43)
        if (checksum(composite) == digit(l2[43])) score++
        return MrzCandidate(l1, l2, score = score, format = MrzFormat.TD3)
    }

    private fun scoreTd1(lines: Triple<String, String, String>): MrzCandidate {
        val (l1, l2, l3) = lines
        var score = 0
        if (checksum(l1.substring(5, 14)) == digit(l1[14])) score++
        if (checksum(l2.substring(0, 6)) == digit(l2[6])) score++
        if (checksum(l2.substring(8, 13)) == digit(l2[14])) score++
        val composite = l1.substring(5, 30) + l2.substring(0, 7) + l2.substring(8, 15) + l3.substring(0, 29)
        if (checksum(composite) == digit(l3[29])) score++
        return MrzCandidate(l1, l2, l3, score = score, format = MrzFormat.TD1)
    }

    private fun digit(c: Char): Int = if (c in '0'..'9') c.digitToInt() else -1
}
package com.example.emrtdreader.utils

class MrzBurstAggregator(
    private val minFrames: Int = 3,
    private val maxFrames: Int = 10
) {
    private val burstResults = mutableListOf<MrzResult>()

    fun aggregate(newResult: MrzResult?): AggregatedMrz? {
        if (newResult != null) {
            burstResults.add(newResult)
        }

        if (burstResults.size < minFrames) return null

        val finalResult = burstResults
            .groupBy { it.format }
            .values
            .mapNotNull { aggregateSameFormat(it) }
            .maxByOrNull { it.confidence }

        if (burstResults.size >= maxFrames) {
            burstResults.clear()
        }

        return finalResult
    }

    private fun aggregateSameFormat(results: List<MrzResult>): AggregatedMrz? {
        if (results.isEmpty()) return null
        val format = results.first().format

        return when (format) {
            MrzFormat.TD3 -> aggregateTd3(results)
            MrzFormat.TD1 -> aggregateTd1(results)
        }
    }

    private fun aggregateTd3(results: List<MrzResult>): AggregatedMrz {
        val l1 = voteLine(results.map { it.line1 }, 44)
        val l2 = voteLine(results.map { it.line2 }, 44)
        val confidence = countValidTd3(results)
        return AggregatedMrz(
            result = MrzResult(l1, l2, null, MrzFormat.TD3),
            confidence = confidence
        )
    }

    private fun aggregateTd1(results: List<MrzResult>): AggregatedMrz {
        val l1 = voteLine(results.map { it.line1 }, 30)
        val l2 = voteLine(results.map { it.line2 }, 30)
        val l3 = voteLine(results.map { it.line3!! }, 30)
        val confidence = countValidTd1(results)
        return AggregatedMrz(
            result = MrzResult(l1, l2, l3, MrzFormat.TD1),
            confidence = confidence
        )
    }

    private fun voteLine(lines: List<String>, length: Int): String {
        val result = CharArray(length)
        for (i in 0 until length) {
            result[i] = lines
                .map { it.getOrNull(i) ?: '<' }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }?.key ?: '<'
        }
        return result.concatToString()
    }

    private fun countValidTd3(results: List<MrzResult>): Int = results.count { MrzChecksumValidator.isValidTd3(it) }
    private fun countValidTd1(results: List<MrzResult>): Int = results.count { MrzChecksumValidator.isValidTd1(it) }
    
    fun clear() = burstResults.clear()
}

object MrzChecksumValidator {
    private val WEIGHTS = intArrayOf(7, 3, 1)

    private fun checksum(data: String): Int {
        var sum = 0
        data.forEachIndexed { i, char ->
            val value = when (char) {
                in '0'..'9' -> char.digitToInt()
                in 'A'..'Z' -> char - 'A' + 10
                '<' -> 0
                else -> 0
            }
            sum += value * WEIGHTS[i % 3]
        }
        return sum % 10
    }
    
    private fun digit(c: Char): Int = if (c in '0'..'9') c.digitToInt() else -1

    fun isValidTd3(r: MrzResult): Boolean {
        val l2 = r.line2
        if (l2.length < 44) return false
        return checksum(l2.substring(0, 9)) == digit(l2[9]) &&
               checksum(l2.substring(13, 19)) == digit(l2[19]) &&
               checksum(l2.substring(21, 27)) == digit(l2[27])
    }

    fun isValidTd1(r: MrzResult): Boolean {
        val l1 = r.line1
        val l2 = r.line2
        val l3 = r.line3 ?: return false
        if (l1.length < 30 || l2.length < 30 || l3.length < 30) return false

        return checksum(l1.substring(5, 14)) == digit(l1[14]) &&
               checksum(l2.substring(0, 6)) == digit(l2[6]) &&
               checksum(l2.substring(8, 14)) == digit(l2[14]) &&
               checksum(l1.substring(5, 30) + l2.substring(0, 7) + l2.substring(8, 15) + l3.substring(0, 29)) == digit(l3[29])
    }
}

data class AggregatedMrz(
    val result: MrzResult,
    val confidence: Int
)

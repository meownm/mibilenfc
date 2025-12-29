package com.example.emrtdreader.utils

import android.util.Log
import java.util.Calendar
import java.util.Locale

/**
 * Utility class for parsing and validating Machine Readable Zone (MRZ) data
 * according to ICAO 9303 standards, with robust, context-aware error correction.
 */
object MRZParser {

    data class MRZData(
        val documentType: String,
        val countryCode: String,
        val lastName: String,
        val firstName: String,
        val documentNumber: String,
        val nationality: String,
        val dateOfBirth: String, // Formatted as YYYY-MM-DD
        val expiryDate: String, // Formatted as YYYY-MM-DD
        val sex: String,
        val personalNumber: String,
        val mrzString: String
    )

    private val charMap = mapOf(
        'O' to 0, 'I' to 1, 'L' to 1, 'Z' to 2, 'S' to 5, 'B' to 8, 'G' to 6, 'T' to 7
    )
    private val reverseCharMap = charMap.entries.associate { (k, v) -> v.digitToChar() to k }

    fun tryExtractMrz(rawText: String): String? {
        val lines = rawText.lines().map { normalize(it) }
        for (i in 0 until lines.size - 1) {
            if (lines[i].startsWith("P<")) {
                val candidate = lines[i] + "\n" + lines[i+1]
                if (parseMRZ(candidate) != null) return candidate
            }
        }
        return null
    }

    fun parseMRZ(mrz: String): MRZData? {
        val lines = mrz.lines().map { normalize(it) }
        if (lines.size < 2) return null

        return parseTD3(lines[0], lines[1])
    }

    private fun parseTD3(line1: String, line2: String): MRZData? {
        val pLine1 = line1.padEnd(44, '<')
        val pLine2 = line2.padEnd(44, '<')
        if (pLine1.length != 44 || pLine2.length != 44) return null

        val docNumResult = validateAndCorrect(pLine2.substring(0, 9), pLine2.substring(9, 10), isNumeric = true)
        if (!docNumResult.first) return null

        val dobResult = validateAndCorrect(pLine2.substring(13, 19), pLine2.substring(19, 20), isNumeric = true)
        if (!dobResult.first) return null

        val expiryResult = validateAndCorrect(pLine2.substring(21, 27), pLine2.substring(27, 28), isNumeric = true)
        if (!expiryResult.first) return null

        val personalNumRaw = pLine2.substring(28, 42)
        if (personalNumRaw.any { it != '<' }) {
            val personalNumCheck = pLine2.substring(42, 43)
            if (!validateAndCorrect(personalNumRaw, personalNumCheck, isNumeric = false).first) return null
        }
        
        val names = parseNames(pLine1.substring(5, 44))

        return MRZData(
            documentType = pLine1.substring(0, 1),
            countryCode = pLine1.substring(2, 5),
            lastName = names.first,
            firstName = names.second,
            documentNumber = docNumResult.second,
            nationality = pLine2.substring(10, 13),
            dateOfBirth = formatMrzDate(dobResult.second),
            expiryDate = formatMrzDate(expiryResult.second),
            sex = pLine2.substring(20, 21),
            personalNumber = personalNumRaw.replace("<", ""),
            mrzString = "${pLine1}\n${pLine2}"
        )
    }

    private fun parseNames(nameString: String): Pair<String, String> {
        val names = nameString.split("<<").map { it.replace('<', ' ').trim() }
        return Pair(names.getOrElse(0) { "" }, names.getOrElse(1) { "" })
    }

    private fun normalize(input: String): String {
        return input.uppercase().replace(Regex("[^A-Z0-9<]"), "")
    }

    private fun calculateChecksum(data: String): Int {
        val weights = intArrayOf(7, 3, 1)
        return data.asSequence().mapIndexed { i, char ->
            val value = when (char) {
                in '0'..'9' -> char.digitToInt()
                in 'A'..'Z' -> char - 'A' + 10
                else -> 0
            }
            value * weights[i % 3]
        }.sum() % 10
    }

    private fun validateAndCorrect(data: String, checkDigit: String, isNumeric: Boolean): Pair<Boolean, String> {
        val expected = checkDigit.toIntOrNull() ?: return Pair(false, data)
        if (calculateChecksum(data) == expected) return Pair(true, data)

        // Attempt to correct one character
        for (i in data.indices) {
            val originalChar = data[i]
            val correctionMap = if (isNumeric) charMap else reverseCharMap

            if (correctionMap.containsKey(originalChar)) {
                val correctedData = data.toCharArray()
                correctedData[i] = if(isNumeric) correctionMap[originalChar]!!.digitToChar() else correctionMap[originalChar]!!
                val correctedString = String(correctedData)
                if (calculateChecksum(correctedString) == expected) {
                    Log.d("MRZParser", "Checksum corrected by context: '$data' -> '$correctedString'")
                    return Pair(true, correctedString)
                }
            }
        }

        return Pair(false, data)
    }

    private fun formatMrzDate(mrzDate: String): String {
        if (mrzDate.length != 6) return ""
        return try {
            val year = mrzDate.substring(0, 2).toInt()
            val currentYear = Calendar.getInstance().get(Calendar.YEAR) % 100
            val fullYear = if (year > currentYear) 1900 + year else 2000 + year
            String.format(Locale.US, "%04d-%s-%s", fullYear, mrzDate.substring(2, 4), mrzDate.substring(4, 6))
        } catch (e: Exception) {
            ""
        }
    }
}
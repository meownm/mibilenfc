package com.example.emrtdreader.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for parsing and validating Machine Readable Zone (MRZ) data
 * according to ICAO 9303 standards, with robust error correction.
 */
object MRZParser {

    data class MRZData(
        val documentType: String,
        val countryCode: String,
        val lastName: String,
        val firstName: String,
        val documentNumber: String,
        val nationality: String,
        val dateOfBirth: String,
        val expiryDate: String,
        val sex: String,
        val personalNumber: String,
        val mrzString: String
    )

    // Heuristic search for MRZ
    fun tryExtractMrz(raw: String): String? {
        val lines = raw.lines().map { normalizeMRZ(it) }

        // Primary strategy: Find a passport anchor "P<"
        for (i in 0 until lines.size - 1) {
            if (lines[i].startsWith("P<")) {
                val twoLines = lines[i] + "\n" + lines[i+1]
                if (parseMRZ(twoLines) != null) {
                    Log.d("MRZParser", "Found MRZ using 'P<' heuristic.")
                    return twoLines
                }
            }
        }

        // Fallback strategy: check all adjacent lines (for ID cards or failed heuristics)
        for (i in 0 until lines.size - 1) {
            val twoLines = lines[i] + "\n" + lines[i+1]
            if (parseMRZ(twoLines) != null) {
                Log.d("MRZParser", "Found 2-line MRZ using fallback.")
                return twoLines
            }
        }
        for (i in 0 until lines.size - 2) {
            val threeLines = lines[i] + "\n" + lines[i+1] + "\n" + lines[i+2]
            if (parseMRZ(threeLines) != null) {
                Log.d("MRZParser", "Found 3-line MRZ using fallback.")
                return threeLines
            }
        }

        return null
    }
    
    fun parseMRZ(mrz: String): MRZData? {
        val normalizedMRZ = normalizeMRZ(mrz)
        val lines = normalizedMRZ.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        if (lines.isEmpty()) return null
        
        return when {
            lines.size >= 2 && lines[0].startsWith("P") -> parseTD3(lines[0], lines[1])
            lines.size >= 3 && (lines[0].startsWith("I") || lines[0].startsWith("A") || lines[0].startsWith("C")) -> parseTD1(lines[0], lines[1], lines[2])
            else -> null
        }
    }
    
    private fun parseTD3(line1: String, line2: String): MRZData? {
        val pLine1 = line1.padEnd(44, '<')
        val pLine2 = line2.padEnd(44, '<')

        if (pLine1.length > 44 || pLine2.length > 44) return null

        val documentNumberRaw = pLine2.substring(0, 9)
        val docNumCheckDigit = pLine2.substring(9, 10).toIntOrNull() ?: return null
        val (docNumValid, correctedDocNum) = validateAndCorrect(documentNumberRaw, docNumCheckDigit)
        if (!docNumValid) return null

        val dobRaw = pLine2.substring(13, 19)
        val dobCheckDigit = pLine2.substring(19, 20).toIntOrNull() ?: return null
        val (dobValid, correctedDob) = validateAndCorrect(dobRaw, dobCheckDigit)
        if (!dobValid) return null

        val expiryRaw = pLine2.substring(21, 27)
        val expiryCheckDigit = pLine2.substring(27, 28).toIntOrNull() ?: return null
        val (expiryValid, correctedExpiry) = validateAndCorrect(expiryRaw, expiryCheckDigit)
        if (!expiryValid) return null
        
        val personalNumberRaw = pLine2.substring(28, 42)
        val personalNumberCheckDigit = pLine2.substring(42, 43).toIntOrNull()
        if (personalNumberCheckDigit != null && personalNumberRaw.any { it != '<' }) {
             val (personalNumValid, _) = validateAndCorrect(personalNumberRaw, personalNumberCheckDigit)
             if (!personalNumValid) return null
        }

        val nameString = pLine1.substring(5, 44)
        val names = parseNames(nameString)
        
        return MRZData(
            documentType = pLine1.substring(0, 1),
            countryCode = pLine1.substring(2, 5),
            lastName = names.first,
            firstName = names.second,
            documentNumber = correctedDocNum,
            nationality = pLine2.substring(10, 13),
            dateOfBirth = formatMRZDate(correctedDob),
            expiryDate = formatMRZDate(correctedExpiry),
            sex = pLine2.substring(20, 21),
            personalNumber = personalNumberRaw.replace("<", "").trim(),
            mrzString = "$pLine1\n$pLine2"
        )
    }
    
    // parseTD1 can be improved similarly if needed
    private fun parseTD1(line1: String, line2: String, line3: String): MRZData? { return null }

    private fun parseNames(nameString: String): Pair<String, String> {
        val names = nameString.split("<<").map { it.replace("<", " ").trim() }
        return Pair(names.getOrElse(0) { "" }, names.getOrElse(1) { "" })
    }
    
    fun normalizeMRZ(input: String): String {
        return input
            .uppercase()
            .replace(Regex("[\\s-]"), "") // Remove all whitespace and hyphens
            .replace('O', '0')
            .replace('I', '1')
            .replace('S', '5')
            .replace('B', '8')
            .replace('Z', '2')
            .replace('G', '6')
    }

    private fun calculateChecksum(data: String): Int {
        val weights = intArrayOf(7, 3, 1)
        var sum = 0
        data.forEachIndexed { i, char ->
            val value = when {
                char.isDigit() -> char.digitToInt()
                char.isLetter() -> char - 'A' + 10
                char == '<' -> 0
                else -> 0
            }
            sum += value * weights[i % 3]
        }
        return sum % 10
    }

    private fun validateAndCorrect(data: String, expectedCheckDigit: Int): Pair<Boolean, String> {
        if (calculateChecksum(data) == expectedCheckDigit) {
            return Pair(true, data)
        }

        val errorMap = mapOf('0' to 'O', '1' to 'I', '5' to 'S', '8' to 'B', '2' to 'Z', '6' to 'G')
        val reversedErrorMap = errorMap.entries.associate { (k, v) -> v to k }
        val combinedMap = errorMap + reversedErrorMap

        for (i in data.indices) {
            val originalChar = data[i]
            if (combinedMap.containsKey(originalChar)) {
                val correctedData = data.toMutableList()
                correctedData[i] = combinedMap[originalChar]!!.toChar()
                val correctedString = correctedData.joinToString("")
                if (calculateChecksum(correctedString) == expectedCheckDigit) {
                    Log.d("MRZParser", "Checksum corrected! '$data' -> '$correctedString'")
                    return Pair(true, correctedString)
                }
            }
        }
        return Pair(false, data)
    }
    
    private fun formatMRZDate(mrzDate: String): String {
        if (mrzDate.length != 6) return mrzDate
        val currentYear = Calendar.getInstance().get(Calendar.YEAR) % 100
        val year = mrzDate.substring(0, 2).toIntOrNull() ?: return mrzDate
        val month = mrzDate.substring(2, 4).toIntOrNull() ?: return mrzDate
        val day = mrzDate.substring(4, 6).toIntOrNull() ?: return mrzDate
        val fullYear = if (year > currentYear) 1900 + year else 2000 + year
        return String.format(Locale.getDefault(), "%04d-%02d-%02d", fullYear, month, day)
    }
}

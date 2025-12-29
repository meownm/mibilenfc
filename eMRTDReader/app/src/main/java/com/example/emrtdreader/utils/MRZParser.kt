package com.example.emrtdreader.utils

import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for parsing and validating Machine Readable Zone (MRZ) data
 * according to ICAO 9303 standards
 */
object MRZParser {

    /**
     * Tries to extract a valid MRZ string from a larger block of text.
     * @param raw The raw text from OCR
     * @return The extracted MRZ string, or null if not found
     */
    fun tryExtractMrz(raw: String): String? {
        val lines = raw.lines().map { normalizeMRZ(it) }

        // Try to find a 2-line TD3 MRZ
        for (i in 0 until lines.size - 1) {
            val twoLines = lines[i] + "\n" + lines[i+1]
            if (parseMRZ(twoLines) != null) {
                return twoLines
            }
        }

        // Try to find a 3-line TD1 MRZ
        for (i in 0 until lines.size - 2) {
            val threeLines = lines[i] + "\n" + lines[i+1] + "\n" + lines[i+2]
            if (parseMRZ(threeLines) != null) {
                return threeLines
            }
        }

        return null
    }
    
    /**
     * Parses MRZ string and validates checksums
     * @param mrz The MRZ string to parse
     * @return Parsed MRZ data or null if invalid
     */
    fun parseMRZ(mrz: String): MRZData? {
        val normalizedMRZ = normalizeMRZ(mrz)
        val lines = normalizedMRZ.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        if (lines.isEmpty()) return null
        
        return when {
            // TD3 format (Passport - 2 lines of 44 characters each)
            lines.size >= 2 && lines[0].startsWith("P") -> parseTD3(lines[0], lines[1])
            // TD1 format (ID card - 3 lines of 30 characters each) 
            lines.size >= 3 && (lines[0].startsWith("I") || lines[0].startsWith("A") || lines[0].startsWith("C")) -> parseTD1(lines[0], lines[1], lines[2])
            else -> null
        }
    }
    
    private fun parseTD3(line1: String, line2: String): MRZData? {
        val paddedLine1 = line1.padEnd(44, '<')
        val paddedLine2 = line2.padEnd(44, '<')

        if (paddedLine1.length != 44 || paddedLine2.length != 44) return null
        
        // Extract document type (position 1)
        val documentType = paddedLine1.substring(0, 1)
        
        // Extract country code (positions 2-4)
        val countryCode = paddedLine1.substring(2, 5).trim()
        
        // Extract name (positions 6-44)
        val nameString = paddedLine1.substring(5, 44).trim()
        val names = parseNames(nameString)
        
        // Extract document number (positions 5-14)
        val documentNumber = paddedLine2.substring(0, 9).trim()
        val docNumCheckDigit = paddedLine2.substring(9, 10).toIntOrNull() ?: return null
        
        // Extract nationality (positions 15-17)
        val nationality = paddedLine2.substring(10, 13).trim()
        
        // Extract date of birth (positions 18-25)
        val dateOfBirth = paddedLine2.substring(13, 19)
        val dobCheckDigit = paddedLine2.substring(19, 20).toIntOrNull() ?: return null
        
        // Extract sex (position 26)
        val sex = paddedLine2.substring(20, 21)
        
        // Extract expiry date (positions 27-34)
        val expiryDate = paddedLine2.substring(21, 27)
        val expiryCheckDigit = paddedLine2.substring(27, 28).toIntOrNull() ?: return null
        
        // Extract nationality (positions 29-42)
        val personalNumber = paddedLine2.substring(28, 42).trim()
        val personalNumCheckDigit = paddedLine2.substring(42, 43).toIntOrNull() ?: return null
        
        // Validate checksums
        if (!validateChecksum(documentNumber, docNumCheckDigit) ||
            !validateChecksum(dateOfBirth, dobCheckDigit) ||
            !validateChecksum(expiryDate, expiryCheckDigit) ||
            personalNumber.isNotEmpty() && !validateChecksum(personalNumber, personalNumCheckDigit)) {
            return null
        }
        
        return MRZData(
            documentType = documentType,
            countryCode = countryCode,
            lastName = names.first,
            firstName = names.second,
            documentNumber = documentNumber,
            nationality = nationality,
            dateOfBirth = formatMRZDate(dateOfBirth),
            expiryDate = formatMRZDate(expiryDate),
            sex = sex,
            personalNumber = personalNumber,
            mrzString = "$paddedLine1\n$paddedLine2"
        )
    }
    
    private fun parseTD1(line1: String, line2: String, line3: String): MRZData? {
        val paddedLine1 = line1.padEnd(30, '<')
        val paddedLine2 = line2.padEnd(30, '<')
        val paddedLine3 = line3.padEnd(30, '<')

        if (paddedLine1.length != 30 || paddedLine2.length != 30 || paddedLine3.length != 30) return null

        // Extract document type (positions 1-2)
        val documentType = paddedLine1.substring(0, 2)
        
        // Extract country code (positions 3-5)
        val countryCode = paddedLine1.substring(2, 5).trim()
        
        // Extract document number (positions 6-14)
        val documentNumber = paddedLine1.substring(5, 14).trim()
        val docNumCheckDigit = paddedLine1.substring(14, 15).toIntOrNull() ?: return null
        
        // Extract personal number (positions 16-29)
        val personalNumber = paddedLine1.substring(15, 29).trim()
        val personalNumCheckDigit = paddedLine1.substring(29, 30).toIntOrNull() ?: return null
        
        // Extract date of birth (positions 1-7)
        val dateOfBirth = paddedLine2.substring(0, 6)
        val dobCheckDigit = paddedLine2.substring(6, 7).toIntOrNull() ?: return null
        
        // Extract sex (position 8)
        val sex = paddedLine2.substring(7, 8)
        
        // Extract expiry date (positions 9-15)
        val expiryDate = paddedLine2.substring(8, 14)
        val expiryCheckDigit = paddedLine2.substring(14, 15).toIntOrNull() ?: return null
        
        // Extract nationality (positions 16-18)
        val nationality = paddedLine2.substring(15, 18).trim()
        
        // Extract name (positions 6-30)
        val nameString = paddedLine3.trim()
        val names = parseNames(nameString)
        
        // Validate checksums
        if (!validateChecksum(documentNumber, docNumCheckDigit) ||
            !validateChecksum(dateOfBirth, dobCheckDigit) ||
            !validateChecksum(expiryDate, expiryCheckDigit) ||
            personalNumber.isNotEmpty() && !validateChecksum(personalNumber, personalNumCheckDigit)) {
            return null
        }
        
        return MRZData(
            documentType = documentType,
            countryCode = countryCode,
            lastName = names.first,
            firstName = names.second,
            documentNumber = documentNumber,
            nationality = nationality,
            dateOfBirth = formatMRZDate(dateOfBirth),
            expiryDate = formatMRZDate(expiryDate),
            sex = sex,
            personalNumber = personalNumber,
            mrzString = "$paddedLine1\n$paddedLine2\n$paddedLine3"
        )
    }
    
    /**
     * Parses the name field from MRZ format (Lastname<<Firstname<Secondname)
     */
    private fun parseNames(nameString: String): Pair<String, String> {
        val names = nameString.replace("<", " ").trim().split("  ") // Split on double space
        val lastName = names.firstOrNull()?.trim() ?: ""
        val firstName = if (names.size > 1) names.drop(1).joinToString(" ").trim() else ""
        
        return Pair(lastName, firstName)
    }
    
    /**
     * Normalizes MRZ string by replacing common OCR errors and removing all whitespace.
     */
    fun normalizeMRZ(input: String): String {
        return input
            .uppercase()
            .replace(Regex("\\s"), "") // Remove all whitespace characters
            // Common OCR substitutions (letters to numbers)
            .replace("O", "0")
            .replace("I", "1")
            .replace("S", "5")
            .replace("B", "8")
    }

    /**
     * Validates a checksum according to ICAO 9303 standards
     */
    private fun validateChecksum(data: String, expectedCheckDigit: Int): Boolean {
        val weights = intArrayOf(7, 3, 1)
        var sum = 0
        
        for (i in data.indices) {
            val char = data[i]
            val value = when {
                char.isDigit() -> char.digitToInt()
                char.isLetter() -> char - 'A' + 10
                char == '<' -> 0
                else -> return false
            }
            
            sum += value * weights[i % weights.size]
        }
        
        val calculatedCheckDigit = sum % 10
        return calculatedCheckDigit == expectedCheckDigit
    }
    
    /**
     * Formats MRZ date (YYMMDD) to standard date format
     */
    private fun formatMRZDate(mrzDate: String): String {
        if (mrzDate.length != 6) return mrzDate
        
        val year = mrzDate.substring(0, 2).toIntOrNull() ?: return mrzDate
        val month = mrzDate.substring(2, 4).toIntOrNull() ?: return mrzDate
        val day = mrzDate.substring(4, 6).toIntOrNull() ?: return mrzDate
        
        // Determine century (assuming years 00-25 are 2000s, 26-99 are 1900s)
        val fullYear = if (year <= 25) 2000 + year else 1900 + year
        
        return String.format(Locale.getDefault(), "%04d-%02d-%02d", fullYear, month, day)
    }
}

/**
 * Data class representing parsed MRZ information
 */
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
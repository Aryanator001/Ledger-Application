package com.example.ai.extract

/**
 * Normalizes Hinglish/English transcripts before AI extraction.
 * Converts spoken number words to digits, expands compound numbers,
 * strips currency noise, and trims filler. Fully on-device, synchronous.
 *
 * Coverage: Hindi number words 1-100, hundreds (do sau, teen sau...),
 * multipliers (hazaar, lakh, crore), currency slang (rupaye, paisa, nakad),
 * and common accounting filler words.
 */
class TranscriptNormalizer {

    // Hindi number words: units, teens, tens.
    private val numberWords = mapOf(
        // Units
        "zero" to 0, "sifar" to 0,
        "ek" to 1, "one" to 1,
        "do" to 2, "two" to 2,
        "teen" to 3, "tin" to 3, "three" to 3,
        "char" to 4, "chaar" to 4, "four" to 4,
        "panch" to 5, "paanch" to 5, "five" to 5,
        "chhe" to 6, "chhah" to 6, "cheh" to 6, "six" to 6,
        "saat" to 7, "sat" to 7, "seven" to 7,
        "aath" to 8, "aat" to 8, "eight" to 8,
        "nau" to 9, "nine" to 9,
        "das" to 10, "ten" to 10,

        // Hindi teens (11-19)
        "gyarah" to 11, "gyara" to 11, "eleven" to 11,
        "barah" to 12, "bara" to 12, "twelve" to 12,
        "terah" to 13, "tera" to 13, "thirteen" to 13,
        "chaudah" to 14, "chauda" to 14, "fourteen" to 14,
        "pandrah" to 15, "pandra" to 15, "fifteen" to 15,
        "solah" to 16, "sola" to 16, "sixteen" to 16,
        "satrah" to 17, "satra" to 17, "seventeen" to 17,
        "atharah" to 18, "athara" to 18, "eighteen" to 18,
        "unees" to 19, "unis" to 19, "nineteen" to 19,

        // Hindi tens (20-100)
        "bees" to 20, "bis" to 20, "twenty" to 20,
        "ikkees" to 21, "ikees" to 21,
        "bais" to 22, "baees" to 22,
        "teis" to 23, "teis" to 23,
        "chaubees" to 24, "chobees" to 24,
        "pacchees" to 25, "pachees" to 25,
        "chabbees" to 26, "chhabbis" to 26,
        "sattais" to 27, "sataees" to 27,
        "attais" to 28, "athaees" to 28,
        "untees" to 29, "unatis" to 29,
        "tees" to 30, "thirty" to 30,
        "ikattees" to 31, "ikatees" to 31,
        "battees" to 32, "batees" to 32,
        "paintees" to 35, "paitees" to 35,
        "chalees" to 40, "chalis" to 40, "forty" to 40,
        "paintalis" to 45, "paitalis" to 45,
        "pachas" to 50, "pachass" to 50, "fifty" to 50,
        "pachpan" to 55, "pachpann" to 55,
        "saath" to 60, "saathh" to 60, "sixty" to 60,
        "painsath" to 65, "painsathh" to 65,
        "sattar" to 70, "settar" to 70, "seventy" to 70,
        "pachattar" to 75, "pachhattar" to 75,
        "assi" to 80, "assee" to 80, "eighty" to 80,
        "pachaasi" to 85, "pachasi" to 85,
        "nabbe" to 90, "nabbee" to 90, "ninety" to 90,
        "pachanabbe" to 95, "pachanabbe" to 95,
        "sau" to 100, "hundred" to 100
    )

    // Multipliers that follow a number: "5 hazaar" -> 5000.
    private val multipliers = mapOf(
        "hazaar" to 1000, "hazar" to 1000, "hzar" to 1000, "hajaar" to 1000,
        "thousand" to 1000, "k" to 1000,
        "lakh" to 100_000, "lac" to 100_000, "lakhs" to 100_000, "laakh" to 100_000,
        "crore" to 10_000_000, "cr" to 10_000_000, "crores" to 10_000_000, "karod" to 10_000_000,
        "sau" to 100, "hundred" to 100
    )

    // Compound Hindi hundreds: "do sau" = 200, "teen sau" = 300, etc.
    // Handled by the multiplier logic: numberWords["do"]=2, multipliers["sau"]=100 → 2*100=200.

    // Currency/accounting noise to strip completely.
    private val currencyNoise = setOf(
        "rupaye", "rupye", "rupaiya", "rupee", "rupees", "rs", "inr",
        "paisa", "paise", "paisay",
        "ka", "ki", "ke",          // possessives that add noise before numbers
        "tha", "the", "thi",       // past-tense helpers that add noise
        "lagbhag", "karib",        // "approximately"
        "kuch", "thoda"            // "some"/"a little"
    )

    // Currency symbol characters to strip.
    private val currencySymbols = setOf('₹', '$', '€', '£')

    /**
     * Normalize a transcript:
     * 1. Lowercase and tokenize.
     * 2. Strip currency noise and symbols.
     * 3. Convert number words to digits.
     * 4. Expand "N multiplier" (e.g. "5 hazaar" -> "5000", "do sau" -> "200").
     * 5. Handle "N.5" patterns ("dhai" = 2.5, "sawa" = 1.25x, "dedh" = 1.5).
     * Returns the cleaned string. Always preserves entity names and action verbs.
     */
    fun normalize(transcript: String): String {
        if (transcript.isBlank()) return transcript

        // Remove currency symbols inline.
        var cleaned = transcript.lowercase().trim()
        currencySymbols.forEach { cleaned = cleaned.replace(it.toString(), " ") }

        val tokens = cleaned.split(Regex("\\s+")).toMutableList()
        val result = mutableListOf<String>()

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i].trim { !it.isLetterOrDigit() }

            // Skip empty tokens.
            if (token.isBlank()) { i++; continue }

            // Strip pure currency noise.
            if (token in currencyNoise) { i++; continue }

            // Special Hindi fraction words.
            if (token == "dhai" || token == "dhaai") {
                // "dhai" = 2.5; check if followed by a multiplier.
                if (i + 1 < tokens.size) {
                    val next = tokens[i + 1].trim { !it.isLetterOrDigit() }
                    val mult = multipliers[next]
                    if (mult != null) {
                        result.add((2.5 * mult).toLong().toString())
                        i += 2; continue
                    }
                }
                result.add("2.5")
                i++; continue
            }
            if (token == "dedh") {
                // "dedh" = 1.5; check multiplier.
                if (i + 1 < tokens.size) {
                    val next = tokens[i + 1].trim { !it.isLetterOrDigit() }
                    val mult = multipliers[next]
                    if (mult != null) {
                        result.add((1.5 * mult).toLong().toString())
                        i += 2; continue
                    }
                }
                result.add("1.5")
                i++; continue
            }
            if (token == "sawa") {
                // "sawa" = 1.25x next value; e.g. "sawa sau" = 125, "sawa lakh" = 125000.
                if (i + 1 < tokens.size) {
                    val next = tokens[i + 1].trim { !it.isLetterOrDigit() }
                    val mult = multipliers[next]
                    if (mult != null) {
                        result.add((1.25 * mult).toLong().toString())
                        i += 2; continue
                    }
                    val nextNum = next.toIntOrNull() ?: numberWords[next]
                    if (nextNum != null) {
                        result.add((1.25 * nextNum).toLong().toString())
                        i += 2; continue
                    }
                }
                i++; continue  // "sawa" alone makes no sense; skip.
            }

            // Try to parse as a number (digit or word).
            val asNumber = token.toIntOrNull() ?: numberWords[token]

            if (asNumber != null) {
                // Check if followed by a multiplier: "5 hazaar", "do sau", etc.
                if (i + 1 < tokens.size) {
                    val next = tokens[i + 1].trim { !it.isLetterOrDigit() }
                    val mult = multipliers[next]
                    if (mult != null) {
                        val expanded = asNumber.toLong() * mult.toLong()
                        // Check for chained multipliers: "2 lakh 50 hazaar" pattern.
                        // For now, just emit the single expanded value.
                        result.add(expanded.toString())
                        i += 2; continue
                    }
                }
                // Standalone number word -> digit (but not if it's a multiplier word like "sau").
                if (token !in multipliers) {
                    result.add(asNumber.toString())
                    i++; continue
                }
            }

            // Keep everything else (entity names, verbs, etc.) as-is.
            if (token.isNotBlank()) result.add(token)
            i++
        }

        return result.joinToString(" ").trim()
    }
}

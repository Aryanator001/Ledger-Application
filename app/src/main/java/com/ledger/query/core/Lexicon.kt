package com.ledger.query.core

object Lexicon {

    val DIRECTION_OUT: Set<String> = setOf(
        // English
        "paid", "pay", "pays", "gave", "give", "spent", "spend", "sent", "send",
        "expense", "expenses", "expenditure", "cost", "costs", "outflow", "debit",
        "purchased", "bought", "buy", "purchase",
        // Hindi romanized
        "kharcha", "kharch", "diya", "diye", "dii", "dena", "de", "deta", "deti",
        "bheje", "bheja", "chukaye", "chukaya", "chuka",
        "kharcha", "kiya", "nikala",
        // Hindi Devanagari
        "दिए", "दिया", "खर्च", "भेजा", "दिया", "खर्चा",
        // Gujarati
        "aapya", "aapyu", "aapyo", "kharch", "ખર્ચ", "aapel"
    )

    val DIRECTION_IN: Set<String> = setOf(
        // English
        "received", "receive", "got", "get", "income", "earned", "earn", "revenue",
        "collected", "collect", "sales", "sale", "credit", "inflow", "deposit",
        // Hindi romanized
        "mila", "mile", "mili", "milya", "kamai", "kamaya", "kamaye",
        "aaya", "aaye", "aayi", "vasool", "bikri", "bika",
        "liya", "liye", "li",
        // Hindi Devanagari
        "मिला", "कमाई", "आया", "वसूल", "मिले",
        // Gujarati
        "kamani", "aavak", "kami", "મળ્યું", "kamela", "milyu"
    )

    val DIRECTION_NET: Set<String> = setOf(
        // English
        "balance", "net", "profit", "outstanding", "due", "owed", "owes",
        "settle", "settled", "clear", "cleared",
        // Hindi romanized
        "bacha", "bakaya", "baki", "baaki", "munafa", "hisab", "hisaab",
        "bhaaki", "udhar",
        // Hindi Devanagari
        "बचा", "बकाया", "नफो", "हिसाब",
        // Gujarati
        "baki", "નફો", "hisab"
    )

    val METRIC_SUM: Set<String> = setOf(
        // English
        "how", "much", "total", "sum", "amount", "money", "rupees", "value",
        // Hindi romanized
        "kitna", "kitne", "kitni", "kul", "paise", "rakam", "rupay", "paisa",
        // Hindi Devanagari
        "कितना", "कितने", "कितनी", "कुल",
        // Gujarati
        "ketla", "ketlu", "ketli", "raqam", "કેટલા"
    )

    val METRIC_COUNT: Set<String> = setOf(
        // English
        "many", "times", "count", "number", "frequency",
        // Hindi romanized
        "baar", "bar", "dafa", "kitni_baar",
        // Hindi Devanagari
        "बार", "दफा",
        // Gujarati
        "vaar", "var"
    )

    val METRIC_AVG: Set<String> = setOf(
        "average", "avg", "mean",
        "ausat", "औसत",
        "sarasari", "sarashri"
    )

    val METRIC_MAX: Set<String> = setOf(
        "biggest", "highest", "maximum", "max", "most", "largest", "top",
        "sabse", "zyada", "jyada",
        "सबसे", "ज़्यादा",
        "sauthi", "vahu"
    )

    val METRIC_MIN: Set<String> = setOf(
        "smallest", "lowest", "minimum", "min", "least",
        "kam", "thoda",
        "कम",
        "ochu", "ochi"
    )

    val METRIC_LIST: Set<String> = setOf(
        "show", "list", "display", "all", "every", "transactions",
        "dikhao", "dikha", "batao", "bata", "sab", "dekho", "dekha",
        "दिखाओ", "बताओ",
        "batavo", "dakho", "badha"
    )

    val GROUP_BY_ENTITY: Set<String> = setOf(
        "each", "everyone", "wise", "breakdown", "per", "individual",
        "har", "harek", "pratyek",
        "हर", "हरेक",
        "dareka", "drek"
    )

    val GROUP_BY_CATEGORY: Set<String> = setOf(
        "category", "categories", "type", "types", "kind",
        "shreni", "prakar",
        "श्रेणी", "प्रकार"
    )

    val GROUP_BY_MONTH: Set<String> = setOf(
        "monthly", "month-wise",
        "har mahine", "pratyek mahine"
    )

    val SELF_REF: Set<String> = setOf(
        // English
        "i", "me", "my", "myself", "our", "we",
        // Hindi romanized — note: maine/mene/mainne explicitly required
        "main", "mai", "maine", "mene", "mainne",
        "mera", "meri", "mere", "mujhe", "humara", "humne",
        // Hindi Devanagari
        "मैं", "मैने", "मेरा", "मेरी",
        // Gujarati
        "hu", "hum", "maro", "mari", "amaro", "amne",
        "હું"
    )

    val ENTITY_MARKER_PRE: Set<String> = setOf(
        "to", "from", "with", "for"
    )

    val ENTITY_MARKER_POST: Set<String> = setOf(
        "ko", "se", "ne", "ka", "ki", "ke",
        "को", "से", "ने",
        // Gujarati
        "ने", "થી", "ne", "thi"
    )

    // ⚠️ DANGER: Every word added here is silently invisible to the router.
    // Only pure function words with ZERO query meaning belong here.
    // Never add a word to make a test pass — fix the correct semantic slot instead.
    val STOPWORDS: Set<String> = setOf(
        // English function words
        "the", "a", "an", "is", "was", "are", "were", "be", "do", "did", "does",
        "have", "has", "of", "in", "on", "at", "by", "and", "or", "so", "then",
        "it", "this", "that", "what", "whats", "tell", "please", "just",
        // Hindi function words (romanized)
        "hai", "hain", "tha", "thi", "ho", "hua", "hui", "huyi", "hue",
        "yeh", "ye", "vo", "woh", "bhai", "ji", "toh", "bhi", "hi",
        // Hindi Devanagari function words
        "है", "हैं", "था", "थी", "हुआ", "हुई",
        // Gujarati function words
        "che", "hato", "hati",
        "છે",
        // Currency symbols (carry no meaning beyond METRIC_SUM which covers paise/rupees)
        "rs", "inr", "rupees", "₹"
    )

    // Analytical markers veto local routing — presence means the user wants reasoning.
    val ANALYTICAL_MARKERS: Set<String> = setOf(
        // Reasoning / advisory
        "why", "reason", "should", "recommend", "suggest", "advice", "improve", "reduce",
        "kyun", "kyu", "kaise", "wajah", "karan", "salah",
        "क्यों", "कैसे", "वजह", "सलाह",
        "kem", "kem", "કેમ",
        // Comparison
        "compare", "comparison", "versus", "vs",
        "tulna", "तुलना", "sarkhamani",
        // Trend / analytics
        "trend", "growth", "increase", "decrease", "change", "pattern",
        "forecast", "predict", "analyze", "analysis", "insight",
        "badha", "ghata", "rujhan", "vishleshan",
        "बढ़ा", "घटा", "विश्लेषण",
        "vadhan", "ghatan", "வளர்ச்சி",
        // Exclusion / conditional
        "except", "excluding", "unless", "if",
        "chhod", "chhodkar", "alawa", "agar", "jo", "jisme",
        "छोड़कर", "अलावा", "अगर", "जो",
        "sivay", "સિવાય",
        // Gujarati analytics
        "vistrit", "talasho", "badho"
    )

    fun isNoise(token: String): Boolean =
        token in STOPWORDS || token.isBlank() || token.length <= 1

    fun allSlotWords(): Set<String> =
        DIRECTION_OUT + DIRECTION_IN + DIRECTION_NET +
        METRIC_SUM + METRIC_COUNT + METRIC_AVG + METRIC_MAX + METRIC_MIN + METRIC_LIST +
        GROUP_BY_ENTITY + GROUP_BY_CATEGORY + GROUP_BY_MONTH +
        SELF_REF + ENTITY_MARKER_PRE + ENTITY_MARKER_POST
}

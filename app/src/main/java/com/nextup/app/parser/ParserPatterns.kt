package com.nextup.app.parser

/**
 * Every regex pattern that identifies a "keyword" in typed input lives here, once.
 * Both TaskParser (which acts on them) and KeywordDetector (which highlights them
 * live while typing) read from this same object, so what gets highlighted always
 * matches what actually gets treated specially — no drift between the two.
 */
object ParserPatterns {

    val alarmRegex = Regex("""\s*,?\s*with alarm\b""", RegexOption.IGNORE_CASE)
    val tillRegex = Regex("""\b(till|until)\b""", RegexOption.IGNORE_CASE)
    val byRegex = Regex("""\bby\b""", RegexOption.IGNORE_CASE)

    val amountRegex = Regex(
        """\$\s?(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)""" +
            """|(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)\s?\$""" +
            """|(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)\s?(?:dollars|bucks)""",
        RegexOption.IGNORE_CASE
    )

    val recipientRegex = Regex("""\bto\s+([A-Z][a-zA-Z]*|\p{Ll}+)\b""")

    val shortPriorityPrefixRegex = Regex("""^\s*(h|m|n)[.:]\s*""", RegexOption.IGNORE_CASE)

    val startPhraseRegex = Regex(
        """^\s*(high|medium|normal|low)\s*(?:priority)?\s*(?:task)?\s*[:.\-]?\s*""",
        RegexOption.IGNORE_CASE
    )

    val anywherePhraseRegex = Regex(
        """\b(high|medium|normal|low)\s*priority(?:\s*task)?\b""",
        RegexOption.IGNORE_CASE
    )

    val dailyRegex = Regex(
        """\b(?:daily\s*:|d\s*:|do this daily|daily task|repeat daily|every day)""",
        RegexOption.IGNORE_CASE
    )
}

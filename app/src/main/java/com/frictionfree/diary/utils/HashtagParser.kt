package com.frictionfree.diary.utils

object HashtagParser {
    // Matches #tag, but ignores markdown headers (# H1, ## H2) which require a space after #
    // Also ignores hex colors like #FF0000 if preceded by quote or in color attributes
    private val HASHTAG_REGEX = Regex("""(?<!\w)#([a-zA-Z0-9_\p{L}]+)""")

    /**
     * Extracts distinct hashtag names (in lowercase, without the leading #)
     */
    fun extractHashtags(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        // Filter out markdown headers line by line first to avoid any confusion
        val linesWithoutHeaders = text.lines().filterNot { line ->
            line.trimStart().startsWith("# ") ||
            line.trimStart().startsWith("## ") ||
            line.trimStart().startsWith("### ") ||
            line.trimStart().startsWith("#### ")
        }.joinToString("\n")

        return HASHTAG_REGEX.findAll(linesWithoutHeaders)
            .map { it.groupValues[1].lowercase().trim() }
            .filter { it.isNotBlank() && !it.all { char -> char.isDigit() } } // skip purely numeric hashtags like #123
            .distinct()
            .toList()
    }
}

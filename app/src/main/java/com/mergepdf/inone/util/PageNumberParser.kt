package com.mergepdf.inone.util

data class PageParseResult(
    val validPages: List<Int>,
    val outOfRangePages: List<Int>,
    val invalidTokens: List<String>,
    val rawCount: Int
) {
    val hasValidPages: Boolean get() = validPages.isNotEmpty()
    val hasOutOfRange: Boolean get() = outOfRangePages.isNotEmpty()
    val hasInvalidTokens: Boolean get() = invalidTokens.isNotEmpty()
}

object PageNumberParser {

    /**
     * Parses an input string containing page numbers and ranges.
     * Supports formats like:
     * - "2,4,89"
     * - "2, 4, 89"
     * - "2 4 89"
     * - "5-8"
     * - "2, 4, 10-12, 89"
     * - "#2, #4, p89"
     *
     * @param input Raw text typed by user in the remove box
     * @param totalPages Total number of pages currently in the document
     */
    fun parse(input: String, totalPages: Int): PageParseResult {
        if (input.isBlank() || totalPages <= 0) {
            return PageParseResult(
                validPages = emptyList(),
                outOfRangePages = emptyList(),
                invalidTokens = emptyList(),
                rawCount = 0
            )
        }

        // Split by commas, semicolons, or whitespace (not dashes within ranges)
        val tokens = input
            .replace("#", "")
            .replace("p.", "")
            .replace("p", "")
            .replace("page", "")
            .split(Regex("[,;\\s]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val collectedPages = mutableSetOf<Int>()
        val outOfRangePages = mutableSetOf<Int>()
        val invalidTokens = mutableListOf<String>()

        for (token in tokens) {
            // Check for range e.g. "5-8" or "5..8"
            val rangeParts = if (token.contains("-")) {
                token.split("-")
            } else if (token.contains("..")) {
                token.split("..")
            } else {
                null
            }

            if (rangeParts != null && rangeParts.size == 2) {
                val start = rangeParts[0].trim().toIntOrNull()
                val end = rangeParts[1].trim().toIntOrNull()
                if (start != null && end != null && start > 0 && end > 0) {
                    val rangeStart = minOf(start, end)
                    val rangeEnd = maxOf(start, end)
                    // Limit range span to avoid huge loops if user typed 1-999999
                    val safeEnd = minOf(rangeEnd, maxOf(totalPages * 2, 500))
                    for (p in rangeStart..safeEnd) {
                        if (p in 1..totalPages) {
                            collectedPages.add(p)
                        } else {
                            outOfRangePages.add(p)
                        }
                    }
                } else {
                    invalidTokens.add(token)
                }
            } else {
                // Single page number
                val pageNum = token.toIntOrNull()
                if (pageNum != null) {
                    if (pageNum in 1..totalPages) {
                        collectedPages.add(pageNum)
                    } else {
                        outOfRangePages.add(pageNum)
                    }
                } else {
                    invalidTokens.add(token)
                }
            }
        }

        return PageParseResult(
            validPages = collectedPages.sorted(),
            outOfRangePages = outOfRangePages.sorted(),
            invalidTokens = invalidTokens,
            rawCount = collectedPages.size + outOfRangePages.size
        )
    }

    /**
     * Generates a comma-separated string of even page numbers up to totalPages.
     */
    fun getEvenPagesString(totalPages: Int): String {
        return (2..totalPages step 2).joinToString(", ")
    }

    /**
     * Generates a comma-separated string of odd page numbers up to totalPages.
     */
    fun getOddPagesString(totalPages: Int): String {
        return (1..totalPages step 2).joinToString(", ")
    }
}

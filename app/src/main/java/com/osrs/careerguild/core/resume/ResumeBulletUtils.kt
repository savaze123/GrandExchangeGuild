package com.osrs.careerguild.core.resume

/**
 * Pure utility functions for detecting and merging shattered bullet fragments in resumes.
 */
object ResumeBulletUtils {

    val BULLET_MARKERS = setOf(
        '•', '-', '–', '—', '*', '·', '・', '▪', '■', '▫', '◽', '◻', '□', '◦'
    )

    val DANGLING_WORDS = setOf(
        "and", "or", "while", "with", "to", "of", "in", "for", "by",
        "improving", "increasing", "reducing", "using"
    )

    private val TERMINAL_PUNCTUATION = setOf('.', '!', '?', ':')

    // Common action verbs in past, present, or participle form
    private val ACTION_VERBS = setOf(
        "built", "developed", "created", "designed", "implemented", "architected",
        "engineered", "led", "managed", "optimized", "deployed", "spearheaded",
        "automated", "authored", "delivered", "integrated", "migrated", "configured",
        "refactored", "launched", "reduced", "increased", "improved", "scaled",
        "established", "executed", "conducted", "directed", "generated", "orchestrated",
        "streamlined", "supervised", "trained", "mentored", "collaborated", "coordinated",
        "analyzed", "maintained", "monitored", "tested", "resolved", "enhanced",
        "facilitated", "produced", "secured", "transformed", "supported", "upgraded",
        "compiled", "customized", "diagnosed", "evaluated", "formulated", "installed",
        "investigated", "modernized", "negotiated", "operated", "overhauled", "partnered",
        "pioneered", "programmed", "published", "rebuilt", "redesigned", "restructured",
        "retrieved", "revamped", "standardized", "strengthened", "synthesized", "validated",
        "wrote", "build", "develop", "create", "design", "implement", "architect",
        "engineer", "lead", "manage", "optimize", "deploy", "automate", "integrate",
        "migrate", "configure", "refactor", "launch", "scale", "maintain", "coordinate",
        "analyze", "collaborate", "deliver", "supervise", "train", "author"
    )

    fun startsWithBulletMarker(text: String): Boolean {
        val trimmed = text.trimStart()
        if (trimmed.isEmpty()) return false
        return trimmed.first() in BULLET_MARKERS
    }

    fun hasLeadingVerb(line: String): Boolean {
        val firstWord = line.trim()
            .trimStart { it in BULLET_MARKERS }
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.trimEnd('.', ',', ';', ':', '!', '?')
            ?.lowercase() ?: return false

        return firstWord in ACTION_VERBS || (firstWord.length >= 5 && firstWord.endsWith("ed"))
    }

    /**
     * Rejoin hyphenated line break or merge with space.
     */
    fun mergeLines(prev: String, next: String): String {
        val cleanNext = next.trimStart { it in BULLET_MARKERS }.trim()
        val trimmedPrev = prev.trimEnd()
        return if (trimmedPrev.endsWith("-") && trimmedPrev.length > 1 &&
            trimmedPrev[trimmedPrev.length - 2].isLetter() &&
            cleanNext.firstOrNull()?.isLetter() == true
        ) {
            trimmedPrev.dropLast(1) + cleanNext
        } else {
            "$trimmedPrev $cleanNext"
        }
    }

    /**
     * Checks if [line] is a continuation of [previousLine].
     */
    fun isContinuation(line: String, previousLine: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if (startsWithBulletMarker(trimmed)) return false

        val prevTrimmed = previousLine.trimEnd()
        if (prevTrimmed.isEmpty()) return false

        // 1. Starts with a lowercase letter
        val firstLetter = trimmed.firstOrNull { it.isLetter() }
        if (firstLetter != null && firstLetter.isLowerCase()) {
            return true
        }

        // 2. Previous line does not end with terminal punctuation (. ! ? :)
        val prevLastChar = prevTrimmed.lastOrNull()
        if (prevLastChar != null && prevLastChar !in TERMINAL_PUNCTUATION) {
            return true
        }

        // 3. Previous line ends with a dangling word
        val prevLastWord = prevTrimmed
            .trimEnd('.', ',', ';', ':', '!', '?')
            .split(Regex("\\s+"))
            .lastOrNull()
            ?.lowercase()
        if (prevLastWord != null && prevLastWord in DANGLING_WORDS) {
            return true
        }

        // 4. Line is a short fragment (under 6 words) without a leading verb
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size < 6 && !hasLeadingVerb(trimmed)) {
            return true
        }

        return false
    }

    /**
     * Standalone fragment check for reviewing an existing bullet list.
     */
    fun isFragment(bullet: String): Boolean {
        val trimmed = bullet.trimStart { it in BULLET_MARKERS }.trim()
        if (trimmed.isEmpty()) return false
        val firstLetter = trimmed.firstOrNull { it.isLetter() }
        if (firstLetter != null && firstLetter.isLowerCase()) return true

        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size < 6 && !hasLeadingVerb(trimmed)) return true

        val firstWord = words.firstOrNull()?.lowercase()?.trimEnd('.', ',', ';')
        if (firstWord != null && firstWord in DANGLING_WORDS) return true

        return false
    }

    /**
     * Merges shattered bullet fragments into cohesive single-sentence bullets.
     */
    fun mergeFragments(lines: List<String>): List<String> {
        if (lines.isEmpty()) return emptyList()
        val result = mutableListOf<String>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (result.isEmpty()) {
                result.add(line.trimStart { it in BULLET_MARKERS }.trim())
                continue
            }

            val prev = result.last()
            if (isContinuation(line, prev)) {
                result[result.size - 1] = mergeLines(prev, line)
            } else {
                result.add(line.trimStart { it in BULLET_MARKERS }.trim())
            }
        }

        return result
    }
}

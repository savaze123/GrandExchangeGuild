package com.osrs.careerguild.presentation.dashboard.courierguild

/**
 * Result of deterministic verification of a synthesized open-ended response (Phase 3.4).
 */
data class AnswerVerificationResult(
    val isValid: Boolean,
    val violations: List<String>,
)

/**
 * Deterministic Verifier for Courier open-ended answers (Phase 3.4 & Reconciliation §8).
 *
 * Enforces:
 * 1. `unsupported == false` and at least one valid `used_fact_ids` (`F1..Fn`).
 * 2. Every number/metric/percentage in the answer must appear in the cited facts.
 * 3. Every recognized technology term in the answer must appear in the cited facts.
 * 4. Every non-sentence-starter proper noun (company, school, project, product name) must appear in the cited facts.
 * 5. Rejects fabrication patterns (invented team sizes, percentages, unverified titles) and AI/arrogant phrases
 *    ("As an AI", "10x", "rockstar", "world-class").
 * 6. Respects field `maxlength` when `maxLength > 0`.
 */
object CourierAnswerVerifier {

    private val NUMBER_REGEX = Regex("""\b\d+(?:[.,]\d+)?%?\b""")

    private val AI_OR_ARROGANT_REGEX = Regex(
        "\\bas an ai\\b|\\bas a language model\\b|\\brockstar\\b|\\bninja\\b|\\b10x\\b|" +
            "\\bworld-class\\b|\\bunparalleled\\b|\\bsynergy\\b",
        RegexOption.IGNORE_CASE
    )

    private val FABRICATED_TEAM_SIZE_REGEX = Regex(
        "\\b(led|managed|mentored|supervised|built) a team of \\d+\\b|\\bmanaged \\d+ (engineers|developers)\\b",
        RegexOption.IGNORE_CASE
    )

    private val TITLE_CLAIM_WORDS = listOf(
        "principal",
        "director",
        "vice president",
        "chief",
        "staff engineer",
        "tech lead",
    )

    private val KNOWN_TECH_TERMS = setOf(
        "kotlin", "java", "python", "rust", "golang", "typescript", "javascript", "c++", "scala", "swift",
        "react", "angular", "vue", "nextjs", "nodejs", "spring", "django", "fastapi", "flask", "rails",
        "android", "jetpack", "compose", "filament", "ktor", "coroutines", "room", "sqlcipher", "graphql",
        "rest", "grpc", "kafka", "spark", "flink", "airflow", "snowflake", "databricks", "bigquery",
        "postgresql", "postgres", "mysql", "mongodb", "redis", "cassandra", "dynamodb", "sqlite",
        "aws", "gcp", "azure", "kubernetes", "k8s", "docker", "terraform", "ansible", "jenkins",
        "pytorch", "tensorflow", "langchain", "openai", "gemini", "vertex", "linux", "git"
    )

    private val ALLOWED_SENTENCE_STARTERS = setOf(
        "i", "i'm", "i'd", "i've", "i'll", "it", "it's", "you", "you're", "we", "there", "here",
        "as", "at", "on", "to", "by", "of", "an", "a", "github", "linkedin",
        "the", "in", "my", "one", "recently", "during", "while", "when", "since", "thank", "thanks",
        "dear", "much", "day", "also", "with", "from", "for", "this", "that", "these", "those",
        "after", "before", "currently", "working", "building", "having", "always", "through",
        "over", "across", "because", "although", "whether", "specifically", "additionally",
        "furthermore", "moreover", "overall", "throughout", "alongside", "contributing", "developing",
        "engineering", "designing", "shipping", "learning", "collaborating", "excited", "grateful"
    )

    fun verify(
        synthesis: GroundedSynthesisResult,
        factSheet: List<FactItem>,
        maxLength: Int = 0,
    ): AnswerVerificationResult {
        val violations = mutableListOf<String>()

        if (synthesis.unsupported) {
            val detail = synthesis.missingInfo?.takeIf { it.isNotBlank() } ?: "Fact sheet lacks required grounding"
            return AnswerVerificationResult(
                isValid = false,
                violations = listOf("Unsupported question: $detail"),
            )
        }

        val answer = synthesis.answer.trim()
        if (answer.isBlank()) {
            return AnswerVerificationResult(
                isValid = false,
                violations = listOf("Answer text is blank"),
            )
        }

        if (maxLength > 0 && answer.length > maxLength) {
            violations.add("Answer length (${answer.length}) exceeds maxlength ($maxLength)")
        }

        if (synthesis.usedFactIds.isEmpty()) {
            violations.add("Answer did not cite any fact IDs (used_fact_ids is empty)")
        }

        val factMap = factSheet.associateBy { it.id }
        val citedFacts = synthesis.usedFactIds.mapNotNull { factMap[it] }
        if (citedFacts.isEmpty()) {
            violations.add("None of the cited fact IDs (${synthesis.usedFactIds.joinToString()}) exist in the fact sheet")
            return AnswerVerificationResult(isValid = false, violations = violations)
        }

        val citedCorpus = citedFacts.joinToString(" ") { it.text }
        val citedCorpusLower = citedCorpus.lowercase()
        val citedNumbers = NUMBER_REGEX.findAll(citedCorpus)
            .flatMap { sequenceOf(it.value.trim(), it.value.replace("%", "").trim()) }
            .toSet()

        // 1. Check AI / buzzword / arrogant phrases
        AI_OR_ARROGANT_REGEX.find(answer)?.let { match ->
            violations.add("Disallowed AI or arrogant phrasing: '${match.value}'")
        }

        // 2. Check every number and percentage against cited facts
        for (match in NUMBER_REGEX.findAll(answer)) {
            val rawNum = match.value.trim()
            val cleanNum = rawNum.replace("%", "").trim()
            if (rawNum !in citedNumbers && cleanNum !in citedNumbers && !citedCorpusLower.contains(rawNum.lowercase())) {
                violations.add("Ungrounded number or metric '$rawNum' not present in cited facts")
            }
        }

        // 3. Check fabrication patterns (team sizes, seniority titles)
        FABRICATED_TEAM_SIZE_REGEX.find(answer)?.let { match ->
            if (!citedCorpusLower.contains(match.value.lowercase())) {
                violations.add("Fabricated team size claim not in cited facts: '${match.value}'")
            }
        }

        val answerLower = answer.lowercase()
        for (titleWord in TITLE_CLAIM_WORDS) {
            if (Regex("\\b${Regex.escape(titleWord)}\\b").containsMatchIn(answerLower) &&
                !citedCorpusLower.contains(titleWord)
            ) {
                violations.add("Ungrounded title/seniority claim '$titleWord' not in cited facts")
            }
        }

        // 4. Check technology terms
        val tokensInAnswer = Regex("[a-zA-Z0-9+#.]{2,}")
            .findAll(answerLower)
            .map { it.value.trimEnd('.', ',') }
            .toSet()

        for (token in tokensInAnswer) {
            if (token in KNOWN_TECH_TERMS) {
                val presentInCited = Regex("\\b${Regex.escape(token)}\\b").containsMatchIn(citedCorpusLower) ||
                    citedCorpusLower.contains(token)
                if (!presentInCited) {
                    violations.add("Ungrounded technology term '$token' not present in cited facts")
                }
            }
        }

        // 5. Check proper nouns (capitalized words not at the start of a sentence)
        val citedWordsLower = Regex("[a-z0-9']{2,}")
            .findAll(citedCorpusLower)
            .map { it.value.removeSuffix("'s") }
            .toSet()

        val sentences = answer.split(Regex("(?<=[.!?])\\s+"))
        for (sentence in sentences) {
            val words = Regex("\\b[A-Za-z][A-Za-z0-9'-]*\\b").findAll(sentence.trim()).map { it.value }.toList()
            words.forEachIndexed { idx, word ->
                val cleanWord = word.removeSuffix("'s").removeSuffix("’s")
                if (cleanWord.length < 2) return@forEachIndexed
                if (!cleanWord[0].isUpperCase()) return@forEachIndexed
                val lowerWord = cleanWord.lowercase()
                if (idx == 0 && lowerWord in ALLOWED_SENTENCE_STARTERS) return@forEachIndexed
                if (lowerWord in ALLOWED_SENTENCE_STARTERS) return@forEachIndexed
                if (lowerWord !in citedWordsLower && !citedCorpusLower.contains(lowerWord)) {
                    violations.add("Ungrounded proper noun '$cleanWord' not present in cited facts")
                }
            }
        }

        return AnswerVerificationResult(
            isValid = violations.isEmpty(),
            violations = violations.distinct(),
        )
    }
}

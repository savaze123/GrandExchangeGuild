package com.osrs.careerguild.core.resume

/**
 * Deterministic technical skill categorizer with canonical name aliases.
 *
 * Generic Taxonomy:
 * - Languages
 * - Frameworks & Libraries
 * - Databases & Tools
 * - AI & Developer Tooling
 * - Other
 */
object SkillCategorizer {

    const val CAT_LANGUAGES = "Languages"
    const val CAT_FRAMEWORKS = "Frameworks & Libraries"
    const val CAT_DATABASES_TOOLS = "Databases & Tools"
    const val CAT_AI_DEV_TOOLING = "AI & Developer Tooling"
    const val CAT_OTHER = "Other"

    val ORDERED_CATEGORIES = listOf(
        CAT_LANGUAGES,
        CAT_FRAMEWORKS,
        CAT_DATABASES_TOOLS,
        CAT_AI_DEV_TOOLING,
        CAT_OTHER,
    )

    // Alias map: lowercased alias/variation -> canonical name
    val ALIASES: Map<String, String> = mapOf(
        "postgres" to "PostgreSQL",
        "postgresql" to "PostgreSQL",
        "js" to "JavaScript",
        "javascript" to "JavaScript",
        "ts" to "TypeScript",
        "typescript" to "TypeScript",
        "py" to "Python",
        "python" to "Python",
        "golang" to "Go",
        "go" to "Go",
        "bash" to "Bash/Shell",
        "shell" to "Bash/Shell",
        "zsh" to "Bash/Shell",
        "sh" to "Bash/Shell",
        "k8s" to "Kubernetes",
        "kubernetes" to "Kubernetes",
        "compose" to "Jetpack Compose",
        "jetpack compose" to "Jetpack Compose",
        "node" to "Node.js",
        "nodejs" to "Node.js",
        "react native" to "React Native",
        "react.js" to "React",
        "reactjs" to "React",
        "next.js" to "Next.js",
        "nextjs" to "Next.js",
        "vue.js" to "Vue.js",
        "vuejs" to "Vue.js",
        "ci cd" to "CI/CD",
        "ci/cd" to "CI/CD",
        "cicd" to "CI/CD",
        "prompt engineering" to "Prompt Engineering",
        "ai coding assistants" to "AI Coding Assistants",
        "rag" to "RAG",
        "retrieval-augmented generation" to "RAG",
    )

    private val LANGUAGES = setOf(
        "Python", "Kotlin", "Java", "JavaScript", "TypeScript", "Go", "Rust",
        "C++", "C", "C#", "Ruby", "Swift", "PHP", "Scala", "R", "Dart",
        "Bash/Shell", "SQL", "HTML", "CSS", "Lua", "Haskell", "Elixir", "Julia"
    )

    private val FRAMEWORKS_LIBRARIES = setOf(
        "React", "React Native", "Angular", "Vue.js", "Next.js", "Node.js", "Express",
        "Spring", "Spring Boot", "Django", "Flask", "FastAPI", "Android SDK",
        "Jetpack Compose", "Flutter", "SwiftUI", "Ktor", "ASP.NET", "Ruby on Rails",
        "PyTorch", "TensorFlow", "Pandas", "NumPy", "Scikit-Learn", "Apache Spark",
        "Spark", "Apache Flink", "Flink", "GraphQL", "gRPC"
    )

    private val DATABASES_TOOLS = setOf(
        "PostgreSQL", "MySQL", "SQLite", "Room", "SQLCipher", "MongoDB", "Redis",
        "Cassandra", "DynamoDB", "Elasticsearch", "Kafka", "RabbitMQ", "Docker",
        "Kubernetes", "Terraform", "AWS", "Google Cloud (GCP)", "GCP", "Microsoft Azure", "Azure",
        "Git", "Linux", "Nginx", "Airflow", "Databricks", "Snowflake", "BigQuery", "Firebase"
    )

    private val AI_DEV_TOOLING = setOf(
        "Prompt Engineering", "AI Coding Assistants", "LLMs", "Gemini API",
        "OpenAI API", "Claude API", "LangChain", "LlamaIndex", "Vector Databases",
        "Chroma", "Pinecone", "RAG", "CI/CD", "GitHub Actions", "GitLab CI",
        "Jenkins", "Playwright", "Selenium", "Unit Testing", "scrcpy", "ADB"
    )

    fun canonicalize(skill: String): String {
        val trimmed = skill.trim()
        val lower = trimmed.lowercase()
        return ALIASES[lower] ?: trimmed
    }

    fun getCategory(skill: String): String {
        val canonical = canonicalize(skill)
        val lower = canonical.lowercase()

        return when {
            LANGUAGES.any { it.equals(canonical, ignoreCase = true) } -> CAT_LANGUAGES
            FRAMEWORKS_LIBRARIES.any { it.equals(canonical, ignoreCase = true) } -> CAT_FRAMEWORKS
            DATABASES_TOOLS.any { it.equals(canonical, ignoreCase = true) } -> CAT_DATABASES_TOOLS
            AI_DEV_TOOLING.any { it.equals(canonical, ignoreCase = true) } ||
                lower.contains("prompt engineering") ||
                lower.contains("ai coding assistant") ||
                lower.contains("ci/cd") || lower.contains("cicd") -> CAT_AI_DEV_TOOLING
            else -> CAT_OTHER
        }
    }

    /**
     * Categorizes a collection of skills without adding any extra skills.
     * Orders skills within each category by relevance to [jdKeywords] if provided.
     * Skips empty categories.
     */
    fun categorize(
        skills: List<String>,
        jdKeywords: List<String> = emptyList(),
    ): Map<String, List<String>> {
        val normalizedJd = jdKeywords.map { it.lowercase().trim() }.filter { it.isNotBlank() }

        val map = linkedMapOf<String, MutableList<String>>()
        for (cat in ORDERED_CATEGORIES) {
            map[cat] = mutableListOf()
        }

        // De-duplicate while preserving case-canonicalized names
        val seen = mutableSetOf<String>()
        for (skill in skills) {
            val trimmed = skill.trim()
            if (trimmed.isBlank()) continue
            val canonical = canonicalize(trimmed)
            if (seen.add(canonical.lowercase())) {
                val category = getCategory(canonical)
                map[category]?.add(canonical)
            }
        }

        // Sort skills within each category by JD keyword relevance first, then alphabetically
        val result = linkedMapOf<String, List<String>>()
        for (cat in ORDERED_CATEGORIES) {
            val list = map[cat].orEmpty()
            if (list.isNotEmpty()) {
                val sorted = list.sortedWith(
                    compareByDescending<String> { item ->
                        val itemLower = item.lowercase()
                        normalizedJd.count { kw -> itemLower.contains(kw) || kw.contains(itemLower) }
                    }.thenBy { it.lowercase() }
                )
                result[cat] = sorted
            }
        }

        return result
    }
}

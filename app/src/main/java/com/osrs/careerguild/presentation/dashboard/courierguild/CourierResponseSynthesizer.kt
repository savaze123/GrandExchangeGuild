package com.osrs.careerguild.presentation.dashboard.courierguild

import com.osrs.careerguild.core.resume.BaseResume

/**
 * instruction.md §2 — Response Synthesis for Custom Questions.
 *
 * Answers are assembled ONLY from verified data: the active [BaseResume] (the source the Forge
 * tailors from), the GitHub URL and the LinkedIn URL. No employer, metric, technology or claim
 * is invented. Questions that cannot be answered truthfully from that data (salary, unknown
 * prompts, etc.) return null and are left for the user.
 *
 * Tone: humble but professional, and eager — highlights real strengths without bragging.
 */
object CourierResponseSynthesizer {

    // instruction.md §8: Applicant Profile & EEO Configuration Literals
    const val DEFAULT_GENDER = "Decline to self-identify"
    val ALLOWED_GENDER = listOf(
        "Male",
        "Female",
        "Non-binary",
        "Prefer not to say",
        "Decline to self-identify",
    )

    const val DEFAULT_RACE = "Decline to self-identify"
    val ALLOWED_RACE = listOf(
        "Hispanic or Latino",
        "White",
        "Black",
        "Native Hawaiian or Other Pacific Islander",
        "Asian",
        "American Indian or Alaska Native",
        "Two or More Races",
        "Decline to self-identify",
    )

    const val DEFAULT_VETERAN_STATUS = "I decline to self-identify for protected veteran status"
    val ALLOWED_VETERAN_STATUS = listOf(
        "Veteran",
        "Not a veteran",
        "Prefer not to say",
        "I identify as a veteran",
        "I am not a protected veteran",
        "I decline to self-identify for protected veteran status",
    )

    fun normalizeGender(raw: String): String {
        val trimmed = raw.trim()
        val exact = ALLOWED_GENDER.firstOrNull { it.equals(trimmed, ignoreCase = true) }
        if (exact != null) return exact
        val lower = trimmed.lowercase()
        return when {
            lower.contains("non-binary") || lower.contains("nonbinary") -> "Non-binary"
            lower.contains("female") || lower.contains("woman") -> "Female"
            lower.contains("male") || lower.contains("man") -> "Male"
            lower.contains("prefer not") -> "Prefer not to say"
            lower.contains("decline") || lower.contains("not wish") || lower.contains("not disclose") -> DEFAULT_GENDER
            else -> DEFAULT_GENDER
        }
    }

    fun normalizeRace(raw: String): String {
        val trimmed = raw.trim()
        return ALLOWED_RACE.firstOrNull { it.equals(trimmed, ignoreCase = true) } ?: DEFAULT_RACE
    }

    fun normalizeVeteranStatus(raw: String): String {
        val trimmed = raw.trim()
        val exact = ALLOWED_VETERAN_STATUS.firstOrNull { it.equals(trimmed, ignoreCase = true) }
        if (exact != null) return exact
        val lower = trimmed.lowercase()
        return when {
            lower.contains("not a veteran") || lower.contains("not a protected") || lower.contains("am not") -> "Not a veteran"
            lower.contains("identify as a veteran") -> "I identify as a veteran"
            lower.contains("veteran") -> "Veteran"
            lower.contains("prefer not") -> "Prefer not to say"
            lower.contains("decline") || lower.contains("not wish") || lower.contains("not disclose") -> DEFAULT_VETERAN_STATUS
            else -> DEFAULT_VETERAN_STATUS
        }
    }

    enum class QuestionKind {
        WHY_COMPANY, ABOUT_YOU, PROJECT, STRENGTHS, COVER_LETTER, ADDITIONAL,
        PHONE, GENDER, RACE, VETERAN,
        SENSITIVE, UNKNOWN,
    }

    /**
     * Phase 3.2 & Reconciliation §4 question categories:
     * - [STANDARD_PERSONAL]: contact, links, location, start date, relocation (from UserProfileManager & ApplicantAnswers).
     * - [EEO_PROFILE]: gender, race, veteran status (from UserProfileManager per instruction.md §8, defaulting to decline).
     * - [PROTECTED]: work authorization, sponsorship, disability, criminal history, salary (ONLY from ApplicantAnswers, no default, blocks if missing, never synthesized).
     * - [EXPERIENCE]: projects, technical challenges, years/skills experience, background (synthesized from Fact Sheet).
     * - [MOTIVATION]: why this company / role / cover letter (synthesized from Fact Sheet + forces review_required = true).
     */
    enum class QuestionCategory {
        STANDARD_PERSONAL,
        EEO_PROFILE,
        PROTECTED,
        EXPERIENCE,
        MOTIVATION,
    }

    fun classifyCategory(question: String): QuestionCategory {
        val q = question.lowercase().trim()
        return when {
            // 1. EEO profile fields (instruction.md §8)
            (Regex("\\bgender\\b|\\bsex\\b").containsMatchIn(q) && !Regex("orientation").containsMatchIn(q)) ||
                Regex("\\brace\\b|ethnic|hispanic or latino").containsMatchIn(q) ||
                Regex("veteran|military status|protected veteran").containsMatchIn(q) -> QuestionCategory.EEO_PROFILE

            // 2. Protected / legal / compensation fields (ApplicantAnswers only, never synthesized, block if missing)
            Regex(
                "authori[sz]ed to work|work authori[sz]ation|legally authori[sz]ed|sponsorship|require.*visa|h-?1b|" +
                    "disabilit|handicap|criminal|felon|convict|background check|pleaded guilty|" +
                    "salary|compensation|pay (range|expectation)|desired (pay|salary|compensation)|expected (pay|rate|salary)|" +
                    "security clearance|date of birth|\\bage\\b|\\bssn\\b|social security|sexual orientation|pronoun|marital status"
            ).containsMatchIn(q) -> QuestionCategory.PROTECTED

            // 3. Motivation questions (synthesized from Fact Sheet + sets review_required = true)
            Regex(
                "why (do you want|are you interested|.*(join|work (at|for|with)))|" +
                    "why (this|our) (company|team|role|organization|mission)|" +
                    "what (excites|interests|draws|attracts|motivates) you|" +
                    "interest(ed)? in (this|the) (role|position|company)|" +
                    "cover letter"
            ).containsMatchIn(q) -> QuestionCategory.MOTIVATION

            // 4. Experience / project / background open-ended questions (synthesized from Fact Sheet)
            Regex(
                "project|proud|accomplishment|achievement|technical challenge|something you('ve| have) built|example of|" +
                    "strength|what (would|will|can) you bring|why should we|stand out|qualif|what makes you|" +
                    "tell us about yourself|about you|introduce yourself|your background|describe (your|a time|how you)|" +
                    "experience with|years of experience|how many years"
            ).containsMatchIn(q) -> QuestionCategory.EXPERIENCE

            // 5. Standard personal / logistical fields
            else -> QuestionCategory.STANDARD_PERSONAL
        }
    }

    data class Grounding(
        val company: String,
        val title: String,
        val currentEmployer: String,
        val currentTitle: String,
        val experienceBullets: List<String>,
        val projectName: String,
        val projectTech: List<String>,
        val projectBullet: String,
        val skills: List<String>,
        val degree: String,
        val school: String,
        val github: String,
        val linkedin: String,
        val phone: String = "",
        val gender: String = "Decline to self-identify",
        val race: String = "Decline to self-identify",
        val veteranStatus: String = "I decline to self-identify for protected veteran status",
    )

    fun groundingFrom(
        base: BaseResume?,
        company: String,
        title: String,
        github: String,
        linkedin: String,
        phone: String = "",
        gender: String = "Decline to self-identify",
        race: String = "Decline to self-identify",
        veteranStatus: String = "I decline to self-identify for protected veteran status",
    ): Grounding {
        val active = base?.activeOnly()
        val job = active?.experience?.firstOrNull { it.isCurrent } ?: active?.experience?.firstOrNull()
        val project = active?.projects?.firstOrNull { it.name.isNotBlank() }
        val edu = active?.education?.firstOrNull()
        val effPhone = phone.ifBlank { active?.contact?.phone.orEmpty() }
        return Grounding(
            company = company.trim(),
            title = title.trim(),
            currentEmployer = job?.employer.orEmpty().trim(),
            currentTitle = job?.title.orEmpty().trim(),
            experienceBullets = job?.bullets.orEmpty().map { it.trim() }.filter { it.isNotBlank() },
            projectName = project?.name.orEmpty().trim(),
            projectTech = project?.technologies.orEmpty().map { it.trim() }.filter { it.isNotBlank() },
            projectBullet = project?.bullets?.firstOrNull { it.isNotBlank() }.orEmpty().trim(),
            skills = active?.skills.orEmpty().map { it.trim() }.filter { it.isNotBlank() },
            degree = edu?.degree.orEmpty().trim(),
            school = edu?.school.orEmpty().trim(),
            github = github.trim(),
            linkedin = linkedin.trim(),
            phone = effPhone.trim(),
            gender = gender.trim(),
            race = race.trim(),
            veteranStatus = veteranStatus.trim(),
        )
    }

    fun classify(question: String): QuestionKind {
        val q = question.lowercase()
        return when {
            // instruction.md §8: Profile & EEO strict classification
            Regex("phone|mobile|telephone|cell (number|phone)").containsMatchIn(q) -> QuestionKind.PHONE
            Regex("\\bgender\\b|\\bsex\\b").containsMatchIn(q) && !Regex("orientation").containsMatchIn(q) -> QuestionKind.GENDER
            Regex("\\brace\\b|ethnic|demographic").containsMatchIn(q) -> QuestionKind.RACE
            Regex("veteran|military status").containsMatchIn(q) -> QuestionKind.VETERAN

            // Sensitive fields left for human review
            Regex("salary|compensation|pay (range|expectation)|desired pay|expected (pay|rate)|disabilit|pronoun|date of birth|\\bage\\b|ssn|social security").containsMatchIn(q) -> QuestionKind.SENSITIVE
            Regex("cover letter").containsMatchIn(q) -> QuestionKind.COVER_LETTER
            Regex("why (do you want|are you interested|.*(join|work (at|for|with)))|why (this|our) (company|team|role)|what (excites|interests|draws|attracts) you|interest(ed)? in (this|the) (role|position|company)").containsMatchIn(q) -> QuestionKind.WHY_COMPANY
            Regex("project|proud|accomplishment|achievement|technical challenge|something you('ve| have) built|example of").containsMatchIn(q) -> QuestionKind.PROJECT
            Regex("strength|what (would|will|can) you bring|why should we|stand out|qualif|what makes you").containsMatchIn(q) -> QuestionKind.STRENGTHS
            Regex("tell us about yourself|about you|introduce yourself|your background|describe yourself").containsMatchIn(q) -> QuestionKind.ABOUT_YOU
            Regex("anything else|additional (info|information|comments)|other information|comments").containsMatchIn(q) -> QuestionKind.ADDITIONAL
            else -> QuestionKind.UNKNOWN
        }
    }

    /** "Built X with Y." -> "built X with Y" (keeps acronyms such as "AWS" intact). */
    internal fun toFirstPersonClause(bullet: String): String {
        val b = bullet.trim().trimStart('•', '-', '*', '–', ' ').trimEnd('.', ';', ' ')
        if (b.length < 2) return b
        return if (b[0].isUpperCase() && b[1].isLowerCase()) b[0].lowercaseChar() + b.substring(1) else b
    }

    private fun joinList(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> "${items[0]} and ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + ", and " + items.last()
    }

    private fun roleAtCompany(g: Grounding): String = when {
        g.title.isNotBlank() && g.company.isNotBlank() -> "the ${g.title} role at ${g.company}"
        g.company.isNotBlank() -> "this role at ${g.company}"
        else -> "this role"
    }

    private fun currentRole(g: Grounding): String = when {
        g.currentTitle.isNotBlank() && g.currentEmployer.isNotBlank() -> "as ${g.currentTitle} at ${g.currentEmployer}"
        g.currentEmployer.isNotBlank() -> "at ${g.currentEmployer}"
        else -> ""
    }

    private fun skillPhrase(g: Grounding, n: Int = 3) = joinList(g.skills.take(n))

    private fun experienceSentence(g: Grounding): String? {
        val bullet = g.experienceBullets.firstOrNull() ?: return null
        val role = currentRole(g)
        return if (role.isNotBlank()) "In my work $role, I ${toFirstPersonClause(bullet)}." else "Recently, I ${toFirstPersonClause(bullet)}."
    }

    private fun projectSentence(g: Grounding): String? {
        if (g.projectName.isBlank()) return null
        val tech = if (g.projectTech.isNotEmpty()) " using ${joinList(g.projectTech.take(3))}" else ""
        val detail = if (g.projectBullet.isNotBlank()) " — I ${toFirstPersonClause(g.projectBullet)}" else ""
        return "One project I'm proud of is ${g.projectName}, which I built$tech$detail."
    }

    private fun githubSentence(g: Grounding): String? =
        g.github.takeIf { it.isNotBlank() }?.let { "You're welcome to look at my work on GitHub ($it)." }

    /**
     * Returns a grounded answer, or null if the question must be left for the user.
     * [maxLength] respects the field's `maxlength` (cut at a sentence boundary).
     */
    fun answer(question: String, g: Grounding, maxLength: Int = 0): String? {
        val skills = skillPhrase(g)
        val role = currentRole(g)
        val sentences: List<String?> = when (classify(question)) {
            QuestionKind.SENSITIVE, QuestionKind.UNKNOWN -> return null
            QuestionKind.PHONE -> return g.phone.ifBlank { null }
            QuestionKind.GENDER -> return normalizeGender(g.gender)
            QuestionKind.RACE -> return normalizeRace(g.race)
            QuestionKind.VETERAN -> return normalizeVeteranStatus(g.veteranStatus)
            QuestionKind.WHY_COMPANY -> listOf(
                "I'm genuinely excited about ${roleAtCompany(g)}.",
                if (skills.isNotBlank()) "Much of my recent work has centered on $skills, and this position looks like a place where I could put that experience to good use." else null,
                experienceSentence(g),
                "I'd be grateful for the chance to contribute to the team and keep learning from the people there.",
            )
            QuestionKind.ABOUT_YOU -> listOf(
                when {
                    role.isNotBlank() -> "I'm currently working $role."
                    g.degree.isNotBlank() && g.school.isNotBlank() -> "I hold a ${g.degree} from ${g.school}."
                    else -> "I enjoy building practical, reliable software."
                },
                if (skills.isNotBlank()) "Day to day I work mostly with $skills." else null,
                experienceSentence(g),
                "I'm always eager to learn, and I'd love to bring that energy to ${roleAtCompany(g)}.",
            )
            QuestionKind.PROJECT -> listOf(
                projectSentence(g) ?: experienceSentence(g) ?: return null,
                "It taught me a lot, and I'm eager to keep building on those lessons.",
                githubSentence(g),
            )
            QuestionKind.STRENGTHS -> listOf(
                if (skills.isNotBlank()) "I'd bring hands-on experience with $skills." else null,
                experienceSentence(g),
                projectSentence(g),
                "I try to stay humble, ask good questions, and learn quickly, and I'd be excited to grow with the team at ${g.company.ifBlank { "your company" }}.",
            )
            QuestionKind.COVER_LETTER -> listOf(
                "Dear ${g.company.ifBlank { "Hiring" }} team,",
                "I'm excited to apply for ${roleAtCompany(g)}.",
                if (role.isNotBlank()) "I currently work $role${if (skills.isNotBlank()) ", where I've focused on $skills" else ""}." else if (skills.isNotBlank()) "My experience centers on $skills." else null,
                experienceSentence(g),
                projectSentence(g),
                "I'd be grateful for the opportunity to contribute, and I'm eager to keep learning alongside your team.",
                "Thank you for your time and consideration.",
            )
            QuestionKind.ADDITIONAL -> listOf(
                "Thank you for considering my application.",
                githubSentence(g),
                g.linkedin.takeIf { it.isNotBlank() }?.let { "My LinkedIn profile is $it." },
                "I'd be happy to share anything else that would be helpful.",
            )
        }
        val text = sentences.filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
        return if (maxLength > 0) clampToSentence(text, maxLength) else text
    }

    internal fun clampToSentence(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text
        val cut = text.substring(0, maxLength)
        val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
        return if (end > 0) cut.substring(0, end + 1) else cut.substringBeforeLast(' ')
    }

    /**
     * Tags every unfilled open-ended question (textareas + long-label text inputs) with
     * `data-cg-q` and returns [{ i, q, max }]. Contact fields and static accessibility/accommodation
     * disclaimers are strictly excluded.
     */
    fun buildOpenQuestionScanJs(): String = """
        (function() {
            const out = [];
            const skip = /(first|last|full|preferred)?\s*name|e-?mail|phone|mobile|telephone|cell|gender|sex|race|ethnic|veteran|military|demographic|address|city|state|zip|postal|country|linkedin|github|website|portfolio|url|password/i;
            const ignoreDisclaimer = /need assistance|reasonable accommodation|accessibility|reach out to tinyurl/i;
            function labelFor(el) {
                let t = '';
                if (el.id) { const l = document.querySelector('label[for="' + el.id + '"]'); if (l) t = l.innerText; }
                if (!t) { const l = el.closest('label'); if (l) t = l.innerText; }
                if (!t) { const box = el.closest('.field, .form-group, .application-question, fieldset'); if (box) t = box.innerText; }
                if (!t) t = el.getAttribute('aria-label') || el.placeholder || '';
                return (t || '').trim().split('\n')[0].substring(0, 300);
            }
            const els = Array.from(document.querySelectorAll('textarea, input[type="text"], input:not([type])'));
            let i = 0;
            els.forEach(el => {
                if (el.offsetParent === null || el.value) return;
                if (el.closest('footer, [role="contentinfo"]')) return;
                const q = labelFor(el);
                if (!q || ignoreDisclaimer.test(q)) return;
                if (el.tagName !== 'TEXTAREA' && (q.length < 25 || skip.test(q))) return;
                if (el.tagName === 'TEXTAREA' && skip.test(q) && q.length < 25) return;
                el.setAttribute('data-cg-q', String(i));
                out.push({ i: i, q: q, max: el.maxLength > 0 ? el.maxLength : 0 });
                i++;
            });
            return out;
        })();
    """.trimIndent()
}

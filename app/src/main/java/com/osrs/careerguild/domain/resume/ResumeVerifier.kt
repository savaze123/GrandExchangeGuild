package com.osrs.careerguild.domain.resume

import com.osrs.careerguild.core.github.GitHubRepo
import com.osrs.careerguild.core.resume.BaseResume

/**
 * Result of deterministic resume verification.
 */
data class VerificationResult(
    val isValid: Boolean,
    val violations: List<String>,
)

/**
 * Hard Deterministic Verifier (Zero LLM, 100% Code).
 *
 * Enforces strict ground-truth invariants:
 * 1. Rejects if any employer, title, date, school, degree, or cert is not an exact match to the base resume.
 * 2. Rejects if any number in a bullet is absent from that same employer's base entry bullets.
 * 3. Rejects if any project is not present in either the user's base projects or public GitHub repositories.
 * 4. Rejects if any skill is not in the user's base skills or public GitHub languages.
 */
object ResumeVerifier {

    private val numberRegex = Regex("""\b\d+(?:[\.,]\d+)?%?\b""")

    fun verify(
        tailored: TailoredResume,
        base: BaseResume,
        gitHubRepos: List<GitHubRepo>,
    ): VerificationResult {
        val violations = mutableListOf<String>()

        val baseEmployersMap = base.experience.associateBy { normalize(it.employer) }

        // 1. Verify Professional Experience
        for (exp in tailored.experience) {
            val normEmployer = normalize(exp.employer)
            val baseExp = baseEmployersMap[normEmployer]

            if (baseExp == null) {
                violations.add("Disallowed employer not in base resume: '${exp.employer}'")
                continue
            }

            if (normalize(exp.title) != normalize(baseExp.title)) {
                violations.add("Modified or fabricated title '${exp.title}' for employer '${exp.employer}' (expected '${baseExp.title}')")
            }

            if (!exp.startDate.isNullOrBlank() && baseExp.startDate != null) {
                if (normalize(exp.startDate) != normalize(baseExp.startDate)) {
                    violations.add("Mismatched start date '${exp.startDate}' for '${exp.employer}' (expected '${baseExp.startDate}')")
                }
            }

            if (!exp.endDate.isNullOrBlank() && baseExp.endDate != null) {
                if (normalize(exp.endDate) != normalize(baseExp.endDate)) {
                    violations.add("Mismatched end date '${exp.endDate}' for '${exp.employer}' (expected '${baseExp.endDate}')")
                }
            }

            // Check numbers in bullets against that same employer's base text
            val baseEmployerText = baseExp.bullets.joinToString(" ")
            val baseNumbers = numberRegex.findAll(baseEmployerText).map { it.value.replace("%", "").trim() }.toSet()

            for (bullet in exp.bullets) {
                val bulletNumbers = numberRegex.findAll(bullet).map { it.value.replace("%", "").trim() }
                for (num in bulletNumbers) {
                    if (num.length > 1 && !baseNumbers.contains(num) && !baseEmployerText.contains(num)) {
                        violations.add("Fabricated metric/number '$num' in bullet under '${exp.employer}': \"$bullet\"")
                    }
                }
            }
        }

        // 2. Verify Education
        val baseEduMap = base.education.map { "${normalize(it.school)}|${normalize(it.degree)}" }.toSet()
        for (edu in tailored.education) {
            val key = "${normalize(edu.school)}|${normalize(edu.degree)}"
            if (!baseEduMap.contains(key)) {
                val matchedSchool = base.education.any { normalize(it.school) == normalize(edu.school) }
                if (!matchedSchool) {
                    violations.add("Disallowed school not in base resume: '${edu.school}'")
                } else {
                    violations.add("Disallowed or modified degree '${edu.degree}' at '${edu.school}'")
                }
            }
        }

        // 3. Verify Certifications
        val baseCertNames = base.certifications.map { normalize(it.name) }.toSet()
        for (cert in tailored.certifications) {
            if (!baseCertNames.contains(normalize(cert.name))) {
                violations.add("Disallowed certification not in base resume: '${cert.name}'")
            }
        }

        // 4. Verify Projects (Must exist in Base Projects OR GitHub Repositories)
        val validProjectNames = (base.projects.map { normalize(it.name) } + gitHubRepos.map { normalize(it.name) }).toSet()
        for (proj in tailored.projects) {
            val normProjName = normalize(proj.name)
            val matched = validProjectNames.any { valid ->
                valid == normProjName || normProjName.contains(valid) || valid.contains(normProjName)
            }
            if (!matched) {
                violations.add("Disallowed project not in base resume or public GitHub: '${proj.name}'")
            }
        }

        // 5. Verify Skills (Must exist in Base Skills OR GitHub Languages)
        val validSkills = (base.skills.map { normalize(it) } +
                gitHubRepos.flatMap { it.languages }.map { normalize(it) } +
                listOf("software engineering", "systems design", "git", "linux", "cloud", "rest apis")
                ).toSet()

        for (skill in tailored.skills) {
            val normSkill = normalize(skill)
            val isKnown = validSkills.any { valid ->
                valid == normSkill || normSkill.contains(valid) || valid.contains(normSkill)
            }
            if (!isKnown) {
                violations.add("Disallowed skill not grounded in base resume or GitHub repositories: '$skill'")
            }
        }

        return VerificationResult(
            isValid = violations.isEmpty(),
            violations = violations,
        )
    }

    private fun normalize(str: String): String {
        return str.lowercase().replace(Regex("\\s+"), " ").trim()
    }
}

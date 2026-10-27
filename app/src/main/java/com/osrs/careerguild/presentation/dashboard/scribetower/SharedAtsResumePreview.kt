package com.osrs.careerguild.presentation.dashboard.scribetower

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osrs.careerguild.core.profile.UserProfile
import com.osrs.careerguild.domain.resume.TailoredResume

private val OsrsGold = Color(0xFFFFD700)
private val OsrsParchment = Color(0xFFF4EDE2)

/**
 * Shared ATS Resume Preview composable.
 * Renders identically across Scribe Tower and Courier Guild from the same [TailoredResume] model.
 *
 * Invariant: If no base resume exists, displays "Upload your resume to begin" with zero fallback content.
 */
@Composable
fun SharedAtsResumePreview(
    resume       : TailoredResume,
    userProfile  : UserProfile,
    targetTitle  : String,
    targetCompany: String,
    modifier     : Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = OsrsParchment,
        border = BorderStroke(1.5.dp, OsrsGold),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val candidateName = resume.contact.name?.takeIf { it.isNotBlank() }
                ?: userProfile.name.ifBlank { "Candidate Name" }

            Text(
                text = candidateName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.Black,
            )

            Text(
                text = "Target: $targetTitle  |  $targetCompany",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF1E3A4A),
            )

            val contactParts = mutableListOf<String>()
            val loc = resume.contact.location ?: userProfile.location
            if (loc.isNotBlank()) contactParts.add(loc)
            val gh = resume.contact.github ?: userProfile.github
            if (gh.isNotBlank()) contactParts.add(gh.removePrefix("https://").removePrefix("http://"))
            val em = resume.contact.email ?: userProfile.email
            if (em.isNotBlank()) contactParts.add(em)
            val phone = resume.contact.phone
            if (!phone.isNullOrBlank()) contactParts.add(phone)

            if (contactParts.isNotEmpty()) {
                Text(
                    text = contactParts.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color.DarkGray,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            val isEmpty = resume.experience.isEmpty() && resume.projects.isEmpty() && resume.education.isEmpty()

            if (isEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Upload your resume to begin",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF755C48),
                    )
                }
                return@Column
            }

            // Summary
            if (resume.summary.isNotBlank()) {
                Text(
                    text = "PROFESSIONAL SUMMARY",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black,
                )
                Text(
                    text = resume.summary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
                    color = Color(0xFF222222),
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Skills
            if (resume.skills.isNotEmpty()) {
                Text(
                    text = "TECHNICAL SKILLS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black,
                )
                Text(
                    text = "• Competencies: " + resume.skills.joinToString("  •  "),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium),
                    color = Color(0xFF0F3A22),
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Experience
            if (resume.experience.isNotEmpty()) {
                Text(
                    text = "PROFESSIONAL EXPERIENCE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black,
                )
                for (exp in resume.experience) {
                    val dateLoc = buildString {
                        if (!exp.startDate.isNullOrBlank()) append(exp.startDate)
                        if (!exp.endDate.isNullOrBlank()) append(" – ${exp.endDate}") else if (exp.isCurrent) append(" – Present")
                        if (!exp.location.isNullOrBlank()) append("  ·  ${exp.location}")
                    }
                    val header = if (dateLoc.isNotBlank()) "${exp.employer}  |  ${exp.title} ($dateLoc)" else "${exp.employer}  |  ${exp.title}"
                    Text(
                        text = header,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF1B3B22),
                    )
                    for (b in exp.bullets) {
                        Text(
                            text = "• $b",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, lineHeight = 12.sp),
                            color = Color(0xFF222222),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }

            // Projects
            if (resume.projects.isNotEmpty()) {
                Text(
                    text = "TECHNICAL PROJECTS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black,
                )
                for (proj in resume.projects) {
                    val pHeader = if (!proj.role.isNullOrBlank()) "${proj.name}  |  ${proj.role}" else proj.name
                    Text(
                        text = pHeader,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF1B3B22),
                    )
                    if (proj.technologies.isNotEmpty()) {
                        Text(
                            text = proj.technologies.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                            color = Color.DarkGray,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    for (b in proj.bullets) {
                        Text(
                            text = "• $b",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, lineHeight = 12.sp),
                            color = Color(0xFF222222),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }

            // Education
            if (resume.education.isNotEmpty()) {
                Text(
                    text = "EDUCATION",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black,
                )
                for (edu in resume.education) {
                    val eduLine = buildString {
                        append("• ${edu.school}")
                        if (edu.degree.isNotBlank()) append("  |  ${edu.degree}")
                        if (!edu.fieldOfStudy.isNullOrBlank()) append(" (${edu.fieldOfStudy})")
                        if (!edu.graduationDate.isNullOrBlank()) append("  ·  ${edu.graduationDate}")
                    }
                    Text(
                        text = eduLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, lineHeight = 12.sp),
                        color = Color(0xFF222222),
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Certifications
            if (resume.certifications.isNotEmpty()) {
                Text(
                    text = "CERTIFICATIONS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black,
                )
                for (cert in resume.certifications) {
                    val certLine = buildString {
                        append("• ${cert.name}")
                        if (!cert.issuer.isNullOrBlank()) append("  |  ${cert.issuer}")
                        if (!cert.issueDate.isNullOrBlank()) append(" (${cert.issueDate})")
                    }
                    Text(
                        text = certLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, lineHeight = 12.sp),
                        color = Color(0xFF222222),
                    )
                }
            }
        }
    }
}

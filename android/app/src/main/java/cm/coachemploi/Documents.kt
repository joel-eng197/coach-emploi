package cm.coachemploi

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Date du jour de la génération, au format de la langue du document. */
fun today(langue: String): String =
    if (langue == "en") SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH).format(Date())
    else SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date())

class Titles(
    val profil: String, val competences: String, val experience: String, val formation: String,
    val certifs: String, val langues: String, val interets: String, val genere: String, val objet: String
)

fun titles(en: Boolean) = if (en) Titles(
    "PROFILE", "SKILLS", "WORK EXPERIENCE", "EDUCATION", "CERTIFICATIONS",
    "LANGUAGES", "INTERESTS", "Generated on", "Subject:"
) else Titles(
    "PROFIL", "COMPÉTENCES", "EXPÉRIENCE PROFESSIONNELLE", "FORMATION", "CERTIFICATIONS",
    "LANGUES", "CENTRES D'INTÉRÊT", "Généré le", "Objet :"
)

/** « Yaoundé, le 2 octobre 2026 » (ou « Yaoundé, October 2, 2026 »). */
fun CvResponse.lieuDate(): String = when {
    lieu.isBlank() -> date
    langue == "en" -> "$lieu, $date"
    else -> "$lieu, le $date"
}

private fun StringBuilder.sec(title: String, body: String) {
    if (body.isBlank()) return
    append('\n').append(title).append('\n')
    append("-".repeat(title.length)).append('\n')
    append(body.trimEnd()).append('\n')
}

/** Version texte du CV (partage) : les sections vides sont ignorées. */
fun CvResponse.cvText(): String {
    val t = titles(langue == "en")
    val c = cv
    val sb = StringBuilder()
    sb.append(c.nom.uppercase()).append('\n')
    if (c.titre.isNotBlank()) sb.append(c.titre).append('\n')
    if (c.contact.isNotEmpty()) sb.append(c.contact.joinToString("  |  ")).append('\n')
    if (date.isNotBlank()) sb.append(t.genere).append(' ').append(date).append('\n')
    sb.sec(t.profil, c.resume)
    sb.sec(t.competences, c.competences.joinToString("\n") { "• $it" })
    sb.sec(t.experience, c.experiences.joinToString("\n\n") { e ->
        val head = listOf(e.poste, e.organisation, e.periode).filter { it.isNotBlank() }.joinToString(" | ")
        (listOf(head) + e.details.map { "   • $it" }).joinToString("\n")
    })
    sb.sec(t.formation, c.formations.joinToString("\n") { f ->
        listOf(f.diplome, f.etablissement, f.periode).filter { it.isNotBlank() }.joinToString(" | ")
    })
    sb.sec(t.certifs, c.certifications.joinToString("\n") { "• $it" })
    sb.sec(t.langues, c.langues.joinToString("\n") { "• $it" })
    sb.sec(t.interets, c.interets.joinToString("  •  "))
    return sb.toString().trimEnd()
}

/** Version texte de la lettre de motivation (partage). */
fun CvResponse.lettreText(): String {
    val t = titles(langue == "en")
    val sb = StringBuilder()
    sb.append(cv.nom).append('\n')
    cv.contact.forEach { sb.append(it).append('\n') }
    val ld = lieuDate()
    if (ld.isNotBlank()) sb.append('\n').append(ld).append('\n')
    if (lettre.objet.isNotBlank()) sb.append('\n').append(t.objet).append(' ').append(lettre.objet).append('\n')
    if (lettre.destinataire.isNotBlank()) sb.append('\n').append(lettre.destinataire).append('\n')
    lettre.paragraphes.filter { it.isNotBlank() }.forEach { sb.append('\n').append(it).append('\n') }
    if (lettre.politesse.isNotBlank()) sb.append('\n').append(lettre.politesse).append('\n')
    sb.append('\n').append(lettre.signature.ifBlank { cv.nom })
    return sb.toString().trim()
}

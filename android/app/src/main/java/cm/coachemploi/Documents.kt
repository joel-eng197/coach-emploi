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

/** Contenu neutre d'un document : affiché à l'écran, exporté en PDF et en Word. */
sealed class Blk {
    class Header(val nom: String, val titre: String, val contact: String, val date: String) : Blk()
    class Heading(val text: String) : Blk()
    class Para(
        val text: String, val size: Float = 10.5f, val bold: Boolean = false, val italic: Boolean = false,
        val muted: Boolean = false, val accent: Boolean = false, val right: Boolean = false
    ) : Blk()
    class Bullet(val text: String) : Blk()
    class Gap(val h: Float) : Blk()
}

fun CvResponse.cvBlocks(): List<Blk> {
    val t = titles(langue == "en")
    val c = cv
    val out = ArrayList<Blk>()
    out.add(Blk.Header(c.nom, c.titre, c.contact.joinToString("  •  "), if (date.isBlank()) "" else t.genere + " " + date))
    if (c.resume.isNotBlank()) { out.add(Blk.Heading(t.profil)); out.add(Blk.Para(c.resume)) }
    if (c.competences.isNotEmpty()) {
        out.add(Blk.Heading(t.competences))
        c.competences.forEach { out.add(Blk.Bullet(it)) }
    }
    if (c.experiences.isNotEmpty()) {
        out.add(Blk.Heading(t.experience))
        c.experiences.forEach { e ->
            if (e.poste.isNotBlank()) out.add(Blk.Para(e.poste, 11.5f, bold = true))
            val sous = listOf(e.organisation, e.periode).filter { it.isNotBlank() }.joinToString("  |  ")
            if (sous.isNotBlank()) out.add(Blk.Para(sous, 9.5f, italic = true, muted = true))
            e.details.forEach { out.add(Blk.Bullet(it)) }
            out.add(Blk.Gap(6f))
        }
    }
    if (c.formations.isNotEmpty()) {
        out.add(Blk.Heading(t.formation))
        c.formations.forEach { f ->
            if (f.diplome.isNotBlank()) out.add(Blk.Para(f.diplome, 11.5f, bold = true))
            val sous = listOf(f.etablissement, f.periode).filter { it.isNotBlank() }.joinToString("  |  ")
            if (sous.isNotBlank()) out.add(Blk.Para(sous, 9.5f, italic = true, muted = true))
            out.add(Blk.Gap(6f))
        }
    }
    if (c.certifications.isNotEmpty()) {
        out.add(Blk.Heading(t.certifs))
        c.certifications.forEach { out.add(Blk.Bullet(it)) }
    }
    if (c.langues.isNotEmpty()) {
        out.add(Blk.Heading(t.langues))
        c.langues.forEach { out.add(Blk.Bullet(it)) }
    }
    if (c.interets.isNotEmpty()) {
        out.add(Blk.Heading(t.interets))
        out.add(Blk.Para(c.interets.joinToString("  •  ")))
    }
    return out
}

fun CvResponse.lettreBlocks(): List<Blk> {
    val t = titles(langue == "en")
    val out = ArrayList<Blk>()
    out.add(Blk.Para(cv.nom, 13f, bold = true, accent = true))
    cv.contact.forEach { out.add(Blk.Para(it, 9.5f, muted = true)) }
    out.add(Blk.Gap(12f))
    val ld = lieuDate()
    if (ld.isNotBlank()) out.add(Blk.Para(ld, 10f, right = true))
    out.add(Blk.Gap(10f))
    if (lettre.objet.isNotBlank()) out.add(Blk.Para(t.objet + " " + lettre.objet, 11f, bold = true))
    if (lettre.destinataire.isNotBlank()) { out.add(Blk.Gap(4f)); out.add(Blk.Para(lettre.destinataire, 11f)) }
    lettre.paragraphes.filter { it.isNotBlank() }.forEach { out.add(Blk.Gap(3f)); out.add(Blk.Para(it, 11f)) }
    if (lettre.politesse.isNotBlank()) { out.add(Blk.Gap(3f)); out.add(Blk.Para(lettre.politesse, 11f)) }
    out.add(Blk.Gap(14f))
    out.add(Blk.Para(lettre.signature.ifBlank { cv.nom }, 11f, bold = true))
    return out
}

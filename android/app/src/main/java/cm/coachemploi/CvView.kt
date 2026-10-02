package cm.coachemploi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF1F2933)
private val Muted = Color(0xFF616E7C)
private val Accent = Color(0xFF0B6E4F)
private val Line = Color(0xFFD9E2EC)

/** Feuille blanche façon document Word / PDF. */
@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
        color = Color.White, shadowElevation = 3.dp
    ) {
        SelectionContainer {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun Rubrique(titre: String, visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    if (!visible) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titre, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Accent)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun Corps(texte: String) =
    Text(texte, fontSize = 13.sp, lineHeight = 19.sp, color = Ink)

@Composable
private fun Puce(texte: String) {
    Row {
        Text("•", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Accent, modifier = Modifier.width(14.dp))
        Text(texte, fontSize = 13.sp, lineHeight = 18.sp, color = Ink, modifier = Modifier.weight(1f))
    }
}

@Composable
fun CvDocument(r: CvResponse) {
    val c = r.cv
    val t = titles(r.langue == "en")
    Page {
        Text(c.nom, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Accent)
        if (c.titre.isNotBlank()) Text(c.titre, fontSize = 15.sp, color = Ink)
        if (c.contact.isNotEmpty()) Text(c.contact.joinToString("  •  "), fontSize = 12.sp, lineHeight = 17.sp, color = Muted)
        if (r.date.isNotBlank()) Text("${t.genere} ${r.date}", fontSize = 11.sp, fontStyle = FontStyle.Italic, color = Muted)
        Box(Modifier.fillMaxWidth().height(2.dp).background(Accent))

        Rubrique(t.profil, c.resume.isNotBlank()) { Corps(c.resume) }
        Rubrique(t.competences, c.competences.isNotEmpty()) { c.competences.forEach { Puce(it) } }
        Rubrique(t.experience, c.experiences.isNotEmpty()) {
            c.experiences.forEach { e ->
                Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(e.poste, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                    val sous = listOf(e.organisation, e.periode).filter { it.isNotBlank() }.joinToString("  |  ")
                    if (sous.isNotBlank()) Text(sous, fontSize = 12.sp, fontStyle = FontStyle.Italic, color = Muted)
                    e.details.forEach { Puce(it) }
                }
            }
        }
        Rubrique(t.formation, c.formations.isNotEmpty()) {
            c.formations.forEach { f ->
                Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(f.diplome, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                    val sous = listOf(f.etablissement, f.periode).filter { it.isNotBlank() }.joinToString("  |  ")
                    if (sous.isNotBlank()) Text(sous, fontSize = 12.sp, fontStyle = FontStyle.Italic, color = Muted)
                }
            }
        }
        Rubrique(t.certifs, c.certifications.isNotEmpty()) { c.certifications.forEach { Puce(it) } }
        Rubrique(t.langues, c.langues.isNotEmpty()) { c.langues.forEach { Puce(it) } }
        Rubrique(t.interets, c.interets.isNotEmpty()) { Corps(c.interets.joinToString("  •  ")) }
    }
}

@Composable
fun LettreDocument(r: CvResponse) {
    val l = r.lettre
    val c = r.cv
    val t = titles(r.langue == "en")
    Page {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(c.nom, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Accent)
            c.contact.forEach { Text(it, fontSize = 12.sp, color = Muted) }
        }
        val ld = r.lieuDate()
        if (ld.isNotBlank()) Text(ld, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End, fontSize = 12.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        if (l.objet.isNotBlank()) Text("${t.objet} ${l.objet}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
        if (l.destinataire.isNotBlank()) Text(l.destinataire, fontSize = 13.sp, color = Ink)
        l.paragraphes.filter { it.isNotBlank() }.forEach {
            Text(it, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Justify, color = Ink)
        }
        if (l.politesse.isNotBlank()) Text(l.politesse, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Justify, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(l.signature.ifBlank { c.nom }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

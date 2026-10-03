package cm.coachemploi

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF1F2933)
private val Muted = Color(0xFF616E7C)
private val Line = Color(0xFFD9E2EC)

private class DocStyle(val modele: String, val accent: Color)

@Composable
fun PhotoCircle(path: String, size: Dp) {
    val bmp = remember(path) { loadBitmap(path)?.asImageBitmap() }
    if (bmp != null) {
        Image(
            bitmap = bmp, contentDescription = "Photo", contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape)
        )
    }
}

@Composable
private fun HeaderView(h: Blk.Header, st: DocStyle, photo: String) {
    when (st.modele) {
        "classique" -> Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (photo.isNotBlank()) PhotoCircle(photo, 72.dp)
            Text(h.nom, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center)
            if (h.titre.isNotBlank()) Text(h.titre, fontSize = 15.sp, color = st.accent, textAlign = TextAlign.Center)
            if (h.contact.isNotBlank()) Text(h.contact, fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center)
            if (h.date.isNotBlank()) Text(h.date, fontSize = 11.sp, fontStyle = FontStyle.Italic, color = Muted)
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(2.dp).background(st.accent))
        }
        "minimaliste" -> Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(h.nom, fontSize = 28.sp, fontWeight = FontWeight.Light, color = Ink)
                if (h.titre.isNotBlank()) Text(h.titre, fontSize = 14.sp, color = Muted)
                if (h.contact.isNotBlank()) Text(h.contact, fontSize = 12.sp, lineHeight = 17.sp, color = Muted)
                if (h.date.isNotBlank()) Text(h.date, fontSize = 11.sp, fontStyle = FontStyle.Italic, color = Muted)
            }
            if (photo.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                PhotoCircle(photo, 72.dp)
            }
        }
        else -> Row(
            Modifier.fillMaxWidth().background(st.accent).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(h.nom, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (h.titre.isNotBlank()) Text(h.titre, fontSize = 15.sp, color = Color.White)
                if (h.contact.isNotBlank()) Text(h.contact, fontSize = 12.sp, lineHeight = 17.sp, color = Color.White)
                if (h.date.isNotBlank()) Text(h.date, fontSize = 11.sp, fontStyle = FontStyle.Italic, color = Color.White)
            }
            if (photo.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                PhotoCircle(photo, 72.dp)
            }
        }
    }
}

@Composable
private fun HeadingView(text: String, st: DocStyle) {
    when (st.modele) {
        "classique" -> Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 4.dp)) {
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Ink)
            Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(1.dp).background(st.accent))
        }
        "minimaliste" -> Text(
            text, fontSize = 11.sp, letterSpacing = 2.sp, color = Muted,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 4.dp)
        )
        else -> Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 4.dp)) {
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = st.accent)
            Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(1.dp).background(Line))
        }
    }
}

@Composable
private fun RenderBlock(b: Blk, st: DocStyle, photo: String) {
    when (b) {
        is Blk.Header -> HeaderView(b, st, photo)
        is Blk.Heading -> HeadingView(b.text, st)
        is Blk.Para -> Text(
            b.text, fontSize = (b.size * 1.25f).sp, lineHeight = (b.size * 1.25f * 1.4f).sp,
            fontWeight = if (b.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (b.italic) FontStyle.Italic else FontStyle.Normal,
            color = if (b.accent) st.accent else if (b.muted) Muted else Ink,
            textAlign = if (b.right) TextAlign.End else TextAlign.Start,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)
        )
        is Blk.Bullet -> Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 1.dp)) {
            Text("•", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = st.accent, modifier = Modifier.width(14.dp))
            Text(b.text, fontSize = 13.sp, lineHeight = 18.sp, color = Ink, modifier = Modifier.weight(1f))
        }
        is Blk.Gap -> Spacer(Modifier.height(b.h.dp))
    }
}

/** Feuille blanche façon document Word / PDF, avec le modèle et la couleur choisis. */
@Composable
private fun DocView(blocks: List<Blk>, r: CvResponse) {
    val st = DocStyle(r.modele, Color(colorInt(r.couleur)))
    ProvideTextStyle(TextStyle(fontFamily = if (st.modele == "classique") FontFamily.Serif else FontFamily.Default)) {
        Surface(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
            color = Color.White, shadowElevation = 3.dp
        ) {
            SelectionContainer {
                Column(Modifier.fillMaxWidth()) {
                    val first = blocks.firstOrNull()
                    if (first !is Blk.Header || st.modele != "moderne") Spacer(Modifier.height(16.dp))
                    blocks.forEach { RenderBlock(it, st, r.photo) }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
fun CvDocument(r: CvResponse) = DocView(r.cvBlocks(), r)

@Composable
fun LettreDocument(r: CvResponse) = DocView(r.lettreBlocks(), r)

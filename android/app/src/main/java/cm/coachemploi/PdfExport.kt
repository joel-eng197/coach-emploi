package cm.coachemploi

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Petit rédacteur de PDF A4 : texte qui passe à la ligne, puces, filets, nouvelles pages automatiques. */
private class PdfWriter {
    private val pageW = 595
    private val pageH = 842
    private val margin = 44f
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var number = 0
    private var y = 0f
    val contentWidth = pageW - 2 * margin

    init { newPage() }

    private fun newPage() {
        page?.let { doc.finishPage(it) }
        number++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, number).create())
        page = p
        canvas = p.canvas
        y = margin
    }

    private fun ensure(height: Float) {
        if (y + height > pageH - margin) newPage()
    }

    private fun layoutOf(
        txt: String, size: Float, col: Int, bold: Boolean, italic: Boolean,
        align: Layout.Alignment, width: Int, spacing: Float, letter: Float
    ): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = size
        paint.color = col
        paint.letterSpacing = letter
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, style)
        return StaticLayout.Builder.obtain(txt, 0, txt.length, paint, width)
            .setAlignment(align).setLineSpacing(0f, spacing).build()
    }

    fun gap(h: Float) { y += h }

    fun text(
        txt: String, size: Float, col: Int, bold: Boolean = false, italic: Boolean = false,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, after: Float = 4f,
        spacing: Float = 1.2f, letter: Float = 0f
    ) {
        if (txt.isBlank()) return
        val layout = layoutOf(txt, size, col, bold, italic, align, contentWidth.toInt(), spacing, letter)
        ensure(layout.height.toFloat())
        val c = canvas!!
        c.save()
        c.translate(margin, y)
        layout.draw(c)
        c.restore()
        y += layout.height + after
    }

    fun bullet(txt: String, size: Float, col: Int, bulletCol: Int, after: Float = 2f) {
        if (txt.isBlank()) return
        val layout = layoutOf(txt, size, col, false, false, Layout.Alignment.ALIGN_NORMAL,
            (contentWidth - 14f).toInt(), 1.2f, 0f)
        ensure(layout.height.toFloat())
        val c = canvas!!
        val bp = Paint(Paint.ANTI_ALIAS_FLAG)
        bp.textSize = size
        bp.color = bulletCol
        bp.typeface = Typeface.DEFAULT_BOLD
        c.drawText("•", margin + 2f, y + layout.getLineBaseline(0), bp)
        c.save()
        c.translate(margin + 14f, y)
        layout.draw(c)
        c.restore()
        y += layout.height + after
    }

    fun rule(col: Int, thickness: Float, after: Float) {
        ensure(thickness + after)
        val p = Paint()
        p.color = col
        p.strokeWidth = thickness
        canvas!!.drawLine(margin, y, pageW - margin, y, p)
        y += thickness + after
    }

    fun heading(title: String, accent: Int, line: Int) {
        ensure(60f)
        y += 6f
        text(title, 10f, accent, bold = true, after = 2f, letter = 0.1f)
        rule(line, 0.8f, 6f)
    }

    fun finish(file: File) {
        page?.let { doc.finishPage(it) }
        page = null
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }
}

object PdfExport {
    private val INK = Color.parseColor("#1F2933")
    private val MUTED = Color.parseColor("#616E7C")
    private val ACCENT = Color.parseColor("#0B6E4F")
    private val LINE = Color.parseColor("#D9E2EC")

    private fun dir(ctx: Context): File = File(ctx.cacheDir, "pdf").apply { mkdirs() }

    private fun fileName(prefix: String, nom: String): String {
        val clean = nom.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifBlank { "candidat" }
        return prefix + "_" + clean + ".pdf"
    }

    private fun cvFile(ctx: Context, r: CvResponse): File {
        val t = titles(r.langue == "en")
        val c = r.cv
        val w = PdfWriter()
        w.text(c.nom, 24f, ACCENT, bold = true, after = 2f)
        w.text(c.titre, 13f, INK, after = 2f)
        if (c.contact.isNotEmpty()) w.text(c.contact.joinToString("  •  "), 10f, MUTED, after = 2f)
        if (r.date.isNotBlank()) w.text(t.genere + " " + r.date, 9f, MUTED, italic = true, after = 6f)
        w.rule(ACCENT, 2f, 8f)

        if (c.resume.isNotBlank()) {
            w.heading(t.profil, ACCENT, LINE)
            w.text(c.resume, 10.5f, INK, after = 4f, spacing = 1.25f)
        }
        if (c.competences.isNotEmpty()) {
            w.heading(t.competences, ACCENT, LINE)
            c.competences.forEach { w.bullet(it, 10.5f, INK, ACCENT) }
        }
        if (c.experiences.isNotEmpty()) {
            w.heading(t.experience, ACCENT, LINE)
            c.experiences.forEach { e ->
                w.text(e.poste, 11.5f, INK, bold = true, after = 1f)
                w.text(listOf(e.organisation, e.periode).filter { it.isNotBlank() }.joinToString("  |  "),
                    9.5f, MUTED, italic = true, after = 2f)
                e.details.forEach { d -> w.bullet(d, 10.5f, INK, ACCENT) }
                w.gap(6f)
            }
        }
        if (c.formations.isNotEmpty()) {
            w.heading(t.formation, ACCENT, LINE)
            c.formations.forEach { f ->
                w.text(f.diplome, 11.5f, INK, bold = true, after = 1f)
                w.text(listOf(f.etablissement, f.periode).filter { it.isNotBlank() }.joinToString("  |  "),
                    9.5f, MUTED, italic = true, after = 2f)
                w.gap(6f)
            }
        }
        if (c.certifications.isNotEmpty()) {
            w.heading(t.certifs, ACCENT, LINE)
            c.certifications.forEach { w.bullet(it, 10.5f, INK, ACCENT) }
        }
        if (c.langues.isNotEmpty()) {
            w.heading(t.langues, ACCENT, LINE)
            c.langues.forEach { w.bullet(it, 10.5f, INK, ACCENT) }
        }
        if (c.interets.isNotEmpty()) {
            w.heading(t.interets, ACCENT, LINE)
            w.text(c.interets.joinToString("  •  "), 10.5f, INK, after = 4f)
        }
        val file = File(dir(ctx), fileName("CV", c.nom))
        w.finish(file)
        return file
    }

    private fun lettreFile(ctx: Context, r: CvResponse): File {
        val t = titles(r.langue == "en")
        val l = r.lettre
        val c = r.cv
        val w = PdfWriter()
        w.text(c.nom, 13f, ACCENT, bold = true, after = 1f)
        c.contact.forEach { w.text(it, 9.5f, MUTED, after = 1f) }
        w.gap(14f)
        w.text(r.lieuDate(), 10f, INK, align = Layout.Alignment.ALIGN_OPPOSITE, after = 18f)
        if (l.objet.isNotBlank()) w.text(t.objet + " " + l.objet, 11f, INK, bold = true, after = 12f)
        w.text(l.destinataire, 11f, INK, after = 10f)
        l.paragraphes.forEach { w.text(it, 11f, INK, after = 9f, spacing = 1.3f) }
        w.text(l.politesse, 11f, INK, after = 20f, spacing = 1.3f)
        w.text(l.signature.ifBlank { c.nom }, 11f, INK, bold = true)
        val file = File(dir(ctx), fileName("Lettre", c.nom))
        w.finish(file)
        return file
    }

    private fun share(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val i = Intent(Intent.ACTION_SEND)
        i.type = "application/pdf"
        i.putExtra(Intent.EXTRA_STREAM, uri)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Partager le PDF"))
    }

    /** Enregistre dans Téléchargements (Android 10+) ; sinon ouvre le partage. */
    private fun saveToDownloads(ctx: Context, file: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { share(ctx, file); return }
        val values = ContentValues()
        values.put(MediaStore.Downloads.DISPLAY_NAME, file.name)
        values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Toast.makeText(ctx, "Enregistrement impossible", Toast.LENGTH_LONG).show()
            return
        }
        ctx.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        Toast.makeText(ctx, "PDF enregistré dans Téléchargements : " + file.name, Toast.LENGTH_LONG).show()
    }

    private fun safely(ctx: Context, download: Boolean, build: () -> File) {
        try {
            val f = build()
            if (download) saveToDownloads(ctx, f) else share(ctx, f)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Export PDF impossible : " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    fun exportCv(ctx: Context, r: CvResponse, download: Boolean) = safely(ctx, download) { cvFile(ctx, r) }
    fun exportLettre(ctx: Context, r: CvResponse, download: Boolean) = safely(ctx, download) { lettreFile(ctx, r) }
}

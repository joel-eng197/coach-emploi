package cm.coachemploi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

/** Rédacteur de PDF A4 : texte à la ligne, puces, filets, bandeau, photo ronde, pages automatiques. */
private class PdfWriter(private val serif: Boolean) {
    private val pageW = 595
    private val pageH = 842
    val margin = 44f
    val pageWidth: Float get() = pageW.toFloat()
    val contentWidth: Float get() = pageW - 2 * margin
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var number = 0
    private var y = 0f

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
        paint.typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, style)
        return StaticLayout.Builder.obtain(txt, 0, txt.length, paint, width)
            .setAlignment(align).setLineSpacing(0f, spacing).build()
    }

    fun top(): Float = y
    fun gap(h: Float) { y += h }
    fun moveTo(minY: Float) { if (y < minY) y = minY }

    fun height(txt: String, size: Float, bold: Boolean, width: Float): Float =
        layoutOf(txt, size, 0, bold, false, Layout.Alignment.ALIGN_NORMAL, width.toInt(), 1.2f, 0f).height.toFloat()

    fun text(
        txt: String, size: Float, col: Int, bold: Boolean = false, italic: Boolean = false,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, after: Float = 4f,
        spacing: Float = 1.2f, letter: Float = 0f, width: Float = contentWidth
    ) {
        if (txt.isBlank()) return
        val layout = layoutOf(txt, size, col, bold, italic, align, width.toInt(), spacing, letter)
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
        canvas!!.drawLine(margin, y, pageWidth - margin, y, p)
        y += thickness + after
    }

    fun heading(title: String, textCol: Int, ruleCol: Int) {
        ensure(60f)
        y += 6f
        text(title, 10f, textCol, bold = true, after = 2f, letter = 0.1f)
        rule(ruleCol, 0.8f, 6f)
    }

    fun headingPlain(title: String, col: Int) {
        ensure(50f)
        y += 8f
        text(title, 9f, col, after = 4f, letter = 0.2f)
    }

    fun band(col: Int, h: Float) {
        val p = Paint()
        p.color = col
        canvas!!.drawRect(0f, 0f, pageWidth, h, p)
    }

    fun photoAt(bmp: Bitmap, x: Float, yy: Float, size: Float) {
        val c = canvas!!
        val path = Path()
        path.addCircle(x + size / 2f, yy + size / 2f, size / 2f, Path.Direction.CW)
        c.save()
        c.clipPath(path)
        c.drawBitmap(bmp, null, RectF(x, yy, x + size, yy + size), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        c.restore()
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
    private val LINE = Color.parseColor("#D9E2EC")
    private val WHITE = Color.WHITE

    private fun header(w: PdfWriter, h: Blk.Header, modele: String, accent: Int, photo: Bitmap?) {
        val ps = 64f
        val center = Layout.Alignment.ALIGN_CENTER
        when (modele) {
            "classique" -> {
                if (photo != null) {
                    w.photoAt(photo, (w.pageWidth - ps) / 2f, w.top(), ps)
                    w.gap(ps + 8f)
                }
                w.text(h.nom, 24f, INK, bold = true, align = center, after = 2f)
                w.text(h.titre, 12.5f, accent, align = center, after = 2f)
                w.text(h.contact, 9.5f, MUTED, align = center, after = 2f)
                w.text(h.date, 9f, MUTED, italic = true, align = center, after = 6f)
                w.rule(accent, 1.5f, 8f)
            }
            "minimaliste" -> {
                val tw = if (photo != null) w.contentWidth - ps - 14f else w.contentWidth
                val y0 = w.top()
                if (photo != null) w.photoAt(photo, w.pageWidth - w.margin - ps, y0, ps)
                w.text(h.nom, 26f, INK, width = tw, after = 2f)
                w.text(h.titre, 12.5f, MUTED, width = tw, after = 2f)
                w.text(h.contact, 9.5f, MUTED, width = tw, after = 2f)
                w.text(h.date, 9f, MUTED, italic = true, width = tw, after = 2f)
                if (photo != null) w.moveTo(y0 + ps)
                w.gap(10f)
            }
            else -> {
                val tw = if (photo != null) w.contentWidth - ps - 14f else w.contentWidth
                fun hh(t: String, size: Float, bold: Boolean): Float =
                    if (t.isBlank()) 0f else w.height(t, size, bold, tw) + 3f
                val textH = hh(h.nom, 24f, true) + hh(h.titre, 12.5f, false) +
                    hh(h.contact, 9.5f, false) + hh(h.date, 9f, false)
                val bandH = w.margin + maxOf(textH, if (photo != null) ps else 0f) + 18f
                w.band(accent, bandH)
                if (photo != null) w.photoAt(photo, w.pageWidth - w.margin - ps, w.margin, ps)
                w.text(h.nom, 24f, WHITE, bold = true, width = tw, after = 3f)
                w.text(h.titre, 12.5f, WHITE, width = tw, after = 3f)
                w.text(h.contact, 9.5f, WHITE, width = tw, after = 3f)
                w.text(h.date, 9f, WHITE, italic = true, width = tw, after = 3f)
                w.moveTo(bandH + 6f)
            }
        }
    }

    fun build(r: CvResponse, blocks: List<Blk>, file: File) {
        val accent = colorInt(r.couleur)
        val modele = r.modele
        val photo = loadBitmap(r.photo)
        val w = PdfWriter(modele == "classique")
        blocks.forEach { b ->
            when (b) {
                is Blk.Header -> header(w, b, modele, accent, photo)
                is Blk.Heading -> when (modele) {
                    "classique" -> w.heading(b.text, INK, accent)
                    "minimaliste" -> w.headingPlain(b.text, MUTED)
                    else -> w.heading(b.text, accent, LINE)
                }
                is Blk.Para -> w.text(
                    b.text, b.size, if (b.accent) accent else if (b.muted) MUTED else INK,
                    bold = b.bold, italic = b.italic,
                    align = if (b.right) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL,
                    after = 3f, spacing = 1.25f
                )
                is Blk.Bullet -> w.bullet(b.text, 10.5f, INK, accent)
                is Blk.Gap -> w.gap(b.h)
            }
        }
        w.finish(file)
    }
}

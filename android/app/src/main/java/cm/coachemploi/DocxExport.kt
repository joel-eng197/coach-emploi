package cm.coachemploi

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Génère un vrai fichier .docx (ZIP de fichiers XML) sans bibliothèque externe. */
object DocxExport {
    private const val INK = "1F2933"
    private const val MUTED = "616E7C"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun run(
        text: String, font: String, size: Int, color: String,
        bold: Boolean = false, italic: Boolean = false, spacing: Int = 0
    ): String {
        val rpr = StringBuilder("<w:rPr><w:rFonts w:ascii=\"$font\" w:hAnsi=\"$font\" w:cs=\"$font\"/>")
        if (bold) rpr.append("<w:b/>")
        if (italic) rpr.append("<w:i/>")
        rpr.append("<w:color w:val=\"$color\"/>")
        if (spacing != 0) rpr.append("<w:spacing w:val=\"$spacing\"/>")
        rpr.append("<w:sz w:val=\"$size\"/></w:rPr>")
        return "<w:r>$rpr<w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r>"
    }

    private fun par(
        runs: String, align: String = "left", before: Int = 0, after: Int = 60,
        shade: String? = null, borderColor: String? = null, borderSz: Int = 6,
        leftInd: Int = 0, hanging: Int = 0, keepNext: Boolean = false
    ): String {
        val ppr = StringBuilder("<w:pPr>")
        if (keepNext) ppr.append("<w:keepNext/>")
        if (borderColor != null) {
            ppr.append("<w:pBdr><w:bottom w:val=\"single\" w:sz=\"$borderSz\" w:space=\"1\" w:color=\"$borderColor\"/></w:pBdr>")
        }
        if (shade != null) ppr.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"$shade\"/>")
        if (hanging > 0) ppr.append("<w:tabs><w:tab w:val=\"left\" w:pos=\"$leftInd\"/></w:tabs>")
        ppr.append("<w:spacing w:before=\"$before\" w:after=\"$after\"/>")
        if (leftInd > 0 || hanging > 0) ppr.append("<w:ind w:left=\"$leftInd\" w:hanging=\"$hanging\"/>")
        ppr.append("<w:jc w:val=\"$align\"/></w:pPr>")
        return "<w:p>$ppr$runs</w:p>"
    }

    private fun photoPar(shade: String?, align: String): String {
        val emu = 864000
        val drawing = """<w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="$emu" cy="$emu"/><wp:docPr id="1" name="Photo"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="0" name="photo.jpg"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rIdPhoto"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$emu" cy="$emu"/></a:xfrm><a:prstGeom prst="ellipse"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"""
        return par(drawing, align = align, shade = shade, after = 60)
    }

    private fun header(sb: StringBuilder, h: Blk.Header, modele: String, accent: String, font: String, hasPhoto: Boolean) {
        when (modele) {
            "classique" -> {
                if (hasPhoto) sb.append(photoPar(null, "center"))
                sb.append(par(run(h.nom, font, 48, INK, bold = true), align = "center", after = 40))
                if (h.titre.isNotBlank()) sb.append(par(run(h.titre, font, 26, accent), align = "center", after = 40))
                if (h.contact.isNotBlank()) sb.append(par(run(h.contact, font, 19, MUTED), align = "center", after = 40))
                val d = if (h.date.isNotBlank()) run(h.date, font, 18, MUTED, italic = true) else ""
                sb.append(par(d, align = "center", after = 120, borderColor = accent, borderSz = 12))
            }
            "minimaliste" -> {
                if (hasPhoto) sb.append(photoPar(null, "right"))
                sb.append(par(run(h.nom, font, 52, INK), after = 40))
                if (h.titre.isNotBlank()) sb.append(par(run(h.titre, font, 26, MUTED), after = 40))
                if (h.contact.isNotBlank()) sb.append(par(run(h.contact, font, 19, MUTED), after = 40))
                if (h.date.isNotBlank()) sb.append(par(run(h.date, font, 18, MUTED, italic = true), after = 120))
            }
            else -> {
                if (hasPhoto) sb.append(photoPar(accent, "right"))
                sb.append(par(run(h.nom, font, 48, "FFFFFF", bold = true), shade = accent, after = 0))
                if (h.titre.isNotBlank()) sb.append(par(run(h.titre, font, 26, "FFFFFF"), shade = accent, after = 0))
                if (h.contact.isNotBlank()) sb.append(par(run(h.contact, font, 19, "FFFFFF"), shade = accent, after = 0))
                val d = if (h.date.isNotBlank()) run(h.date, font, 18, "FFFFFF", italic = true) else ""
                sb.append(par(d, shade = accent, after = 160))
            }
        }
    }

    fun build(r: CvResponse, blocks: List<Blk>, file: File) {
        val accent = hexNoHash(r.couleur)
        val modele = r.modele
        val font = if (modele == "classique") "Times New Roman" else "Calibri"
        val photoBytes: ByteArray? = try {
            if (r.photo.isNotBlank()) File(r.photo).readBytes() else null
        } catch (e: Exception) {
            null
        }
        val hasPhoto = photoBytes != null
        val body = StringBuilder()
        blocks.forEach { b ->
            when (b) {
                is Blk.Header -> header(body, b, modele, accent, font, hasPhoto)
                is Blk.Heading -> body.append(
                    when (modele) {
                        "classique" -> par(run(b.text, font, 22, INK, bold = true), before = 220, after = 80,
                            borderColor = accent, borderSz = 8, keepNext = true)
                        "minimaliste" -> par(run(b.text, font, 18, MUTED, spacing = 40), before = 240, after = 60, keepNext = true)
                        else -> par(run(b.text, font, 22, accent, bold = true, spacing = 20), before = 220, after = 80,
                            borderColor = "D9E2EC", borderSz = 6, keepNext = true)
                    }
                )
                is Blk.Para -> body.append(
                    par(
                        run(b.text, font, (b.size * 2).toInt(), if (b.accent) accent else if (b.muted) MUTED else INK,
                            bold = b.bold, italic = b.italic),
                        align = if (b.right) "right" else "left", after = 50
                    )
                )
                is Blk.Bullet -> body.append(
                    par(
                        run("•", font, 21, accent, bold = true) + "<w:r><w:tab/></w:r>" + run(b.text, font, 21, INK),
                        leftInd = 284, hanging = 284, after = 20
                    )
                )
                is Blk.Gap -> body.append(par("", after = (b.h * 20).toInt()))
            }
        }
        val ns = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
            "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
            "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
            "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\""
        val sect = "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
            "<w:pgMar w:top=\"900\" w:right=\"1000\" w:bottom=\"900\" w:left=\"1000\" w:header=\"450\" w:footer=\"450\" w:gutter=\"0\"/></w:sectPr>"
        val doc = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document $ns><w:body>$body$sect</w:body></w:document>"
        val types = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpg" ContentType="image/jpeg"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        val photoRel = if (hasPhoto) """<Relationship Id="rIdPhoto" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/photo.jpg"/>""" else ""
        val docRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$photoRel</Relationships>"""
        ZipOutputStream(FileOutputStream(file)).use { z ->
            fun add(name: String, data: ByteArray) {
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
            add("[Content_Types].xml", types.toByteArray(Charsets.UTF_8))
            add("_rels/.rels", rels.toByteArray(Charsets.UTF_8))
            add("word/document.xml", doc.toByteArray(Charsets.UTF_8))
            add("word/_rels/document.xml.rels", docRels.toByteArray(Charsets.UTF_8))
            if (photoBytes != null) add("word/media/photo.jpg", photoBytes)
        }
    }
}

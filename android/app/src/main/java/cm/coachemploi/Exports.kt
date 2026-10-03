package cm.coachemploi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object Exports {
    private const val PDF = "application/pdf"
    private const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    private fun dir(ctx: Context): File = File(ctx.cacheDir, "pdf").apply { mkdirs() }

    private fun fileName(prefix: String, nom: String, ext: String): String {
        val clean = nom.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifBlank { "candidat" }
        return prefix + "_" + clean + "." + ext
    }

    /** format = "pdf" ou "docx" ; download = true : enregistre dans Téléchargements, sinon ouvre le partage. */
    fun export(ctx: Context, r: CvResponse, lettre: Boolean, format: String, download: Boolean) {
        try {
            val blocks = if (lettre) r.lettreBlocks() else r.cvBlocks()
            val ext = if (format == "docx") "docx" else "pdf"
            val file = File(dir(ctx), fileName(if (lettre) "Lettre" else "CV", r.cv.nom, ext))
            if (format == "docx") DocxExport.build(r, blocks, file) else PdfExport.build(r, blocks, file)
            val mime = if (format == "docx") DOCX else PDF
            if (download) saveToDownloads(ctx, file, mime) else share(ctx, file, mime)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Export impossible : " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Coach Emploi IA", text))
        Toast.makeText(ctx, "Texte copié !", Toast.LENGTH_SHORT).show()
    }

    private fun share(ctx: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val i = Intent(Intent.ACTION_SEND)
        i.type = mime
        i.putExtra(Intent.EXTRA_STREAM, uri)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Partager"))
    }

    private fun saveToDownloads(ctx: Context, file: File, mime: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { share(ctx, file, mime); return }
        val values = ContentValues()
        values.put(MediaStore.Downloads.DISPLAY_NAME, file.name)
        values.put(MediaStore.Downloads.MIME_TYPE, mime)
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Toast.makeText(ctx, "Enregistrement impossible", Toast.LENGTH_LONG).show()
            return
        }
        ctx.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        Toast.makeText(ctx, "Enregistré dans Téléchargements : " + file.name, Toast.LENGTH_LONG).show()
    }
}

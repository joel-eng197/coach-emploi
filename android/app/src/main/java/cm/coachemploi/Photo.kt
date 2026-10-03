package cm.coachemploi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

fun loadBitmap(path: String): Bitmap? =
    if (path.isBlank()) null else try { BitmapFactory.decodeFile(path) } catch (e: Exception) { null }

object PhotoStore {
    /** Recadre la photo en carré, la réduit à 400 px et l'enregistre dans le stockage privé de l'appli. */
    fun save(ctx: Context, uri: Uri): String? {
        return try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            val angle = ctx.contentResolver.openInputStream(uri)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val side = minOf(src.width, src.height)
            val m = Matrix()
            m.postRotate(angle)
            val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side, m, true)
            val out = Bitmap.createScaledBitmap(square, 400, 400, true)
            val file = File(ctx.filesDir, "photo_" + System.currentTimeMillis() + ".jpg")
            FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}

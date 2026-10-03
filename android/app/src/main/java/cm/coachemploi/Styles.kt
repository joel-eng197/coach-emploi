package cm.coachemploi

import android.graphics.Color as AColor

val Palette = listOf("#0B6E4F", "#1E5AA8", "#B3261E", "#6A1B9A", "#C77700", "#2B2B2B")
val Modeles = listOf("moderne" to "Moderne", "classique" to "Classique", "minimaliste" to "Minimaliste")

fun colorInt(hex: String): Int =
    try { AColor.parseColor(hex) } catch (e: Exception) { AColor.parseColor("#0B6E4F") }

fun hexNoHash(hex: String): String = "%06X".format(colorInt(hex) and 0xFFFFFF)

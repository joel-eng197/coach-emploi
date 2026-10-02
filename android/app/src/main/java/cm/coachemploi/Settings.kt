package cm.coachemploi

import android.content.Context
import com.google.gson.Gson

/** Formulaire du profil, partagé entre les onglets et mémorisé sur le téléphone. */
data class Form(
    val nom: String = "", val telephone: String = "", val email: String = "",
    val adresse: String = "", val ville: String = "", val lien: String = "",
    val resume: String = "", val formation: String = "", val experience: String = "",
    val certifications: String = "", val competences: String = "", val langues: String = "",
    val interets: String = "", val metier: String = "", val contrat: String = "", val en: Boolean = false
) {
    val langue get() = if (en) "en" else "fr"
    fun toProfil() = Profil(
        nom = nom, formation = formation, competences = competences, experience = experience,
        ville = ville, metier_vise = metier, langue = langue, telephone = telephone, email = email,
        adresse = adresse, lien = lien, resume = resume, langues_parlees = langues,
        interets = interets, certifications = certifications, contrat = contrat
    )
}

/** Réglages enregistrés sur le téléphone : adresse du serveur, mode démo, profil. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    var serverUrl: String
        get() = prefs.getString("url", BuildConfig.BASE_URL) ?: BuildConfig.BASE_URL
        set(v) = prefs.edit().putString("url", normalize(v)).apply()

    /** Par défaut, démo tant que l'adresse du serveur est encore provisoire. */
    var demo: Boolean
        get() = prefs.getBoolean("demo", serverUrl.contains("ton-url") || serverUrl.contains("VOTRE-API"))
        set(v) = prefs.edit().putBoolean("demo", v).apply()

    fun saveForm(f: Form) = prefs.edit().putString("form", gson.toJson(f)).apply()

    fun loadForm(): Form {
        val s = prefs.getString("form", null)
        if (s.isNullOrBlank()) return Form()
        return try { gson.fromJson(s, Form::class.java) ?: Form() } catch (e: Exception) { Form() }
    }

    private fun normalize(u: String): String {
        var s = u.trim()
        if (s.isEmpty()) return s
        if (!s.startsWith("http")) s = "https://$s"
        if (!s.endsWith("/")) s += "/"
        return s
    }
}

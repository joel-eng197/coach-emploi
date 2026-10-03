package cm.coachemploi

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class UiState(
    val loading: Boolean = false,
    val cv: CvResponse? = null,
    val diag: SkillsResponse? = null,
    val chat: List<Msg> = emptyList(),
    val error: String? = null,
    val notice: String? = null
)

class CoachViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    private val gson = Gson()
    val settings = Settings(app)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private val _demo = MutableStateFlow(settings.demo)
    val demo: StateFlow<Boolean> = _demo.asStateFlow()
    val history: StateFlow<List<Saved>> =
        dao.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Profil saisi : survit à la rotation et au redémarrage de l'appli. */
    var form by mutableStateOf(settings.loadForm())
        private set

    val serverUrl: String get() = settings.serverUrl
    private val erreur = "Connexion impossible. Vérifiez votre réseau et réessayez."
    private var variante = 0

    fun updateForm(f: Form) { form = f; settings.saveForm(f) }

    fun setDemo(on: Boolean) { settings.demo = on; _demo.value = on }

    fun saveUrl(url: String) {
        settings.serverUrl = url
        if (url.isNotBlank()) setDemo(false)
        _ui.update { it.copy(error = null, notice = "Adresse enregistrée.") }
    }

    fun setPhoto(path: String) {
        val old = form.photo
        if (old.isNotBlank() && old != path) {
            try { File(old).delete() } catch (e: Exception) { }
        }
        updateForm(form.copy(photo = path))
    }

    /** Efface le profil saisi, la photo, les résultats et l'entretien (l'historique est conservé). */
    fun clearAll() {
        val old = form.photo
        if (old.isNotBlank()) {
            try { File(old).delete() } catch (e: Exception) { }
        }
        updateForm(Form(modele = form.modele, couleur = form.couleur))
        variante = 0
        _ui.value = UiState(notice = "Contenu effacé.")
    }

    fun testConnection() = viewModelScope.launch {
        _ui.update { it.copy(loading = true, error = null, notice = null) }
        try {
            Net.api(settings.serverUrl).health().close()
            _ui.update { it.copy(loading = false, notice = "Serveur joignable ✔") }
        } catch (e: Exception) {
            _ui.update {
                it.copy(loading = false, error = "Serveur injoignable. Vérifie l'adresse " +
                    "(le serveur gratuit peut mettre 1 minute à se réveiller).")
            }
        }
    }

    private fun parseCv(json: String): CvResponse? =
        try { gson.fromJson(json, CvResponse::class.java) } catch (e: Exception) { null }

    fun generateCv(p: Profil) = viewModelScope.launch {
        _ui.update { it.copy(loading = true, error = null, notice = null) }
        val isDemo = settings.demo
        val f = form
        try {
            val raw = if (isDemo) Demo.cv(p) else Net.api(settings.serverUrl).cv(CvRequest(p))
            // La date du jour est ajoutée automatiquement par l'appli
            val r = raw.copy(
                date = today(p.langue), lieu = p.ville.ifBlank { p.adresse }, langue = p.langue,
                modele = f.modele, couleur = f.couleur, photo = f.photo
            )
            variante = 0
            val titre = (if (isDemo) "Démo · " else "") + "CV : " + p.metier_vise.ifBlank { p.nom }
            dao.insert(Saved(type = "cv", title = titre, json = gson.toJson(r)))
            val avis = when {
                raw.offre_status == "lien_illisible" ->
                    "Lien de l'offre illisible : colle plutôt le texte de l'annonce."
                isDemo && p.offre.isNotBlank() -> "Mode démo : l'adaptation à l'offre nécessite le serveur IA."
                else -> null
            }
            _ui.update { it.copy(loading = false, cv = r, notice = avis) }
        } catch (e: Exception) {
            val last = dao.last("cv")?.let { parseCv(it.json) }
            if (last != null) {
                _ui.update { it.copy(loading = false, cv = last, notice = "Hors ligne : dernier CV enregistré affiché.") }
            } else {
                _ui.update { it.copy(loading = false, error = erreur) }
            }
        }
    }

    /** Demande une autre variante de la lettre de motivation. */
    fun regenerateLettre(p: Profil) = viewModelScope.launch {
        val cur = _ui.value.cv ?: return@launch
        _ui.update { it.copy(loading = true, error = null, notice = null) }
        val isDemo = settings.demo
        variante++
        try {
            val l = if (isDemo) Demo.lettre(p, variante)
            else Net.api(settings.serverUrl).lettre(LettreRequest(p, cur.lettre))
            val r = cur.copy(lettre = l)
            val titre = (if (isDemo) "Démo · " else "") + "CV : " + p.metier_vise.ifBlank { p.nom }
            dao.insert(Saved(type = "cv", title = titre, json = gson.toJson(r)))
            _ui.update { it.copy(loading = false, cv = r, notice = "Nouvelle variante de la lettre.") }
        } catch (e: Exception) {
            _ui.update { it.copy(loading = false, error = erreur) }
        }
    }

    /** Réécrit le résumé ou les expériences avec un style professionnel. */
    fun reformuler(type: String, texte: String) = viewModelScope.launch {
        if (texte.isBlank()) {
            _ui.update { it.copy(notice = "Écris d'abord un texte à reformuler.") }
            return@launch
        }
        _ui.update { it.copy(loading = true, error = null, notice = null) }
        val isDemo = settings.demo
        try {
            val out = if (isDemo) Demo.reformuler(texte)
            else Net.api(settings.serverUrl).reformuler(
                ReformulerRequest(type, texte, form.langue, form.metier)
            ).texte
            val f = form
            updateForm(if (type == "resume") f.copy(resume = out) else f.copy(experience = out))
            val msg = if (isDemo) "Mode démo : correction simple. Le serveur IA propose une vraie réécriture."
            else "Texte reformulé ✔"
            _ui.update { it.copy(loading = false, notice = msg) }
        } catch (e: Exception) {
            _ui.update { it.copy(loading = false, error = erreur) }
        }
    }

    fun diagnose(p: Profil) = viewModelScope.launch {
        _ui.update { it.copy(loading = true, error = null, notice = null) }
        val isDemo = settings.demo
        try {
            val r = if (isDemo) Demo.skills(p) else Net.api(settings.serverUrl).skills(CvRequest(p))
            val titre = (if (isDemo) "Démo · " else "") + "Diagnostic : ${p.metier_vise}"
            dao.insert(Saved(type = "diag", title = titre, json = gson.toJson(r)))
            _ui.update { it.copy(loading = false, diag = r) }
        } catch (e: Exception) {
            val last = dao.last("diag")
            if (last != null) {
                _ui.update {
                    it.copy(loading = false, diag = gson.fromJson(last.json, SkillsResponse::class.java),
                        notice = "Hors ligne : dernier diagnostic enregistré affiché.")
                }
            } else {
                _ui.update { it.copy(loading = false, error = erreur) }
            }
        }
    }

    /** Démarre (answer = null) ou poursuit l'entretien. */
    fun interview(metier: String, langue: String, answer: String? = null) = viewModelScope.launch {
        val hist = if (answer == null) emptyList() else _ui.value.chat + Msg("user", answer)
        _ui.update { it.copy(loading = true, error = null, notice = null, chat = hist) }
        try {
            val reply = if (settings.demo) Demo.interview(hist, metier)
            else Net.api(settings.serverUrl).interview(InterviewRequest(metier, langue, hist)).reponse
            _ui.update { it.copy(loading = false, chat = hist + Msg("assistant", reply)) }
        } catch (e: Exception) {
            _ui.update { it.copy(loading = false, error = erreur) }
        }
    }

    fun delete(s: Saved) = viewModelScope.launch { dao.delete(s) }

    fun readable(s: Saved): String = when (s.type) {
        "cv" -> parseCv(s.json)?.let { it.cvText() + "\n\n----------\n\n" + it.lettreText() }
            ?: "Ancien format : régénère ton CV."
        else -> gson.fromJson(s.json, SkillsResponse::class.java).toText()
    }
}

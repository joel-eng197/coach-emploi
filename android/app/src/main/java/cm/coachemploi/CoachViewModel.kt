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

    fun updateForm(f: Form) { form = f; settings.saveForm(f) }

    fun setDemo(on: Boolean) { settings.demo = on; _demo.value = on }

    fun saveUrl(url: String) {
        settings.serverUrl = url
        if (url.isNotBlank()) setDemo(false)
        _ui.update { it.copy(error = null, notice = "Adresse enregistrée.") }
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
        try {
            val raw = if (isDemo) Demo.cv(p) else Net.api(settings.serverUrl).cv(CvRequest(p))
            // La date du jour est ajoutée automatiquement par l'appli
            val r = raw.copy(date = today(p.langue), lieu = p.ville.ifBlank { p.adresse }, langue = p.langue)
            val titre = (if (isDemo) "Démo · " else "") + "CV : " + p.metier_vise.ifBlank { p.nom }
            dao.insert(Saved(type = "cv", title = titre, json = gson.toJson(r)))
            _ui.update { it.copy(loading = false, cv = r) }
        } catch (e: Exception) {
            val last = dao.last("cv")?.let { parseCv(it.json) }
            if (last != null) {
                _ui.update { it.copy(loading = false, cv = last, notice = "Hors ligne : dernier CV enregistré affiché.") }
            } else {
                _ui.update { it.copy(loading = false, error = erreur) }
            }
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

package cm.coachemploi

import android.app.Application
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
    val notice: String? = null   // ex. : affichage du dernier résultat enregistré (hors ligne)
)

class CoachViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    private val gson = Gson()
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    val history: StateFlow<List<Saved>> =
        dao.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val erreur = "Connexion impossible. Vérifiez votre réseau et réessayez."

    fun generateCv(p: Profil) = viewModelScope.launch {
        _ui.update { it.copy(loading = true, error = null, notice = null) }
        try {
            val r = Net.api.cv(CvRequest(p))
            dao.insert(Saved(type = "cv", title = "CV : ${p.metier_vise}", json = gson.toJson(r)))
            _ui.update { it.copy(loading = false, cv = r) }
        } catch (e: Exception) {
            val last = dao.last("cv")
            if (last != null) _ui.update {
                it.copy(loading = false, cv = gson.fromJson(last.json, CvResponse::class.java),
                    notice = "Hors ligne : dernier CV enregistré affiché.")
            } else _ui.update { it.copy(loading = false, error = erreur) }
        }
    }

    fun diagnose(p: Profil) = viewModelScope.launch {
        _ui.update { it.copy(loading = true, error = null, notice = null) }
        try {
            val r = Net.api.skills(CvRequest(p))
            dao.insert(Saved(type = "diag", title = "Diagnostic : ${p.metier_vise}", json = gson.toJson(r)))
            _ui.update { it.copy(loading = false, diag = r) }
        } catch (e: Exception) {
            val last = dao.last("diag")
            if (last != null) _ui.update {
                it.copy(loading = false, diag = gson.fromJson(last.json, SkillsResponse::class.java),
                    notice = "Hors ligne : dernier diagnostic enregistré affiché.")
            } else _ui.update { it.copy(loading = false, error = erreur) }
        }
    }

    /** Démarre (answer = null) ou poursuit l'entretien. */
    fun interview(metier: String, langue: String, answer: String? = null) = viewModelScope.launch {
        val history = if (answer == null) emptyList() else _ui.value.chat + Msg("user", answer)
        _ui.update { it.copy(loading = true, error = null, notice = null, chat = history) }
        try {
            val r = Net.api.interview(InterviewRequest(metier, langue, history))
            _ui.update { it.copy(loading = false, chat = history + Msg("assistant", r.reponse)) }
        } catch (e: Exception) {
            _ui.update { it.copy(loading = false, error = erreur) }
        }
    }

    fun delete(s: Saved) = viewModelScope.launch { dao.delete(s) }

    /** Texte lisible d'un élément enregistré. */
    fun readable(s: Saved): String = when (s.type) {
        "cv" -> gson.fromJson(s.json, CvResponse::class.java).let { "${it.cv}\n\n--- Lettre ---\n${it.lettre}" }
        else -> gson.fromJson(s.json, SkillsResponse::class.java).toText()
    }
}

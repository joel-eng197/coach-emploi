package cm.coachemploi

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import okhttp3.ResponseBody
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class Profil(
    val nom: String = "", val formation: String = "", val competences: String = "",
    val experience: String = "", val ville: String = "", val metier_vise: String = "",
    val langue: String = "fr",
    val telephone: String = "", val email: String = "", val adresse: String = "", val lien: String = "",
    val resume: String = "", val langues_parlees: String = "", val interets: String = "",
    val certifications: String = "", val contrat: String = ""
)
data class CvRequest(val profil: Profil)
// Valeurs par défaut : Gson tolère ainsi un champ manquant dans la réponse de l'IA
data class Experience(
    val poste: String = "", val organisation: String = "", val periode: String = "",
    val details: List<String> = emptyList()
)
data class Etude(val diplome: String = "", val etablissement: String = "", val periode: String = "")
data class CvDoc(
    val nom: String = "", val titre: String = "", val contact: List<String> = emptyList(),
    val resume: String = "", val competences: List<String> = emptyList(),
    val experiences: List<Experience> = emptyList(), val formations: List<Etude> = emptyList(),
    val certifications: List<String> = emptyList(), val langues: List<String> = emptyList(),
    val interets: List<String> = emptyList()
)
data class LettreDoc(
    val objet: String = "", val destinataire: String = "Madame, Monsieur,",
    val paragraphes: List<String> = emptyList(), val politesse: String = "", val signature: String = ""
)
/** Réponse complète : date, lieu et langue sont ajoutés par l'appli au moment de la génération. */
data class CvResponse(
    val cv: CvDoc = CvDoc(), val lettre: LettreDoc = LettreDoc(),
    val date: String = "", val lieu: String = "", val langue: String = "fr"
)
data class Formation(val titre: String = "", val pourquoi: String = "")
data class SkillsResponse(
    val score: Int = 0,
    val points_forts: List<String> = emptyList(),
    val competences_manquantes: List<String> = emptyList(),
    val formations: List<Formation> = emptyList(),
    val plan_30_jours: List<String> = emptyList()
)
data class Msg(val role: String, val content: String)
data class InterviewRequest(val metier: String, val langue: String, val historique: List<Msg>)
data class InterviewResponse(val reponse: String = "")

fun SkillsResponse.toText(): String = buildString {
    appendLine("Adéquation avec le poste : $score/100")
    appendLine("\nPoints forts :"); points_forts.forEach { appendLine("• $it") }
    appendLine("\nCompétences à acquérir :"); competences_manquantes.forEach { appendLine("• $it") }
    appendLine("\nFormations conseillées :"); formations.forEach { appendLine("• ${it.titre} : ${it.pourquoi}") }
    appendLine("\nPlan sur 30 jours :"); plan_30_jours.forEach { appendLine("• $it") }
}

interface CoachApi {
    @GET("health") suspend fun health(): ResponseBody
    @POST("cv") suspend fun cv(@Body r: CvRequest): CvResponse
    @POST("skills-gap") suspend fun skills(@Body r: CvRequest): SkillsResponse
    @POST("interview") suspend fun interview(@Body r: InterviewRequest): InterviewResponse
}

object Net {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private var cachedUrl = ""
    private var cachedApi: CoachApi? = null

    /** Client Retrofit pour l'adresse choisie dans les réglages (recréé si elle change). */
    @Synchronized
    fun api(baseUrl: String): CoachApi {
        val existing = cachedApi
        if (existing != null && cachedUrl == baseUrl) return existing
        val created = Retrofit.Builder().baseUrl(baseUrl).client(http)
            .addConverterFactory(GsonConverterFactory.create()).build().create(CoachApi::class.java)
        cachedApi = created
        cachedUrl = baseUrl
        return created
    }
}

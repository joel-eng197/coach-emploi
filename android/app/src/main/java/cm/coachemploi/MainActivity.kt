@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cm.coachemploi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }
}

/** Formulaire partagé entre les onglets CV, Diagnostic et Entretien. */
data class Form(
    val nom: String = "", val formation: String = "", val competences: String = "",
    val experience: String = "", val ville: String = "", val metier: String = "", val en: Boolean = false
) {
    val langue get() = if (en) "en" else "fr"
    fun toProfil() = Profil(nom, formation, competences, experience, ville, metier, langue)
}

fun share(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    ctx.startActivity(Intent.createChooser(i, "Partager"))
}

@Composable
fun App(vm: CoachViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    var form by remember { mutableStateOf(Form()) }
    val ui by vm.ui.collectAsState()
    val history by vm.history.collectAsState()
    val tabs = listOf("CV & Lettre", "Diagnostic", "Entretien", "Historique")
    Scaffold { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }
            if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
            ui.notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(12.dp)) }
            when (tab) {
                0 -> CvScreen(ui, vm, form) { form = it }
                1 -> DiagScreen(ui, vm, form)
                2 -> InterviewScreen(ui, vm, form)
                else -> HistoryScreen(history, vm)
            }
        }
    }
}

@Composable
fun CvScreen(ui: UiState, vm: CoachViewModel, f: Form, onForm: (Form) -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Champ("Nom complet", f.nom) { onForm(f.copy(nom = it)) }
        Champ("Formation / diplômes", f.formation) { onForm(f.copy(formation = it)) }
        Champ("Compétences", f.competences) { onForm(f.copy(competences = it)) }
        Champ("Expériences (stages, jobs, projets)", f.experience) { onForm(f.copy(experience = it)) }
        Champ("Ville", f.ville) { onForm(f.copy(ville = it)) }
        Champ("Métier visé", f.metier) { onForm(f.copy(metier = it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Générer en anglais"); Spacer(Modifier.width(8.dp))
            Switch(f.en, { onForm(f.copy(en = it)) })
        }
        Button(
            onClick = { vm.generateCv(f.toProfil()) },
            enabled = !ui.loading && f.metier.isNotBlank(), modifier = Modifier.fillMaxWidth()
        ) { Text("Générer mon CV et ma lettre") }
        ui.cv?.let { cv ->
            Text("Mon CV", style = MaterialTheme.typography.titleMedium)
            SelectionContainer { Text(cv.cv) }
            Text("Ma lettre de motivation", style = MaterialTheme.typography.titleMedium)
            SelectionContainer { Text(cv.lettre) }
            OutlinedButton({ share(ctx, "${cv.cv}\n\n${cv.lettre}") }) { Text("Partager") }
        }
    }
}

@Composable
fun DiagScreen(ui: UiState, vm: CoachViewModel, f: Form) {
    val ctx = LocalContext.current
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Compare ton profil au métier visé et te propose un plan de 30 jours.")
        if (f.metier.isBlank()) Text("Remplis d'abord ton profil dans l'onglet « CV & Lettre ».",
            color = MaterialTheme.colorScheme.error)
        Button(
            onClick = { vm.diagnose(f.toProfil()) },
            enabled = !ui.loading && f.metier.isNotBlank(), modifier = Modifier.fillMaxWidth()
        ) { Text("Analyser mon profil") }
        ui.diag?.let { d ->
            Text("Adéquation : ${d.score}/100", style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(progress = d.score.coerceIn(0, 100) / 100f, modifier = Modifier.fillMaxWidth())
            Bloc("Points forts", d.points_forts)
            Bloc("Compétences à acquérir", d.competences_manquantes)
            Bloc("Formations conseillées", d.formations.map { "${it.titre} : ${it.pourquoi}" })
            Bloc("Plan sur 30 jours", d.plan_30_jours)
            OutlinedButton({ share(ctx, d.toText()) }) { Text("Partager") }
        }
    }
}

@Composable
fun Bloc(titre: String, lignes: List<String>) {
    if (lignes.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(titre, style = MaterialTheme.typography.titleMedium)
            lignes.forEach { Text("• $it") }
        }
    }
}

@Composable
fun InterviewScreen(ui: UiState, vm: CoachViewModel, f: Form) {
    var metier by remember { mutableStateOf(f.metier) }
    var answer by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Champ("Poste visé", metier) { metier = it }
        Button(onClick = { vm.interview(metier, f.langue) }, enabled = !ui.loading && metier.isNotBlank()) {
            Text(if (ui.chat.isEmpty()) "Commencer l'entretien" else "Recommencer")
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ui.chat) { m ->
                val moi = m.role == "user"
                Card(
                    colors = CardDefaults.cardColors(
                        if (moi) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = if (moi) 32.dp else 0.dp, end = if (moi) 0.dp else 32.dp)
                ) { Text(m.content, Modifier.padding(10.dp)) }
            }
        }
        if (ui.chat.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(answer, { answer = it }, Modifier.weight(1f), label = { Text("Ma réponse") })
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { vm.interview(metier, f.langue, answer); answer = "" },
                enabled = !ui.loading && answer.isNotBlank()
            ) { Text("Envoyer") }
        }
    }
}

@Composable
fun HistoryScreen(items: List<Saved>, vm: CoachViewModel) {
    val ctx = LocalContext.current
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE) }
    var open by remember { mutableStateOf<Long?>(null) }
    if (items.isEmpty()) {
        Text("Rien d'enregistré pour l'instant. Tes CV et diagnostics apparaîtront ici, même hors ligne.",
            Modifier.padding(16.dp))
        return
    }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.id }) { s ->
            Card(Modifier.fillMaxWidth(), onClick = { open = if (open == s.id) null else s.id }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.title, style = MaterialTheme.typography.titleMedium)
                    Text(fmt.format(Date(s.createdAt)), style = MaterialTheme.typography.bodySmall)
                    if (open == s.id) {
                        val txt = vm.readable(s)
                        SelectionContainer { Text(txt) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ share(ctx, txt) }) { Text("Partager") }
                            TextButton({ vm.delete(s) }) { Text("Supprimer") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Champ(label: String, value: String, onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) })

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cm.coachemploi

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CoachTheme { App() } }
    }
}

@Composable
fun CoachTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF6FD3A8), onPrimary = Color(0xFF00382A))
    else lightColorScheme(
        primary = Color(0xFF0B6E4F), onPrimary = Color.White,
        primaryContainer = Color(0xFFD2F0E2), onPrimaryContainer = Color(0xFF002114)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

fun share(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    ctx.startActivity(Intent.createChooser(i, "Partager"))
}

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun Header(demo: Boolean, onClear: () -> Unit, onQuit: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "Logo", modifier = Modifier.size(56.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Coach Emploi IA", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    if (demo) "Mode démo : exemples sans IA" else "Ton CV, ton diagnostic, ton entretien",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Text("⋮", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Effacer le contenu") }, onClick = { menu = false; onClear() })
                    DropdownMenuItem(text = { Text("Quitter") }, onClick = { menu = false; onQuit() })
                }
            }
        }
    }
}

@Composable
fun App(vm: CoachViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    var askClear by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val ui by vm.ui.collectAsState()
    val history by vm.history.collectAsState()
    val demo by vm.demo.collectAsState()
    val tabs = listOf("CV & Lettre", "Diagnostic", "Entretien", "Historique", "Réglages")
    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text("Effacer le contenu ?") },
            text = { Text("Le profil saisi, la photo et les résultats affichés seront supprimés. L'historique est conservé.") },
            confirmButton = { TextButton({ vm.clearAll(); askClear = false; tab = 0 }) { Text("Effacer") } },
            dismissButton = { TextButton({ askClear = false }) { Text("Annuler") } }
        )
    }
    Scaffold { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Header(demo, onClear = { askClear = true }, onQuit = { ctx.findActivity()?.finishAffinity() })
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }
            if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
            ui.notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(12.dp)) }
            when (tab) {
                0 -> CvScreen(ui, vm, vm.form) { vm.updateForm(it) }
                1 -> DiagScreen(ui, vm, vm.form)
                2 -> InterviewScreen(ui, vm, vm.form)
                3 -> HistoryScreen(history, vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
fun Titre(texte: String) =
    Text(texte, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

@Composable
fun DocActions(ctx: Context, r: CvResponse, lettre: Boolean, loading: Boolean, onRegenerate: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ Exports.export(ctx, r, lettre, "pdf", true) }, Modifier.weight(1f)) { Text("Télécharger PDF") }
        Button({ Exports.export(ctx, r, lettre, "docx", true) }, Modifier.weight(1f)) { Text("Télécharger Word") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ Exports.export(ctx, r, lettre, "pdf", false) }, Modifier.weight(1f)) { Text("Partager") }
        OutlinedButton(
            { Exports.copy(ctx, if (lettre) r.lettreText() else r.cvText()) }, Modifier.weight(1f)
        ) { Text("Copier le texte") }
    }
    if (lettre) {
        OutlinedButton(onRegenerate, Modifier.fillMaxWidth(), enabled = !loading) {
            Text("Régénérer la lettre (autre variante)")
        }
    }
}

@Composable
fun CvScreen(ui: UiState, vm: CoachViewModel, f: Form, onForm: (Form) -> Unit) {
    val ctx = LocalContext.current
    val contrats = listOf("CDI", "CDD", "Stage", "Alternance", "Freelance", "Temps partiel")
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val path = PhotoStore.save(ctx, uri)
            if (path != null) vm.setPhoto(path)
            else Toast.makeText(ctx, "Photo illisible", Toast.LENGTH_SHORT).show()
        }
    }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Seul le nom est obligatoire. Les rubriques laissées vides sont simplement ignorées dans ton CV.",
            style = MaterialTheme.typography.bodySmall
        )
        Titre("Photo (optionnelle)")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (f.photo.isNotBlank()) PhotoCircle(f.photo, 64.dp)
            Button({ picker.launch("image/*") }) { Text(if (f.photo.isBlank()) "Choisir une photo" else "Changer") }
            if (f.photo.isNotBlank()) TextButton({ vm.setPhoto("") }) { Text("Retirer") }
        }

        Titre("Identité et contact")
        Champ("Nom complet (obligatoire)", f.nom) { onForm(f.copy(nom = it)) }
        Champ("Téléphone", f.telephone, KeyboardType.Phone) { onForm(f.copy(telephone = it)) }
        Champ("Email", f.email, KeyboardType.Email) { onForm(f.copy(email = it)) }
        Champ("Adresse / Pays", f.adresse) { onForm(f.copy(adresse = it)) }
        Champ("Ville", f.ville) { onForm(f.copy(ville = it)) }
        Champ("LinkedIn ou portfolio (lien)", f.lien, KeyboardType.Uri) { onForm(f.copy(lien = it)) }

        Titre("Profil")
        Champ("Résumé professionnel (courte accroche)", f.resume, minLines = 3) { onForm(f.copy(resume = it)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = { vm.reformuler("resume", f.resume) },
                enabled = !ui.loading && f.resume.isNotBlank()
            ) { Text("✨ Reformuler (style pro)") }
        }

        Titre("Parcours")
        Champ("Formation / diplômes", f.formation, minLines = 2) { onForm(f.copy(formation = it)) }
        Champ("Expériences (stages, jobs, projets), une par ligne", f.experience, minLines = 3) { onForm(f.copy(experience = it)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = { vm.reformuler("experiences", f.experience) },
                enabled = !ui.loading && f.experience.isNotBlank()
            ) { Text("✨ Reformuler (style pro)") }
        }
        Champ("Certifications et formations complémentaires", f.certifications, minLines = 2) { onForm(f.copy(certifications = it)) }

        Titre("Compétences, langues et loisirs")
        Champ("Compétences (séparées par des virgules)", f.competences, minLines = 2) { onForm(f.copy(competences = it)) }
        Champ("Langues parlées (ex : Français courant, Anglais B2)", f.langues) { onForm(f.copy(langues = it)) }
        Champ("Centres d'intérêt / loisirs", f.interets) { onForm(f.copy(interets = it)) }

        Titre("Objectif")
        Champ("Métier visé", f.metier) { onForm(f.copy(metier = it)) }
        Text("Type de contrat recherché", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            contrats.forEach { c ->
                FilterChip(
                    selected = f.contrat == c,
                    onClick = { onForm(f.copy(contrat = if (f.contrat == c) "" else c)) },
                    label = { Text(c) }
                )
            }
        }
        Champ("Lien ou texte de l'offre d'emploi (optionnel)", f.offre, minLines = 3) { onForm(f.copy(offre = it)) }
        Text(
            "Colle le lien ou le texte de l'annonce : l'IA reprend ses mots-clés dans ton CV et ta lettre.",
            style = MaterialTheme.typography.bodySmall
        )

        Titre("Modèle du CV")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Modeles.forEach { (id, label) ->
                FilterChip(
                    selected = f.modele == id,
                    onClick = { onForm(f.copy(modele = id)) },
                    label = { Text(label) }
                )
            }
        }
        Text("Couleur principale", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Palette.forEach { hex ->
                val sel = f.couleur.equals(hex, ignoreCase = true)
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(colorInt(hex)))
                        .border(
                            if (sel) 3.dp else 1.dp,
                            if (sel) MaterialTheme.colorScheme.onSurface else Color.LightGray,
                            CircleShape
                        )
                        .clickable { onForm(f.copy(couleur = hex)) }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Générer en anglais"); Spacer(Modifier.width(8.dp))
            Switch(f.en, { onForm(f.copy(en = it)) })
        }
        Button(
            onClick = { vm.generateCv(f.toProfil()) },
            enabled = !ui.loading && f.nom.isNotBlank(), modifier = Modifier.fillMaxWidth()
        ) { Text("Générer mon CV et ma lettre") }

        ui.cv?.let { raw ->
            // Le modèle, la couleur et la photo s'appliquent tout de suite, sans régénérer
            val r = raw.copy(modele = f.modele, couleur = f.couleur, photo = f.photo)
            Titre("Mon CV")
            CvDocument(r)
            DocActions(ctx, r, false, ui.loading) { }
            Titre("Ma lettre de motivation")
            LettreDocument(r)
            DocActions(ctx, r, true, ui.loading) { vm.regenerateLettre(f.toProfil()) }
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
        if (f.metier.isBlank()) Text(
            "Remplis d'abord ton profil dans l'onglet « CV & Lettre ».",
            color = MaterialTheme.colorScheme.error
        )
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
    var metier by remember(f.metier) { mutableStateOf(f.metier) }
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
        Text(
            "Rien d'enregistré pour l'instant. Tes CV et diagnostics apparaîtront ici, même hors ligne.",
            Modifier.padding(16.dp)
        )
        return
    }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.id }) { s ->
            Card(onClick = { open = if (open == s.id) null else s.id }, modifier = Modifier.fillMaxWidth()) {
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
fun SettingsScreen(vm: CoachViewModel) {
    val demo by vm.demo.collectAsState()
    var url by remember { mutableStateOf(vm.serverUrl) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Serveur", style = MaterialTheme.typography.titleMedium)
        Text("Colle ici l'adresse de ton serveur, par exemple coach-emploi.onrender.com. Pas besoin de recompiler l'appli.")
        Champ("Adresse du serveur", url) { url = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ vm.saveUrl(url) }) { Text("Enregistrer") }
            OutlinedButton({ vm.saveUrl(url); vm.testConnection() }) { Text("Tester") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mode démo (sans serveur ni IA)", Modifier.weight(1f))
            Switch(demo, { vm.setDemo(it) })
        }
        Text(
            "En mode démo, l'appli répond avec des exemples locaux : pratique pour tester ou présenter sans connexion.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun Champ(
    label: String, value: String,
    keyboard: KeyboardType = KeyboardType.Text, minLines: Int = 1,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), minLines = minLines
    )
}


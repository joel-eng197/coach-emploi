package cm.coachemploi

import kotlinx.coroutines.delay

/** Réponses d'exemple générées localement : l'appli marche sans serveur ni IA (démo, tests). */
object Demo {
    private val questions = listOf(
        "Présentez-vous en une minute.",
        "Pourquoi ce poste vous intéresse-t-il ?",
        "Racontez une difficulté que vous avez surmontée.",
        "Quelle est votre plus grande force ?",
        "Comment travaillez-vous en équipe ?",
        "Où vous voyez-vous dans trois ans ?"
    )

    suspend fun cv(p: Profil): CvResponse {
        delay(900)
        val nom = p.nom.ifBlank { "Candidat(e)" }
        val ville = p.ville.ifBlank { "Cameroun" }
        val a = "À compléter"
        return CvResponse(
            cv = "PROFIL\n$nom, $ville. Candidat(e) motivé(e) visant un poste de ${p.metier_vise}.\n\n" +
                "COMPÉTENCES\n${p.competences.ifBlank { a }}\n\n" +
                "EXPÉRIENCE\n${p.experience.ifBlank { a }}\n\n" +
                "FORMATION\n${p.formation.ifBlank { a }}",
            lettre = "Madame, Monsieur,\n\nJe souhaite postuler au poste de ${p.metier_vise}. " +
                "Ma formation et mes expériences m'ont appris la rigueur, l'esprit d'équipe et le sens du service. " +
                "Je suis prêt(e) à relever de nouveaux défis et à apprendre vite au sein de votre structure.\n\n" +
                "Je reste disponible pour un entretien.\n\nCordialement,\n$nom"
        )
    }

    suspend fun skills(p: Profil): SkillsResponse {
        delay(900)
        val comp = p.competences.split(",", ";", "\n").map { it.trim() }.filter { it.isNotEmpty() }
        return SkillsResponse(
            score = (40 + comp.size * 8).coerceAtMost(85),
            points_forts = comp.take(3).ifEmpty { listOf("Motivation et envie d'apprendre") },
            competences_manquantes = listOf(
                "Maîtrise d'outils numériques", "Communication professionnelle", "Gestion de projet"
            ),
            formations = listOf(
                Formation("freeCodeCamp - Développement web", "Renforcer tes compétences numériques"),
                Formation("Khan Academy - Maths et bases de gestion", "Consolider les bases de gestion")
            ),
            plan_30_jours = listOf(
                "Semaine 1 : mets à jour ton CV et liste tes réalisations",
                "Semaine 2 : suis une formation gratuite en ligne",
                "Semaine 3 : entraîne-toi aux entretiens avec l'appli",
                "Semaine 4 : envoie 10 candidatures ciblées"
            )
        )
    }

    suspend fun interview(history: List<Msg>, metier: String): String {
        delay(700)
        val n = history.count { it.role == "user" }
        return when {
            n == 0 -> "Bonjour ! Entretien pour le poste de $metier.\nQuestion 1/6 : ${questions[0]}"
            n < questions.size ->
                "Retour : réponse claire. Ajoute un exemple concret ou un chiffre.\nQuestion ${n + 1}/6 : ${questions[n]}"
            else -> "Entretien terminé. Note : 7/10.\nConseils : 1) donne des exemples chiffrés, " +
                "2) parle des résultats obtenus, 3) prépare des questions pour le recruteur."
        }
    }
}

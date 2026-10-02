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
        val en = p.langue == "en"
        fun items(s: String) = s.split(",", ";", "\n").map { it.trim() }.filter { it.isNotEmpty() }
        fun lines(s: String) = s.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val nom = p.nom.trim()
        val poste = p.metier_vise.trim()
        val contrat = p.contrat.trim()
        val comp = items(p.competences)
        val contact = listOf(p.telephone, p.email, p.adresse.ifBlank { p.ville }, p.lien)
            .map { it.trim() }.filter { it.isNotEmpty() }
        val resume = when {
            p.resume.isNotBlank() -> p.resume.trim()
            poste.isNotEmpty() && en -> "Motivated candidate seeking a $poste position."
            poste.isNotEmpty() -> "Candidat(e) motivé(e) visant un poste de $poste."
            else -> ""
        }
        val intro = when {
            en && poste.isNotEmpty() -> "I am writing to apply for the position of $poste."
            en -> "I am writing to submit my spontaneous application."
            poste.isNotEmpty() -> "Je me permets de vous adresser ma candidature pour le poste de $poste."
            else -> "Je me permets de vous adresser ma candidature spontanée."
        }
        val contratPhrase = when {
            contrat.isEmpty() -> ""
            en -> " I am looking for a $contrat position."
            else -> " Je recherche un contrat de type $contrat."
        }
        val atouts = comp.take(3).joinToString(", ")
        val p2 = when {
            en && atouts.isNotEmpty() -> "My background has taught me rigor, teamwork and a sense of service, and I have built skills in: $atouts."
            en -> "My background has taught me rigor, teamwork and a sense of service."
            atouts.isNotEmpty() -> "Mon parcours m'a appris la rigueur, l'esprit d'équipe et le sens du service, et j'ai développé des compétences en : $atouts."
            else -> "Mon parcours m'a appris la rigueur, l'esprit d'équipe et le sens du service."
        }
        val p3 = if (en) "Motivated and available, I would be glad to discuss my application in an interview."
        else "Motivé(e) et disponible, je serais heureux(se) de vous exposer ma motivation lors d'un entretien."
        return CvResponse(
            cv = CvDoc(
                nom = nom, titre = poste, contact = contact, resume = resume, competences = comp,
                experiences = lines(p.experience).map { Experience(poste = it) },
                formations = lines(p.formation).map { Etude(diplome = it) },
                certifications = items(p.certifications), langues = items(p.langues_parlees),
                interets = items(p.interets)
            ),
            lettre = LettreDoc(
                objet = when {
                    en && poste.isNotEmpty() -> "Application for the position of $poste"
                    en -> "Spontaneous application"
                    poste.isNotEmpty() -> "Candidature au poste de $poste"
                    else -> "Candidature spontanée"
                },
                destinataire = if (en) "Dear Sir or Madam," else "Madame, Monsieur,",
                paragraphes = listOf(intro + contratPhrase, p2, p3),
                politesse = if (en) "Thank you for considering my application. Yours faithfully,"
                else "Dans l'attente de votre retour, je vous prie d'agréer, Madame, Monsieur, l'expression de mes salutations distinguées.",
                signature = nom
            )
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

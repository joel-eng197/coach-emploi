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

    private fun items(s: String) = s.split(",", ";", "\n").map { it.trim() }.filter { it.isNotEmpty() }
    private fun lines(s: String) = s.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    /** Trois variantes de lettre : la variante change à chaque « Régénérer ». */
    fun lettreDoc(p: Profil, variante: Int): LettreDoc {
        val en = p.langue == "en"
        val poste = p.metier_vise.trim()
        val contrat = p.contrat.trim()
        val atouts = items(p.competences).take(3).joinToString(", ")
        val v = variante % 3
        val intro = when {
            en && v == 0 -> if (poste.isEmpty()) "I am writing to submit my spontaneous application." else "I am writing to apply for the position of $poste."
            en && v == 1 -> "Your organization interests me greatly, and I would like to submit my application."
            en -> "Convinced that my profile matches your needs, I am pleased to apply."
            v == 0 -> if (poste.isEmpty()) "Je me permets de vous adresser ma candidature spontanée." else "Je me permets de vous adresser ma candidature pour le poste de $poste."
            v == 1 -> if (poste.isEmpty()) "Votre structure m'intéresse vivement : je vous adresse ma candidature spontanée." else "Votre offre pour le poste de $poste a retenu toute mon attention : je vous adresse ma candidature."
            else -> "Convaincu(e) que mon profil peut répondre à vos besoins, je vous adresse ma candidature."
        }
        val contratPhrase = when {
            contrat.isEmpty() -> ""
            en -> " I am looking for a $contrat position."
            else -> " Je recherche un contrat de type $contrat."
        }
        val milieu = when {
            en && v == 0 -> "My background has taught me rigor, teamwork and a sense of service."
            en && v == 1 -> "Organized and curious, I adapt quickly and enjoy learning on the job."
            en -> "My experience has given me a taste for work well done and for results."
            v == 0 -> "Mon parcours m'a appris la rigueur, l'esprit d'équipe et le sens du service."
            v == 1 -> "Sérieux(se), organisé(e) et curieux(se), je sais m'adapter vite et apprendre sur le terrain."
            else -> "Mon expérience m'a donné le goût du travail bien fait et le sens des résultats."
        }
        val atoutsPhrase = when {
            atouts.isEmpty() -> ""
            en -> " I have notably built skills in: $atouts."
            else -> " Je maîtrise notamment : $atouts."
        }
        val fin = when {
            en && v == 0 -> "Motivated and available, I would be glad to discuss my application in an interview."
            en && v == 1 -> "I would be delighted to discuss how I can contribute to your team."
            en -> "Available quickly, I remain at your disposal for an interview."
            v == 0 -> "Motivé(e) et disponible, je serais heureux(se) de vous exposer ma motivation lors d'un entretien."
            v == 1 -> "Je serais ravi(e) d'échanger avec vous sur la façon dont je peux contribuer à votre équipe."
            else -> "Disponible rapidement, je me tiens à votre disposition pour un entretien."
        }
        return LettreDoc(
            objet = when {
                en && poste.isNotEmpty() -> "Application for the position of $poste"
                en -> "Spontaneous application"
                poste.isNotEmpty() -> "Candidature au poste de $poste"
                else -> "Candidature spontanée"
            },
            destinataire = if (en) "Dear Sir or Madam," else "Madame, Monsieur,",
            paragraphes = listOf(intro + contratPhrase, milieu + atoutsPhrase, fin),
            politesse = if (en) "Thank you for considering my application. Yours faithfully,"
            else "Dans l'attente de votre retour, je vous prie d'agréer, Madame, Monsieur, l'expression de mes salutations distinguées.",
            signature = p.nom.trim()
        )
    }

    suspend fun cv(p: Profil): CvResponse {
        delay(900)
        val en = p.langue == "en"
        val poste = p.metier_vise.trim()
        val contact = listOf(p.telephone, p.email, p.adresse.ifBlank { p.ville }, p.lien)
            .map { it.trim() }.filter { it.isNotEmpty() }
        val resume = when {
            p.resume.isNotBlank() -> p.resume.trim()
            poste.isNotEmpty() && en -> "Motivated candidate seeking a $poste position."
            poste.isNotEmpty() -> "Candidat(e) motivé(e) visant un poste de $poste."
            else -> ""
        }
        return CvResponse(
            cv = CvDoc(
                nom = p.nom.trim(), titre = poste, contact = contact, resume = resume,
                competences = items(p.competences),
                experiences = lines(p.experience).map { Experience(poste = it) },
                formations = lines(p.formation).map { Etude(diplome = it) },
                certifications = items(p.certifications), langues = items(p.langues_parlees),
                interets = items(p.interets)
            ),
            lettre = lettreDoc(p, 0)
        )
    }

    suspend fun lettre(p: Profil, variante: Int): LettreDoc {
        delay(700)
        return lettreDoc(p, variante)
    }

    /** Correction simple en démo : espaces, majuscules, ponctuation et quelques verbes plus forts. */
    suspend fun reformuler(texte: String): String {
        delay(600)
        val remplacements = listOf(
            "j'ai fait" to "j'ai réalisé", "travaillé sur" to "contribué à",
            "aidé" to "accompagné", "gérer" to "piloter"
        )
        return texte.split("\n").map { l ->
            var s = l.trim().replace(Regex("\\s+"), " ")
            remplacements.forEach { (a, b) -> s = s.replace(a, b, ignoreCase = true) }
            if (s.isNotEmpty()) {
                s = s.replaceFirstChar { it.uppercase() }
                if (!s.endsWith(".") && !s.endsWith("!") && !s.endsWith("?")) s += "."
            }
            s
        }.filter { it.isNotEmpty() }.joinToString("\n")
    }

    suspend fun skills(p: Profil): SkillsResponse {
        delay(900)
        val comp = items(p.competences)
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

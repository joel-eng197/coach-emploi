# Coach Emploi IA

## Backend
cd backend && pip install -r requirements.txt
export ANTHROPIC_API_KEY="sk-ant-..."
uvicorn main:app --host 0.0.0.0 --port 8000
Déploiement (Render) : build `pip install -r requirements.txt`, start `uvicorn main:app --host 0.0.0.0 --port $PORT`, variable ANTHROPIC_API_KEY.

## Appli
1. Dans android/app/build.gradle.kts, remplace BASE_URL par l'URL de ton API.
2. Pousse sur GitHub : l'onglet Actions produit l'APK (artefact coach-emploi-apk).

## Fonctions
CV & lettre (FR/EN), diagnostic de compétences + plan 30 jours, simulateur d'entretien, historique hors ligne (Room), partage.

"""API Coach Emploi IA. Lancer : uvicorn main:app --host 0.0.0.0 --port 8000
Variable d'environnement requise : ANTHROPIC_API_KEY (jamais dans l'appli Android)."""
import json
import os
import time
from collections import defaultdict
from pathlib import Path

import anthropic
from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel

from prompts import CV_SYSTEM, INTERVIEW_SYSTEM, SKILLS_SYSTEM

app = FastAPI(title="Coach Emploi IA")
client = anthropic.Anthropic()
MODEL = os.getenv("MODEL", "claude-sonnet-5-5")
CATALOGUE = (Path(__file__).parent / "data" / "formations.json").read_text(encoding="utf-8")
_hits: dict = defaultdict(list)


def limiter(request: Request, max_par_minute: int = 10) -> None:
    """Limite simple par IP (en mémoire) pour protéger ton crédit API."""
    ip = request.client.host if request.client else "?"
    now = time.time()
    _hits[ip] = [t for t in _hits[ip] if now - t < 60]
    if len(_hits[ip]) >= max_par_minute:
        raise HTTPException(429, "Trop de requêtes, patientez une minute.")
    _hits[ip].append(now)


def ask(system: str, messages: list, max_tokens: int = 1500) -> str:
    try:
        r = client.messages.create(model=MODEL, max_tokens=max_tokens, system=system, messages=messages)
        return r.content[0].text
    except anthropic.APIError:
        raise HTTPException(502, "Service IA indisponible, réessayez.")


def ask_json(system: str, user: str) -> dict:
    txt = ask(system, [{"role": "user", "content": user}], 2000).strip()
    txt = txt.removeprefix("```json").removeprefix("```").removesuffix("```").strip()
    try:
        return json.loads(txt)
    except json.JSONDecodeError:
        raise HTTPException(502, "Réponse IA invalide, réessayez.")


class Profil(BaseModel):
    nom: str = ""
    formation: str = ""
    competences: str = ""
    experience: str = ""
    ville: str = ""
    metier_vise: str = ""
    langue: str = "fr"


class CvRequest(BaseModel):
    profil: Profil


class Msg(BaseModel):
    role: str  # "user" ou "assistant"
    content: str


class InterviewRequest(BaseModel):
    metier: str
    langue: str = "fr"
    historique: list[Msg] = []


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/cv")
def cv(req: CvRequest, request: Request):
    limiter(request)
    return ask_json(CV_SYSTEM, f"Langue : {req.profil.langue}\nProfil : {req.profil.model_dump_json()}")


@app.post("/skills-gap")
def skills_gap(req: CvRequest, request: Request):
    limiter(request)
    return ask_json(
        SKILLS_SYSTEM,
        f"Langue : {req.profil.langue}\nProfil : {req.profil.model_dump_json()}\nCATALOGUE : {CATALOGUE}",
    )


@app.post("/interview")
def interview(req: InterviewRequest, request: Request):
    limiter(request, 30)
    msgs = [m.model_dump() for m in req.historique[-12:]]
    while msgs and msgs[0]["role"] != "user":
        msgs.pop(0)
    if not msgs:
        msgs = [{"role": "user", "content": "Commence l'entretien."}]
    system = INTERVIEW_SYSTEM + f"\nMétier visé : {req.metier}\nLangue : {req.langue}"
    return {"reponse": ask(system, msgs, 400)}

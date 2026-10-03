"""API Coach Emploi IA. Lancer : uvicorn main:app --host 0.0.0.0 --port 8000
Variable d'environnement requise : ANTHROPIC_API_KEY (jamais dans l'appli Android)."""
import ipaddress
import json
import os
import re
import socket
import time
import urllib.request
from html import unescape
from urllib.parse import urlparse
from collections import defaultdict
from pathlib import Path

import anthropic
from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel

from prompts import CV_SYSTEM, INTERVIEW_SYSTEM, LETTRE_SYSTEM, REFORMULER_SYSTEM, SKILLS_SYSTEM

app = FastAPI(title="Coach Emploi IA")
client = anthropic.Anthropic()
MODEL = os.getenv("MODEL", "claude-sonnet-5-5")
CATALOGUE = (Path(__file__).parent / "data" / "formations.json").read_text(encoding="utf-8")
_hits: dict = defaultdict(list)


def _check_url(url: str) -> None:
    """Refuse les adresses locales ou privées (protection contre les abus du lecteur d'annonces)."""
    u = urlparse(url)
    if u.scheme not in ("http", "https") or not u.hostname:
        raise ValueError("url invalide")
    port = u.port or (443 if u.scheme == "https" else 80)
    for info in socket.getaddrinfo(u.hostname, port):
        ip = ipaddress.ip_address(info[4][0])
        if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved or ip.is_multicast:
            raise ValueError("adresse interdite")


class _SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        _check_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def fetch_page(url: str) -> str:
    _check_url(url)
    opener = urllib.request.build_opener(_SafeRedirect())
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (CoachEmploiIA)"})
    with opener.open(req, timeout=8) as r:
        raw = r.read(300_000)
    html = raw.decode("utf-8", errors="ignore")
    html = re.sub(r"(?is)<(script|style|noscript|svg).*?</\1>", " ", html)
    text = unescape(re.sub(r"(?s)<[^>]+>", " ", html))
    text = re.sub(r"\s+", " ", text).strip()
    if len(text) < 200:
        raise ValueError("page vide")
    return text[:6000]


def offre_texte(offre: str):
    """Retourne (texte de l'offre, statut). Le lien est lu par le serveur ; sinon le texte collé est utilisé."""
    offre = (offre or "").strip()
    if not offre:
        return "", ""
    if re.match(r"^https?://\S+$", offre):
        try:
            return fetch_page(offre), "ok"
        except Exception:
            return "", "lien_illisible"
    return offre[:6000], "ok"


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
    txt = ask(system, [{"role": "user", "content": user}], 3500).strip()
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
    telephone: str = ""
    email: str = ""
    adresse: str = ""
    lien: str = ""
    resume: str = ""
    langues_parlees: str = ""
    interets: str = ""
    certifications: str = ""
    contrat: str = ""
    offre: str = ""


class Experience(BaseModel):
    poste: str = ""
    organisation: str = ""
    periode: str = ""
    details: list[str] = []


class Etude(BaseModel):
    diplome: str = ""
    etablissement: str = ""
    periode: str = ""


class CvDoc(BaseModel):
    nom: str = ""
    titre: str = ""
    contact: list[str] = []
    resume: str = ""
    competences: list[str] = []
    experiences: list[Experience] = []
    formations: list[Etude] = []
    certifications: list[str] = []
    langues: list[str] = []
    interets: list[str] = []


class LettreDoc(BaseModel):
    objet: str = ""
    destinataire: str = "Madame, Monsieur,"
    paragraphes: list[str] = []
    politesse: str = ""
    signature: str = ""


class CvOut(BaseModel):
    cv: CvDoc = CvDoc()
    lettre: LettreDoc = LettreDoc()
    offre_status: str = ""


class CvRequest(BaseModel):
    profil: Profil


class LettreRequest(BaseModel):
    profil: Profil
    precedente: LettreDoc | None = None


class ReformulerRequest(BaseModel):
    type: str = "resume"
    texte: str
    langue: str = "fr"
    metier: str = ""


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
    offre, statut = offre_texte(req.profil.offre)
    profil = req.profil.model_copy(update={"offre": ""}).model_dump_json()
    user = f"Langue : {req.profil.langue}\nProfil : {profil}"
    if offre:
        user += f"\nOFFRE D'EMPLOI (adapte le CV et la lettre à cette offre) :\n{offre}"
    data = ask_json(CV_SYSTEM, user)
    try:
        res = CvOut(**data)
        res.offre_status = statut
        return res.model_dump()
    except Exception:
        raise HTTPException(502, "Réponse IA invalide, réessayez.")


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


@app.post("/lettre")
def lettre(req: LettreRequest, request: Request):
    limiter(request)
    offre, _ = offre_texte(req.profil.offre)
    profil = req.profil.model_copy(update={"offre": ""}).model_dump_json()
    user = f"Langue : {req.profil.langue}\nProfil : {profil}"
    if offre:
        user += f"\nOFFRE D'EMPLOI :\n{offre}"
    if req.precedente:
        user += "\nLETTRE PRÉCÉDENTE (à ne pas reproduire) :\n" + "\n".join(req.precedente.paragraphes)
    data = ask_json(LETTRE_SYSTEM, user)
    try:
        return LettreDoc(**data).model_dump()
    except Exception:
        raise HTTPException(502, "Réponse IA invalide, réessayez.")


@app.post("/reformuler")
def reformuler(req: ReformulerRequest, request: Request):
    limiter(request)
    texte = req.texte.strip()[:4000]
    if not texte:
        raise HTTPException(400, "Texte vide.")
    msg = f"Type : {req.type}\nLangue : {req.langue}\nMétier visé : {req.metier}\nTexte :\n{texte}"
    return {"texte": ask(REFORMULER_SYSTEM, [{"role": "user", "content": msg}], 1000).strip()}

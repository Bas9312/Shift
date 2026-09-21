"""SHIFT familiar chat proxy.

Assistants API was sunset by OpenAI on 2026-08-26, so this service is stateless now:
conversation state lives only in the local SQLite database, and every reply is a fresh
Responses API call with the familiar's system prompt plus the tail of that chat's history.

On top of the conversation the service tracks the *bond* between a player and a familiar:

    NEGOTIATING  the familiar has not agreed yet
    CONSENTED    the familiar agreed, the ritual has not been confirmed
    BOUND        a game master confirmed the ritual

Consent is produced here and stored locally. Confirmation of the ritual belongs to the game
server (shift96.ru), because that is where masters work; this service only mirrors it, and
only ever in one direction (once seen confirmed, it stays confirmed). That way a familiar
keeps answering even when the game server is unreachable.

The HTTP contract with the Android client is unchanged.
"""

import asyncio
import json
import logging
import os
import sqlite3
import time
from contextlib import asynccontextmanager
from typing import Dict, List, Optional, Tuple

import httpx
from fastapi import BackgroundTasks, FastAPI, Header, HTTPException, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from openai import AsyncOpenAI

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("gptproxy")

# -------------------- Конфиг --------------------
OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY", "").strip()
if not OPENAI_API_KEY:
    raise RuntimeError("OPENAI_API_KEY не задан")

# Имя модели не зашито в код: у OpenAI они меняются чаще, чем этот файл.
MODEL = os.environ.get("OPENAI_MODEL", "").strip()
if not MODEL:
    raise RuntimeError("OPENAI_MODEL не задан")

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.environ.get("DATA_DIR", "/opt/gptproxy/data")
PROMPTS_DIR = os.environ.get("PROMPTS_DIR", os.path.join(BASE_DIR, "prompts"))
os.makedirs(DATA_DIR, exist_ok=True)
DB_PATH = os.path.join(DATA_DIR, "familiar_chat.sqlite3")

# Сколько последних реплик уходит в модель. В базе при этом хранится всё, всегда.
HISTORY_TURNS = int(os.environ.get("HISTORY_TURNS", "40"))
MAX_OUTPUT_TOKENS = int(os.environ.get("MAX_OUTPUT_TOKENS", "1024"))
MAX_INPUT_CHARS = int(os.environ.get("MAX_INPUT_CHARS", "4000"))
OPENAI_TIMEOUT_S = float(os.environ.get("OPENAI_TIMEOUT_S", "60"))
# Пустой = параметр не передаётся вовсе (модели без reasoning его не принимают).
REASONING_EFFORT = os.environ.get("REASONING_EFFORT", "").strip()

# Игровой сервер: источник истины по подтверждению обряда и адресат уведомлений МГ.
GAME_API_BASE = os.environ.get("GAME_API_BASE", "http://shift96.ru").rstrip("/")
GAME_API_TIMEOUT_S = float(os.environ.get("GAME_API_TIMEOUT_S", "10"))
BOND_TOKEN = os.environ.get("BOND_TOKEN", "").strip()  # X-Bond-Token для /familiars_api/bonds
MG_RECIPIENT_ID = os.environ.get("MG_RECIPIENT_ID", "MG_Bas")
MG_NOTIFY_TAG = os.environ.get("MG_NOTIFY_TAG", "10")  # 10 = «Общие вопросы»
BOND_RECHECK_S = float(os.environ.get("BOND_RECHECK_S", "60"))
OUTBOX_TICK_S = float(os.environ.get("OUTBOX_TICK_S", "30"))

# Опциональный общий секрет. Пустой = проверки нет, поведение как раньше.
PROXY_SECRET = os.environ.get("PROXY_SECRET", "").strip()

# --- Общий тред для familiar_mirror ---
# Зеркало намеренно видит всех игроков как одного собеседника: реплики складываются
# в общую историю без указания автора, и это часть задумки, а не потеря данных.
SHARED_FAMILIAR = "familiar_mirror"
SHARED_USER_ID_FOR_MIRROR = "__GLOBAL__MIRROR__"

PHASE_NEGOTIATING = "NEGOTIATING"
PHASE_CONSENTED = "CONSENTED"
PHASE_BOUND = "BOUND"


def _canon_user_id(user_id: str, familiar: str) -> str:
    """Ключ ИСТОРИИ. Для familiar_mirror общий на всех, иначе — реальный игрок.

    Связь этим ключом не ключуется никогда: история у зеркала общая, а связь личная
    (см. bond_key). Перепутать эти два ключа — значит связать зеркало со всеми сразу.
    """
    if familiar == SHARED_FAMILIAR:
        return SHARED_USER_ID_FOR_MIRROR
    return user_id


def _check_secret(token: Optional[str]):
    if PROXY_SECRET and token != PROXY_SECRET:
        raise HTTPException(401, "unauthorized")


# -------------------- Промпты --------------------
# Общее ядро — prompts/_core.md, характер фамильяра — prompts/<familiar_id>.md.
# Файлы, начинающиеся с подчёркивания, фамильярами не считаются.
_PROMPTS: Dict[str, str] = {}
_CORE: str = ""
_NAMES: Dict[str, str] = {}


def _display_name(card: str, familiar: str) -> str:
    """Имя для уведомления мастерам. Понимает оба формата карточек: строку «Имя: …»
    и markdown-заголовок «# …» — вторым написаны боевые карточки."""
    for line in card.splitlines():
        line = line.strip()
        if line.lower().startswith("имя:"):
            return line.split(":", 1)[1].strip().rstrip(".") or familiar
        if line.startswith("#"):
            name = line.lstrip("#").strip().rstrip(".")
            if name:
                # У части карточек в заголовке приписка: «Пингвин Пинг, дух
                # discovery-протокола» — мастеру достаточно первой части. Но запятая
                # внутри кавычек именем и является: «Компас "Туда, где странно"».
                depth = 0
                for i, ch in enumerate(name):
                    if ch in '"«»':
                        depth = 0 if depth else 1
                    elif ch == "," and not depth:
                        return name[:i].strip()
                return name
    return familiar


def read_prompts() -> Tuple[str, Dict[str, str]]:
    core = ""
    cards: Dict[str, str] = {}
    if not os.path.isdir(PROMPTS_DIR):
        log.error("PROMPTS_DIR %s не существует", PROMPTS_DIR)
        return core, cards
    for name in sorted(os.listdir(PROMPTS_DIR)):
        stem, ext = os.path.splitext(name)
        if ext.lower() not in (".md", ".txt"):
            continue
        path = os.path.join(PROMPTS_DIR, name)
        try:
            with open(path, encoding="utf-8") as fh:
                text = fh.read().strip()
        except OSError as e:
            log.error("не прочитался промпт %s: %s", path, e)
            continue
        if not text:
            log.warning("промпт %s пустой, пропущен", path)
            continue
        if stem == "_core":
            core = text
        elif stem.startswith("_") or stem.lower() == "readme":
            continue  # служебные файлы каталога фамильярами не являются
        else:
            cards[stem] = text
    return core, cards


def reload_prompts() -> int:
    """Перечитывает каталог промптов. Пустой результат не применяется —
    кривой деплой не должен на ходу оставить игру вообще без фамильяров."""
    global _PROMPTS, _CORE, _NAMES
    core, cards = read_prompts()
    if not cards:
        log.error("карточек не найдено в %s, оставляю прежние (%d)", PROMPTS_DIR, len(_PROMPTS))
        return len(_PROMPTS)
    if not core:
        log.warning("нет %s/_core.md — фамильяры поедут без общего ядра", PROMPTS_DIR)
    _CORE = core
    _PROMPTS = cards
    _NAMES = {fid: _display_name(card, fid) for fid, card in cards.items()}
    log.info("загружено карточек: %d (%s), ядро: %s",
             len(cards), ", ".join(sorted(cards)), "есть" if core else "НЕТ")
    return len(cards)


def instructions_for(familiar: str) -> Optional[str]:
    card = _PROMPTS.get(familiar)
    if not card:
        return None
    return f"{_CORE}\n\n{card}" if _CORE else card


# -------------------- БД --------------------
def init_db():
    with sqlite3.connect(DB_PATH) as cx:
        # WAL: чтение истории не блокируется параллельной записью.
        cx.execute("PRAGMA journal_mode=WAL")
        cx.execute("""
        CREATE TABLE IF NOT EXISTS messages(
            id        INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id   TEXT NOT NULL,
            familiar  TEXT NOT NULL,
            role      TEXT CHECK(role IN ('user','assistant')) NOT NULL,
            content   TEXT NOT NULL,
            ts        REAL NOT NULL
        )""")
        cx.execute("CREATE INDEX IF NOT EXISTS idx_messages_chat ON messages(user_id, familiar, id)")
        # Связь. user_id здесь — ВСЕГДА реальный игрок, даже для зеркала.
        cx.execute("""
        CREATE TABLE IF NOT EXISTS bonds(
            user_id           TEXT NOT NULL,
            familiar          TEXT NOT NULL,
            consented_at      REAL,
            pushed_at         REAL,
            notified_at       REAL,
            bond_confirmed_at REAL,
            checked_at        REAL,
            PRIMARY KEY(user_id, familiar)
        )""")
        cx.commit()
    # Таблица threads от Assistants API осталась в базе и больше не используется.
    # Не удаляю её здесь намеренно: снос данных — ручная операция, не побочный эффект старта.


def db_exec(sql: str, params: Tuple = ()) -> int:
    with sqlite3.connect(DB_PATH) as cx:
        cur = cx.execute(sql, params)
        cx.commit()
        return cur.rowcount


def db_q(sql: str, params: Tuple = ()) -> List[Tuple]:
    with sqlite3.connect(DB_PATH) as cx:
        cx.row_factory = sqlite3.Row
        cur = cx.execute(sql, params)
        return [tuple(r) for r in cur.fetchall()]


def save_msg(user_id: str, familiar: str, role: str, content: str):
    db_exec("INSERT INTO messages(user_id,familiar,role,content,ts) VALUES(?,?,?,?,?)",
            (user_id, familiar, role, content, time.time()))


def load_history(user_id: str, familiar: str, limit: int) -> List[Dict]:
    # Сортировка по id, а не по ts: ts — float, две реплики в один тик могут перевернуться.
    rows = db_q("""
      SELECT role, content, ts FROM messages
      WHERE user_id=? AND familiar=? ORDER BY id DESC LIMIT ?
    """, (user_id, familiar, limit))
    return [{"role": r[0], "content": r[1], "ts": r[2]} for r in reversed(rows)]


# -------------------- Связь --------------------
def get_bond(user_id: str, familiar: str) -> Optional[Dict]:
    rows = db_q("""SELECT consented_at, bond_confirmed_at, checked_at
                   FROM bonds WHERE user_id=? AND familiar=?""", (user_id, familiar))
    if not rows:
        return None
    return {"consented_at": rows[0][0], "bond_confirmed_at": rows[0][1], "checked_at": rows[0][2]}


def phase_of(bond: Optional[Dict]) -> str:
    if bond and bond.get("bond_confirmed_at"):
        return PHASE_BOUND
    if bond and bond.get("consented_at"):
        return PHASE_CONSENTED
    return PHASE_NEGOTIATING


def record_consent(user_id: str, familiar: str) -> bool:
    """Фиксирует согласие. Возвращает True только тому вызову, который записал его первым —
    на этом и держится идемпотентность при двух одновременных запросах."""
    changed = db_exec("""
        INSERT INTO bonds(user_id, familiar, consented_at) VALUES(?,?,?)
        ON CONFLICT(user_id, familiar) DO UPDATE SET consented_at=excluded.consented_at
        WHERE bonds.consented_at IS NULL
    """, (user_id, familiar, time.time()))
    return changed == 1


def mark_confirmed(user_id: str, familiar: str, when: float):
    """Подтверждение обряда только проставляется и никогда не снимается: локальная
    копия ходит в одну сторону, поэтому недоступность игрового сервера не может
    «расвязать» уже связанного игрока."""
    db_exec("""
        INSERT INTO bonds(user_id, familiar, bond_confirmed_at, checked_at) VALUES(?,?,?,?)
        ON CONFLICT(user_id, familiar) DO UPDATE SET
            bond_confirmed_at=COALESCE(bonds.bond_confirmed_at, excluded.bond_confirmed_at),
            checked_at=excluded.checked_at
    """, (user_id, familiar, when, time.time()))


def touch_checked(user_id: str, familiar: str):
    db_exec("""
        INSERT INTO bonds(user_id, familiar, checked_at) VALUES(?,?,?)
        ON CONFLICT(user_id, familiar) DO UPDATE SET checked_at=excluded.checked_at
    """, (user_id, familiar, time.time()))


# -------------------- Игровой сервер --------------------
game = httpx.AsyncClient(timeout=GAME_API_TIMEOUT_S)

# Итог отправки: ушло / попробуем позже / больше не пытаться.
SENT, RETRY, DROP = "sent", "retry", "drop"


def _bond_headers() -> Dict[str, str]:
    return {"X-Bond-Token": BOND_TOKEN} if BOND_TOKEN else {}


def _outcome(e: Exception, what: str, user_id: str, familiar: str) -> str:
    """4xx — это наша ошибка (не тот игрок, не тот фамильяр, протухший токен), и повторять
    её бессмысленно: будем долбить сервер вечно. 5xx и сеть — временное, ретраим."""
    status = getattr(getattr(e, "response", None), "status_code", None)
    if status is not None and 400 <= status < 500:
        log.error("%s отвергнут сервером (%s/%s): HTTP %s — повторять не буду",
                  what, user_id, familiar, status)
        return DROP
    log.warning("%s не удался (%s/%s): %s — повторю позже", what, user_id, familiar, e)
    return RETRY


async def fetch_bond_confirmation(user_id: str, familiar: str) -> None:
    """Спрашивает игровой сервер, подтверждён ли обряд. Любая ошибка — не фатальна:
    просто оставляем прежнюю фазу и попробуем в следующий раз."""
    try:
        r = await game.get(f"{GAME_API_BASE}/familiars_api/api/v1/bonds",
                           params={"user_id": user_id, "familiar": familiar},
                           headers=_bond_headers())
        r.raise_for_status()
        data = r.json()
    except Exception as e:
        log.warning("не спросили подтверждение связи у игрового сервера (%s/%s): %s",
                    user_id, familiar, e)
        return
    if data.get("confirmed"):
        mark_confirmed(user_id, familiar, time.time())
        log.info("связь подтверждена: %s / %s", user_id, familiar)
    else:
        touch_checked(user_id, familiar)


async def push_consent(user_id: str, familiar: str) -> str:
    try:
        r = await game.post(f"{GAME_API_BASE}/familiars_api/api/v1/bonds",
                            data={"user_id": user_id, "familiar": familiar},
                            headers=_bond_headers())
        r.raise_for_status()
        return SENT
    except Exception as e:
        return _outcome(e, "пуш согласия", user_id, familiar)


async def notify_mg(user_id: str, familiar: str) -> str:
    """Кладёт уведомление в тот же чат с МГ, куда пишет сам игрок."""
    name = _NAMES.get(familiar, familiar)
    text = (f"[авто] Фамильяр «{name}» согласился на связь с игроком {user_id}. "
            f"Обряд ещё не проведён — подтвердить можно в панели МГ.")
    try:
        r = await game.post(
            f"{GAME_API_BASE}/messages_api/messages",
            headers={"X-User-Id": user_id},
            data={"text": text, "recipient_id": MG_RECIPIENT_ID, "tags": MG_NOTIFY_TAG},
        )
        r.raise_for_status()
        return SENT
    except Exception as e:
        return _outcome(e, "уведомление МГ", user_id, familiar)


async def outbox_tick() -> int:
    """Досылает то, что не ушло с первого раза. Исходящие никогда не должны ронять
    разговор с фамильяром, поэтому они живут отдельно от обработки запроса."""
    pending = db_q("""SELECT user_id, familiar, pushed_at, notified_at FROM bonds
                      WHERE consented_at IS NOT NULL AND (pushed_at IS NULL OR notified_at IS NULL)""")
    done = 0
    for user_id, familiar, pushed_at, notified_at in pending:
        if pushed_at is None:
            # DROP тоже закрывает строку: иначе безнадёжная запись долбилась бы вечно.
            # Сигналом о проблеме остаётся ERROR в логе.
            if await push_consent(user_id, familiar) in (SENT, DROP):
                db_exec("UPDATE bonds SET pushed_at=? WHERE user_id=? AND familiar=?",
                        (time.time(), user_id, familiar))
                done += 1
        if notified_at is None:
            if await notify_mg(user_id, familiar) in (SENT, DROP):
                db_exec("UPDATE bonds SET notified_at=? WHERE user_id=? AND familiar=?",
                        (time.time(), user_id, familiar))
                done += 1
    return done


async def outbox_loop():
    while True:
        try:
            await asyncio.sleep(OUTBOX_TICK_S)
            await outbox_tick()
        except asyncio.CancelledError:
            raise
        except Exception:
            log.exception("outbox: неожиданная ошибка, продолжаю")


# -------------------- OpenAI --------------------
client = AsyncOpenAI(api_key=OPENAI_API_KEY, timeout=OPENAI_TIMEOUT_S, max_retries=2)

REPLY_SCHEMA = {
    "type": "object",
    "properties": {
        "reply": {"type": "string"},
        "consent": {"type": "boolean"},
    },
    "required": ["reply", "consent"],
    "additionalProperties": False,
}


async def ask_familiar(uid: str, familiar: str, instructions: str, phase: str) -> Tuple[str, bool]:
    """Один запрос к модели. Возвращает (реплика, согласие).

    Статическая часть (ядро + карточка) уходит в instructions и не меняется от запроса
    к запросу — это то, что кэшируется. Фаза идёт первым элементом input, история после.
    """
    history = load_history(uid, familiar, HISTORY_TURNS)
    if not history:
        raise RuntimeError("история пуста, нечего отправлять")

    items: List[Dict] = [{"role": "developer", "content": f'<state phase="{phase}" />'}]
    items += [{"role": h["role"], "content": h["content"]} for h in history]

    kwargs = dict(
        model=MODEL,
        instructions=instructions,
        input=items,
        max_output_tokens=MAX_OUTPUT_TOKENS,
        store=False,
        text={"format": {"type": "json_schema", "name": "familiar_reply",
                         "strict": True, "schema": REPLY_SCHEMA}},
    )
    if REASONING_EFFORT:
        kwargs["reasoning"] = {"effort": REASONING_EFFORT}

    resp = await client.responses.create(**kwargs)

    raw = (resp.output_text or "").strip()
    if not raw:
        status = getattr(resp, "status", None)
        details = getattr(resp, "incomplete_details", None)
        raise RuntimeError(f"пустой ответ модели (status={status}, details={details})")

    # Расход пишем в лог: по нему считается стоимость игры и видно, не раздулись ли
    # скрытые рассуждения (они тарифицируются как выход).
    u = getattr(resp, "usage", None)
    if u is not None:
        cached = getattr(getattr(u, "input_tokens_details", None), "cached_tokens", 0)
        reasoning = getattr(getattr(u, "output_tokens_details", None), "reasoning_tokens", 0)
        log.info("токены: вход %s (из них кэш %s), выход %s (из них рассуждения %s)",
                 getattr(u, "input_tokens", "?"), cached,
                 getattr(u, "output_tokens", "?"), reasoning)

    data = json.loads(raw)  # strict-схема гарантирует форму; сломанный JSON — это ошибка
    reply = (data.get("reply") or "").strip()
    if not reply:
        raise RuntimeError("модель вернула пустое поле reply")
    return reply, bool(data.get("consent"))


# -------------------- FastAPI --------------------
@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    reload_prompts()
    log.info("модель: %s, окно истории: %d реплик, база: %s", MODEL, HISTORY_TURNS, DB_PATH)
    log.info("игровой сервер: %s, уведомления -> %s, тег %s",
             GAME_API_BASE, MG_RECIPIENT_ID, MG_NOTIFY_TAG)
    task = asyncio.create_task(outbox_loop())
    try:
        yield
    finally:
        task.cancel()
        await asyncio.gather(task, return_exceptions=True)
        await game.aclose()


app = FastAPI(title="SHIFT GPT Proxy", version="2.1.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], allow_methods=["*"], allow_headers=["*"], allow_credentials=False
)


@app.get("/health")
def health():
    return {"ok": True, "time": time.time(), "model": MODEL,
            "prompts": len(_PROMPTS), "core": bool(_CORE)}


@app.get("/familiars")
def familiars():
    # какие фамильяры реально готовы отвечать, без содержимого промптов
    return JSONResponse({k: {"configured": True} for k in sorted(_PROMPTS)})


@app.post("/admin/reload-prompts")
def admin_reload_prompts(x_shift_token: Optional[str] = Header(None)):
    """Подхватить правки промптов без рестарта — удобно править характер по ходу игры."""
    _check_secret(x_shift_token)
    return {"prompts": reload_prompts()}


@app.post("/admin/outbox-flush")
async def admin_outbox_flush(x_shift_token: Optional[str] = Header(None)):
    _check_secret(x_shift_token)
    return {"sent": await outbox_tick()}


@app.get("/bond")
def bond_state(
    user_id: str = Query(..., min_length=1),
    familiar: str = Query(..., min_length=1),
    x_shift_token: Optional[str] = Header(None),
):
    _check_secret(x_shift_token)
    bond = get_bond(user_id, familiar)
    return {"user_id": user_id, "familiar": familiar, "phase": phase_of(bond),
            "consented_at": (bond or {}).get("consented_at"),
            "bond_confirmed_at": (bond or {}).get("bond_confirmed_at")}


@app.get("/chat/history")
def chat_history(
    user_id: str = Query(..., min_length=1),
    familiar: str = Query(..., min_length=1),
    limit: int = Query(100, ge=1, le=200),
    x_shift_token: Optional[str] = Header(None),
):
    _check_secret(x_shift_token)
    if familiar not in _PROMPTS:
        raise HTTPException(400, f"unknown familiar '{familiar}'")
    uid = _canon_user_id(user_id, familiar)  # <- для зеркала всегда __GLOBAL__MIRROR__
    return JSONResponse({
        "messages": load_history(uid, familiar, limit),
        "phase": phase_of(get_bond(user_id, familiar)),  # связь личная — по реальному id
    })


@app.post("/chat/send")
async def chat_send(req: Request, bg: BackgroundTasks, x_shift_token: Optional[str] = Header(None)):
    _check_secret(x_shift_token)
    body = await req.json()
    user_id = (body.get("user_id") or "").strip()
    familiar = (body.get("familiar") or "").strip()
    text = (body.get("text") or "").strip()
    if not user_id or not familiar or not text:
        raise HTTPException(400, "required: user_id, familiar, text")
    if len(text) > MAX_INPUT_CHARS:
        raise HTTPException(400, f"text too long (max {MAX_INPUT_CHARS} chars)")

    instructions = instructions_for(familiar)
    if not instructions:
        raise HTTPException(400, f"unknown familiar '{familiar}'")

    # !!! ключевая строка — общий user_id для зеркала (только для истории)
    uid = _canon_user_id(user_id, familiar)

    # Связь ключуется по настоящему игроку: у зеркала история общая, а связь личная.
    bond = get_bond(user_id, familiar)
    phase = phase_of(bond)
    if phase != PHASE_BOUND:
        last_check = (bond or {}).get("checked_at") or 0
        if time.time() - last_check > BOND_RECHECK_S:
            await fetch_bond_confirmation(user_id, familiar)
            phase = phase_of(get_bond(user_id, familiar))

    # сохраняем вход до запроса к модели: что игрок сказал, то сказал,
    # даже если ответ не придёт
    save_msg(uid, familiar, "user", text)

    started = time.time()
    try:
        answer, consent = await ask_familiar(uid, familiar, instructions, phase)
    except Exception as e:
        log.exception("familiar=%s uid=%s: запрос к модели не удался", familiar, uid)
        raise HTTPException(502, f"familiar error: {e}")

    save_msg(uid, familiar, "assistant", answer)

    # Согласие принимается только из NEGOTIATING. BOUND словами модели не ставится никогда.
    if consent and phase == PHASE_NEGOTIATING and record_consent(user_id, familiar):
        log.info("СОГЛАСИЕ: %s / %s", user_id, familiar)
        bg.add_task(outbox_tick)
    elif consent:
        log.info("consent=true проигнорирован (фаза %s): %s / %s", phase, user_id, familiar)

    log.info("familiar=%s uid=%s phase=%s ok за %.1fs, %d символов",
             familiar, uid, phase, time.time() - started, len(answer))
    return JSONResponse({"text": answer})

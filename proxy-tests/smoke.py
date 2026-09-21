"""Smoke test for gptFamiliarProxy.py with a stubbed OpenAI SDK and a fake game server."""
import importlib.util, json, os, shutil, sys, tempfile

PROXY_PATH = os.environ.get("PROXY_PATH") or os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "gptFamiliarProxy.py")
tmp = tempfile.mkdtemp(prefix="proxytest-")
prompts = os.path.join(tmp, "prompts")
os.makedirs(prompts)
open(os.path.join(prompts, "_core.md"), "w", encoding="utf-8").write("ОБЩЕЕ ЯДРО ПРАВИЛ")
open(os.path.join(prompts, "familiar_mirror.md"), "w", encoding="utf-8").write("Имя: Зеркало.\nТы зеркало.")
open(os.path.join(prompts, "familiar_fox.md"), "w", encoding="utf-8").write("Имя: Лис.\nТы лис.")
open(os.path.join(prompts, "_notes.md"), "w", encoding="utf-8").write("служебное, не фамильяр")
open(os.path.join(prompts, "empty.md"), "w").write("   ")

os.environ.update(OPENAI_API_KEY="test", OPENAI_MODEL="test-model", DATA_DIR=tmp,
                  PROMPTS_DIR=prompts, HISTORY_TURNS="4", OUTBOX_TICK_S="3600",
                  BOND_RECHECK_S="0", MG_RECIPIENT_ID="MG_Bas", MG_NOTIFY_TAG="10",
                  BOND_TOKEN="bondtok")

spec = importlib.util.spec_from_file_location("proxy", PROXY_PATH)
proxy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(proxy)
import openai
from fastapi.testclient import TestClient


# ---- фальшивый игровой сервер -------------------------------------------------
class HTTPError(Exception):
    """Похоже на httpx.HTTPStatusError настолько, насколько нужно прокси: есть .response."""
    def __init__(self, status):
        super().__init__(f"HTTP {status}")
        self.response = type("R", (), {"status_code": status})()


class FakeResp:
    def __init__(self, payload, status=200):
        self._p, self.status_code = payload, status
    def raise_for_status(self):
        if self.status_code >= 400:
            raise HTTPError(self.status_code)
    def json(self):
        return self._p


class FakeGame:
    def __init__(self):
        self.gets, self.posts = [], []
        self.confirmed = set()          # (user_id, familiar)
        self.down = False
        self.status = 200               # ответ на POST-ы
    async def get(self, url, params=None, headers=None, **kw):
        self.gets.append((url, params, headers or {}))
        if self.down:
            raise RuntimeError("game server down")
        key = (params["user_id"], params["familiar"])
        return FakeResp({"confirmed": key in self.confirmed})
    async def post(self, url, headers=None, data=None, **kw):
        self.posts.append((url, headers or {}, data or {}))
        if self.down:
            raise RuntimeError("game server down")
        return FakeResp({"ok": True}, self.status)
    async def aclose(self):
        pass


game = FakeGame()
proxy.game = game

fails = []
def check(name, cond, extra=""):
    print(("  ok  " if cond else "  FAIL") + f"  {name}" + (f"   [{extra}]" if extra and not cond else ""))
    if not cond:
        fails.append(name)

def say(c, uid, fam, text, reply="ответ фамильяра", consent=False):
    openai.NEXT[0] = (reply, consent)
    return c.post("/chat/send", json={"user_id": uid, "familiar": fam, "text": text})

def phase(c, uid, fam):
    return c.get("/bond", params={"user_id": uid, "familiar": fam}).json()["phase"]


with TestClient(proxy.app) as c:
    print("\n-- промпты и сборка запроса")
    h = c.get("/health").json()
    check("ядро загружено, карточек 2 (_notes и пустой не в счёт)",
          h["core"] and h["prompts"] == 2, h)
    check("/familiars — только фамильяры с карточками",
          sorted(c.get("/familiars").json()) == ["familiar_fox", "familiar_mirror"])
    say(c, "Bas", "familiar_fox", "привет")
    call = openai.CALLS[-1]
    check("instructions = ядро + карточка",
          call["instructions"] == "ОБЩЕЕ ЯДРО ПРАВИЛ\n\nИмя: Лис.\nТы лис.", call["instructions"])
    check("фаза идёт первым элементом input, ролью developer",
          call["input"][0] == {"role": "developer", "content": '<state phase="NEGOTIATING" />'},
          call["input"][0])
    check("история после фазы, реплика игрока последняя",
          call["input"][1] == {"role": "user", "content": "привет"}, call["input"][1])
    check("строгая json-схема на {reply, consent}",
          call["text"]["format"]["type"] == "json_schema"
          and call["text"]["format"]["strict"] is True
          and sorted(call["text"]["format"]["schema"]["required"]) == ["consent", "reply"],
          call.get("text"))
    blob = json.dumps(call, ensure_ascii=False, default=str)
    check("модели не уходят служебные поля игры",
          not any(k in blob for k in ("bondConfirmed", "abilityAvailable", "storedItems",
                                      "masterResult", "bond_confirmed_at", "consented_at")))
    check("в историю попал только reply",
          c.get("/chat/history", params={"user_id": "Bas", "familiar": "familiar_fox"})
           .json()["messages"][-1]["content"] == "ответ фамильяра")

    print("\n-- фазы")
    check("новая пара игрок/фамильяр = NEGOTIATING", phase(c, "Bas", "familiar_fox") == "NEGOTIATING")
    say(c, "Bas", "familiar_fox", "будешь со мной?", reply="Ладно, согласен.", consent=True)
    check("consent=true из NEGOTIATING -> CONSENTED", phase(c, "Bas", "familiar_fox") == "CONSENTED")
    check("следующий запрос видит фазу CONSENTED",
          (say(c, "Bas", "familiar_fox", "ну как"),
           openai.CALLS[-1]["input"][0]["content"])[1] == '<state phase="CONSENTED" />')
    check("модель не может объявить BOUND сама",
          (say(c, "Bas", "familiar_fox", "мы связаны?", reply="Мы связаны!", consent=True),
           phase(c, "Bas", "familiar_fox"))[1] == "CONSENTED")
    game.confirmed.add(("Bas", "familiar_fox"))
    say(c, "Bas", "familiar_fox", "а теперь?")
    check("подтверждение обряда с игрового сервера -> BOUND", phase(c, "Bas", "familiar_fox") == "BOUND")
    check("BOUND виден модели",
          openai.CALLS[-1]["input"][0]["content"] == '<state phase="BOUND" />'
          or say(c, "Bas", "familiar_fox", "ещё") is not None
          and openai.CALLS[-1]["input"][0]["content"] == '<state phase="BOUND" />')

    print("\n-- подтверждение кэшируется в одну сторону")
    game.confirmed.discard(("Bas", "familiar_fox"))
    say(c, "Bas", "familiar_fox", "всё ещё?")
    check("сервер «передумал» — связь не снимается", phase(c, "Bas", "familiar_fox") == "BOUND")
    n_before = len(game.gets)
    say(c, "Bas", "familiar_fox", "и ещё раз")
    check("подтверждённую связь больше не переспрашиваем", len(game.gets) == n_before, len(game.gets))

    print("\n-- игровой сервер лежит")
    game.down = True
    r = say(c, "Bas", "familiar_mirror", "ты тут?")
    check("фамильяр отвечает даже когда игровой сервер недоступен", r.status_code == 200, r.text)
    check("фаза при недоступном сервере остаётся прежней", phase(c, "Bas", "familiar_mirror") == "NEGOTIATING")
    game.down = False

    print("\n-- зеркало: история общая, связь личная")
    say(c, "Tari", "familiar_mirror", "я Тари")
    m_bas = c.get("/chat/history", params={"user_id": "Bas", "familiar": "familiar_mirror"}).json()
    m_tari = c.get("/chat/history", params={"user_id": "Tari", "familiar": "familiar_mirror"}).json()
    check("оба игрока видят один лог зеркала", m_bas["messages"] == m_tari["messages"])
    say(c, "Bas", "familiar_mirror", "свяжемся?", reply="Согласен.", consent=True)
    check("согласие зеркала с Басом -> CONSENTED у Баса", phase(c, "Bas", "familiar_mirror") == "CONSENTED")
    check("а у Тари зеркало по-прежнему NEGOTIATING (главная ловушка)",
          phase(c, "Tari", "familiar_mirror") == "NEGOTIATING", phase(c, "Tari", "familiar_mirror"))
    check("фаза в истории считается по реальному игроку",
          c.get("/chat/history", params={"user_id": "Tari", "familiar": "familiar_mirror"})
           .json()["phase"] == "NEGOTIATING")

    print("\n-- повторное согласие и посторонние фазы")
    posts_before = len([p for p in game.posts if "messages" in p[0]])
    say(c, "Bas", "familiar_mirror", "точно?", reply="Да, согласен.", consent=True)
    check("повторный consent=true ничего не меняет", phase(c, "Bas", "familiar_mirror") == "CONSENTED")
    check("и не шлёт второе уведомление МГ",
          len([p for p in game.posts if "messages" in p[0]]) == posts_before,
          len([p for p in game.posts if "messages" in p[0]]))

    print("\n-- уведомление МГ")
    notes = [p for p in game.posts if "messages_api" in p[0]]
    check("уведомление ушло в messages_api", len(notes) >= 1, len(notes))
    url, headers, data = notes[0]
    check("отправитель — игрок", headers.get("X-User-Id") == "Bas", headers)
    check("адресат — MG_Bas", data.get("recipient_id") == "MG_Bas", data)
    check("тег 10 (Общие вопросы)", data.get("tags") == "10", data)
    check("в тексте отображаемое имя фамильяра, не id",
          "Лис" in data.get("text", "") and "familiar_fox" not in data.get("text", ""), data.get("text"))
    check("текст помечен как машинный", data.get("text", "").startswith("[авто]"), data.get("text"))
    pushes = [p for p in game.posts if p[0].endswith("/bonds")]
    check("согласие отдано игровому серверу", len(pushes) >= 1, len(pushes))

    print("\n-- outbox переживает недоступность")
    game.down = True
    say(c, "Tari", "familiar_fox", "а ты со мной?", reply="Согласен.", consent=True)
    check("согласие записано локально несмотря на лежащий сервер",
          phase(c, "Tari", "familiar_fox") == "CONSENTED")
    pending = proxy.db_q("SELECT user_id FROM bonds WHERE consented_at IS NOT NULL AND notified_at IS NULL")
    check("недосланное помечено как недосланное", ("Tari",) in pending, pending)
    game.down = False
    sent = c.post("/admin/outbox-flush").json()["sent"]
    check("после починки outbox дослал", sent == 2, sent)
    pending = proxy.db_q("SELECT user_id FROM bonds WHERE consented_at IS NOT NULL AND notified_at IS NULL")
    check("очередь пуста", pending == [], pending)
    check("повторный flush не шлёт дублей", c.post("/admin/outbox-flush").json()["sent"] == 0)

    print("\n-- токен к игровому серверу")
    bond_posts = [p for p in game.posts if p[0].endswith("/bonds")]
    check("X-Bond-Token уходит с пушем согласия",
          bond_posts and bond_posts[-1][1].get("X-Bond-Token") == "bondtok", bond_posts[-1][1])
    check("X-Bond-Token уходит и с запросом подтверждения",
          game.gets[-1][2].get("X-Bond-Token") == "bondtok", game.gets[-1][2])
    check("в чат с МГ токен связи не утекает",
          all("X-Bond-Token" not in p[1] for p in game.posts if "messages_api" in p[0]))

    print("\n-- безнадёжную отправку не долбим вечно")
    game.status = 400
    openai.NEXT[0] = ("Согласен.", True)
    c.post("/chat/send", json={"user_id": "Mira", "familiar": "familiar_fox", "text": "будешь?"})
    check("согласие всё равно записано локально", phase(c, "Mira", "familiar_fox") == "CONSENTED")
    n_after_first = len([p for p in game.posts if p[0].endswith("/bonds")])
    check("после 4xx строка закрыта и не висит в очереди",
          proxy.db_q("SELECT 1 FROM bonds WHERE user_id='Mira' AND pushed_at IS NULL") == [])
    c.post("/admin/outbox-flush")
    check("повторный flush не стучится снова",
          len([p for p in game.posts if p[0].endswith("/bonds")]) == n_after_first)
    game.status = 200

    print("\n-- раздельность историй")
    check("история Баса с лисом не смешалась с зеркалом",
          all("Тари" not in m["content"] for m in
              c.get("/chat/history", params={"user_id": "Bas", "familiar": "familiar_fox"}).json()["messages"]))
    check("окно истории = HISTORY_TURNS",
          len(openai.CALLS[-1]["input"]) - 1 <= 4, len(openai.CALLS[-1]["input"]) - 1)

    print("\n-- ошибки не двигают состояние")
    before = phase(c, "Tari", "familiar_mirror")
    openai.RAISE[0] = RuntimeError("upstream on fire")
    check("ошибка модели -> 502",
          c.post("/chat/send", json={"user_id": "Tari", "familiar": "familiar_mirror",
                                     "text": "падай"}).status_code == 502)
    openai.RAISE[0] = None
    openai.RAW[0] = "это не json"
    check("невалидный JSON -> 502",
          c.post("/chat/send", json={"user_id": "Tari", "familiar": "familiar_mirror",
                                     "text": "кривой"}).status_code == 502)
    openai.RAW[0] = json.dumps({"reply": "  ", "consent": True})
    check("пустой reply при consent=true -> 502",
          c.post("/chat/send", json={"user_id": "Tari", "familiar": "familiar_mirror",
                                     "text": "пусто"}).status_code == 502)
    openai.RAW[0] = None
    check("после всех ошибок фаза не изменилась", phase(c, "Tari", "familiar_mirror") == before)
    check("согласие при ошибке не записалось",
          proxy.get_bond("Tari", "familiar_mirror") is None
          or proxy.get_bond("Tari", "familiar_mirror")["consented_at"] is None)

    print("\n-- валидация входа")
    check("неизвестный фамильяр -> 400",
          c.post("/chat/send", json={"user_id": "Bas", "familiar": "_notes", "text": "x"}).status_code == 400)
    check("пустой текст -> 400",
          c.post("/chat/send", json={"user_id": "Bas", "familiar": "familiar_fox", "text": " "}).status_code == 400)
    check("слишком длинный текст -> 400",
          c.post("/chat/send", json={"user_id": "Bas", "familiar": "familiar_fox",
                                     "text": "x" * 4001}).status_code == 400)

    print("\n-- перезагрузка промптов")
    open(os.path.join(prompts, "familiar_fox.md"), "w", encoding="utf-8").write("Имя: Другой лис.\nТы другой лис.")
    c.post("/admin/reload-prompts")
    say(c, "Bas", "familiar_fox", "ты кто")
    check("правка карточки подхватилась без рестарта",
          "Ты другой лис." in openai.CALLS[-1]["instructions"])
    for f in os.listdir(prompts):
        os.remove(os.path.join(prompts, f))
    check("пустой каталог отвергнут, старые карточки живы",
          c.post("/admin/reload-prompts").json()["prompts"] == 2)
    check("фамильяр отвечает после кривого деплоя",
          say(c, "Bas", "familiar_fox", "жив?").status_code == 200)

print("\n-- согласие переживает рестарт сервиса")
open(os.path.join(prompts, "_core.md"), "w", encoding="utf-8").write("ОБЩЕЕ ЯДРО ПРАВИЛ")
open(os.path.join(prompts, "familiar_fox.md"), "w", encoding="utf-8").write("Имя: Лис.\nТы лис.")
open(os.path.join(prompts, "familiar_mirror.md"), "w", encoding="utf-8").write("Имя: Зеркало.\nТы зеркало.")
proxy2 = importlib.util.module_from_spec(spec); spec.loader.exec_module(proxy2)
proxy2.game = game
with TestClient(proxy2.app) as c2:
    check("CONSENTED сохранилось после рестарта",
          c2.get("/bond", params={"user_id": "Bas", "familiar": "familiar_mirror"}).json()["phase"] == "CONSENTED")
    check("BOUND сохранилось после рестарта",
          c2.get("/bond", params={"user_id": "Bas", "familiar": "familiar_fox"}).json()["phase"] == "BOUND")
    check("история тоже на месте",
          len(c2.get("/chat/history", params={"user_id": "Bas", "familiar": "familiar_fox"}).json()["messages"]) > 0)

shutil.rmtree(tmp, ignore_errors=True)
print("\n" + ("ВСЁ ЗЕЛЁНОЕ" if not fails else f"ПРОВАЛОВ {len(fails)}: {fails}"))
sys.exit(1 if fails else 0)

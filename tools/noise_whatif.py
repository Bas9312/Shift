#!/usr/bin/env python3
"""
Что было бы со всеми игроками прошлой игры при разных настройках шума.

Берёт настоящие команды всех четырёх шумомантов (18–21 сентября 2025) и прогоняет их
через серверную формулу. Нужен, чтобы подбирать баланс не на выдуманных сценариях,
а на том, как люди играли в реальности.

Запуск: python3 tools/noise_whatif.py
"""
import json, math, collections, datetime, os

DATA = os.path.join(os.path.dirname(__file__), "..", "_local", "noise_history.json")
FALLBACK = "/tmp/claude-1000/-home-bas-Shift/fa11d156-88dd-46c5-93b4-8a6538ce7e8a/scratchpad/all_players.json"

PRICES = {"NET.SEARCH":2,"TRACE.USER":2,"TRACE.PHONE":2,"INVENTORY.STORE":2,"INVENTORY.RETRIEVE":1,
          "CAMERA.FIND":1,"CAMERA.ERASE":2,"DEVICE.UNLOCK":1,"DEVICE.OFF":1,"DEVICE.CONTROL":2,
          "HUMAN.UPLOAD":4,"HUMAN.EXIT":0,"CROSS.LINK":0,"CROSS.RETRIEVE":1,
          "DEEP_DIVE.START":0,"DEEP_DIVE.END":3,"USER.FORMAT":-10,"SHIFT.PROXY.DEPLOY":2}

def load():
    path = DATA if os.path.exists(DATA) else FALLBACK
    raw = json.load(open(path, encoding="utf-8"))
    out = {}
    for who, items in raw.items():
        out[who] = [(datetime.datetime.strptime(t, "%Y-%m-%d %H:%M"), cmd) for t, cmd in items]
    return out

class Cfg:
    def __init__(self, **kw):
        self.calm_h, self.spam_h = kw.get("calm_h",1.0), kw.get("spam_h",4.0)
        self.calm_m, self.spam_m = kw.get("calm_m",0.7), kw.get("spam_m",1.5)
        self.lsoft, self.steep   = kw.get("lsoft",6.0), kw.get("steep",2.0)
        self.boost               = kw.get("boost",1.4)
        self.pct, self.flat      = kw.get("pct",0.20), kw.get("flat",0.2)
        self.memory              = kw.get("memory",20.0)
        self.label               = kw.get("label","")

    def mult(self, per_hour):
        if per_hour <= self.calm_h: return self.calm_m
        if per_hour >= self.spam_h: return self.spam_m
        return self.calm_m + (self.spam_m-self.calm_m)*((per_hour-self.calm_h)/(self.spam_h-self.calm_h))
    def soft(self, L): return 1/(1+math.exp((L-self.lsoft)/self.steep))
    def decay(self, L): return max(0.0, L - L*self.pct - self.flat)

def run_player(events, cfg):
    """Возвращает (пик, сколько раз доходил до каждого уровня, минуты на уровнях)."""
    L, ema, last = 0.0, 0.0, None
    peak, reached = 0.0, collections.Counter()
    minutes = collections.Counter()
    was = 0
    for ts, cmd in events:
        if last is not None:
            gap = (ts-last).total_seconds()/60.0
            # часовые кроны за время паузы
            steps = int(gap // 60)
            for _ in range(min(steps, 48)):
                lv = int(L//2); minutes[lv] += 60
                L = cfg.decay(L)
            rest = gap - steps*60
            if rest > 0: minutes[int(L//2)] += int(rest)
            dt = max(1/60, gap)
            inst = 1.0/dt
            d = math.exp(-dt/cfg.memory)
            ema = d*ema + (1-d)*inst
        else:
            ema = 0.0
        price = PRICES.get(cmd, 1)
        if price > 0:
            L = min(10.0, L + price*cfg.mult(ema*60)*cfg.soft(L)*cfg.boost)
        elif price < 0:
            L = max(0.0, L + price*2)
        lv = int(L//2)
        if lv > was:
            for x in range(was+1, lv+1): reached[x] += 1
        was = lv
        peak = max(peak, L)
        last = ts
    return peak, reached, minutes

def report(cfg, players):
    print(f"\n=== {cfg.label} ===")
    print(f"{'игрок':<9}{'команд':>7}{'пик':>8}{'уровень':>9}   сколько раз доходил до уровня")
    print("-"*72)
    for who, ev in sorted(players.items(), key=lambda kv: -len(kv[1])):
        peak, reached, _ = run_player(ev, cfg)
        hits = " ".join(f"ур.{lv}×{n}" for lv, n in sorted(reached.items()) if lv >= 2)
        print(f"{who:<9}{len(ev):>7}{peak:>8.2f}{int(peak//2):>9}   {hits or '—'}")

if __name__ == "__main__":
    players = load()
    report(Cfg(label="ТЕКУЩИЕ настройки"), players)

#!/usr/bin/env python3
"""
Прогон реального игрового дня по текущим настройкам шума.

Берёт настоящую последовательность команд (lina, 19 сентября 2025 — самый плотный день
прошлой игры) и считает, что было бы сегодня. Логика повторяет сервер построчно:
EMA темпа, множитель активности, софткап, часовой пропорциональный спад.

Добавлено то, чего в данных нет: игрок сбрасывает шум, как только доходит до 2-го уровня,
и дальше по кулдаунам (USER.REBOOT.END — час, USER.UPGRADE.END — четыре часа).
"""
import math

# --- настройки сервера (noize_api/tuning_constants.php + tuning.php) ---
CALM_RATE, SPAM_RATE = 0.0167, 0.0667      # команд в минуту
CALM_MULT, SPAM_MULT = 0.7, 1.5
RATE_MEMORY_MIN = 20.0                     # за сколько минут забывается «серия»
LSOFT, STEEP, BOOST = 6.0, 2.0, 1.4
DECAY_PCT, DECAY_FLAT = 0.20, 0.2          # раз в час
LMAX = 10.0

PRICES = {"NET.SEARCH": 2, "TRACE.USER": 2, "INVENTORY.STORE": 2,
          "CAMERA.FIND": 1, "DEEP_DIVE.START": 0}
RESETS = {"USER.REBOOT.END": (-1, 60), "USER.UPGRADE.END": (-2, 240)}  # (цена UI, кулдаун мин)

DAY = [  # реальный лог: (минуты от полуночи, команда)
    (12*60+54, "NET.SEARCH"), (13*60+18, "NET.SEARCH"), (13*60+20, "TRACE.USER"),
    (14*60+40, "NET.SEARCH"), (14*60+44, "NET.SEARCH"), (14*60+48, "NET.SEARCH"),
    (16*60+35, "NET.SEARCH"), (17*60+2, "INVENTORY.STORE"), (19*60+4, "DEEP_DIVE.START"),
    (20*60+6, "NET.SEARCH"), (20*60+7, "CAMERA.FIND"), (20*60+42, "INVENTORY.STORE"),
]

def act_mult(rate):
    if rate <= CALM_RATE: return CALM_MULT
    if rate >= SPAM_RATE: return SPAM_MULT
    return CALM_MULT + (SPAM_MULT-CALM_MULT)*((rate-CALM_RATE)/(SPAM_RATE-CALM_RATE))

def softcap(L): return 1.0/(1.0+math.exp((L-LSOFT)/STEEP))
def level(raw): return int(raw/2.0)

def run(with_resets=True):
    L, rate_ema, last_min = 0.0, 0.0, None
    last_use = {k: -10**6 for k in RESETS}
    events, trace = [], [(DAY[0][0]-30, 0.0)]

    def decay_until(minute):
        """часовой крон: применяем все границы часа, которые прошли"""
        nonlocal L
        if last_min is None: return
        h = (last_min//60)+1
        while h*60 <= minute:
            before = L
            L = max(0.0, L - L*DECAY_PCT - DECAY_FLAT)
            if before > 0.01:
                trace.append((h*60, L))
                events.append((h*60, "— крон —", before, L, ""))
            h += 1

    for minute, cmd in DAY:
        decay_until(minute)
        # темп, как считает сервер: вес прошлого падает экспоненциально с паузой
        if last_min is not None:
            dt_min = max(1/60, minute-last_min)
            rate_inst = 1.0/dt_min
            decay = math.exp(-dt_min/RATE_MEMORY_MIN)
            rate_ema = decay*rate_ema + (1-decay)*rate_inst
        else:
            rate_ema = 0.0
        price = PRICES.get(cmd, 0)
        before = L
        if price:
            L = min(LMAX, L + price*act_mult(rate_ema)*softcap(L)*BOOST)
        note = f"темп {rate_ema*60:.1f}/ч, ×{act_mult(rate_ema):.2f}" if price else "шума не даёт"
        events.append((minute, cmd, before, L, note))
        trace.append((minute, L))
        last_min = minute

        # сброс, если добрался до 2-го уровня и кулдаун позволяет
        if with_resets and L >= 4.0:
            for rcmd, (ui, cd) in RESETS.items():
                if minute - last_use[rcmd] >= cd:
                    before = L
                    L = max(0.0, L + ui*2)          # отрицательные удваиваются сервером
                    last_use[rcmd] = minute
                    events.append((minute, rcmd, before, L, f"сброс, кулдаун {cd//60} ч"))
                    trace.append((minute, L))
                    break
    decay_until(23*60+59)
    return events, trace

def fmt(m): return f"{m//60:02d}:{m%60:02d}"

if __name__ == "__main__":
    for with_resets in (False, True):
        title = "СО СБРОСАМИ" if with_resets else "БЕЗ СБРОСОВ (как было в реальном логе)"
        events, trace = run(with_resets)
        peak = max(v for _, v in trace)
        print(f"\n=== {title} ===")
        print(f"{'время':<8}{'команда':<20}{'шум до':>9}{'шум после':>11}{'ур.':>5}   примечание")
        print("-"*88)
        for m, cmd, b, a, note in events:
            print(f"{fmt(m):<8}{cmd:<20}{b:9.2f}{a:11.2f}{level(a):>5}   {note}")
        print(f"\nпик за день: {peak:.2f} = уровень {level(peak)}")
        by_level = {}
        for i in range(len(trace)-1):
            lv = level(trace[i][1]); by_level[lv] = by_level.get(lv, 0) + (trace[i+1][0]-trace[i][0])
        total = sum(by_level.values())
        print("сколько времени провёл на каждом уровне:")
        for lv in sorted(by_level):
            mins = by_level[lv]
            print(f"   ур.{lv}: {mins//60} ч {mins%60:02d} мин  ({100*mins/total:.0f} %)")


def svg(path="noise_day.svg"):
    """Рисует динамику шума за день. Без библиотек — обычный SVG руками."""
    W, H = 1100, 520
    ML, MR, MT, MB = 70, 210, 50, 60
    PW, PH = W-ML-MR, H-MT-MB
    t0, t1 = 12*60+30, 23*60+30

    def x(m): return ML + PW*(m-t0)/(t1-t0)
    def y(v): return MT + PH*(1 - v/10.0)

    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
           f'<rect width="{W}" height="{H}" fill="#12141a"/>',
           '<style>text{font-family:DejaVu Sans,Arial,sans-serif;fill:#c8d0dc}'
           '.t{font-size:12px}.s{font-size:11px;fill:#7f8a9a}.h{font-size:16px;fill:#e8edf5}</style>']
    out.append(f'<text class="h" x="{ML}" y="28">Шум за 19 сентября — реальные команды lina по нынешним настройкам</text>')

    # полосы уровней
    colors = ["#1b2430","#1b2a30","#2a2a1b","#33291b","#3a1f1f"]
    for lvl in range(5):
        yt, yb = y((lvl+1)*2), y(lvl*2)
        out.append(f'<rect x="{ML}" y="{yt:.1f}" width="{PW}" height="{yb-yt:.1f}" fill="{colors[lvl]}"/>')
        out.append(f'<line x1="{ML}" y1="{yb:.1f}" x2="{ML+PW}" y2="{yb:.1f}" stroke="#2c3542"/>')
        out.append(f'<text class="s" x="{ML-10}" y="{(yt+yb)/2+4:.1f}" text-anchor="end">ур. {lvl}</text>')
    out.append(f'<text class="s" x="{ML-10}" y="{y(10)+4:.1f}" text-anchor="end">ур. 5</text>')
    out.append(f'<line x1="{ML}" y1="{y(6):.1f}" x2="{ML+PW}" y2="{y(6):.1f}" stroke="#c0603c" stroke-dasharray="5 4"/>')
    out.append(f'<text class="s" x="{ML+PW-4}" y="{y(6)-6:.1f}" text-anchor="end" fill="#c0603c">порог эффектов</text>')

    # часовые метки
    for h in range(13, 24):
        out.append(f'<line x1="{x(h*60):.1f}" y1="{MT}" x2="{x(h*60):.1f}" y2="{MT+PH}" stroke="#222a35"/>')
        out.append(f'<text class="s" x="{x(h*60):.1f}" y="{MT+PH+18}" text-anchor="middle">{h}:00</text>')

    for with_resets, color, label in ((False, "#e0574a", "как играла на самом деле"),
                                      (True,  "#4ea8de", "если бы сбрасывала шум")):
        _, trace = run(with_resets)
        pts = " ".join(f"{x(m):.1f},{y(v):.1f}" for m, v in trace if t0 <= m <= t1)
        out.append(f'<polyline points="{pts}" fill="none" stroke="{color}" stroke-width="2.5"/>')
        for m, v in trace:
            if t0 <= m <= t1:
                out.append(f'<circle cx="{x(m):.1f}" cy="{y(v):.1f}" r="2.6" fill="{color}"/>')
        peak = max(v for _, v in trace)
        yy = MT+16 if not with_resets else MT+40
        out.append(f'<rect x="{ML+PW+16}" y="{yy-10}" width="16" height="4" fill="{color}"/>')
        out.append(f'<text class="t" x="{ML+PW+38}" y="{yy-4}">{label}</text>')
        out.append(f'<text class="s" x="{ML+PW+38}" y="{yy+12}">пик {peak:.1f} — уровень {level(peak)}</text>')

    out.append(f'<text class="s" x="{ML}" y="{H-16}">Точки — команды и часовые срабатывания крона. '
               f'Красная линия: 12 реальных команд без сбросов. Синяя: те же команды, но игрок '
               f'сбрасывает шум по кулдаунам.</text>')
    out.append('</svg>')
    open(path, "w", encoding="utf-8").write("\n".join(out))
    return path

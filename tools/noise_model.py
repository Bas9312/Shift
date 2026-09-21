#!/usr/bin/env python3
"""
Модель шумомантии: считает, во что превращаются команды игрока по той же формуле,
что стоит на сервере (noize_api/service.php и api.php).

Зачем: проверить баланс до игры, а не во время неё. Печатает таблицу ожидаемых
значений по типичным сценариям — с узлом Proxy и без, с партнёром по Cross-Link и без.
Эти же числа лежат в analysis/16-noise-testcases.md как ожидаемые.

Запуск:
    python3 tools/noise_model.py              # предлагаемые настройки
    python3 tools/noise_model.py --current    # то, что стоит на сервере сейчас
    python3 tools/noise_model.py --compare    # обе колонки рядом
"""
import argparse
import math

# --- настройки -------------------------------------------------------------
# Пороги активности заданы в КОМАНДАХ В ЧАС: так их видит мастер, и так о них
# думают за столом. Сервер хранит их в командах в минуту, перевод — в to_server().

# Сверено с сервером 2026-09-21: noize_api/tuning_constants.php (альфа, пороги, софткап)
# и noize_api/tuning.php (шаги спада, их пишет панель мастера).
#
# ЧАСТОТА — часть баланса, а не деталь развёртывания. Спад считается не «в час», а «за один
# прогон крона», и кроны ходят с разной частотой (планировщик beget, сверено 2026-09-21):
#   decrease_local_noise_cron.php   0 */1 * * *  — раз в час
#   decrease_global_noise_cron.php  0 */4 * * *  — раз в четыре часа
# Раньше модель применяла оба спада каждый час и поэтому сильно завышала, как быстро
# остывает город: по её числам единица глобального шума уходила в ноль за 13 часов, а на
# сервере — за 52. Глобальный шум копится через всю игру, и это осознанно (см. doc 15 §9.5).
CURRENT = dict(
    name="сейчас на сервере",
    calm_per_hour=1.0,       # NOISE_CALM_RATE 0.0167/мин
    spam_per_hour=4.0,       # NOISE_SPAM_RATE 0.0667/мин
    calm_mult=0.7,
    spam_mult=1.5,
    softcap_at=6.0,          # NOISE_SOFTCAP_LSOFT
    softcap_steep=2.0,       # NOISE_SOFTCAP_STEEP
    boost=1.4,
    local_up=0.0,            # авторост выключен в планировщике
    local_down_flat=0.2,     # tuning.php local_down
    local_down_pct=0.20,     # tuning.php local_down_pct
    local_every_hours=1,
    global_alpha=0.30,       # NOISE_ALPHA
    global_k=3.0,
    global_beta=1.0,
    global_up=0.0,
    global_down_flat=0.05,   # tuning.php global_down
    global_down_pct=0.07,    # tuning.php global_down_pct
    global_every_hours=4,
)

# Для сравнения: как вёл бы себя город, если бы глобальный крон ходил раз в час с теми
# числами, из которых считался doc 15. Не предложение — справочная колонка для `--compare`,
# чтобы видеть цену переключения частоты.
PROPOSED = dict(
    name="если бы глобальный спад был почасовым",
    calm_per_hour=1.0,       # тише одной команды в час — «спокойно»
    spam_per_hour=4.0,       # чаще четырёх в час — уже долбёжка
    calm_mult=0.7,
    spam_mult=1.5,
    softcap_at=6.0,
    softcap_steep=2.0,
    boost=1.4,
    local_up=0.0,            # авторост выключен: нужен был разово на прошлой игре
    local_down_flat=0.2,
    local_down_pct=0.20,     # минус 20 % от текущего в час
    # Глобальный: спад тоже пропорциональный, но мягче локального — город остывает
    # медленнее человека. alpha поднята с 0.20, иначе при выключенном авторосте и
    # пропорциональном спаде глобальный не успевает вырасти за игровой день.
    global_alpha=0.30,
    global_k=3.0,
    global_beta=1.0,
    global_up=0.0,           # авторост выключен
    global_down_flat=0.1,
    global_down_pct=0.10,
    local_every_hours=1,
    global_every_hours=1,
)

RAW_MAX = 10.0


def activity_mult(cmds_per_hour, p):
    """Множитель за темп. Между порогами — линейно, как на сервере."""
    if cmds_per_hour <= p["calm_per_hour"]:
        return p["calm_mult"]
    if cmds_per_hour >= p["spam_per_hour"]:
        return p["spam_mult"]
    span = p["spam_per_hour"] - p["calm_per_hour"]
    k = (cmds_per_hour - p["calm_per_hour"]) / span
    return p["calm_mult"] + (p["spam_mult"] - p["calm_mult"]) * k


def softcap(level_raw, p):
    return 1.0 / (1.0 + math.exp((level_raw - p["softcap_at"]) / p["softcap_steep"]))


def gain(level_raw, price, cmds_per_hour, p):
    """Прирост от одной команды в сырой шкале."""
    return price * activity_mult(cmds_per_hour, p) * softcap(level_raw, p) * p["boost"]


def decay_step(value, flat, pct):
    """Один прогон крона спада. Как часто он случается — см. *_every_hours в профиле."""
    return max(0.0, value - value * pct - flat)


def level(raw):
    """Уровень, который видит игрок: сырое пополам, вниз до целого."""
    return int(raw / 2.0)


def run(scenario, p):
    """
    Гоняет сценарий и возвращает (пик сырой, финал сырой, глобальный сырой).
    Деление на узел Proxy и партнёра повторяет nm_split_shares: половина, потом
    половина остатка. Игроку достаётся только его доля.
    """
    own = 0.0
    peak = 0.0
    world = 0.0
    per_hour = scenario["cmds_per_hour"]
    price = scenario["price"]
    proxy = scenario.get("proxy", False)
    partner = scenario.get("partner", False)
    players = scenario.get("players", 1)

    pending = 0.0
    for _hour in range(scenario["hours"]):
        pending += per_hour
        while pending >= 1.0:
            share = price
            if proxy:
                share /= 2.0
            if partner:
                share /= 2.0
            # шум игрока считается от его доли; мир слышит команду целиком
            own = min(RAW_MAX, own + gain(own, share, per_hour, p))
            peak = max(peak, own)
            crowd = (p["global_k"] / (p["global_k"] + max(1, players))) ** p["global_beta"]
            world = min(RAW_MAX, world + p["global_alpha"] * gain(own, price, per_hour, p) * crowd * players)
            pending -= 1.0
        hour = _hour + 1
        if hour % p.get("local_every_hours", 1) == 0:
            own = decay_step(own + p["local_up"], p["local_down_flat"], p["local_down_pct"])
        if hour % p.get("global_every_hours", 1) == 0:
            world = decay_step(world + p["global_up"], p["global_down_flat"], p["global_down_pct"])
    return peak, own, world


# --- сценарии --------------------------------------------------------------
# Темпы подобраны под живую игру: одна команда — это сцена, а не клик.
# Больше 4 команд в час один игрок физически не отыграет.

SCENARIOS = [
    dict(title="Осторожный: 1 команда за 2 часа, цена 2",
         cmds_per_hour=0.5, price=2.0, hours=6),
    dict(title="Обычный: 1 команда в час, цена 2",
         cmds_per_hour=1.0, price=2.0, hours=6),
    dict(title="Активный: 2 команды в час, цена 2",
         cmds_per_hour=2.0, price=2.0, hours=6),
    dict(title="Раш: 4 команды в час, цена 2 (предел реализма)",
         cmds_per_hour=4.0, price=2.0, hours=6),
    dict(title="Дешёвые команды: 2 в час, цена 1",
         cmds_per_hour=2.0, price=1.0, hours=6),
    dict(title="Тяжёлая команда: 1 в час, цена 4 (HUMAN.UPLOAD)",
         cmds_per_hour=1.0, price=4.0, hours=6),
    dict(title="Активный + узел Proxy",
         cmds_per_hour=2.0, price=2.0, hours=6, proxy=True),
    dict(title="Активный + Proxy + Cross-Link",
         cmds_per_hour=2.0, price=2.0, hours=6, proxy=True, partner=True),
    dict(title="Раш + узел Proxy",
         cmds_per_hour=4.0, price=2.0, hours=6, proxy=True),
]

WORLD_SCENARIOS = [
    dict(title="3 шумоманта работают (1 команда в час каждый)",
         cmds_per_hour=1.0, price=2.0, hours=6, players=3),
    dict(title="3 шумоманта активны (2 в час)",
         cmds_per_hour=2.0, price=2.0, hours=6, players=3),
    dict(title="4 шумоманта активны (2 в час)",
         cmds_per_hour=2.0, price=2.0, hours=6, players=4),
    dict(title="4 шумоманта увлеклись (4 в час)",
         cmds_per_hour=4.0, price=2.0, hours=6, players=4),
]


def to_server(p):
    """Как эти пороги выглядят в config.php (сервер думает в командах в минуту)."""
    return p["calm_per_hour"] / 60.0, p["spam_per_hour"] / 60.0


def print_table(p, compare_with=None):
    print(f"\n=== Локальный шум игрока: {p['name']} ===")
    print("Через 6 часов такой игры. «Пик» — максимум за время, «в конце» — что осталось.\n")
    head = f"{'Сценарий':<48} {'пик':>12} {'в конце':>12}"
    if compare_with:
        head += f"   |{'пик (сейчас)':>14}"
    print(head)
    print("-" * (len(head) + 2))
    for s in SCENARIOS:
        peak, fin, _ = run(s, p)
        row = f"{s['title']:<48} {peak:5.2f} = ур.{level(peak)}   {fin:5.2f} = ур.{level(fin)}"
        if compare_with:
            cp, _, _ = run(s, compare_with)
            row += f"   |{cp:8.2f} = ур.{level(cp)}"
        print(row)

    print(f"\n=== Глобальный шум города: {p['name']} ===\n")
    print(f"{'Сценарий':<48} {'пик':>12} {'в конце':>12}")
    print("-" * 76)
    for s in WORLD_SCENARIOS:
        _, _, world_peak = run(s, p)
        print(f"{s['title']:<48} {world_peak:5.2f} = ур.{level(world_peak)}")

    print("\n=== Сколько держится накопленный ЛОКАЛЬНЫЙ шум ===")
    for start in (4.0, 6.0, 8.0):
        v, hours = start, 0
        while v > 1.0 and hours < 240:
            hours += 1
            if hours % p.get("local_every_hours", 1) == 0:
                v = decay_step(v + p["local_up"], p["local_down_flat"], p["local_down_pct"])
        print(f"  с уровня {level(start)} ({start:.0f} сырых) до уровня 0: {hours} ч")

    print("\n=== Глобальный шум за трёхдневную игру ===")
    print("Город поднимают на +3 сырых за игровой день, ночью он только остывает.")
    world = 0.0
    for day in (1, 2, 3):
        world = min(RAW_MAX, world + 3.0)
        for h in range(24):
            if (h + 1) % p.get("global_every_hours", 1) == 0:
                world = decay_step(world + p["global_up"], p["global_down_flat"], p["global_down_pct"])
        print(f"  конец дня {day}: {world:5.2f} сырых = ур.{level(world)}")
    print(f"  (спад глобального крона применяется раз в {p.get('global_every_hours', 1)} ч)")

    calm, spam = to_server(p)
    print(f"\nВ config.php это: NOISE_CALM_RATE {calm:.4f}, NOISE_SPAM_RATE {spam:.4f} (команд в минуту)")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--current", action="store_true", help="считать по текущим настройкам сервера")
    ap.add_argument("--compare", action="store_true", help="предложение рядом с текущим")
    args = ap.parse_args()

    if args.compare:
        print_table(PROPOSED, compare_with=CURRENT)
    elif args.current:
        print_table(CURRENT)
    else:
        print_table(PROPOSED)

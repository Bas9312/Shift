# Ауры в Shift: правила и запись в базу

Справочник для генератора НПС. Всё проверено по живой базе и коду сервера 21.09.2026.
Цель файла — чтобы сгенерированная аура была валидной: не сломала данные и корректно
отрисовалась в приложении у экстрасенса.

---

## 1. Из чего состоит аура

У каждого персонажа ровно одна аура. Она собирается из трёх независимых частей:

| Часть | Таблица | Сколько | Что это |
|---|---|---|---|
| Сама аура | `auras` | ровно 1 строка на персонажа | тип существа, человечность, фон, скрытость |
| Метки | `aura_marks` | 0..N | что экстрасенс «читает» на человеке |
| Проблемы | `aura_problems` | 0..10 | дыры, разрывы, шрамы, паразиты |

Персонаж обязан существовать в `users` — во всех трёх таблицах стоит внешний ключ на
`users.userId` с `ON DELETE CASCADE`.

---

## 2. Таблица `auras`

```sql
userId               VARCHAR(255)  -- PK, = users.userId
type                 ENUM(...)     -- тип существа, см. ниже
percent_of_humanism  TINYINT       -- 0..100, CHECK в схеме
aura_hidden          TINYINT(1)    -- 0/1
aura_image           TEXT          -- URL фона или NULL/пусто
```

**`type`** — цвет и общий вид ауры. Допустимы только эти значения:

| Значение | По-русски |
|---|---|
| `human` | Человек |
| `mage` | Маг |
| `creature_of_spirit_world` | Существо мира духов |
| `creature_of_abyss` | Существо Бездны |
| `creature_of_myth` | Существо мифа |
| `creature_of_reality` | Существо реальности |
| `demon` | Демон |
| `angel` | Ангел |
| `other` | Другое |

**`percent_of_humanism`** — 100 у обычного человека, чем меньше, тем дальше от
человеческого. Значение вне 0..100 база отвергнет (CHECK).

**`aura_hidden`** — 1 означает, что экстрасенсы ауру не видят вообще.

**`aura_image`** — фон под метками. Пусто или NULL → приложение рисует стандартную ауру по
`type`. Если задаёте, то полным URL вида
`http://shift96.ru/static/images/<файл>` из набора `aura_*.png`.

> **Важно:** `auras.type` и `users.type` — два разных поля, и они должны совпадать. Панель
> мастера подсвечивает расхождение как ошибку. Генератор обязан писать одно и то же значение
> в оба места.

---

## 3. Таблица `aura_marks`

```sql
mark_id          INT AUTO_INCREMENT  -- PK
userId           VARCHAR(255)
mark_type        ENUM(...)           -- см. ниже
image_url        VARCHAR(255)        -- NOT NULL
name             VARCHAR(255)        -- NOT NULL, это заголовок метки
description      TEXT                -- может быть NULL/пусто
external         TINYINT(1)          -- 0 = внутренняя, 1 = внешняя
number_of_stars  TINYINT             -- 0..5, CHECK в схеме
```

### 3.1 Типы меток

| `mark_type` | По-русски | Кто её ставит |
|---|---|---|
| `MAGIC_DISCIPLINE` | Магическая дисциплина | **только сервер** |
| `ABILITY` | Абилка | **только сервер** |
| `INSTRUMENT_LINK` | Связь с инструментом | **только сервер** |
| `FAMILIAR_LINK` | Связь с фамильяром | **только сервер** |
| `BLESSING` | Благословение | свободно |
| `CURSE` | Проклятие | свободно |
| `JUDGE_STATUS` | Статус судьи | свободно |
| `CONTRACT_BREACH` | Нарушение контракта | свободно |
| `SPIRITUAL_BEING_INSIDE` | Духовное существо внутри | свободно |
| `MARK_OF_CREATION` | Метка Пробуждения | свободно |
| `MAGIC_CONTRACT` | Магический контракт | свободно |
| `MAGIC_LINK` | Магическая связь | свободно |
| `ARTIFACT_LINK` | Связь с артефактом | свободно |
| `FOREIGN_PLANE_INFLUENCE` | Влияние чужого плана | свободно |

### 3.2 ⚠ Четыре типа меток генерировать НЕЛЬЗЯ

`MAGIC_DISCIPLINE`, `ABILITY`, `INSTRUMENT_LINK`, `FAMILIAR_LINK` — **производные**. Сервер
пересобирает их сам из полей профиля игрока (`users.disciplines`, `users.modules`,
`users.abilities`, `users.instrument`, `users.familiar`) при каждом сохранении профиля через
`PUT /mage_profile_api/api/v1/user/{userId}`.

Если вставить такую метку напрямую в `aura_marks`, при ближайшем сохранении профиля она
будет удалена или перезаписана. Обратное тоже верно: чтобы НПС имел дисциплину — надо
записать её в `users.disciplines` и сохранить профиль **через API**, а метка появится сама.

Как сервер их строит (справочно, повторять вручную не нужно):

- **`MAGIC_DISCIPLINE`** — по одной метке на дисциплину из `users.disciplines`.
  `name` = название дисциплины, `image_url` = `magic_discipline.mark_image_url`,
  `number_of_stars` = **сколько модулей этой дисциплины выдано игроку**.
  Модуль принадлежит дисциплине по своему id: `дисциплина = id_модуля DIV 10`
  (модуль 23 → дисциплина 2). Отсюда потолок в 5 звёзд.
- **`ABILITY`** — по одной на способность из `users.abilities`. `name` = `Способность #<id>`,
  `description` = текст способности на момент выдачи (дальнейшая правка справочника его не
  переписывает), `image_url` — по типу способности, см. таблицу ниже.
- **`INSTRUMENT_LINK`** — одна, если `users.instrument` непустой.
- **`FAMILIAR_LINK`** — одна, если у игрока выбран фамильяр.

Картинка метки-абилки по типу способности:

| `abilities.type` | Файл |
|---|---|
| `атака` | `ability_attack_type.png` |
| `защита` | `ability_protection_type.png` |
| `усиление` | `ability_buff_type.png` |
| `ослабление` | `ability_debuff_type.png` |
| `изменение` | `ability_transform_type.png` |
| `познание` | `ability_insight_type.png` |
| `прочее` | `ability_misc.png` |

### 3.3 `external` — внутренняя или внешняя

- `0` — метка «своя»: дисциплины, абилки, пробуждение, то, что растёт изнутри.
- `1` — метка наложена извне: проклятия, контракты, влияние чужого плана, чужие связи.

Поле независимое: приложение просто рисует внешние отдельно от внутренних. Жёсткой привязки
к `mark_type` нет, но по смыслу проклятие почти всегда `external = 1`, а метка Пробуждения — `0`.

### 3.4 `number_of_stars`

Осмысленно только для `MAGIC_DISCIPLINE` (и там его считает сервер). Для всех остальных
типов ставьте `0`. Допустимый диапазон 0..5, иначе вставка упадёт на CHECK.

### 3.5 `image_url`

Обязательное поле, пустым быть не может. Полный URL вида
`http://shift96.ru/static/images/<файл>`.

Картинки, пригодные для меток (набор на сервере на 21.09.2026):

```
abuss_plane_influence.png     angel_plane_influence.png     artifact_bond.png
artifactology.png             biomagick_and_microdosing.png blessing_and_curses.png
blessing.png                  breached_contract.png         curse.png
death_curse.png               demon_plane_influence.png     extrasens.png
familir_bond.png              info_apostoling.png           instrument_bond.png
judge.png                     magic_contract.png            magic_link_negative.png
magic_link_neutral.png        magic_link_positive.png       mark_adutant.png
mark_anti.png                 mark_of_creation_finnougr.png mark_of_creation_magic_creature.png
mark_of_creation_neo_by_site.png                            mark_of_creation_standart.png
mental_link.png               mental_magic.png              noize_magick.png
noosfer_plane_influence.png   prophecy.png                  ritualistics.png
seal_of_solomon.png           shamanizm.png                 spiritual_inside.png
```

Придумывать имена файлов нельзя: если картинки нет на сервере, метка отрисуется пустой.
Не уверены — берите `magic_link_neutral.png`.

---

## 4. Таблица `aura_problems`

```sql
id            INT AUTO_INCREMENT
userId        VARCHAR(255)
slot          TINYINT       -- 0..9, CHECK; UNIQUE(userId, slot)
problem_type  ENUM('HOLE','TEAR','SCAR','PARASITE','OTHER')
name          VARCHAR(255)  -- NOT NULL
description   TEXT
created_at    DATETIME      -- ставится сам
```

| `problem_type` | По-русски |
|---|---|
| `HOLE` | Дыра |
| `TEAR` | Разрыв |
| `SCAR` | Шрам |
| `PARASITE` | Паразит |
| `OTHER` | Другое |

**`slot` — это место на теле ауры, где проблема нарисована.** Ровно 10 слотов, 0..9. На
одного персонажа не может быть двух проблем в одном слоте — в схеме стоит
`UNIQUE(userId, slot)`. Генератор должен раздавать слоты без повторов.

---

## 5. Как записывать: порядок действий

### 5.1 Создание НПС с аурой

1. `INSERT INTO users (...)` — персонаж. `userId` — латиница, цифры, дефис, подчёркивание,
   2..60 символов.
2. `INSERT INTO auras (userId, type, percent_of_humanism, aura_hidden, aura_image)` —
   **обязательно в той же транзакции**, `type` тот же, что в `users.type`.
3. Игровые поля (дисциплины, модули, способности, инструмент, фамильяр) —
   **через `PUT /mage_profile_api/api/v1/user/{userId}`**, не SQL-ом. Метки появятся сами.
4. Свободные метки (проклятия, контракты, связи) — `INSERT INTO aura_marks`.
5. Проблемы — `INSERT INTO aura_problems`, слоты без повторов.

### 5.2 Железное правило

> Всё, что сервер умеет пересобирать сам, пишется **через API**. Всё остальное — SQL-ом.

Прямая запись в `users.disciplines` / `modules` / `abilities` / `instrument` / `familiar`
обновит профиль, но **не тронет метки в ауре**, и они разъедутся: в профиле дисциплина есть,
в ауре её нет. Починится это только следующим сохранением через API.

### 5.3 Ловушка сервера: пустой `sql_mode`

На этом MySQL `sql_mode` пустой. Это значит, что **невалидное значение ENUM не вызовет
ошибку** — оно молча превратится в пустую строку. Строка длиннее `VARCHAR` молча обрежется.

Поэтому генератор обязан валидировать значения `type`, `mark_type`, `problem_type` **у себя**
до вставки, сверяя со списками из этого файла. База не подстрахует.

CHECK-ограничения (`percent_of_humanism` 0..100, `number_of_stars` 0..5, `slot` 0..9)
работают и вставку отклонят — но на ENUM это не распространяется.

---

## 6. Пример: полный НПС

Шаман, наполовину утративший человечность, с проклятием и дырой в ауре.

```sql
START TRANSACTION;

INSERT INTO users (userId, player_name, name, type, lat, lng, showUser,
                   disciplines, modules, abilities, artifacts, misc, instrument, familiar)
VALUES ('npc_burlak', 'НПС', 'Семён Бурлак', 'human',
        0, 0, 1, '[]', '[]', '[]', '[]', '[]', '', NULL);

INSERT INTO auras (userId, type, percent_of_humanism, aura_hidden, aura_image)
VALUES ('npc_burlak', 'human', 55, 0, NULL);

-- свободные метки: их сервер не пересобирает
INSERT INTO aura_marks (userId, mark_type, image_url, name, description, external, number_of_stars)
VALUES ('npc_burlak', 'CURSE',
        'http://shift96.ru/static/images/curse.png',
        'Родовое проклятие',
        'Тянется по материнской линии. Пахнет стоячей водой.', 1, 0);

INSERT INTO aura_problems (userId, slot, problem_type, name, description)
VALUES ('npc_burlak', 3, 'HOLE', 'Дыра под рёбрами',
        'Края рваные, затягиваться не начала.');

COMMIT;
```

Дисциплины этому НПС **не** выдаются SQL-ом. Если нужен Шаманизм с двумя модулями:

```http
PUT http://shift96.ru/mage_profile_api/api/v1/user/npc_burlak
Content-Type: application/json

{
  "disciplines": [6],
  "modules": [61, 63],
  "abilities": [],
  "instrument": "",
  "familiar": "",
  "misc": []
}
```

После этого сервер сам создаст метку `MAGIC_DISCIPLINE` «Шаманизм» с двумя звёздами.

> **Что API проверяет, а что нет.** Несуществующие id он отвергает с кодом 400 и списком
> неизвестных (`Unknown module ids`, `Unknown disciplines ids`, `Unknown ability ids`).
> А вот модуль, выданный **без своей дисциплины**, он молча примет: такой модуль просто не
> даст звезды и нигде не отобразится. Эту проверку делает только панель мастера, поэтому
> следить за соответствием `id DIV 10` обязан сам генератор.

---

## 7. Справочник дисциплин

| id | Название | Картинка метки |
|---|---|---|
| 1 | Артефактология | `artifactology.png` |
| 2 | Ритуалистика | `ritualistics.png` |
| 3 | Предсказания | `prophecy.png` |
| 4 | Проклятья и Благословения | `blessing_and_curses.png` |
| 5 | Экстрасенсорика | `extrasens.png` |
| 6 | Шаманизм | `shamanizm.png` |
| 7 | Ментальная магия | `mental_magic.png` |
| 8 | Биомагия и Микродозинг | `biomagick_and_microdosing.png` |
| 9 | Шумомантия | `noize_magick.png` |
| 10 | Общие вопросы | — |
| 11 | Городские загадки | — |

> **Дисциплина 9 записана в базе искажённо** (`ШЖ╫■┐ьЮ≈╒╬м╤нт&╜╓я`) — это внутриигровая
> стилизация Шумомантии, а не битая кодировка. Не «чинить» и не переписывать. Если генератор
> сверяет названия, он должен принимать оба написания.

> Дисциплины 10 и 11 — служебные, для маршрутизации вопросов в чате мастеров. Живым
> персонажам их обычно не выдают, и картинки метки у них нет.

Актуальные списки модулей, способностей и фамильяров берите из таблиц `magic_modules`,
`abilities`, `familiars` — они меняются чаще, чем этот файл.

---

## 8. Чеклист перед вставкой

- [ ] `users.type` и `auras.type` совпадают
- [ ] `type`, `mark_type`, `problem_type` — строго из списков выше
- [ ] `percent_of_humanism` в 0..100
- [ ] `number_of_stars` = 0 у всех меток, кроме дисциплин
- [ ] `image_url` непустой и файл существует на сервере
- [ ] слоты проблем уникальны в пределах персонажа и лежат в 0..9
- [ ] не генерятся метки `MAGIC_DISCIPLINE`, `ABILITY`, `INSTRUMENT_LINK`, `FAMILIAR_LINK`
- [ ] дисциплины и модули выданы через `PUT /mage_profile_api/...`, а не SQL-ом
- [ ] модули принадлежат выданным дисциплинам (`id DIV 10`) — API это НЕ проверяет
- [ ] все id дисциплин, модулей, способностей существуют в справочниках (вот это API проверит)

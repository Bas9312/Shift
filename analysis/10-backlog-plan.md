# План по бэклогу (клиент Shift) — 2026-07-28

> **Status, 2026-07-28 (fifth pass).** See section 0д: the two remaining "small, unblocked"
> tail items are done — the view-id mismatch in the point-info dialog is fixed, and the aura
> cleanup notification now fires via AlarmManager + a BroadcastReceiver, tested live.
>
> **Status, 2026-07-28 (fourth pass).** See section 0г: `aura_text` is now editable through
> `PATCH` on the server (uploaded and verified live) and through the MG point card in the app.
> From this pass on, new text in this document is written in English by the owner's request.
>
> **Статус на 2026-07-28, утро.** Третий заход — см. раздел 0в: серверные правки залиты и
> проверены на живом сервере, **#14** переделан на fail-closed, **#28** (аура места) сделан.
> **#15** отложен: базовая логика на клиенте и так работает.
>
> **Статус на 2026-07-28, ночь.** Второй заход — см. раздел 0б: #4 переформулирован,
> #14 реализован, поправлена моя ошибка про «сервер не готов», есть правки сервера на заливку.
>
> **Статус на 2026-07-28, вечер.** Сделано и проверено на эмуляторе: **#4** (плашка про ИИ
> в чате фамильяра), **#9** (trackable), **#5** (чистка ауры экстрасенсом).
> Проверено запросами к серверу: **#13** работает как надо, правки не нужны;
> **#28** сервер отдаёт `aura_text`, клиент его не использует (по решению — пока не делаем).
> **#7** отложен по решению владельца. Подробности — в разделе 0.

---

## 0д. Fifth pass, 2026-07-28: the two small unblocked tail items

### View-id mismatch in `dialog_point_info.xml` — fixed

The ids were shifted by one relative to what each view actually displayed (`tvPointType`
showed the radius, `tvPointRadius` showed coordinates, and so on — see section 6 for the
original finding). Renamed every id in `dialog_point_info.xml` and its
[EkatMaps.kt:520-530](app/src/main/java/bas/app/shift/EkatMaps.kt:520) bindings to match what
they display: `tvPointRadius` → radius, `tvPointCoordinates` → coordinates, `tvPointDescription`
→ description, `tvPointTextOnEnter` → text-on-enter, `tvAuraLabel` → the static "Аура места"
label above the aura `EditText`. Pure rename, no behavior change. Verified live as `MG_Bas`:
opened a point card and confirmed each line still shows the right content.

### #5 tail — "cleanup ready" push notification

Previously the aura-cleanup countdown only existed on-screen — closing the app meant losing
track of when a cleanup finished. Added:

- `AuraCleanupManager` now schedules an inexact, Doze-tolerant alarm
  (`AlarmManager.setAndAllowWhileIdle`) for `startedAt + duration` whenever a cleanup starts,
  and cancels it on `cancel()`. No `SCHEDULE_EXACT_ALARM` permission needed — a cleanup window
  is 5-15 minutes, so a few minutes of slack on the notification is fine; the on-screen
  countdown is still exact whenever the player reopens the aura screen.
- New `AuraCleanupReadyReceiver` ([receivers/AuraCleanupReadyReceiver.kt](app/src/main/java/bas/app/shift/receivers/AuraCleanupReadyReceiver.kt)),
  registered `exported="false"` in the manifest. On fire, it re-checks
  `AuraCleanupManager.progress()` is still pending and ready before notifying — guards against
  the player having already confirmed or cancelled the cleanup from the foreground.
- `LocationNotifications.showAuraCleanupReadyNotification()` posts on the existing
  `points_notifications_channel` (already `IMPORTANCE_HIGH`), tapping it opens `AuraActivity`
  with the right `aura_id`.
- Alarms don't survive a reboot, and there's no `BOOT_COMPLETED` receiver — instead
  `AuraCleanupManager.progress()` re-arms the alarm on every read if the cleanup isn't ready yet.
  Cheap and idempotent, so simply reopening the aura screen after a restart restores the pending
  notification without extra plumbing.

Verified on `emulator-5554`: wrote a fake in-progress `TEAR` cleanup directly into
`aura_cleanup_prefs.xml` with a past `startedAt` (already due), force-stopped the app, and sent
the equivalent alarm broadcast — `dumpsys notification` confirmed a HIGH-importance
notification titled "Чистка ауры завершена" posted on `points_notifications_channel` with a
working `contentIntent` into `AuraActivity`. Test prefs file restored to its original empty
`<map />` afterward.

---

## 0г. Fourth pass, 2026-07-28: aura_text became editable

### Server (`api_geo/api.php`, uploaded via FTP and verified live)

`PATCH /api/v1/points/{id}` now accepts `aura_text` alongside `hidden`, `next_point_id` and
`trackable`. Previously the aura could only be set at point creation, so a typo meant deleting
the point and making a new one — with a new `pointId`, which breaks any quest chain pointing
at it.

Semantics chosen to match the rest of the endpoint: a field absent from the body is left
untouched; `aura_text: ""` (or whitespace only) clears the aura back to `NULL`, i.e. back to
"not defined". The error message for an empty body was updated to list the new field.

Before uploading, the live file was downloaded and diffed against the local copy — the only
difference was this change, so nothing of Тари's was overwritten.

Verified against the live server:

| Case | Result |
|---|---|
| `PATCH {"aura_text": "..."}` | 200, text stored and returned |
| `PATCH {"aura_text": ""}` | 200, `aura_text` becomes `null` |
| `PATCH {}` | 400 with the updated field list |
| `PATCH` on a nonexistent point | 404 |
| `PATCH {"hidden": ...}` | still works, no regression |

### Client

The MG point card (`dialog_point_info.xml`) showed the aura as a read-only line. It is now an
`EditText` (`etAuraText`) saved by the existing "Save" button, next to `hidden` and
`trackable`. `UpdatePointRequest` gained `aura_text`; `ServerService.updatePoint` gained an
`auraText` parameter. Gson omits `null` but serialises `""`, which is exactly the
"don't touch" vs "clear" distinction the server expects — so no custom serialiser is needed.

`R.string.point_no_aura_text` became unused once the read-only line was replaced, and was
removed.

Verified on `emulator-5554` as `MG_Bas`: typed an aura into a familiar point, saved, confirmed
the text server-side; reopened the card and saw the saved text loaded back; cleared the field,
saved, and confirmed `aura_text` went back to `null` on the server. The emulator was then
restored to the `Bas` user from a byte-for-byte backup of `user_prefs.xml` (3518 bytes, taken
with `adb root` before switching), and the player UI came back correctly.

### Repository hygiene

`SERVER/` and `API/` were moved into the project directory and are now gitignored — `SERVER/`
carries DB credentials in `config.php` and must not reach git. FTP access details and the
diff-before-upload rule are recorded in `CLAUDE.md` and in private memory.

---

## 0в. Третий заход, 2026-07-28 (утро): сервер залит, #14 fail-closed, #28 сделан

### Серверные правки залиты
`public_html/api_geo/api.php` залит по FTP. Перед перезаписью живой файл скачан и сравнён —
дифф ровно наш, чужих правок Тари не было; копия старого лежит в скратчпаде сессии.
Проверено на живом сервере: `PATCH {trackable}` в обе стороны ✅, `bind` чужого занятого
фамильяра → **409** ✅, `bind` своего → 200 ✅, авто-снятие через 15 минут ✅ (поймано вживую:
привязка `MG_Bas` от 03:21 отвалилась сама).

### #14 — теперь fail-closed
Раньше при сетевой ошибке (не 409) игрок всё равно попадал в чат с предупреждением. Это
ровно та дыра, ради которой механика делается: двое с плохой связью оказались бы в чате
вдвоём. Теперь не занял — не пускаем, независимо от причины.

### #28 — аура места (сделано)
- Экстрасенс (дисциплина «Экстрасенсорика») при тапе по маркеру получает кнопку
  **«Прочитать ауру места»**, если стоит ближе 50 м от реального центра точки. Маркер игрок
  и так видит только внутри радиуса, но радиус бывает и в километр — поэтому отдельный барьер.
- Есть текст — показываем `aura_text` с сервера. Нет — «ничего внятного не считываешь…
  спроси у мастеров».
- У МГ в диалоге создания точки появилось поле **«Аура места»**, в карточке точки —
  строка «Аура места: … / не задана». Раньше задать её из приложения было нечем.
- Проверка «экстрасенс ли игрок» вынесена в `User.isExtrasensory` — строка «Экстрасенсорика»
  была вписана в двух местах `MainActivity`.

Проверено на эмуляторе: текст есть ✅, текста нет ✅, точка в 150 м (маркер виден,
кнопки нет) ✅, создание точки с аурой из приложения МГ ✅.

**Что осталось незакрытым:** `PATCH` на сервере не принимает `aura_text` — задать ауру можно
только при создании точки, отредактировать у существующей нельзя. Правится одной веткой в
`api.php` по образцу `trackable`.

### #15 — отложен
Разобрано: на сервере механика есть целиком (`quests`, `user_quest_progress`,
`handlePointEntry`, фильтр цепочки в `GET /points` для не-МГ). Игроку на клиенте логика не
нужна вообще — точки сами появляются и исчезают на существующем опросе, текст при входе
показывается. Недостающее — только инструмент МГ для сборки цепочки. Решено не делать.
Открытые вопросы к механике (стартовая точка цепочки игроку не видна; погоня не проходится
дважды; `POINT_WITH_TEXT` игрокам не рисуется; вход считается по реальным координатам, а
круг рисуется по виртуальным) — в переписке, к коду не привязаны.

---

## 0б. Второй заход, 2026-07-28 (ночь): #4 переформулирован, #14 реализован

### Поправка к разделу 0 — я ошибся про #14
Раньше я написал, что «серверная часть #14 не готова». **Это неверно.** Я перебирал имена
эндпоинтов (`assign`, `talk`, `start_talk`…) и не угадал настоящие. В коде сервера
(`SHIFT/SERVER/public_html/api_geo/api.php`, версия 2.0) механика есть целиком и **работает
на живом сервере**:

- `POST /api/v1/points/{id}/bind` `{playerId}` — занять фамильяра;
- `POST /api/v1/points/{id}/touch` — продлить (двигает `last_message_time`);
- авто-снятие привязки через 15 минут молчания — внутри `GET /api/v1/points`.

Заодно из того же файла видно, что **#15 (погоня) на сервере тоже сделан целиком**: есть
таблицы `quests` / `user_quest_progress`, `POST /api/v1/quests`, обработка входа в точку с
переходом к `next_point_id`, и `GET /points` для не-МГ уже отдаёт только текущую цель цепочки.
Клиент об этом не знает вообще — когда дойдут руки до #15, клиентской работы там сильно
меньше, чем я оценивал (создание квеста + показ, а не своя логика цепочки).

### #4 — текст переписан
Добавлен абзац про сообщения, которых игрок сам не писал, без упоминания механики общего чата:

> Фамильяр — это ИИ. Всё, что он отвечает, — просто текст.
>
> Он может пообещать тебе что угодно: прислать предмет, наложить эффект, отправить в нужное
> место, позвать кого-то на помощь. Ничего из этого не произойдёт — сам по себе фамильяр на
> игру не влияет. Такое уже случалось, поэтому не верь его обещаниям и не ходи туда, куда он
> тебя отправил. Любое игровое действие — только через мастеров.
>
> А вот сообщения в чате, которых ты сам не писал, — это не глюк и не выдумка ИИ. Это
> настоящая игровая информация, и пользоваться ей можно.

### #14 — реализовано на клиенте
- Тап по фамильяру: если `assigned_player` — не ты, показываем «Фамильяр занят» и не пускаем.
- Иначе кнопка «Начинаю общаться» → `POST /bind` → открывается экран фамильяра и чат.
- Каждое отправленное сообщение дёргает `POST /touch`, поэтому 15 минут отсчитываются
  от последней реплики, а не от начала разговора.
- `pointId` прокидывается `EkatMaps → FamiliarFoundActivity → FamiliarChatActivity`. Если его
  нет (свой фамильяр с главного экрана, переход из уведомления) — привязка просто не трогается.
- На 409 не пускаем. На любой другой сбой (нет сети, игрока нет в таблице `users`) —
  **пускаем с предупреждением**: остаться без разговора из-за моргнувшей сети хуже, чем
  изредка разойтись у одного фамильяра.

Файлы: [EkatMaps.kt](app/src/main/java/bas/app/shift/EkatMaps.kt),
[ShiftApi.kt](app/src/main/java/bas/app/shift/api/ShiftApi.kt),
[ServerService.kt](app/src/main/java/bas/app/shift/services/ServerService.kt),
[Point.kt](app/src/main/java/bas/app/shift/models/Point.kt),
[FamiliarFoundActivity.kt](app/src/main/java/bas/app/shift/ui/FamiliarFoundActivity.kt),
[FamiliarChatActivity.kt](app/src/main/java/bas/app/shift/ui/FamiliarChatActivity.kt).

### Правки сервера — НУЖНО ЗАЛИТЬ
Файл: **`/home/bas/SHIFT/SERVER/public_html/api_geo/api.php`** (синтаксис проверен `php -l`).
Три изменения:

1. **`PATCH` теперь принимает `trackable`** (было только `hidden` и `next_point_id`). Без этого
   флаг «нужен мастер» нельзя было исправить после создания точки — только пересоздать.
2. **`/bind` отдаёт 409, если фамильяр занят другим.** Раньше bind молча перезаписывал
   привязку: двое могли «начать общаться» одновременно, и механика ничего не давала.
3. 15 минут вынесены в `FAMILIAR_HOLD_MINUTES` + функцию `releaseExpiredFamiliars()`, которая
   теперь вызывается и в `GET /points`, и в `/bind` (иначе игрок между опросами упирается
   в давно ушедшего).

Клиент под это уже готов: чекбокс «нужен мастер» в диалоге точки стал редактируемым и
шлёт `PATCH {trackable}`.

### Как проверялось
На живом сервере, приложение на `emulator-5554`:
- **#4** — плашка и полный текст на экране чата.
- **#14 «занято»** — фамильяр привязан к `MG_Bas` → тап под `Bas` даёт «Фамильяр занят». ✅
- **#14 happy path** — свободный фамильяр → «Начинаю общаться» → на сервере
  `assigned_player: "Bas"`. ✅
- **#14 продление** — отправка сообщения в чате сдвинула `last_message_time`
  с `03:52:11` на `03:53:05`. ✅

**Что НЕ проверено вживую** (упирается в незалитый файл):
- 409 при гонке двоих за одного фамильяра;
- `PATCH {trackable}` — живой сервер пока отвечает
  `400 No fields to update (allowed: hidden, next_point_id)`;
- авто-снятие привязки ровно через 15 минут (код есть и в живой версии, но ждать не стал).

После заливки `api.php` эти три стоит прогнать — скажи, и я проверю.

---

## 0. Что сделано и что выяснилось (2026-07-28)

### Проверки на живом сервере

**#13 — точки с временем в будущем: всё в порядке, клиентский фикс НЕ нужен.**
Создал точку с `createdAt = 2026-07-30 20:00:00` → `201 Created`, но в `GET /points`
она не появилась (список остался 41 точка). Сервер сам скрывает точки с будущим временем.
Тестовую точку удалил.

**#28 — `aura_text` с сервера ПРИХОДИТ.** В боевом списке точек есть три с непустым полем
(две тестовые от `MG_TARI`, одна — «Исходная точка с аурой»). То есть серверная часть готова,
не хватает только клиента: поля при создании и кнопки «посмотреть ауру места» у экстрасенса.
Делать пока не стали.

**Ограничение сервера, важное для #9:** `PATCH /api_geo/api/v1/points/{id}` отвечает
`400 {"error":"No fields to update (allowed: hidden, next_point_id)"}`. Значит **`trackable`
можно задать только при создании точки**, поменять у существующей нельзя.
В `POST` он принимается и сохраняется корректно (проверено: `trackable: 1`).
→ **Просьба к Тари:** добавить `trackable` в белый список PATCH, тогда флаг можно будет
править у уже созданных точек. Пока в диалоге точки он показан только на чтение.

**Ещё одна находка (не из бэклога):** сервер отдаёт у точек поля `assigned_player` и
`last_message_time` — это ровно та механика из **#14** («кнопка "начинаю общаться"»:
поле игрока + время последнего сообщения). **В клиентской модели `Point` этих полей нет**,
клиент их не читает. То есть #14 на сервере есть, а на клиенте блокировка не реализована.
Стоит уточнить у Тари, ожидается ли, что клиент их использует.

### Сделано в коде

| # | Что | Файлы |
|---|---|---|
| 4 | Плашка «Фамильяр — это ИИ, его ответы это просто текст». Полный текст показывается принудительно при первом открытии чата, дальше висит постоянная янтарная полоса, тап по ней открывает текст снова. | [FamiliarChatActivity.kt](app/src/main/java/bas/app/shift/ui/FamiliarChatActivity.kt), `activity_familiar_chat.xml`, `strings.xml` |
| 9 | Чекбокс «Нужен мастер или игротех» при создании точки → уходит в `trackable`. На карте такая точка — жирная янтарная пунктирная обводка, и её круг кликабелен: игрок тыкает ИЗДАЛЕКА и читает предупреждение. В диалоге точки у МГ — строка «Взаимодействие: …» (только чтение). | [EkatMaps.kt](app/src/main/java/bas/app/shift/EkatMaps.kt), [PointVisualizer.kt](app/src/main/java/bas/app/shift/utils/PointVisualizer.kt), [MapPointsRenderer.kt](app/src/main/java/bas/app/shift/utils/MapPointsRenderer.kt), `dialog_create_point.xml`, `dialog_point_info.xml` |
| 5 | Чистка проблем ауры экстрасенсом: долгий тап по проблеме → старт на время → по истечении «Подтвердить». | [AuraCleanupManager.kt](app/src/main/java/bas/app/shift/helpers/AuraCleanupManager.kt) (новый), [AuraActivity.kt](app/src/main/java/bas/app/shift/ui/AuraActivity.kt) |

**Почему обводка, а не иконка (#9).** У обычного игрока маркер точки создаётся, только
когда он уже внутри радиуса ([MapPointsRenderer.refreshMarkersForLocation](app/src/main/java/bas/app/shift/utils/MapPointsRenderer.kt)) —
издалека виден исключительно круг. Поэтому пометка живёт на самой обводке, а круг
trackable-точки сделан кликабельным (обычные круги некликабельны, чтобы по ним нельзя
было собирать информацию, не доходя до места).

**Правила чистки ауры (#5), как договорились:**

| Тип проблемы | Время | Результат |
|---|---|---|
| `TEAR` Разрыв | 15 мин | превращается в `SCAR` Шрам |
| `SCAR` Шрам | 5 мин | снимается полностью |
| `HOLE` Дыра, `PARASITE` Паразит, `OTHER` | — | «эту проблему экстрасенс не снимает, решается через мастера» |

Надёжность: время старта хранится **абсолютным** в `SharedPreferences`, а не обратным
отсчётом — процесс переживает сворачивание и убийство приложения (проверено force-stop'ом).
Автоприменения по таймеру намеренно нет: если экстрасенса отвлекли, чистка не должна пройти
сама. Перед записью на сервер сверяется, что в слоте всё ещё та же проблема — иначе правку
мастера, сделанную за эти 15 минут, молча затёрло бы.

### Как проверялось

Собрано (`assembleDebug`), поставлено на `emulator-5554`, прогнано вживую:
- **#4** — диалог при первом входе + постоянная плашка (скриншот подтверждает).
- **#5** — на реальной проблеме `TEAR` в ауре `Bas`: диалог старта → отсчёт «Осталось: 14:45»
  → force-stop приложения → состояние восстановилось → «Подтвердить» → на сервере
  `TEAR` стал `SCAR`, состояние чистки очистилось. Отдельно проверены ветка `SCAR` (5 мин)
  и ветка «через мастера» на временно добавленном паразите.
- **#9** — созданы две точки рядом с эмулятором (trackable и обычная): на карте у первой
  янтарный пунктир, тап по её кругу даёт «Нужен мастер», тап по обычному кругу — ничего.

**Тестовые данные откачены:** все созданные мной тестовые точки удалены, проблема в
ауре `Bas` возвращена в исходный `TEAR` с прежним описанием и `created_at`, аура снова
скрыта (`aura_hidden: true`), тестовый паразит удалён, `current_user_id` на эмуляторе
возвращён с `MG_Bas` на `Bas`.

⚠️ **Один хвост остался:** пока я проверял МГ-сценарий, пришлось переключить эмулятор на
учётку `MG_Bas` и включить «В игре» — сервер на это завёл точку-игрока `p-MG_Bas`
(тип `USER`, координаты центра Екатеринбурга, `createdAt 2026-07-28 03:08`). Удалить её я
не стал: это общее игровое состояние, а не мой объект. Она перезапишется, как только
настоящий телефон `MG_Bas` отправит локацию, либо снимается кнопкой «Скрыть меня на карте»
на главном экране МГ. Если мешает — скажи, уберу.

### Что осталось по этим пунктам

- **#9:** `trackable` нельзя менять у существующей точки — ждём Тари (см. выше).
- **#5:** уведомления «чистка готова» нет. Сознательно: точные будильники на Android 12+
  требуют отдельного разрешения, а полумера тихо не сработала бы в дозе. Пока экстрасенс
  видит отсчёт на экране и корректное состояние при повторном открытии ауры. Если на выезде
  окажется неудобно — добавим.
- **#5:** вход только через QR-сканер ауры, то есть механика доступна ровно тем, у кого есть
  «Экстрасенсорика» (кнопка «Просмотр ауры» показывается по этой дисциплине). Отдельной
  проверки роли внутри экрана нет — если понадобится сузить до конкретного модуля, скажи.

---

Источник: `~/Downloads/Бэклог.docx`. Легенда цветов вытащена из разметки docx:

| Заливка | Значение |
|---|---|
| `#d9ead3` зелёный | «сделано» |
| `#f4cccc` красный | «решили не делать» |
| `#fff2cc` жёлтый | «в процессе» |
| без заливки | не начато |

Отсекаем: пункты Тари (сервер/БД), Жени (сайт), Коли, Лёши — трогаем только там,
где нужен клиент. Ниже — только то, что упирается в Android-приложение.

**Главный вывод:** три пункта помечены зелёным, но в клиенте от них есть только поле в
DTO и больше ничего — ни UI, ни логики (#9 trackable, #28 ауры мест, #15 погоня).
Похоже, Тари сделал серверную часть, а клиентская не поехала, и в доке закрасили целиком.
Плюс найдена вероятная причина «ивенты высокого шума работают редко» (см. #7; сам пункт
отложен, но находка остаётся в силе).

---

## 1. Проверено по коду: реально сделано

| # | Пункт | Где в коде | Статус |
|---|---|---|---|
| 8 | Радиус точки при создании | `dialog_create_point.xml:115-139` (cbCustomRadius + sliderRadius), [EkatMaps.kt:613](app/src/main/java/bas/app/shift/EkatMaps.kt:613), [PointRadiusMath.kt](app/src/main/java/bas/app/shift/helpers/PointRadiusMath.kt) | ✅ |
| 10 | Скрывание точек | создание — `dialog_create_point.xml:184`; правка — [EkatMaps.kt:405-436](app/src/main/java/bas/app/shift/EkatMaps.kt:405) через `PATCH /points/{id}`; фильтрация для не-МГ — [MapPointsRenderer.kt:36](app/src/main/java/bas/app/shift/utils/MapPointsRenderer.kt:36), `:182` | ✅ |
| 13 | Время начала точки в будущем | [EkatMaps.kt:581-618](app/src/main/java/bas/app/shift/EkatMaps.kt:581), NumberPicker день/час/минута → `PointRequest.createdAt` | ✅ проверено на сервере |
| 21 | Поиск игроков по карте | [EkatMaps.kt:940-982](app/src/main/java/bas/app/shift/EkatMaps.kt:940) `showPlayersPickerDialog`, `MapPointsRenderer.usersSnapshot()` | ✅ (только МГ) |
| 23 | Картинка в полном размере | [ImageViewerActivity.kt](app/src/main/java/bas/app/shift/ui/ImageViewerActivity.kt) | ✅ |
| 24 | Отметить прочитанным без ответа | [MessagesChatActivity.kt:169-215](app/src/main/java/bas/app/shift/ui/MessagesChatActivity.kt:169), кнопка `btnMarkRead`, только для `MG_*` | ✅ |
| 27 | Кликабельные ссылки в точке | [EkatMaps.kt:331](app/src/main/java/bas/app/shift/EkatMaps.kt:331) и `:398-402`, `LinkifyCompat` | ✅ |

### Риск по #13 — ПРОВЕРЕН, риска нет
Опасение было в том, что поле называется `createdAt`, а не `startAt`, и клиент никак не
фильтрует будущие точки. Проверено запросом к серверу (см. раздел 0): сервер сам не отдаёт
точки с `createdAt > now`. Клиентская страховка не нужна.

---

## 2. Помечено зелёным, но в клиенте НЕ сделано

Это самое важное в этом разборе. Во всех трёх случаях поле есть в `Point`/`PointRequest`
и больше нигде — проверено `grep` по всему `app/src/main`.

### #9 — Варьирование точки (отслеживаемая / не отслеживаемая) — ✅ СДЕЛАНО 2026-07-28
`trackable` живёт только в [Point.kt:19](app/src/main/java/bas/app/shift/models/Point.kt:19)
и [PointRequest.kt:13](app/src/main/java/bas/app/shift/models/PointRequest.kt:13).
Ни чекбокса при создании, ни отображения в инфо-диалоге, ни влияния на отрисовку.

**Что сделать (≈1-2 часа):**
1. Чекбокс `cbTrackable` в `dialog_create_point.xml` рядом с `cbHidden`, значение в
   `PointRequest(trackable = ...)` в [EkatMaps.kt:676](app/src/main/java/bas/app/shift/EkatMaps.kt:676).
2. В `dialog_point_info.xml` — строка «Требует игротеха: да/нет».
3. В `MapPointsRenderer.upsertPoint()` — визуально отличать: например, `trackable == 1`
   рисуем обводку круга сплошной, иначе пунктирной (`strokePattern`). Чтобы МГ на карте
   с одного взгляда видел, куда надо гнать игротеха.

### #28 — Ауры мест — DONE (2026-07-28)

Implemented in the third and fourth passes — see sections 0в and 0г for details and for the
verification runs. Summary of what shipped:

- Psychics (`User.isExtrasensory`, extracted so the "Экстрасенсорика" string lives in one
  place) get a "Прочитать ауру места" button on the point dialog, gated on being within
  `AURA_READ_MAX_DISTANCE_M` = 50 m of the point's **real** centre.
- Server text when present, otherwise a "the aura of this place is not described, ask the
  masters" fallback.
- MG authoring: an aura field on point creation, and an editable aura field on the point card
  backed by `PATCH {aura_text}` (added to the server in the fourth pass). Empty string clears.

### #15 — Погоня за объектом (цепочка точек)
`next_point_id` только в моделях. Логики цепочки нет. В доке пункт закрашен
**одновременно зелёным и жёлтым** — то есть сервер, вероятно, готов, клиент нет.

**Что сделать (≈4-6 часов, самый дорогой пункт):**
Механику надо сначала уточнить у Лёши, но клиентская часть примерно такая:
1. `GET /points?user_id=X` уже передаёт `user_id` — значит сервер может отдавать
   персональный набор. **Уточнить у Тари:** сервер сам режет цепочку по игроку
   (отдаёт только текущую активную точку) или клиент должен это делать?
2. Если режет сервер — на клиенте почти ничего не нужно, кроме показа
   `textToShowOnEnter` при входе (уже есть в `LocationService`).
3. Если не режет — клиенту нужен локальный `SharedPreferences`-стейт «моя текущая точка
   цепочки» и фильтр в `MapPointsRenderer.syncPoints()`. Это хрупко (переустановка
   приложения обнуляет прогресс), лучше давить на серверный вариант.

**Рекомендация: делать серверным. На клиенте — только показ текста при входе.**

---

## 3. Не начато — мои задачи

### #4 — «То, что отвечает фамильяр — ЭТО ПРОСТО ТЕКСТ» — ✅ СДЕЛАНО 2026-07-28
Самое дешёвое в списке, делать первым.
Сейчас [ChatAdapter.kt](app/src/main/java/bas/app/shift/ui/terminal/ChatAdapter.kt)
подписывает ответ просто «Фамильяр».

**Что сделать (≈20 минут):**
- В `activity_familiar_chat.xml` — постоянная плашка сверху, жирным:
  «Ответы фамильяра — ЭТО ПРОСТО ТЕКСТ, а не игровое действие».
- Плюс в `ChatViewHolder.bind()` для `role == "assistant"` рядом с «Фамильяр»
  приписывать курсивом «(текст)».
Плашка важнее, чем подпись: её видно всегда, а подпись игрок перестаёт замечать.

### #5 — Автоматизация чистки проблем в ауре — ✅ СДЕЛАНО 2026-07-28

> Итоговые правила и реализация — в разделе 0. Ниже сохранён исходный разбор; таблица
> в нём УСТАРЕЛА, актуальная: разрыв 15 мин → шрам, шрам 5 мин → снимается,
> дыра/паразит/другое — через мастера.
Сейчас проблемы ауры может править только МГ вручную через
[AuraEditorActivity.kt:482-660](app/src/main/java/bas/app/shift/ui/AuraEditorActivity.kt:482)
(`addAuraProblem` / `updateAuraProblem` / `deleteAuraProblem`). У игрока —
[AuraActivity.kt](app/src/main/java/bas/app/shift/ui/AuraActivity.kt) только просмотр.

Правила из бэклога: **дыра → шрам**, остальное снимается полностью, «прочее» (`OTHER`) нельзя.
По типам из [AuraProblemType.kt](app/src/main/java/bas/app/shift/models/AuraProblemType.kt):

| Тип | Результат чистки |
|---|---|
| `HOLE` (дыра) | → `SCAR` (шрам), через `PUT .../problems/{slot}` |
| `TEAR` (разрыв) | удалить |
| `PARASITE` (паразит) | удалить |
| `SCAR` (шрам) | нельзя (уже шрам) |
| `OTHER` | нельзя |

**Что сделать (≈4-5 часов):**
1. Экран/диалог «Чистка ауры» у игрока с нужной техникой. Признак допуска надо
   определить — сейчас в [ReferenceData.kt](app/src/main/java/bas/app/shift/constants/ReferenceData.kt)
   подходящих кандидатов два: дисциплина «Экстрасенсорика» (id 5) и модуль
   «Работа с местами»/«Разрыв связей» (51/53). **Уточнить у Лёши**, какая именно техника чистит.
2. Старт: выбираем слот проблемы → пишем в `SharedPreferences` `cleanup_<entityId>_<slot>` =
   `startedAt`. Таймер на 15 минут показываем на экране.
3. По истечении — кнопка «Подтвердить». Только по нажатию дёргаем API
   (`updateAuraProblem` с `SCAR` либо `deleteAuraProblem`). Автоматом без подтверждения
   не делать: если игрок прервал процесс, чистка не должна пройти.
4. **Критично для надёжности:** таймер должен переживать закрытие приложения — хранить
   абсолютный `startedAt` (System.currentTimeMillis), а не отсчитывать `Handler`-ом.
   Нужен `WorkManager`/`AlarmManager` для нотификации «чистка готова», иначе игрок
   уйдёт с экрана и забудет. Уведомления уже настроены в
   [LocationNotifications.kt](app/src/main/java/bas/app/shift/services/LocationNotifications.kt) —
   переиспользовать канал оттуда.

### #7 — Баланс шума + монитор шума в мастерке — ОТЛОЖЕНО по решению владельца

> Решение 2026-07-28: пока не делаем. Разбор ниже сохранён — в нём, в частности, найденная
> причина «ивенты высокого шума работают редко» (подзадача **b**), она никуда не делась.
Разбирается на три независимые подзадачи. **Подзадача (b) — вероятный корень жалобы.**

**(a) Баланс значений.** Текущие дельты — в
[TerminalCommandManager.kt:9-57](app/src/main/java/bas/app/shift/helpers/TerminalCommandManager.kt:9):
большинство команд `+1`/`+2`, `HUMAN.UPLOAD` `+4`, `USER.FORMAT` `-10`,
`USER.REBOOT.END` `-1`, `USER.UPGRADE.END` `-2`. Пороги эффектов — 3/4/5
([NoiseHelper.getNoiseLevel](app/src/main/java/bas/app/shift/helpers/NoiseHelper.kt)).
Правка — одна строка на команду, но значения должен назначить ты, не я.

**(b) Периодический шум НЕ триггерит эффекты.** Вот это, скорее всего, и есть причина,
почему «ивенты высокого шума работают редко»:

```kotlin
// NoiseManager.kt:75-77
// Для fetchCurrentNoise не проверяем эффекты, так как это периодическое обновление
// Эффекты проверяются только при adjustNoise, где у нас есть точные before/after значения
```

`fetchCurrentNoise()` крутится раз в минуту ([NoiseManager.kt:57](app/src/main/java/bas/app/shift/helpers/NoiseManager.kt:57)),
но эффекты навешиваются **только когда игрок сам ввёл команду в терминале**
([NoiseManager.kt:151-159](app/src/main/java/bas/app/shift/helpers/NoiseManager.kt:151)).
Значит: шум вырос из-за глобалки, из-за Proxy-партнёра, из-за действий других шумомантов
или из-за серверной механики — **эффект игроку не придёт вообще**, пока он сам что-то не
наберёт. А шумомант, который сидит тихо, ловит ровно ноль ивентов.

**Фикс (≈2 часа):** в `fetchCurrentNoise().onResponse` вызывать
`noiseEffectManager.checkAndApplyNoiseEffects(previousNoise, currentNoise, userId)`.
`previousNoise` там уже считается ([NoiseManager.kt:73-74](app/src/main/java/bas/app/shift/helpers/NoiseManager.kt:73)).
Подводные камни:
- При первом запуске `previousNoise == 0.0`, а `currentNoise` может прийти сразу 4 —
  сработают все пороги разом. Нужен флаг «первая загрузка», иначе после каждого рестарта
  приложения игрок получает полный набор эффектов заново.
- Защита от повторов уже есть — `checkAndApplyLevelNEffect` сверяет наличие эффекта по
  тексту в кэше профиля. Но кэш обновляется не всегда синхронно, так что дубли возможны.
  Стоит добавить локальный «последний применённый уровень» в `SharedPreferences`.

**(c) Монитор шума в мастерке.** Сейчас индикатор глобального шума есть только в терминале
([TerminalActivity.kt:469-480](app/src/main/java/bas/app/shift/ui/terminal/TerminalActivity.kt:469),
`globalNoiseValue` с цветовой градацией), а терминал у МГ **скрыт**
([MainActivity.kt:300](app/src/main/java/bas/app/shift/MainActivity.kt:300)).
То есть мастер не видит шум вообще.

**Что сделать (≈2 часа):** в МГ-ветке `updateUI()`
([MainActivity.kt:298-330](app/src/main/java/bas/app/shift/MainActivity.kt:298)) показать
блок с `GET /noize_api/api/v1/global` + `noisemancers` из `NoiseState`, опрос раз в 30-60 сек,
та же цветовая шкала, что в терминале. Цвета вынести в общий `NoiseHelper.colorForNoise()`,
чтобы не дублировать пороги 2.0/3.0/4.0.

### #3 (жёлтый) — Шумомантия ломает сайт
Совместное с Женей. **На клиенте не начато**: в
[TerminalCommandManager.kt](app/src/main/java/bas/app/shift/helpers/TerminalCommandManager.kt)
нет ни одной команды для сайта. Нужны примерно `SITE.KARMA <user> <delta>`,
`SITE.POST.ANON <text>`, `SITE.POST.AS <user> <text>` — с высоким `noiseIncrease` (3-4),
раз это громкое действие.

**Блокер:** сначала Женя должен дать эндпоинты. До этого клиентскую часть делать
бессмысленно. Оценка после появления API — ≈3 часа (команды в реестр + обработчики
по образцу `TerminalProxyCommands`).

---

## 4. Что стоит отложить

- **#15 погоня** — самая дорогая механика, а правила ещё не финализированы («уточнить у Лёши»).
  Отложить до тех пор, пока Лёша не опишет механику, и делать серверным вариантом.
- **#25 лента игротехнических сообщений** (Коля) — клиенту нужен режим «сообщение без
  возможности ответа». В `Message` для этого нет флага, придётся тянуть через `tags`.
  Ждать, пока Коля/Тари сделают рассылку в админке.
- **#3 шумомантия ломает сайт** — ждём API от Жени.
- Все «технические идеи» из конца дока (свечи, ардуино, AR, лифт между мирами, вай-фай-пуши,
  ChatGPT-артефакт, аура по кускам через QR) — это следующая игра, не этот выезд.

**Красные (не делаем, подтверждаю — в коде их и нет):** #2 генератор НПС, #6 камера-серч,
#11 бэтч-выгрузка точек, #17 улучшение геолокации, #19 запросы ноосферы, #31 UI сайта.

---

## 5. Расхождения «док API ↔ код» — НЕ ПРАВИТЬ, обсудить

Как договорились: ничего не трогаю, просто фиксирую. Почти везде похоже, что доки
устарели, а не код.

### `API геолокации.txt` — сильно устарел
| В доке | В коде |
|---|---|
| `POST /api/v1/points` с полями `type/lat/lng/ownerId` | [PointRequest.kt](app/src/main/java/bas/app/shift/models/PointRequest.kt) шлёт ещё `radius`, `description`, `textToShowOnEnter`, `aura_text`, `next_point_id`, `trackable`, `hidden`, `createdAt` |
| Радиус целиком определяет сервер по типу | Клиент может прислать свой (`radius = null` → серверный дефолт) |
| Метода правки точки нет | Клиент использует `PATCH /api/v1/points/{id}` с `{hidden}` ([ShiftApi.kt:19](app/src/main/java/bas/app/shift/api/ShiftApi.kt:19)) |
| `GET /api/v1/points` без параметров | Клиент шлёт `?user_id=` ([ShiftApi.kt:15](app/src/main/java/bas/app/shift/api/ShiftApi.kt:15)) |
| 10 типов точек | В коде 11 — есть `POINT_WITH_TEXT`, которого нет в доке |
| Префикс `/api/v1/...` | Реально `/api_geo/api/v1/...` |

Всё это — фичи, которые мы реально делали (радиус, скрытие, ауры мест, погоня). Похоже,
просто не внесли в док. **Уточнить у Тари** только одно: `createdAt` в будущем — сервер
такие точки скрывает или отдаёт сразу (см. риск по #13).

### `API Messages.txt` — описывает другую систему
Док описывает `msg.send` / `feed.player` / `inbox.master` / `subs.set`, категории
`ART/RIT/DIV/...`, `client_msg_id` для идемпотентности. Код реализует **совсем другое**:
`messages_api/messages` с заголовком `X-User-Id`, `tags` числами, вложениями через
multipart, `messages_api/chats/{peerId}/history`
([MessagesApi.kt](app/src/main/java/bas/app/shift/api/MessagesApi.kt)).
Это не «расхождение в деталях», а два разных API. Код рабочий — значит док относится к
отменённому варианту. **Сказать Тари, что док по сообщениям надо переписать или выкинуть.**

Отдельно: `client_msg_id` из дока (защита от дублей при повторной отправке) в коде **нет**.
Ретраи POST у нас отключены специально
([RetrofitClient.kt](app/src/main/java/bas/app/shift/api/RetrofitClient.kt) — «POST/PUT
НЕ повторяются, чтобы не задваивать»), так что дыры нет, но и защиты от двойного тапа
по кнопке тоже нет.

### `API аур существ.txt` — мелочи
- Клиент использует `PUT /aura/{id}/hidden` ([AuraApi.kt:47](app/src/main/java/bas/app/shift/api/AuraApi.kt:47)) — в доке такого метода нет.
- В доке есть `GET /aura/:id/marks` — клиент им не пользуется (берёт метки из `GET /aura/:id`).
- В `Aura` у клиента есть `aura_image`, в доке его нет.
- `AuraHiddenRequest.auraHidden` — `Int`, хотя в доке `boolean`. Работает, но выглядит
  как подгонка под сервер; стоит спросить, что там реально ждут.

### `API users.txt`
- Док: `PATCH /api/v1/users/{id}`. Код: `PUT /mage_profile_api/api/v1/user/{id}`
  ([UserProfileApi.kt:23](app/src/main/java/bas/app/shift/api/UserProfileApi.kt:23)) —
  другой глагол, `user` в единственном числе, другой префикс.
- В доке нет `GET /abilities`, который клиент дёргает.
- В доке нет `effects` в модели `User`, а клиент на них завязан
  ([NoiseEffectManager](app/src/main/java/bas/app/shift/helpers/NoiseEffectManager.kt)
  ищет свои эффекты по тексту в `user.effects`).

---

## 6. Техдолг, замеченный попутно (не из бэклога)

**Разъехавшийся маппинг в диалоге точки — FIXED 2026-07-28 (fifth pass).** Ids in
`dialog_point_info.xml` and their bindings in
[EkatMaps.kt:520-530](app/src/main/java/bas/app/shift/EkatMaps.kt:520) were shifted by one
relative to what each view displayed. Renamed to match; see section 0д for details.

**Хрупкая привязка эффектов к тексту.** `NoiseEffectManager` определяет наличие эффекта
сравнением `textToShowPlayers` с длинной строковой константой, а партнёра Cross-Link
вытаскивает регуляркой из текста эффекта. Любая правка формулировки (в том числе на
сервере) молча ломает механику. Для боевого выезда — риск; но чинить это ради самого
факта не надо, только если полезешь туда по #7.

---

## Порядок работ — обновлено 2026-07-28

**Закрыто:** #4, #9, #5 (сделаны и проверены, включая уведомление «чистка готова»),
#13 (проверен, правки не нужны), #28 (аура места, включая PATCH и правку клиента).
Техдолг по маппингу вьюх в диалоге точки — тоже устранён (см. раздел 0д).

**Отложено по решению владельца:** #7 (весь, включая фикс `fetchCurrentNoise`).

**Ждём других:**
- #15 погоня — Лёша (механика) + Тари (режет ли сервер цепочку по игроку).
- #3 шумомантия ломает сайт — Женя (эндпоинты).
- #25 лента игротехнических сообщений — Коля/Тари (рассылка в админке).

## Вопросы к остальным

- **Тари:** поля `assigned_player` и `last_message_time` у точек — это механика #14
  («начинаю общаться»)? Клиент их сейчас не читает вообще, в модели `Point` их нет.
- **Тари:** цепочка точек — сервер сам режет по игроку в `GET /points?user_id=`, или это на клиенте? (#15)
- **Тари:** док по сообщениям (`API Messages.txt`) не соответствует коду вообще — он актуален или его выкинуть?
- **Тари:** `API геолокации.txt` сильно отстал от реальности (нет radius/hidden/aura_text/
  next_point_id/trackable/createdAt, нет PATCH, нет типа `POINT_WITH_TEXT`) — обновить бы.
- **Лёша:** механика погони — как активируется следующая точка? (#15)
- **Женя:** когда будут эндпоинты для «шумомантия ломает сайт»? (#3)

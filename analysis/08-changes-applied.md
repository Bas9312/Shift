# 08. Внесённые изменения (фиксы надёжности)

> Дата: 2026-07-22. По итогам аудита и после уточнения приоритетов владельцем.
> **Из области сознательно исключено** (доверенный свой сервер + ~30 знакомых игроков):
> секреты в репозитории, cleartext HTTP, `debuggable/minify` release, проверка sha256
> обновления, логирование тел. Фокус — надёжность, снижение багов, отзывчивость.
>
> Все изменения собираются (`assembleDebug`, exit 0) и проверены на эмуляторе (API 35)
> под ролями `Bas` и `MG_Bas` без крашей. Скриншоты прогона — в `screenshots/fix_*`.

## Сводка по файлам

| Файл | Что сделано |
|------|-------------|
| `MainActivity.kt` | Уведомления/карта на Android < 33; `GlobalScope`→`lifecycleScope`; кулдаун ритуала в prefs |
| `MessagesChatActivity.kt` | Отправка вложений с IO-потока; убраны дубли multipart; безопасный temp-id; polling-цикл |
| `NoiseManager.kt` | `cleanup()` зануляет global-listener; убрано двойное начисление шума (Cross-Link) |
| `NoiseEffectManager.kt` | Применяются эффекты ВСЕХ пройденных уровней шума |
| `TerminalActivity.kt` | `noiseManager` всегда инициализирован; подтверждение для `USER.FORMAT` |
| `TerminalCommandManager.kt` | `findCommand` — точный матч по первому токену вместо префикса |
| `ChatsListActivity.kt` | Один цикл авто-обновления вместо двух; null-safe отмена |
| `AuraCanvasView.kt` | Кеш bitmap проблем ауры (не декодировать в каждом `onDraw`) |
| `EkatMaps.kt` | Дифф-обновление карты вместо полного пересоздания каждые 10с |

---

## Детали

### Wave 1 — уведомления/карта на старых Android + lifecycle-скоупы

**R1. `POST_NOTIFICATIONS` и карта на Android < 33** — `MainActivity.kt`
- `hasNotificationPermission()` теперь возвращает `true` на API < 33 (там разрешение не
  требуется), не отправляя бессмысленный запрос → раньше на Android ≤ 12 карта была
  недоступна навсегда.
- `requestNotificationPermission()` на API < 33 сразу переходит к запросу геолокации.
- Кнопка карты (`btnOpenMap`) и её обработчик **отвязаны** от разрешения на уведомления —
  теперь зависят только от состояния «в игре». Карта и уведомления работают на всех версиях.

**R3. `GlobalScope` → `lifecycleScope`** — `MainActivity.kt`
- Все сетевые корутины (`loadUserAura`, `toggleAuraHidden`, `performRitual`) переведены на
  `lifecycleScope` → отменяются при уничтожении экрана, нет утечек Activity и обращения к
  разрушённому UI после ответа сети.

### Wave 2 — краши/ANR

**CH1. Вложение читается с IO-потока** — `MessagesChatActivity.kt`
- Чтение файла (`readBytes()`) вынесено в `lifecycleScope.launch(Dispatchers.IO)`; сам запрос
  собирается и отправляется после. Крупное фото больше не вешает UI (ANR/OOM).

**CH3. Дубли multipart** — `MessagesChatActivity.kt`
- `text`/`recipient_id`/`tags`/`answer_to` теперь передаются один раз, как отдельные
  `@Part`-параметры; в список файлов идут только сами файлы. Раньше `text` и `recipient_id`
  слались дважды, а reply-`answer_to` и `tags` терялись. Проверено в логах: в запросе ровно
  по одному полю `text`, `recipient_id`, `tags`.

**CH6. Безопасный temp-id** — `MessagesChatActivity.kt`
- Временный id сообщения теперь отрицательный убывающий счётчик (реальные id сервера —
  положительные), нет риска коллизии и удаления не того сообщения.

**CH2. Polling-цикл** — `MessagesChatActivity.kt`
- `while(true)` заменён на `while(isActive)` в `lifecycleScope` с `delay` в начале итерации.
  Раньше при `isScreenActive=false` цикл крутился вхолостую на главном потоке (риск ANR/CPU).

**NO3/NO5. NoiseManager** — `NoiseManager.kt`
- `cleanup()` теперь зануляет и `onGlobalNoiseUpdateListener` → нет обращения к `binding`
  уничтоженного терминала из ин-флайт ответа.
- `adjustNoise` переписан так, что шум начисляется себе ровно один раз и суммарно сохраняется.
  Раньше при активном Cross-Link и ненайденном партнёре шум начислялся дважды.

**T1. `noiseManager` всегда инициализирован** — `TerminalActivity.kt`
- Менеджер создаётся даже при пустом `userId` (в этом режиме его методы — no-op). Убран
  латентный `UninitializedPropertyAccessException` при шумовых командах.

### Wave 3 — игровые баги

**NO1. Эффекты всех уровней шума** — `NoiseEffectManager.kt`
- `when` (только первая ветка) заменён на последовательные `if`. При скачке шума (напр. 0→5)
  теперь применяются эффекты уровней 3, 4 и 5, а не только 3. Раньше терялись критичные
  эффекты (ранение, дыра в ауре, блок шумомантии).

**R6. Кулдаун ритуала в prefs** — `MainActivity.kt`
- Время последнего ритуала хранится в `SharedPreferences`; доступность считается по времени.
  Кулдаун 30 минут переживает поворот экрана, уход на карту и перезапуск процесса. Раньше жил
  в поле Activity + `postDelayed` и сбрасывался — ритуал можно было спамить.

**T6. Подтверждение `USER.FORMAT`** — `TerminalActivity.kt`
- Опасная команда (−10 шума) теперь требует подтверждения в диалоге. Общая логика вынесена в
  `executeGenericNoiseCommand`. Проверено на эмуляторе: диалог появляется, отмена не выполняет.

**T5. Точный матч команды** — `TerminalCommandManager.kt`
- `findCommand` матчит по первому токену (имени до пробела), а не по префиксу. Раньше
  «CROSS.LINKAGE» ошибочно матчилось на «CROSS.LINK», «USER.REBOOT» — на «USER.REBOOT.START».
  Аргументы («CAMERA.FIND 123») по-прежнему поддерживаются.

**CH4. Один цикл авто-обновления** — `ChatsListActivity.kt`
- `startPeriodicRefresh` больше не запускается и в `onCreate`, и в `onResume` (плодило
  параллельные Handler/Runnable). Теперь идемпотентен, отмена — null-safe.

**AU1. Кеш bitmap проблем ауры** — `AuraCanvasView.kt`
- Иконки проблем декодируются один раз и кешируются по `resId`. Раньше `decodeResource`
  вызывался в каждом `onDraw` для каждой проблемы → GC-штормы и фризы при drag/zoom.

### Wave 4 — обновление карты

**MA1/MA3. Дифф вместо полного пересоздания** — `EkatMaps.kt`
- `updatePointsFromServer` больше не сносит все круги/маркеры и не чистит `pointsOfInterest`
  каждые 10 секунд. Теперь: удаляются только исчезнувшие точки, добавляются только новые,
  а существующие круги/маркеры **двигаются на месте** (`upsertPoint`). Маркер геолокации
  тоже перемещается, а не пересоздаётся. Убирает мерцание, «телепортацию» маркеров игроков и
  сброс открытого info-window. Проверено: 4 цикла обновления (41 точка) без крашей и CME.

---

# Часть 2 — качество, устойчивость сети, данные, UX, чистка

> Второй заход (точечный рефакторинг, без ломки архитектуры). Направления: устойчивость
> сети, надёжность данных, UX/юзабилити, чистка кода. Всё собирается (exit 0) и проверено
> на эмуляторе (терминал/история, чат, карта) без крашей — скриншоты `screenshots/fix2_*`.

### Wave 5 — надёжность данных

**LocalTime-адаптер истории терминала** — новый `models/LocalTimeAdapter.kt`, `TerminalHistoryHelper.kt`
- История терминала теперь сериализуется через Gson с адаптером `LocalTime` (строка ISO).
  Раньше `LocalTime` шёл рефлексией и на новых Android мог падать → вся история молча
  обнулялась. Проверено: история переживает перезапуск (старые записи мигрируют с временем 00:00).

**Лимит истории ответов** — `TerminalHistoryHelper.kt`
- Ответы терминала теперь ограничены `MAX_HISTORY_SIZE` (как и команды). Раньше росли без
  предела → раздувание prefs и всё более тяжёлая запись за длинную сессию.

**AuraMarkType-адаптер** — новый `models/AuraMarkTypeAdapter.kt`, `RetrofitClient.kt`
- Неизвестный с сервера тип метки ауры больше не превращается в null (и метка не пропадает
  с холста), а маппится в запасной тип через `fromServerValue`.

### Wave 6 — остаточные lifecycle-скоупы

- `AuraEditorActivity`, `AuraActivity`, `AuraScannerActivity`, `AuraFragment`,
  `FamiliarChatActivity`: `CoroutineScope(Dispatchers.X).launch` → `lifecycleScope` →
  корутины отменяются с экраном, нет обращения к разрушённому UI после ответа сети.
- `NoiseEffectManager`: scope получил `SupervisorJob` (изоляция сбоев). Сознательно оставлен
  независимым от экрана — применение эффектов шума это записи на сервер, которые должны
  завершиться даже после закрытия терминала (UI отсюда не трогается).

### Wave 7 — устойчивость сети

**Retry-интерцептор** — `RetrofitClient.kt`
- Идемпотентные GET (профиль, точки, сообщения) повторяются до 2 раз с нарастающим бэкоффом
  при обрыве сети и 5xx. POST/PUT НЕ повторяются (чтобы не задваивать сообщения/точки).

**`getPoints()` → null при ошибке** — `ServerService.kt`, `LocationService.kt`, `EkatMaps.kt`
- Теперь различаются «точек реально нет» (пустой список) и «сеть отвалилась» (null). При null
  карта сохраняет текущие точки, а `LocationService` пропускает цикл — раньше разовый обрыв
  «выкидывал» игрока из всех зон и порождал ложные уведомления вход/выход.

**Офлайн-толерантный главный экран** — `MainActivity.kt`
- При сетевом сбое/5xx, если есть кэш профиля, экран показывает функционал по последним
  данным (`updateUI()`) и тост «показаны последние данные», а не «раздевает» кнопки.
  Кнопки скрываются только если кэша нет вообще или это 4xx (нет доступа/не найден).

### Wave 8 — UX и чистка

**Единые сетевые ошибки** — новый `helpers/NetworkErrors.kt`
- `http(code)` и `network(throwable)` вместо копий `when`-блоков. Применено в
  `MessagesChatActivity` и `ChatsListActivity` (консистентные тексты: «Нет связи с сервером» и т.п.).

**Хелпер имён** — новый `helpers/DisplayNames.kt`
- `combine(character, player, fallback)` вместо повторяющейся склейки «Персонаж / Игрок».

**Убран PII из логов** — `LocationService.kt`, `MessagesChatActivity.kt`
- Тексты сообщений больше не пишутся в logcat (логируется только id/длина/отправитель).

**Мёртвый код** — `TerminalActivity.kt` (`incNoise`/`showGlitchEvent`), `EkatMaps.kt` (`generatePointId`) — удалён.

**Одна библиотека картинок** — `build.gradle`, `ImageViewerActivity.kt`, `AttachmentsAdapter.kt`
- Glide убран, всё на Coil (`ImageView.load { … }`). В APK больше не тянутся две image-библиотеки.

---

## Что осталось за кадром (кандидаты на будущее, НЕ сделано на момент 2026-07-22)

> **Историческая секция, 2026-07-22.** Актуальный список открытых пунктов — в
> [11-status.md](11-status.md). Ниже оставлено как есть, для контекста того захода.


- ~~RxJava-рудимент~~ — сделано, см. Часть 3 (Wave 9).
- ~~`О(n)` запись истории терминала~~ — сделано, см. Часть 3 (Wave 9).
- **Проверка фона в Doze / при заблокированном экране** на реальном устройстве (R4/R5) — это
  тестирование, а не код: 30–60 мин с заблокированным экраном + отключить оптимизацию батареи.
  Всё ещё не сделано ни разу за все ночные сессии — нужен живой человек.
- **God-классы** (`TerminalActivity`, `EkatMaps`, `LocationService`, `MainActivity`) —
  по договорённости структуру не ломали целиком; точечный вынос кусков — см. Часть 3.
- ~~**Оптимистичный UI** в терминале~~ — уточнено в сессии 21 (2026-07-27): эта запись устарела.
  На деле уже решено в Wave 13 — `NoiseManager.onCommandFailureListener` подключён в
  `TerminalActivity.initNoiseManager()`, при сетевом сбое терминал печатает явную ошибку вместо
  вечного молчания на "Команда в процессе выполнения...". Успешный путь по-прежнему без
  отдельного подтверждения (полагается на визуальное обновление шкалы шума) — это осознанный
  минимум, не тянет на отдельный TODO.
- Security-пункты (keystore/пароли, cleartext HTTP, debuggable/minify, sha256, логи) —
  сознательно вне области (доверенный свой сервер + ~30 знакомых игроков).

См. [01-executive-summary.md](01-executive-summary.md), [03-reliability.md](03-reliability.md) и
[09-nightly-progress.md](09-nightly-progress.md) (подробный журнал по сессиям ниже).

---

# Часть 3 — ночные автономные сессии (2026-07-23 → 2026-07-25, сессии 1–14+)

> С 2026-07-23 проект дорабатывается сериями коротких автономных ночных сессий (окно
> 05:00–08:00, тик ~20 мин, журнал — [09-nightly-progress.md](09-nightly-progress.md)).
> Здесь — сводка по темам, а не по сессиям; за деталями (что именно проверялось на
> эмуляторе, построчные разборы веток) — в журнал. Всё ниже собирается (`assembleDebug
> --offline`, exit 0) и проверено на эмуляторе без крашей; ничего не закоммичено самими
> ночными сессиями (владелец закоммитил часть волн 1–9 отдельно, коммит `5f8e813`).

### Wave 9 — RxJava-рудимент, буфер истории терминала

- **`LocationService.locationSource`**: `BehaviorSubject<Location>` (RxJava2) →
  `MutableStateFlow<Location?>`/публичный `StateFlow`. `EkatMaps` — `Disposable`/`.dispose()`
  → `lifecycleScope.launch { locationSource.filterNotNull().collect {…} }`/`Job.cancel()`.
  Зависимости `rxjava`/`rxandroid` удалены из `app/build.gradle`.
- **История терминала**: `TerminalHistoryHelper` даёт чистые (без I/O) `appendCommand`/
  `appendResponse`; `TerminalActivity` копит изменения в памяти и сбрасывает на диск одним
  `saveHistory` через debounce (`HISTORY_FLUSH_DELAY_MS`), плюс безусловный флеш в
  `onPause`/`onDestroy`. Раньше каждая строка (до 5 за одну команду) делала полный
  load+parse+serialize+write.

### Wave 10 — унификация сетевых ошибок (`NetworkErrors`), продолжение Wave 8

`helpers/NetworkErrors.kt` (`http(code)`/`network(throwable)`) применён последовательно,
экран за экраном, ещё примерно к 20 файлам поверх `MessagesChatActivity`/`ChatsListActivity`
из Wave 8: `FamiliarChatActivity`, `ProfileEditActivity`, `ProfileActivity`,
`MgProfileViewActivity`, `AuraScannerActivity`, `ArtifactScannerActivity`,
`ArtifactPassportActivity`, `AuraEditorActivity`, `TerminalActivity` (сетевые ветки),
`ArtifactCreatorActivity`, `ArtifactDetailsFragment`, `AuraFragment`, `EffectEditorActivity`,
`WikipediaHelper` и другие — где нюанс важен (напр. отдельный текст «не найдено» для 404),
он сохранён явной веткой поверх `NetworkErrors`, не потерян. Осознанно не тронуты: фоновые
сервисы без user-facing Toast (`NewMessagesChecker`, `ServerService`, `LocationService`) и
чисто диагностические `LogHelper.e` в `TerminalActivity`/`EkatMaps`/`MainActivity` — там
нет текста для пользователя, унифицировать нечего.

### Wave 11 — дедуп склейки имён

`helpers/DisplayNames.combinePlayerFirst(player, character, fallback)` — устранены 7
копипаст-мест в 4 файлах (`MgProfileViewActivity`, `AuraEditorActivity`,
`ArtifactDetailsFragment`, `ArtifactCreatorActivity`), где формат "Игрок / Персонаж"
собирался руками. Проверено живьём на `MgProfileViewActivity` под `MG_Bas`.

### Wave 12 — god-class extraction: `TerminalActivity`

`TerminalActivity` (изначально 1325 строк) поточечно разгружен вынесением самодостаточных
кластеров в отдельные классы пакета `ui/terminal`:
- `helpers/TerminalVisualEffects.kt` — `showNoise`/`applyGlitch`/`showRedScrim`/
  `demonJumpScare`/`vibrate` (чистые view-эффекты, без сети).
- `ui/terminal/TerminalProxyCommands.kt` — кластер команд `PROXY.*`/`CROSS.*`.
- `ui/terminal/TerminalUpgradeRebootCommands.kt` — `USER.UPGRADE.START/.END`,
  `USER.REBOOT.START/.END` (+ владение полями сессии).
- `ui/terminal/TerminalDeepDiveCommands.kt` — `DEEP_DIVE.START/.END` +
  `UTILS.GLOBAL_NOIZE`/`UTILS.USER_COUNT`.

Итог: `TerminalActivity.kt` дошёл до ~552 строк (специализированные обработчики команд
полностью разнесены; в Activity остались только `HELP` и generic noise-путь). Каждый вынос
проверен сборкой (`assembleDebug` + `compileDebugKotlin --rerun-tasks`) и живьём (полные
циклы команд на эмуляторе, включая негативные пути — команда без активной сессии и т.п.).

### Wave 13 — молчаливый сбой шум-эффектов

`NoiseEffectManager`/связанный код — исправлен случай, когда ошибка сети при применении
эффекта уровня шума молча проглатывалась (эффект не применялся и пользователь не узнавал
почему). См. журнал, сессия 11, за деталями.

### Wave 14 — god-class extraction: `EkatMaps`/`LocationService`/`MainActivity` (ручная сессия владельца)

Не ночная автономная работа, а отдельная дневная сессия владельца с Claude Code
(2026-07-24, поверх коммита `5f8e813`), обнаруженная и задокументирована ночной сессией 10:
- `helpers/PointRadiusMath.kt`, `helpers/ProfileDiffer.kt` — вынесены из `EkatMaps`/
  `MainActivity` (−374 строки в `EkatMaps.kt`).
- `services/LocationNotifications.kt`, `services/NewMessagesChecker.kt` — вынесены из
  `LocationService`/`ServerService` (−664 строки в `LocationService.kt`).
- `utils/MapPointsRenderer.kt` — вынесен из `EkatMaps`.

### Wave 15 — ревизия R1–R13, остаточная утечка receiver, последний пропуск `NetworkErrors`

Ночная сессия 17 (2026-07-27) прошлась по всем находкам исходного аудита `03-reliability.md`
(R1–R13) и сверила их с текущим кодом (не только с этим файлом). Большинство подтвердились
закрытыми ранее без изменений; добила два реальных хвоста:
- **`UpdateService.kt`** (R9, утечка `BroadcastReceiver`) — добавлен `DefaultLifecycleObserver`,
  снимающий `onComplete`-receiver в `onDestroy()` экрана, если загрузка ещё не завершилась
  (использует существующий флаг `downloadHandled`, гонок с обычным путём нет). Раньше receiver
  оставался зарегистрированным на context уничтоженной Activity до broadcast от
  `DownloadManager`, который мог не прийти вовсе при убитом процессе.
- **`MainActivity.kt`** (продолжение Wave 10) — найден единственный пропущенный экран с
  сетевыми `Toast` без `NetworkErrors` (`toggleAuraHidden()`, создание точки ритуала). Текст
  унифицирован. `checkUserDisciplines()` сознательно НЕ трогали — там нюансированный текст по
  `userId`/коду ошибки и offline-tolerant ветвление (Wave 7), унификация потеряла бы нюанс.

---

# Part 4 — nightly sessions 18–56 (2026-07-27 → 2026-08-14)

> Same format as Part 3: grouped by theme, not by session. Full per-session detail is in
> [archive/09-nightly-sessions-01-39.md](archive/09-nightly-sessions-01-39.md) and
> [archive/09-nightly-sessions-40-51.md](archive/09-nightly-sessions-40-51.md); the newest
> sessions stay in [09-nightly-progress.md](09-nightly-progress.md).
> Everything below is committed — `16362d1 Claude improvements4` (2026-08-15) closes the diff
> that had been accumulating uncommitted since session 15.

### Wave 16 — dead-code purge (sessions 18–21)

Deleted, each confirmed unreferenced project-wide: `ServerService.notifyHiddenEffectEnter/Exit`
(Toast + `TODO`, no endpoint behind them) with three orphaned imports;
`MainActivity.checkNotificationPermission()` (byte-identical unused twin of
`checkPermissionsSequentially()`); `TerminalCommandManager.getCommandsForAutocomplete/
getCommandsForDisplay/getCommandNameOnly`; `NoiseHelper.isMaxNoiseLevel/isMinNoiseLevel`;
`NoiseManager.getCurrentNoise()`; `UserPrefsHelper.hasUserData()`; `DateTimeHelper.isExpired()`
and the unused `SERVER_TIMEZONE_OFFSET`; `LogHelper.getFirstOurAppEntryFromStacktrace/
getShortStackTraceString` (the first filtered on `.knext.` — copy-paste from another project);
`TimePickerHelper.getTimeOptions()`; `TerminalHistoryHelper.clearHistory()`;
`CommandAutocompleteAdapter.getCommandNameAt()`; the never-wired
`LocationService.clearMessagesCache` → `NewMessagesChecker.clearCache` chain; and five log
sites in `MainActivity` referring to a `ProfileUpdateService` class that no longer exists.
Also collapsed the identical Android 13+/12− branches in
`MessagesChatActivity.checkPermissionsAndPickFiles()`.

Sessions 20 and 21 independently concluded the dead-code direction was exhausted; a re-check in
session 41 confirmed it.

### Wave 17 — message cache and terminal session state (sessions 20, 23)

- **`NewMessagesChecker`** gained a real `clearCache(context, userId)` clearing **both**
  prefs keys, with the key formats extracted into private helpers.
  `MainActivity.onCheckChanged()` now calls it instead of hand-building a `SharedPreferences`
  removal that only cleared `last_known_message_ids_$userId` and silently missed
  `notified_message_ids_$userId` — i.e. after a user switch, old messages could stay suppressed.
- **T4 — REBOOT session survives process death**: `TerminalUpgradeRebootCommands` persists
  `isRebootSessionActive` in `terminal_prefs`, so a rotation or process kill no longer strands
  the player mid-`USER.REBOOT`.
- **T3 — typing timers cancelled**: `ConsoleAdapter` tracks its recursive `postDelayed(25 ms)`
  typing handlers and `TerminalActivity.onDestroy()` calls `cancelAllTyping()`.

### Wave 18 — sweep of the `04-subsystems.md` findings (sessions 23–29)

Maps:
- **MA2** — `EkatMaps.onResume` unchecked `findFragmentById(R.id.map) as SupportMapFragment`
  → `as?` + null check; removes a `ClassCastException`/NPE on the state-restore race.
- **MA7** — `PointType.UNKNOWN` added; an unknown server type used to silently become `USER`,
  i.e. an unknown point was drawn as a live player. `MapPointsRenderer.getPointTitle` and the
  point-creation spinner updated accordingly.
- **MA8** — `MapPointsRenderer.getPointDescription` no longer hardcodes "Длительность: 30 мин"
  for `SHRINKING_CIRCLE`; it formats the real `expireAt`.

Artifacts:
- **AR1** — `ArtifactDetailsFragment` guards `context ?: return` before Toasts and
  `if (!isAdded) return` before opening the binding dialog (which calls `requireContext()`).
- **AR3** — `ArtifactPassportActivity` fragment transactions → `commitAllowingStateLoss()`
  (the spinner callback can fire after `onSaveInstanceState`).
- **AR4** — removed the misleading "Фокус установлен" Toast in `CustomScannerActivity`; no
  real camera focus control existed behind it.
- **AR5** — `ArtifactCreatorActivity.createArtifact()` blocks double-tap re-entry.

Aura:
- **AU10 (the most serious bug of the whole run)** — `activity_aura_editor.xml` hardcodes
  `loadingLayout` visible and `userSelectionLayout` gone, and nothing in the Activity ever
  toggled them: the **Aura Editor was permanently stuck on "Загрузка пользователей…" and user
  selection was physically untappable**. `loadUsers()` now hides the loader on completion,
  shows the selection on success, and reports failures through `NetworkErrors`.
- **AU5** — `AuraEditorActivity.setupUI()` uses `commitNow()` so the mark/editor callbacks are
  assigned synchronously in `onCreate`, closing a frame-long null-callback race.
- **AU6** — QR generation (~640k `setPixel`) moved off the main thread to `Dispatchers.Default`.
- **AU7** — `AuraScannerActivity` validates empty QR content instead of calling `getAura("")`;
  an unreachable `catch (NumberFormatException)` removed.
- **AU9** — `AuraFragment.loadAura()` returns early if `_binding == null` (fragment scope
  outlives `onDestroyView`).

Chat:
- **CH7** — `MessagesAdapter` sets the attachment `RecyclerView`'s layout manager and adapter
  once in the ViewHolder `init` instead of on every `bind()`.

### Wave 19 — last `NetworkErrors` sites and the `NoiseManager` tail (sessions 29, 32–33)

`NoiseManager.cleanup()` now also nulls `onCommandFailureListener` — it was the only one of
four callbacks left dangling (finding NO3). `MessagesChatActivity.markAsRead` and three
`catch` blocks in `EkatMaps` (point update / delete / create) switched to `NetworkErrors`.
With that the unification is complete: the remaining raw `response.code()` sites are
`LogHelper` diagnostics in background services with no user-facing text.

### Wave 20 — duplication removed with generics (sessions 34–38)

- `EffectEditorActivity` — duplicate toolbar setup and a second identical
  `setOnClickListener` on the mark-type input removed.
- `MainActivity` — four byte-identical three-branch `LocationService` logging blocks in
  `onStart`/`onResume`/`onPause`/`onDestroy` → one `logLocationServiceState(...)`.
- `ProfileEditFragment` — `updateModulesDisplay`/`updateDisciplinesDisplay`/`updateMiscDisplay`
  → generic `updateRemovableListDisplay<T>(...)`.
- `ProfileFragment` — four identical read-only list renderers → generic `renderTextList<T>(...)`.
- `EkatMaps.extractFamiliarIdFromPoint` — two branch regexes merged into one.
- `LocationService.calculateDistance()` — hand-rolled Haversine replaced with
  `Location.distanceBetween(...)`, matching what `MapPointsRenderer` already used.

### Wave 21 — migration off deprecated Android APIs (sessions 39–43, 48)

`ShiftApplication` off `LifecycleObserver`/`@OnLifecycleEvent` → `DefaultLifecycleObserver`;
`LocationService.stopForeground(true)` → `stopForeground(STOP_FOREGROUND_REMOVE)` in both call
sites; five `LinkifyCompat.addLinks(..., Linkify.ALL)` calls → an explicit per-file
`LINKIFY_MASK` (drops deprecated `MAP_ADDRESSES`); redundant `package="bas.app.shift"` removed
from `AndroidManifest.xml` (already set via `namespace`); `startActivityForResult`/
`onActivityResult` → `registerForActivityResult` in `ProfileFragment` and
`MessagesChatActivity`; zxing `IntentIntegrator` → `ScanContract`/`ScanOptions` plus
`ActivityResultContracts.RequestPermission()` in both scanner activities. After this the only
compiler deprecation warning left in the project is `ShiftApplication.getRunningServices`,
which is kept deliberately.

### Wave 22 — unit test suite, 0 → 146 tests (sessions 44–52)

New tests under `app/src/test/java/bas/app/shift/`, all on pure logic, zero production changes:
`NetworkErrorsTest`, `DisplayNamesTest`, `AuraCleanupManagerTest`, `PointRadiusMathTest`,
`NoiseHelperTest`, `TimePickerHelperTest`, `TerminalCommandManagerTest`, `ProfileDifferTest`,
`UserRolesTest`, `DateTimeHelperTest`, `TerminalHistoryHelperTest`, `FamiliarDataTest`,
`EnumFromServerValueTest`, `GsonTypeAdapterTest`, `LogHelperTest`. Two of them are explicit
regression tests for bugs described in kdoc (`AuraMarkTypeAdapter` unknown value used to null
out the mark; `LocalTimeAdapter` bad input used to break terminal history loading), and
`TerminalCommandManagerTest` pins the `findCommand` exact-token match that fixed T5.

### Wave 23 — shared helpers instead of copy-paste (sessions 46–50)

- **`helpers/UserRoles.kt`** (new) — `UserRoles.isMg(userId)` replacing 12 hand-written
  `userId.startsWith("MG_")` checks across `MessagesChatActivity`, `MessagesAdapter`,
  `NewMessagesChecker`, `MainActivity`. Case sensitivity preserved 1:1.
- **`NoiseHelper`** gained the pure `thresholdsCrossed(old, new)` and
  `calculateNoiseSplit(delta, hasProxy, hasCrossLink)`; `NoiseEffectManager` and `NoiseManager`
  now call them instead of inline `if`s. Behaviour 1:1, but the arithmetic is now unit-tested.
- **`DateTimeHelper.formatMessageTime`** — inline time formatting in `MessagesAdapter` and
  `ChatsAdapter` unified; `ChatsAdapter` also picked up the `DisplayNames.combine` it had
  missed in Wave 11.
- **`TerminalCommandManager.shouldSkipMgNotification(command)`** — moved out of
  `TerminalActivity`, now testable.

### Wave 24 — real bugs found by line-by-line reading (sessions 53–56)

- **`utils/PointVisualizer.kt`** — `markerColors` listed `PointType.OPEN_PROBLEM` twice
  (`HUE_RED`, then `HUE_BLUE`); Kotlin's `mapOf` keeps the last, so the marker rendered **blue
  while its circle was red**. Duplicate removed, red kept. Verified visually under `MG_Bas`.
- **`services/NewMessagesChecker.kt`** — `notifyIfNotAlreadyNotified` always passed
  `onNewMessages(1, isMG)`, so a notification about several new messages always read
  "У вас 1 новое сообщение". Now passes the real count of not-yet-notified ids.
- **`utils/MapPointsRenderer.kt`** — unreachable branch `point.type == "USER" && !isMgUser`
  inside a block that only runs when `isMgUser == true`, plus its misleading comment, removed.
  No behaviour change; it was a refactoring leftover that read like a rule.
- **`api/NoiseApi.kt`** — dead `getGlobalNoise()` (`GET /noize_api/api/v1/global`) and its
  `models/GlobalNoiseResponse.kt` deleted; the client only ever reads global noise from
  `NoiseState.globalNoise` in `GET /user/{id}`. Removing it surfaced a **server-side**
  asymmetry, recorded in [11-status.md](11-status.md) §F for the owner.

---

### Wave 25 — the three defects the fix documents got wrong (2026-08-15)

Found by re-verifying the 2026-07-22 top-20 table against current code rather than against the
fix documents. All three were recorded as done, or not recorded at all.

**P1 — the game master could not open the map or the chat list when not "в игре".**
The audit blamed one line; the reality was four independent gates, and closing only the first
would have looked fixed while staying broken:
- `MainActivity.updateUI()` — the unconditional tail re-assigned `btnOpenMap`/`btnMessagesChat`
  `isEnabled = isInGame()` right after the MG branch had enabled them. The tail now reads a
  single local `inGame` and grants both buttons to MG explicitly.
- `MainActivity` `btnOpenMap` click handler — silently did nothing unless `isInGame()`.
- `EkatMaps.onCreate()` — checked `is_in_game` *before* determining the role and finished the
  Activity. Role detection moved ahead of the check; the check now applies to players only.
- `EkatMaps.onResume()` — the same check again, so even a started map closed itself.
  Same treatment. A player pulled out of the game mid-session is still ejected, as before.

Verified live under `MG_Bas` with `is_in_game=false`: both buttons enabled, the map opens and
syncs 46 points, the chat list opens, no `FATAL` in logcat, emulator state left as found.

**Side finding, fixed with it:** three different definitions of "who is MG" coexisted —
`UserRoles.isMg` (strict `MG_` prefix, the project standard since Wave 23) against
`startsWith("MG", ignoreCase = true)` in `MainActivity.checkIfMgUser` and
`EkatMaps.checkIfMgUser`. Button state and click behaviour could disagree about the same user.
Both now delegate to `UserRoles.isMg`.

**P2 — `EkatMaps` held a `CoroutineScope(Dispatchers.Main)` that was never cancelled.**
It drove point create/update/delete and familiar binding, and every one of those continues
after the response with `Toast`, `updatePointsFromServer()`, `mMap.animateCamera(...)` or
`startActivity(...)` — all unsafe on a destroyed Activity. `onDestroy()` only logged. All four
call sites moved to `lifecycleScope`; the field and its now-unused import are gone. Accepted
trade-off: a write in flight is cancelled if the screen dies, which is how the rest of the app
already behaves.

**P3 — chat attachments were still read fully into memory.** Wave 2 moved `readBytes()` off
the UI thread, which removed the ANR but not the OOM; the unused `asRequestBody` import sitting
in the file showed the streaming change had been started and dropped. Attachments are now
copied to a temp file in `cacheDir` with a bounded buffer and sent via `File.asRequestBody`, so
the request keeps an honest `Content-Length` and the wire format is unchanged (no chunked
encoding for the PHP side to deal with). Temp files are deleted in both request outcomes, and
an age-guarded sweep on screen open clears anything orphaned by a killed process — age-guarded
so it cannot delete an upload still in flight from a previous instance after a rotation.
**Not verified live:** doing so means posting a real message into production chat.

---

## Backlog

The live backlog is **not** in this file. See **[11-status.md](11-status.md)** — current state,
open items, closed directions, and field notes. This document only records what was changed.

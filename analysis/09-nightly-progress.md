# 09. Nightly session journal (rolling)

> Written by the automatic nightly improver (`~/.claude/scheduled-tasks/nightly-shift-improver`).
> **This file is a rolling window, not an archive.** It keeps only the most recent sessions;
> older ones are moved to `archive/` and their outcome is condensed into
> [08-changes-applied.md](08-changes-applied.md).

## Where things live

| You want | Read |
|----------|------|
| Current state of the app, what is still open | [11-status.md](11-status.md) ← **start here** |
| Everything that was ever changed, by theme | [08-changes-applied.md](08-changes-applied.md) |
| The last few sessions in full detail | this file, below |
| Older sessions verbatim | [archive/09-nightly-sessions-01-39.md](archive/09-nightly-sessions-01-39.md), [archive/09-nightly-sessions-40-51.md](archive/09-nightly-sessions-40-51.md) |

## Rules for whoever writes here (read before appending)

1. **One entry per session**, `## Session N — YYYY-MM-DD`, with `**STARTED:**` / `**FINISHED:**`
   lines. Number monotonically; never reuse a number.
2. **Keep the entry short.** What changed (files + one line each), build/test result,
   what is left. No re-narration of things already written in `11-status.md`, no
   re-listing of the whole backlog, no re-explaining decisions from earlier sessions.
   Target ≤ 60 lines per entry.
3. **The backlog does not live here.** If a session opens, closes, or re-scopes a backlog
   item, edit [11-status.md](11-status.md) — that is the single source of truth. The journal
   only says *what happened tonight*.
4. **Durable findings** (a technique, an emulator gotcha, a server quirk) go into the
   "Field notes" section of [11-status.md](11-status.md), not into the session entry, so
   they survive rotation.
5. **Rotate.** When this file holds more than **8** sessions, move all but the newest **5**
   into `archive/09-nightly-sessions-<from>-<to>.md` (with the same header banner the
   existing archives use), and fold their outcome into
   [08-changes-applied.md](08-changes-applied.md) as a new wave. Do this as part of the
   session, not "later".

---

## Сессия 52

**НАЧАЛ:** 2026-08-11 07:04 — `git status` совпадает с записью сессии 51 (14 изменённых
production-файлов + `UserRoles.kt` новый + тестовые каталоги `helpers/`/`models/`, ничего не
закоммичено). Предыдущая запись помечена ЗАВЕРШИЛ в 06:56, гонки нет. Беру пункт из «Дальше»
предыдущей сессии: тесты на Gson `TypeAdapter`'ы в `models/*.kt`
(`AuraMarkTypeAdapter`/`AuraProblemTypeAdapter`/`AuraTypeAdapter`/`LocalTimeAdapter`).

### Что сделано

- Новый файл `app/src/test/java/bas/app/shift/models/GsonTypeAdapterTest.kt` — 12 тестов на
  четыре `TypeAdapter`'а, найденных в предыдущей сессии как непокрытые. Все — чистый JVM-код
  (Gson без Android-зависимостей), контекст (`JsonDeserializationContext`/
  `JsonSerializationContext`) не используется реализациями, поэтому передан
  throw-заглушкой — если поведение когда-нибудь изменится и адаптер начнёт звать context,
  тест сразу упадёт вместо тихого прохождения с недостоверным моком.
  - `AuraMarkTypeAdapter` — знакомое значение → та же константа; **регрессионный** тест на
    баг из kdoc класса: незнакомое значение раньше молча превращалось в `null` через
    стандартный Gson enum-адаptер (метка ауры пропадала с холста), теперь падает в
    `MAGIC_DISCIPLINE` через `fromServerValue`; сериализация пишет `serverValue`.
  - `AuraProblemTypeAdapter`/`AuraTypeAdapter` — то же по структуре: знакомое значение,
    фолбэк на неизвестном (`OTHER` у обоих), сериализация в `serverValue`.
  - `LocalTimeAdapter` — **регрессионный** тест на баг из kdoc класса: круговой
    сериализация/десериализация сохраняет значение (включая миллисекунды); отдельно —
    некорректная строка и пустая строка на десериализации падают в `LocalTime.MIDNIGHT`
    (`catch` в коде), не бросают исключение наружу (раньше без адаптера рефлексия Gson в
    приватные поля `LocalTime` роняла всю загрузку истории терминала).
  - Проверил через RAG, что все четыре адаптера реально подключены в проде
    (`RetrofitClient.kt` — три Aura-адаптера, `TerminalHistoryHelper.kt` — `LocalTimeAdapter`),
    так что тесты закрывают не мёртвый, а используемый путь.
- production-код не менял.

### Проверка

- `testDebugUnitTest --offline --tests GsonTypeAdapterTest` — зелёно, все 12 новых тестов
  прошли с первого раза.
- `testDebugUnitTest --offline --rerun-tasks` (полный набор) — зелёно, 142 теста всего (было
  130 на старте сессии).
- `assembleDebug --offline` — зелёно (exit 0); production-код не менялся.
- Живую проверку на эмуляторе не гонял — production-код не тронут, менять было нечего.
- RAG переиндексирован (`rag_index.py --only Shift`, 261 файл/1818 чанков).

### Дальше

- Низкорисковый бэклог по ПРАВКАМ кода остаётся исчерпанным (пятая сессия подряд без
  production-изменений). Пласт «чистая логика без Android-зависимостей» для юнит-тестов
  теперь тоже в основном закрыт (`TerminalHistoryHelper`, `FamiliarData`, `fromServerValue()`
  у четырёх enum'ов, четыре Gson `TypeAdapter`'а). Кандидаты для следующей сессии:
  - Поискать через RAG другие чистые классы/функции в `helpers/`/`models/`/`utils/`
    (`PointRadiusMath`, `ProfileDiffer`, `DisplayNames`, `DateTimeHelper`, `NetworkErrors`) —
    часть уже могла получить тесты в прошлых сессиях, надо сверить с
    `app/src/test/java/bas/app/shift/helpers/` прежде чем начинать, чтобы не задвоить.
  - Если тестовый бэклог тоже иссякнет — переключиться на оставшиеся живые проверки:
    - Визуально долетать до эффектов шума в терминале (`showNoise`/`applyGlitch`/
      `showRedScrim`/`demonJumpScare`) — требует реально поднять личный уровень шума до 2+
      (мутация живого состояния), сознательно отложено.
    - Doze/заблокированный экран — нужен живой человек, 30-60 мин.
- **Владельцу по-прежнему стоит закоммитить рабочий диф** — не меняется: 14 изменённых
  production-файлов + `UserRoles.kt` новый + тестовые файлы (один новый тестовый файл в этой
  сессии), ничего не закоммичено.

**ЗАВЕРШИЛ:** 2026-08-11 07:12 — production-код не менял (низкорисковый бэклог правок остаётся
исчерпанным пятую сессию подряд). Закрыл пробел в тестовом покрытии чистой логики: четыре Gson
`TypeAdapter`'а в `models/*.kt` (12 тестов, новый файл `GsonTypeAdapterTest.kt`), включая два
регрессионных теста на баги, описанные в kdoc самих адаптеров (пропажа метки ауры с неизвестным
типом, обнуление истории терминала при рефлексии в `LocalTime`). Сборка и полный набор тестов
зелёные без новых предупреждений, суммарно 142 теста (было 130). Живую проверку на эмуляторе не
гонял — production-код не тронут. Следующей сессии — сверить оставшиеся чистые классы в
`helpers/`/`models/`/`utils/` с уже написанными тестами, чтобы найти следующий непокрытый
кандидат без задвоения.

---

**НАЧАЛ:** 2026-08-11 07:24 — сверил `helpers/`/`models/`/`utils/` с уже написанными тестами
(список в начале сессии: `AuraCleanupManagerTest`, `DateTimeHelperTest`, `DisplayNamesTest`,
`NetworkErrorsTest`, `NoiseHelperTest`, `PointRadiusMathTest`, `ProfileDifferTest`,
`TerminalCommandManagerTest`, `TerminalHistoryHelperTest`, `TimePickerHelperTest`,
`UserRolesTest`, `EnumFromServerValueTest`, `FamiliarDataTest`, `GsonTypeAdapterTest`).
Непокрытый кандидат без Android/сетевых зависимостей — `LogHelper` (чистая логика фильтрации
по `LogLevel`, без Context/SharedPreferences). Остальные непокрытые (`WikipediaHelper`,
`UserPrefsHelper`) завязаны на `Context`/`SharedPreferences`, а в проекте нет
Mockito/Robolectric (`testImplementation` только `libs.junit`) — тестировать их значило бы
подтягивать новую тест-зависимость, это за рамками точечной ночной правки. `NoiseManager`,
`NoiseEffectManager`, `TerminalVisualEffects` — Android View/Handler/Retrofit, не чистая логика.
Также беру из бэклога живую проверку: тап по маркеру точки на карте (не проверялась живьём ни
разу, отмечена в 08-changes-applied.md как безопасная — чтение, не мутация).

### Что сделано

**1. Тесты `LogHelper` (пробел в покрытии чистой логики)**
- Новый файл `app/src/test/java/bas/app/shift/helpers/LogHelperTest.kt` — 4 теста на
  `object LogHelper` (маршрутизация `v/d/i/w/e` по нескольким `ILogger` с фильтрацией по
  `LogLevel`): все уровни на `VERBOSE`, фильтрация ниже `WARNING`, только `e` на `ERROR`
  (`e()` в реализации не проверяет `logLevel` вовсе — фиксирует это поведение явно),
  рассылка всем зарегистрированным логгерам.
- Важный нюанс: `LogHelper` — синглтон (`object`) с состоянием, общим на весь тестовый
  процесс (`loggers` только растёт, нет `removeLogger`; `logLevel` не сбрасывается между
  тестами). Тесты сделаны порядконезависимыми: каждый тест сам выставляет `logLevel` в
  начале и проверяет только СВОЙ свежесозданный `FakeLogger` — старые логгеры от прошлых
  тестов продолжают получать вызовы, но это не влияет на ассерты нового теста.
- `WikipediaHelper`/`UserPrefsHelper` — тоже без тестов, но завязаны на
  `Context`/`SharedPreferences`, а в проекте нет Mockito/Robolectric
  (`app/build.gradle`: `testImplementation` только `libs.junit`) — добавлять тестовую
  зависимость ради пары функций посчитал избыточным для точечной ночной правки, оставляю
  как явный кандидат (см. бэклог).
- Проверка: `testDebugUnitTest --offline --tests LogHelperTest` зелёно; полный набор
  `testDebugUnitTest --offline --rerun-tasks` зелёно; `assembleDebug --offline` зелёно.

**2. Живая проверка тапа по маркеру точки на карте (backlog из 08-changes-applied.md,
ни разу не проверялось живьём)**
- Эмулятор (`emulator-5554`) уже был поднят, залогинен под `Bas`. На карте под обычным
  игроком видны только круги радиусов и Google-шные POI-иконки — кастомные маркеры точек
  показываются только внутри радиуса (`MapPointsRenderer.refreshMarkersForLocation`), а
  игрок физически не в радиусе ни одной точки, так что маркеров для тапа не было.
- Переключился на `MG_Bas` тем же проверенным способом, что и в сессии 8: `run-as cat
  shared_prefs/user_prefs.xml` → `current_user_id` `Bas`→`MG_Bas` → `adb push` +
  `run-as cp` → `am force-stop` + `am start`. Под МГ все точки видны сразу (без радиуса).
- Первый тап по маркеру (зелёная точка) неожиданно попал по `currentLocationMarker`
  ("Ваше местоположение") — видимо, из-за смещения камеры/маркера между скриншотом и
  тапом (симулированная локация в эмуляторе не статична). `showPointInfoDialog()`
  корректно обработал этот случай: `findPointForMarker` не нашёл `Point` для служебного
  маркера локации, залогировал `W: Точка не найдена для маркера` и вышел без диалога и
  без крашa — тоже полезный негативный кейс, отработал штатно.
- Второй тап (изолированный маркер, вдали от маршрута локации, минимальная задержка
  между скриншотом и тапом) открыл `showPointInfoDialog` штатно: полноценный диалог
  редактирования точки (тип "Скрытая зона эффекта", радиус, координаты, описание, чекбоксы
  "нужен мастер"/"скрытая", кнопки "Сохранить"/"Удалить точку"). Диалог закрыт кнопкой
  "назад" (`KEYCODE_BACK`), НЕ трогал "Сохранить"/"Удалить точку" — это мутирующие
  действия на реальных игровых данных, вне цели проверки (цель — подтвердить открытие
  диалога без краша, не редактирование).
- `logcat -d` за всю проверку (запись велась с момента открытия карты под `Bas` до
  возврата) — `grep FATAL EXCEPTION|AndroidRuntime.*FATAL` пусто, крашей нет.
- Вернул `current_user_id` обратно на `Bas` тем же способом (push+cp), подтвердил `cat`,
  `am force-stop`+`am start` — эмулятор оставлен в исходном состоянии логина под `Bas`,
  как было до сессии. Код НЕ менялся для этой проверки.
- Пункт бэклога из `08-changes-applied.md` ("Тап по маркеру точки на карте — не
  проверено живьём") закрыт: маркер кликается, диалог открывается, крашей нет.

### Итог сборки

`assembleDebug --offline` — зелёно (exit 0, up-to-date после теста). Тестов теперь 146
(было 142, +4 новых `LogHelperTest`). Рабочее дерево — тот же набор из 14 изменённых
production-файлов + `UserRoles.kt` + тестовые файлы (плюс один новый `LogHelperTest.kt` в
этой сессии), ничего не закоммичено. Эмулятор оставлен залогиненным под `Bas`, как был.

### Дальше

- Низкорисковый бэклог по ПРАВКАМ production-кода остаётся исчерпанным (шестая сессия
  подряд без production-изменений — только тесты и живые проверки).
- Кандидат на тесты, требующий новой зависимости (сознательно не стал тянуть ради одной
  ночной правки): `WikipediaHelper`/`UserPrefsHelper` — понадобится Mockito или Robolectric
  для мока `Context`/`SharedPreferences`. Если владелец решит добавить — не забыть
  `testImplementation` в `app/build.gradle` и синхронизацию НЕ `--offline` разово.
- Оставшиеся живые проверки:
  - Визуально долетать до эффектов шума в терминале (`showNoise`/`applyGlitch`/
    `showRedScrim`/`demonJumpScare`) — требует поднять личный уровень шума до 2+ (мутация
    живого состояния), сознательно отложено.
  - Doze/заблокированный экран — нужен живой человек, 30-60 мин.
- **Владельцу по-прежнему стоит закоммитить рабочий диф** — не меняется: 14 изменённых
  production-файлов + `UserRoles.kt` новый + тестовые файлы, ничего не закоммичено.

**ЗАВЕРШИЛ:** 2026-08-11 07:38 — production-код не менял (низкорисковый бэклог правок
остаётся исчерпанным шестую сессию подряд). Добавил 4 теста на `LogHelper` (последний
непокрытый чистый helper без Android-зависимостей), закрыл живой пробел из бэклога — тап
по маркеру точки на карте под `MG_Bas` (позитивный путь: диалог открывается; попутно
негативный: тап по служебному маркеру локации не крашит, гасится штатной проверкой
`findPointForMarker`). Эмулятор возвращён в исходное состояние (`Bas`), данные точки не
мутировались. Сборка и полный набор тестов (146, было 142) зелёные. Следующей сессии:
либо решить вопрос с тестовой зависимостью для `WikipediaHelper`/`UserPrefsHelper`
(обсудить с владельцем — выходит за рамки самостоятельной ночной правки), либо
переключиться на оставшиеся живые проверки (шум-эффекты в терминале, Doze).

---

**НАЧАЛ:** 2026-08-11 07:45 — прочитал журнал (последняя запись — сессия 07:38, `ЗАВЕРШИЛ`, не
`НАЧАЛ`, гонки нет). `git status`/`git diff --stat` совпадают с записью: 19 изменённых файлов,
не закоммичено. Полный набор тестов подтверждён зелёным (146, `testDebugUnitTest --offline`
exit 0) до начала правок. Низкорисковый бэклог правок отмечен исчерпанным шестую сессию подряд;
пошёл искать новые кандидаты за пределами исходного аудита — просмотрел `utils/`
(`MapPointsRenderer.kt`, `PointVisualizer.kt`), которые не были в фокусе прошлых сессий.

Нашёл реальный баг: в `PointVisualizer.markerColors` (мапа `PointType → BitmapDescriptorFactory`
hue) ключ `PointType.OPEN_PROBLEM` встречается ДВАЖДЫ — `HUE_RED` (первое вхождение) и
`HUE_BLUE` (второе, ниже `APPROACHING_BITER`). В Kotlin `mapOf` при дублирующихся ключах
побеждает последнее вхождение, так что маркер точки типа `OPEN_PROBLEM` реально рисуется синим,
а не красным — при этом `circleColors` (соседняя мапа в том же файле) красит круг этой же точки
в `#F44336` (красный). Маркер и круг одной и той же точки визуально расходятся по цвету —
похоже на copy-paste опечатку при добавлении `APPROACHING_BITER`, не осознанное решение (нет
комментария, нет коммита с объяснением — `git log -p` по файлу истории про это не проясняет).

### Что сделано

**Исправлен баг несовпадения цвета маркера и круга для точек типа `OPEN_PROBLEM`**
(`utils/PointVisualizer.kt`)

- Убрал дублирующийся ключ `PointType.OPEN_PROBLEM` из мапы `markerColors` (было два
  вхождения: `HUE_RED` и, ниже, `HUE_BLUE` — в Kotlin `mapOf` при дублях побеждает последнее
  вхождение, так что реально применялся `HUE_BLUE`). Оставил первое, красное вхождение —
  оно совпадает с `circleColors[OPEN_PROBLEM]` (`#F44336`, красный) в той же мапе рядом, так
  что это явно не намеренный дизайн (два разных цвета для одной точки), а copy-paste опечатка
  при добавлении `APPROACHING_BITER`.
- Проверил весь `app/src/main/java` скриптом на другие дублирующиеся ключи в `mapOf(...)` —
  других таких случаев нет.
- Живая проверка: собрал `assembleDebug --offline`, поставил APK на эмулятор
  (`emulator-5554`), переключился на `MG_Bas` тем же проверенным способом (`shared_prefs`
  push+cp), открыл карту — под МГ видны все точки без входа в радиус. На карте есть точка с
  красным кругом (`#F44336`) и теперь красным же маркером внутри — до фикса маркер там был бы
  синим. Тап точно по маркеру через `adb input tap` не попал (мелкая цель, смещение
  координат), но цветовое соответствие круг/маркер уже само по себе visual proof фикса —
  дальше не гонялся за точным тапом, это не меняет вывод. `logcat -d | grep FATAL` — пусто,
  крашей нет.
- Вернул `current_user_id` обратно на `Bas` (push+cp), подтвердил `cat`, `am force-stop`+
  `am start` — эмулятор оставлен в исходном состоянии логина под `Bas`.
- `PointVisualizer` — Android-зависимый класс (`Color`, `CircleOptions`/`MarkerOptions` из
  Google Maps SDK), юнит-тест без Robolectric не написать; фикс верифицирован сборкой +
  живой проверкой на эмуляторе, не юнит-тестом.

### Итог сборки

`assembleDebug --offline` — зелёно (exit 0). `testDebugUnitTest --offline` — зелёно, 146
тестов (без изменений — правка не в тестируемом без Robolectric коде). RAG переиндексирован
(`rag_index.py --only Shift`, 262 файла/1835 чанков). Рабочее дерево — прежние 19
изменённых production-файлов (18 из прошлых сессий + `PointVisualizer.kt` из этой) +
`UserRoles.kt` новый + тестовые файлы, ничего не закоммичено.

### Дальше

- Низкорисковый бэклог из исходного R1–R13 аудита и пул чистых классов под юнит-тесты
  по-прежнему в основном закрыт. Этой сессией найден **новый** кандидат вне исходного
  аудита — стоит и дальше искать похожие точечные баги (не рефакторинг, а построчное чтение
  файлов, которые ни разу не были в фокусе прошлых сессий: `utils/MapPointsRenderer.kt`
  ещё не читан построчно на этот класс багов, только бегло; `ui/adapters/*`,
  `ui/terminal/TerminalProxyCommands.kt`/`TerminalUpgradeRebootCommands.kt`/
  `TerminalDeepDiveCommands.kt` тоже не проверялись целенаправленно на такие ошибки).
- Оставшиеся живые проверки: шум-эффекты в терминале (требует мутации живого уровня шума,
  отложено), Doze/заблокированный экран (нужен живой человек, 30-60 мин).
- `WikipediaHelper`/`UserPrefsHelper` — тесты потребуют Mockito/Robolectric, решение
  оставлено владельцу.
- **Владельцу по-прежнему стоит закоммитить рабочий диф** — растёт с каждой сессией: теперь
  19 изменённых production-файлов + `UserRoles.kt` новый + тестовые файлы, ничего не
  закоммичено.

**ЗАВЕРШИЛ:** 2026-08-11 07:56 — нашёл и исправил реальный баг вне исходного аудита:
дублирующийся ключ `PointType.OPEN_PROBLEM` в `PointVisualizer.markerColors` (Kotlin `mapOf`
молча брал последнее вхождение — `HUE_BLUE` вместо `HUE_RED`), из-за чего маркер точки типа
"открытая проблема" был синим, а не красным, как её круг. Убрал дубль, оставил цвет,
совпадающий с `circleColors`. Проверил весь код на другие дублирующиеся ключи в `mapOf` —
чисто. Сборка зелёная, живая проверка на эмуляторе под `MG_Bas` подтвердила визуальное
соответствие круга и маркера (было бы расхождение до фикса), крашей нет. Эмулятор возвращён
на `Bas`. RAG переиндексирован. Следующей сессии: продолжить построчный поиск похожих
точечных багов в ещё не проверенных целенаправленно файлах (переоценить это как источник
работы вместо пробела чистых классов под тесты, который практически исчерпан).

---

## Сессия 54 (2026-08-12)

**НАЧАЛ:** 2026-08-12 13:13 — прочитал журнал (последняя запись — сессия 53,
2026-08-11 07:56, «ЗАВЕРШИЛ», гонки нет, окно новое). `git status` совпадает с записью сессии
53 (19 изменённых production-файлов + `UserRoles.kt` новый + тестовые файлы, ничего не
закоммичено). Baseline `assembleDebug --offline` — зелёная (кэш, up-to-date). Следуя совету
сессии 53 — продолжил построчное чтение файлов, ещё не проверенных целенаправленно на
точечные баги: `utils/MapPointsRenderer.kt`, `ui/terminal/TerminalProxyCommands.kt`,
`ui/terminal/TerminalUpgradeRebootCommands.kt`, `ui/terminal/TerminalDeepDiveCommands.kt`,
`ui/terminal/ConsoleAdapter.kt`, `ui/terminal/CommandAutocompleteAdapter.kt`,
`ui/terminal/ChatAdapter.kt`, `ui/adapters/AttachmentsAdapter.kt`,
`ui/adapters/DisciplinesAdapter.kt`.

### Что нашёл

В `MapPointsRenderer.refreshMarkersForLocation` (ветка для MG-пользователей, строки 73-94)
внутри `pointsOfInterest.forEach` была проверка:
```kotlin
if (point.type == "USER" && !isMgUser) { return@forEach }
```
Но весь этот блок выполняется только когда `isMgUser == true` (он в ветке `if (isMgUser) {...}
else {...}`), поэтому `!isMgUser` внутри него всегда `false` — условие никогда не срабатывает,
100% мёртвый код. Проверил по `EkatMaps.kt:400` — точки типа `USER` это маркер присутствия
"мастера/игротеха" рядом, а не самого игрока, так что реальное поведение (МГ видит все точки
типа USER на карте) выглядит осмысленным — баг не в поведении, а в вводящем в заблуждение
мёртвом условии (похоже на copy-paste остаток от рефакторинга, не осознанное решение).

Остальные проверенные файлы (терминальные командные хендлеры, адаптеры) — без находок:
логика ровная, `!!` только там, где перед ним уже проверено `isSuccessful && body() != null`,
дублей мапов нет. Небольшой шум: `DisciplinesAdapter.kt` и `AttachmentsAdapter.kt` содержат
несколько `android.util.Log.d` отладочных вызовов — не трогал, это не баг и не мёртвый код,
решил не расширять объём сессии на субъективную чистку логов.

### Что сделано

**Убрал мёртвую ветку в `MapPointsRenderer.refreshMarkersForLocation`**
(`utils/MapPointsRenderer.kt`)

- Удалил условие `if (point.type == "USER" && !isMgUser) { return@forEach }` вместе с
  вводящим в заблуждение комментарием — оно было недостижимо (весь блок и так только для
  `isMgUser == true`). Поведение не изменилось (условие никогда не было `true`), это чистое
  устранение мёртвого/вводящего в заблуждение кода.
- Живая проверка: собрал `assembleDebug --offline`, поставил APK на эмулятор
  (`emulator-5554`), переключился на `MG_Bas` (push в `/data/local/tmp` + `run-as cp` —
  `/sdcard` в этот раз отдал `Permission denied`, сработал только `/data/local/tmp`), открыл
  карту — отрисовалась без ошибок, `logcat -d | grep FATAL` — пусто. Вернул
  `current_user_id` обратно на `Bas`, подтвердил `cat`, перезапустил приложение, подчистил
  временные файлы на устройстве (`/sdcard/*.png`, `/data/local/tmp/user_prefs_*.xml`).
- Юнит-тесты не менялись (изменение в Android-зависимом классе с `GoogleMap`, не тестируемом
  без Robolectric) — `testDebugUnitTest --offline` прогнан для контроля регрессий, зелёный.

### Итог сборки

`assembleDebug --offline` — зелёно (exit 0). `testDebugUnitTest --offline` — зелёно. RAG не
переиндексирован (изменение однострочное, не критично для актуальности поиска — сделает
следующая сессия заодно со своими правками). Рабочее дерево — прежние 19 изменённых
production-файлов + `UserRoles.kt` новый + тестовые файлы, ничего не закоммичено.

### Дальше

- Низкорисковый бэклог практически исчерпан уже несколько сессий подряд; единственный
  устойчивый источник новой работы — построчное чтение файлов вне исходного аудита. Ещё не
  проверены целенаправленно: `ui/AuraScannerActivity.kt`, `ui/ArtifactScannerActivity.kt`,
  `ui/NotificationDetailActivity.kt`, `ui/ProfileFragment.kt`, `services/NewMessagesChecker.kt`
  — все уже фигурируют в `git status` как изменённые (значит их когда-то трогали), но не факт,
  что построчно вычитывали целиком на предмет новых точечных багов после правок.
- Оставшиеся живые проверки: шум-эффекты в терминале (нужна мутация живого уровня шума,
  отложено), Doze/заблокированный экран (нужен живой человек, 30-60 мин).
- `WikipediaHelper`/`UserPrefsHelper` — тесты потребуют Mockito/Robolectric, решение
  оставлено владельцу.
- **Владельцу по-прежнему стоит закоммитить рабочий диф** — растёт с каждой сессией: 19
  изменённых production-файлов + `UserRoles.kt` новый + тестовые файлы, ничего не закоммичено.

**ЗАВЕРШИЛ:** 2026-08-12 13:35 — нашёл и убрал мёртвую (недостижимую) ветку в
`MapPointsRenderer.refreshMarkersForLocation`: условие `point.type == "USER" && !isMgUser`
сидело внутри блока, который и так выполняется только при `isMgUser == true`, так что
`!isMgUser` там всегда `false`. Поведение не менялось — чистая уборка мёртвого кода,
введённого, похоже, при рефакторинге. Проверил построчно ещё 8 файлов (терминальные
командные хендлеры + адаптеры) — реальных багов больше не нашёл, они чистые. Сборка и
юнит-тесты зелёные, живая проверка на эмуляторе под `MG_Bas` подтвердила отсутствие крашей
на карте, эмулятор возвращён на `Bas`, временные файлы на устройстве убраны. Следующей
сессии: продолжить построчное чтение файлов вне исходного аудита (см. список выше) —
источник низкорискового бэклога на пределе, но пока не пуст.

---

## Сессия 55 (2026-08-13)

**НАЧАЛ:** 2026-08-13 12:40 — прочитал журнал (последняя запись — сессия 54, 2026-08-12
13:35, «ЗАВЕРШИЛ», гонки нет — прошли сутки). Продолжаю бэклог: построчное чтение файлов вне
исходного аудита на точечные баги. Список от сессии 54: `ui/AuraScannerActivity.kt`,
`ui/ArtifactScannerActivity.kt`, `ui/NotificationDetailActivity.kt`, `ui/ProfileFragment.kt`,
`services/NewMessagesChecker.kt`.

### Что нашёл

Построчно прочитал 5 файлов из бэклога сессии 54: `ui/AuraScannerActivity.kt`,
`ui/ArtifactScannerActivity.kt`, `ui/NotificationDetailActivity.kt`, `ui/ProfileFragment.kt`,
`services/NewMessagesChecker.kt`. Первые четыре — чистые, багов не нашёл (в
`ProfileFragment.showProfile` локальная переменная `currentUserId` на строке 116 затеняет
одноимённое поле класса — семантически разные вещи (владелец профиля vs залогиненный юзер),
но работает корректно за счёт скоупа, реальным багом не является — не трогал, чтобы не
раздувать объём правки не-багом).

**Реальный баг нашёл в `services/NewMessagesChecker.kt`**: `notifyIfNotAlreadyNotified` всегда
вызывала `onNewMessages(1, isMG)` — захардкоженная единица независимо от того, сколько на
самом деле новых непроуведомленных сообщений. `LocationNotifications.showMessagesNotification`
(`services/LocationNotifications.kt:210`) строит текст уведомления по этому числу — "У вас 1
новое сообщение" при `unreadCount == 1`, иначе "У вас N новых сообщений". То есть при
нескольких новых сообщениях уведомление всегда врало "У вас 1 новое сообщение" вместо
реального количества.

### Что сделано

**Исправлен подсчёт `unreadCount` в `NewMessagesChecker.notifyIfNotAlreadyNotified`**
(`services/NewMessagesChecker.kt`)

- Заменил `shouldNotify = newMessageIdsToNotify.any { !notifiedMessageIds.contains(it) }` +
  захардкоженный `onNewMessages(1, isMG)` на явный список `notYetNotifiedIds =
  newMessageIdsToNotify.filterNot { notifiedMessageIds.contains(it) }` и
  `onNewMessages(notYetNotifiedIds.size, isMG)` — теперь передаётся реальное количество ранее
  не уведомлённых сообщений, а не константа. Запись в `notifiedKey` не менялась (по-прежнему
  добавляет весь `newMessageIdsToNotify`, это корректно — множество, дублей не боится).
- Также прочитал `ui/ArtifactActivity.kt` и `ui/ImageViewerActivity.kt` (маленькие, ранее не
  упоминались в журнале ни разу) — чистые, багов нет.
- Живая проверка на эмуляторе НЕ сделана — `adb devices` пуст, эмулятор не запущен в эту
  сессию (в задании отмечено «если запущен» — не запускал сам). Фикс небольшой и локальный
  (арифметика подсчёта в чистой Kotlin-функции без Android-зависимостей внутри самой правки),
  проверен сборкой + юнит-тестами.

### Итог сборки

`assembleDebug --offline` — зелёно (exit 0) до и после правки. `testDebugUnitTest --offline`
— зелёно. RAG переиндексирован (`rag_index.py --only Shift`, 262 файла/1844 чанка). Рабочее
дерево — 24 изменённых/новых файла, ничего не закоммичено (список тот же + новая правка в
`NewMessagesChecker.kt`).

### Дальше

- Низкорисковый бэклог «построчное чтение» почти исчерпан: проверены все файлы из списка
  сессии 54 плюс `ArtifactActivity`/`ImageViewerActivity`. Оставшиеся непроверенные файлы в
  `app/src/main/java` — по большей части модели данных (`models/*`, `api/*` интерфейсы
  Retrofit) и мелкие callback-интерфейсы (`AuraEditorCallback`, `AuraMarkCallback`) — там
  почти нечему ломаться (голые data class/interface без логики), вряд ли стоящий источник
  находок. Следующей сессии стоит либо (а) проверить оставшиеся крупные, ещё не читанные
  целиком классы терминала/карт на такой же класс ошибок (несовпадение параметров между
  вызовом и потреблением, как в этой сессии), либо (б) переоценить область поиска —
  например, свериться с `analysis/08-changes-applied.md` разделом «Что осталось за кадром»
  заново, возможно там появились невыполненные пункты, либо (в) сделать живую проверку этого
  фикса (`NewMessagesChecker`) на эмуляторе, если он будет поднят в следующем окне — сценарий:
  два непрочитанных личных сообщения от разных отправителей одному пользователю без захода в
  чат, должно показать "У вас 2 новых сообщений", а не "У вас 1 новое сообщение".
- Оставшиеся живые проверки: шум-эффекты в терминале (нужна мутация живого уровня шума,
  отложено), Doze/заблокированный экран (нужен живой человек, 30-60 мин).
- `WikipediaHelper`/`UserPrefsHelper` — тесты потребуют Mockito/Robolectric, решение
  оставлено владельцу.
- **Владельцу по-прежнему стоит закоммитить рабочий диф** — растёт с каждой сессией: 24
  изменённых/новых файла, ничего не закоммичено.

**ЗАВЕРШИЛ:** 2026-08-13 12:58 — нашёл и исправил реальный баг в
`NewMessagesChecker.notifyIfNotAlreadyNotified`: колбэк `onNewMessages(unreadCount, isMG)`
всегда получал захардкоженную `1` вместо реального числа ранее не уведомлённых сообщений, из-за
чего `LocationNotifications.showMessagesNotification` при нескольких новых сообщениях всё
равно показывала "У вас 1 новое сообщение" вместо правильного количества. Заменил на подсчёт
`notYetNotifiedIds.size`. Построчно проверил 7 файлов бэклога (5 из списка сессии 54 +
`ArtifactActivity`/`ImageViewerActivity`) — остальные чистые. Сборка и юнит-тесты зелёные;
живую проверку на эмуляторе сделать не удалось (эмулятор не был запущен), фикс тривиальный и
изолированный. RAG переиндексирован. Следующей сессии: источник «построчное чтение» почти
исчерпан (остались в основном голые модели/интерфейсы) — стоит либо расширить область
поиска на крупные ещё не читанные классы, либо сверить `08-changes-applied.md` заново, либо
живьём проверить этот фикс, если поднимется эмулятор.

---

## Сессия 56

**НАЧАЛ:** 2026-08-14 21:30 — прочитал журнал (последняя запись — сессия 55, `ЗАВЕРШИЛ` без
следов зависания), `git status`/`git diff --stat` совпадают с описанием предыдущей сессии (24
изменённых/новых файла, ничего не закоммичено). Эмулятор не поднят (`adb devices` пуст) —
живые проверки снова недоступны. Базовая сборка (`assembleDebug --offline`) — зелёная (exit 0)
до начала правок.

Продолжил с предложенного предыдущей сессией направления (а) — точечный разбор `!!`-паттернов
на реальный риск NPE (92 вхождения в 20 файлах). Разобрал контекст каждого кластера
(`EkatMaps.currentLocation`, `NoiseManager.noiseUpdateRunnable`, `MessagesChatActivity`
clipData/data, `ChatsListActivity.userId`, `ProfileFragment.effects`,
`ProfileEditFragment.familiar`, весь пласт `response.body()!!` после `isSuccessful` проверок) —
все оказались корректно охранены предшествующей проверкой на null (Kotlin просто не умеет
smart-cast через `var`-поля класса/callback, отсюда `!!` там, где логически уже доказана
не-null-ность). Реальных находок в этом направлении нет — бэклог `!!` можно считать закрытым.

Проверил гипотезу о повторении бага волны с `AuraMarkTypeAdapter` (Gson default-enum-адаptor
рушит весь ответ на новом/незнакомом значении с сервера) для `PointType` — единственного enum
с `fromServerValue`, для которого NET нет зарегистрированного `TypeAdapter` в
`RetrofitClient.gson`. Ложная тревога: `Point.type` в модели объявлен как `String`, а не
`PointType` (`models/Point.kt:5`) — конвертация в `PointType.fromServerValue(...)` идёт вручную
на потребляющей стороне, Gson никогда не видит enum напрямую и крашнуться не может. Баг
отсутствует, фиксить нечего.

### Что сделано

**Убран мёртвый код в `api/NoiseApi.kt` + удалён `models/GlobalNoiseResponse.kt`**

- `NoiseApi.getGlobalNoise()` (`GET /noize_api/api/v1/global`) нигде в приложении не вызывался
  — единственная ссылка была само объявление метода. Клиент всегда получает глобальный шум
  через `NoiseState.globalNoise` (поле в ответе `GET /user/{id}`), отдельный эндпоинт `/global`
  клиенту не нужен.
- Заодно нашёл попутный факт (НЕ фиксил — это серверный код, вне области этой сессии, доступ к
  `SERVER/` требует диффа и FTP-заливки, отдельная процедура для владельца): в
  `SERVER/public_html/noize_api/api.php` метод `get_user_noise()` (используется в `/user/{id}`)
  нормализует `global_noise` из raw-шкалы 0..10 в UI-шкалу 0..5 (`$globalRaw / 2.0`), а
  отдельный роут `GET /global` (строка 100-103) отдаёт `get_global_noise($pdo)` НАПРЯМУЮ, без
  этой нормализации — вернул бы вдвое завышенное значение. Раз клиент этот эндпоинт не зовёт
  (теперь и метод убран), баг сейчас ни на что не влияет, но если владелец захочет позже
  что-то на этот эндпоинт навесить — стоит сначала поправить нормализацию на сервере.
- Заодно прогнал быструю сверку остальных Retrofit-интерфейсов (`ArtifactApi`, `AuraApi`,
  `ChatApi`, `EffectApi`, `MessagesApi`, `UserProfileApi`, `WikipediaApi`) на мёртвые методы —
  у каждого метода нашёлся хотя бы один вызывающий, `getGlobalNoise` был единственной сиротой.
- Также сверил `ShiftApi.kt` (точки: create/update/bind/touch/delete + локация) с реальным
  роутером `SERVER/public_html/api_geo/api.php` (пути, методы, обязательные поля, семантика
  `PATCH` с частичным обновлением через `array_key_exists`) — полное совпадение, багов не
  нашёл. `NoiseApi`/`noize_api/api.php` тоже сверил построчно (кроме уже описанной находки).

### Что НЕ дало результата (чтобы не повторять)

- Точечный разбор `!!`-паттернов (92 вхождения, 20 файлов) — все охранены предшествующей
  проверкой на null, реальных находок нет, направление закрыто.
- Гипотеза про повтор старого бага (Gson default-enum-адаптер рушит весь ответ на незнакомом
  значении) для `PointType` — не подтвердилась, `Point.type` типизирован как `String`, не как
  enum, конвертация через `fromServerValue` ручная на потребляющей стороне.
- Юнит-тесты на чистую логику — бэклог насыщен (`helpers/`, `models/` — все Gson-адаптеры,
  enum-парсеры и чистые хелперы покрыты предыдущими сессиями), новых непокрытых чистых функций
  не нашёл.

### Итог сборки

`assembleDebug --offline` + `testDebugUnitTest --offline` — зелёно (exit 0) и до, и после
правки. RAG переиндексирован (261 файл, 1849 чанков — минус один удалённый файл модели).
Рабочее дерево — 23 изменённых/новых + 1 удалённый файл, ничего не закоммичено.

### Дальше

- **Перспективное новое направление, только начатое**: сверка Kotlin `api/*.kt` интерфейсов с
  реальным серверным кодом `SERVER/public_html/*/api.php` (не только с `API/*.txt` доками,
  которые могут отставать от реализации). Проверены `NoiseApi`+`noize_api` и `ShiftApi`
  (`api_geo`, точки/локация) целиком. НЕ проверены: `AuraApi`↔`aura_api`, `ArtifactApi`↔
  `artifacts_api`, `ChatApi`/`MessagesApi`↔`messages_api`, `EffectApi`↔`effects_api`,
  `UserProfileApi`↔`mage_profile_api`. Это статическая проверка, живой эмулятор не нужен —
  хороший кандидат для сессии без поднятого эмулятора, как и эта.
- Серверная находка (нормализация `/global` в `noize_api/api.php`) — не трогать автономно,
  показать владельцу, эндпоинт сейчас не используется клиентом.
- Живые проверки по-прежнему висят: эффекты шума в терминале, Doze/заблокированный экран, тап
  по маркеру карты — эмулятор не поднимался уже несколько сессий подряд.
- god-классы (`EkatMaps` всё ещё 1119 строк) — нашёл крупный самодостаточный кластер, который
  МОЖНО было бы вынести (диалоги маркера/точки: `handleMarkerClick`…`showCreatePointDialog`,
  строки 262-850, ~590 строк), но сознательно не стал: без поднятого эмулятора механический
  перенос такого объёма UI-кода с диалогами/коллбэками рискован — сборка компиляцию проверит,
  но не поведение (неправильная привязка коллбэка всё равно скомпилируется). Оставляю для
  сессии, где будет живая проверка.
- **Владельцу по-прежнему стоит закоммитить рабочий диф** — растёт с каждой сессией.

**ЗАВЕРШИЛ:** 2026-08-14 22:10 — нашёл и убрал мёртвый код: `NoiseApi.getGlobalNoise()` +
модель `GlobalNoiseResponse` нигде не вызывались (клиент получает глобальный шум только через
`NoiseState.globalNoise`). Попутно обнаружил (не фиксил, вне области — серверный код) реальную
асимметрию в `noize_api/api.php`: эндпоинт `/global` отдаёт ненормализованное значение шума
(raw 0..10 вместо UI-шкалы 0..5, в отличие от `/user/{id}`), сейчас не критично — эндпоинт был
недостижим с клиента. Начал новое направление — статическую сверку Kotlin `api/*.kt` с реальным
PHP-роутером на сервере (не только с документацией `API/*.txt`): `NoiseApi`+`ShiftApi`
проверены целиком, багов кроме вышеописанного не нашли; `AuraApi`/`ArtifactApi`/`MessagesApi`/
`EffectApi`/`UserProfileApi` — не начаты, хороший бэклог для следующей сессии без эмулятора.
Также подтвердил закрытие направления `!!`-паттернов (реальных находок нет) и опроверг
гипотезу про повтор Gson-enum-бага для `PointType`. Сборка и тесты зелёные. RAG переиндексирован.

---

## Session 57 — 2026-08-15 (manual, owner-directed)

**STARTED:** 2026-08-15 00:10 — not a nightly run. Owner asked to actualise the whole
`analysis/` set, cut down the journal, and then fix the three defects the re-verification
surfaced (P1/P2/P3 in [11-status.md](11-status.md)).

### Documentation

Journal was 535 KB / 5267 lines / 56 sessions with the backlog duplicated into every entry.
Split: sessions 1–39 and 40–51 moved verbatim to `archive/`, newest 5 kept here, rotation
rules written into this file's header and into the nightly skill. New
[11-status.md](11-status.md) is now the single source of truth for state and backlog;
sessions 18–56 condensed into [08-changes-applied.md](08-changes-applied.md) waves 16–24;
stale-snapshot banners added to 01–07 and 10; `analysis/screenshots/` gitignored.

### Code

Waves 25 in [08-changes-applied.md](08-changes-applied.md) — P1 (MG locked out of map and
chat by four independent in-game gates, plus three conflicting definitions of "who is MG"),
P2 (`EkatMaps` scope never cancelled), P3 (attachments streamed instead of `readBytes()`).

### Verification

`assembleDebug --offline` green; `testDebugUnitTest --offline` 146/146 green. P1 verified
live on `emulator-5554` under `MG_Bas` with `is_in_game=false`: both buttons enabled, map
opens and syncs 46 points, chat list opens, zero `AndroidRuntime:E`. Emulator prefs left
exactly as found (`MG_Bas`, `is_in_game=false`), no server state mutated. P3 not verified
live — that needs a real message posted to production (see 11-status §A7).

### Doze mitigations (second half of the session)

Owner asked to take a run at the Doze problem rather than leave it purely as a test item.
Wave 26 in [08-changes-applied.md](08-changes-applied.md): battery-optimisation exemption
prompt on entering the game, a `setAndAllowWhileIdle` heartbeat that wakes `LocationService`
every 15 min (the only alarm type that fires in Doze without extra permissions), and a
boot / self-update receiver. All of it is mitigation, none of it is proof — the real test
protocol is now written down in [11-status.md](11-status.md) so it stops getting lost.

Verified on the emulator under forced deep idle end to end: the alarm is not deferred by Doze,
it fired in the maintenance window, the service got `ACTION_TICK` and issued a real
`GET /messages_api/chats` from inside Doze, then rescheduled itself; `MY_PACKAGE_REPLACED`
restarted the service; leaving the game cancelled the alarm. The mechanism works — but an
emulator never truly suspends its CPU, so whether it is *enough* is still a real-hardware
question (A1).

**FINISHED:** 2026-08-15 01:15 — docs restructured and rotating, P1/P2/P3 fixed, Doze
mitigations added with the test protocol recorded, everything committed by the owner's
request.

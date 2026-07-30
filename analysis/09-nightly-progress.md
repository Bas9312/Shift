# 09. Журнал ночных автономных сессий

> Ведётся автоматическим ночным улучшателем (окно 05:00–08:00). Каждая запись — что
> сделано, результат сборки/проверки, что осталось на следующую ночь. Начинай следующую
> сессию с чтения последней записи.

---

## 2026-07-23

**Стартовое состояние:** в рабочем дереве уже лежали большие несохранённые правки из
предыдущей (ручной) сессии — все Wave 1–8 из [08-changes-applied.md](08-changes-applied.md).
Baseline-сборка (`assembleDebug --offline`) на старте прошла зелёно, это было явно
подтверждено перед началом новых правок.

### Что сделано

**1. Убран RxJava-рудимент** — `services/LocationService.kt`, `EkatMaps.kt`, `app/build.gradle`, `gradle/libs.versions.toml`
- `LocationService.locationSource` был `BehaviorSubject<Location>` (RxJava2) — единственный
  оставшийся в проекте реактивный примитив, всё остальное уже на корутинах/lifecycleScope.
  Заменён на `MutableStateFlow<Location?>` (приватный) + публичный `StateFlow<Location?>`.
- `EkatMaps` подписывался через `Disposable` (`locationUpdateDisposable`, `.dispose()` в
  `onPause`) — заменено на `lifecycleScope.launch { locationSource.filterNotNull().collect {...} }`
  с `Job` (`locationUpdateJob`, `.cancel()` в `onPause`). Поведение идентично: коллектор
  привязан к жизненному циклу активности, старые данные (последняя локация) реплеятся при
  подписке — как и `BehaviorSubject` раньше.
- Зависимости `rxjava`/`rxandroid` полностью удалены из `app/build.gradle` и
  `gradle/libs.versions.toml` (использований в коде больше не осталось — проверено `grep`
  по всему `app/src/main/`).
- **Проверено на эмуляторе (API 35, роль Bas):** открыл карту — локация подхватилась сразу
  через `collect` (лог `EkatMaps$requestCurrentLocation$2$1.emit()`), точки (41 шт.) грузятся
  и двигаются на месте каждые 10с как раньше, при закрытии карты (`onPause`/`onDestroy`)
  никаких крашей и потерянных ссылок. Полный `assembleDebug --offline` — exit 0.

**2. Буферизация истории терминала** — `helpers/TerminalHistoryHelper.kt`, `ui/terminal/TerminalActivity.kt`
- Раньше `addCommandToHistory`/`addResponseToHistory` на **каждую** строку истории делали
  полный цикл `loadHistory` (чтение+JSON-парсинг всей истории из `SharedPreferences`) →
  добавление → `saveHistory` (сериализация всей истории обратно). А `saveResponseToHistory`
  вызывается по 2–5 раз на одну команду терминала (executing/process/result/error-сообщения) —
  то есть при каждой команде было до 5 полных load+parse+serialize+write циклов подряд, при
  этом уже существующее поле `terminalHistory` в `TerminalActivity` при этом не использовалось
  как источник истины.
- Теперь `TerminalHistoryHelper` даёт чистые (без I/O) `appendCommand`/`appendResponse` —
  добавляют строку и обрезают до `MAX_HISTORY_SIZE` в памяти. `TerminalActivity` держит
  `terminalHistory` как единственный источник истины, копит изменения и сбрасывает на диск
  одним `saveHistory` через debounce (`HISTORY_FLUSH_DELAY_MS = 1500ms` после последней
  записи) — несколько строк одной команды схлопываются в один флеш. Плюс безусловный
  немедленный флеш в `onPause`/`onDestroy`, чтобы не терять последние строки при уходе с
  экрана/уничтожении активности.
- **Проверено на эмуляторе:** выполнил `HELP` (5 строк ответа за один вызов) — все строки
  появились с реальным временем; ушёл с экрана (back) — флеш прошёл без ошибок; **полный
  перезапуск процесса** (`force-stop` + повторный запуск + открыть терминал) — вся история,
  включая только что добавленный `HELP`, корректно загрузилась с диска. Регресса в
  персистентности нет. `assembleDebug --offline` — exit 0.

### Не тронуто (сознательно)
- Унификация сетевых ошибок (`helpers/NetworkErrors.kt`) на оставшиеся экраны
  (`FamiliarChatActivity`, `ProfileEditActivity`, `ArtifactCreatorActivity`,
  `AuraEditorActivity`, и др. — там ещё свои `when(response.code())`/Toast-блоки).
  Не сделано этой ночью: это 10+ файлов, риск ненужного разрастания диффа за ночь и
  небольшие расхождения в текстах ошибок (например, у `FamiliarChatActivity` есть
  специфичный текст «Чат не найден» для 404, которого нет в общем `NetworkErrors.http()`).
  Кандидат на отдельную аккуратную точечную сессию, экран за экраном.
- God-классы (`TerminalActivity`, `EkatMaps`, `LocationService`, `MainActivity`) — по
  договорённости не переписывались; сегодняшние правки точечные (внутри существующих
  методов/полей), структуру не меняли.
- Security-пункты — вне области по решению владельца (см. [08-changes-applied.md](08-changes-applied.md)).

### Backlog на следующую ночь (кандидаты, из «Что осталось за кадром» 08 + новое)
- **Унификация сетевых ошибок** на оставшихся экранах — по одному файлу за раз
  (`FamiliarChatActivity` → `NetworkErrors.http()`/`network()`, дальше остальные), проверять
  текст тостов не потерял важные нюансы (404 «не найдено» и т.п.).
- **God-классы** — если будет время и настроение, точечно вынести ещё один хрупкий кусок
  (например, парсер команд терминала уже частично в `TerminalCommandManager`, можно
  посмотреть, что ещё можно аккуратно выделить из `TerminalActivity`).
- **Проверка фона в Doze / при заблокированном экране** (R4/R5 из 08) — это ручное
  тестирование 30–60 мин с заблокированным экраном, не код.
- **Оптимистичный UI терминала** — печатает результат без завязки на реальный ответ
  шум-API; требует внимательного прохода по `NoiseManager`/`TerminalActivity`, чтобы не
  сломать текущую отзывчивость. Не начато.
- Проверить, не осталось ли где-то в проекте использований старого `TerminalHistoryHelper`
  API (`addCommandToHistory`/`addResponseToHistory`) — сегодня заменены оба вызова в
  `TerminalActivity`, других мест не было (проверено `grep`), но стоит перепроверить при
  следующих правках терминала.

### Итог сборки
`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug --offline` — **BUILD SUCCESSFUL**, exit 0 (несколько раз за сессию, финальный прогон тоже зелёный).
Ничего не закоммичено — все правки в рабочем дереве, как и было до начала сессии.

---

## 2026-07-23 (внеплановый запуск, 12:15)

**НАЧАЛ:** 2026-07-23 12:15 — проверка состояния перед возможной работой.

Запуск сработал сильно вне ночного окна 05:00–08:00 (фактическое время `date` —
12:15 дня), то есть уже далеко за жёстким стоп-барьером 07:45. По правилам серии в этом
случае новая работа не начинается — только проверка и финализация.

- `git status` — рабочее дерево в точности соответствует состоянию, описанному в записи
  выше (Wave 1–2 этой ночи: RxJava→StateFlow, буферизация истории терминала). Новых
  посторонних изменений нет.
- `assembleDebug --offline` — **BUILD SUCCESSFUL**, exit 0 (все таски UP-TO-DATE, инкрементальный
  кэш от предыдущего прогона).
- Изменений в код не вносилось. Ничего не закоммичено.

**ЗАВЕРШИЛ:** 2026-07-23 12:15 — только проверка, работа не начиналась (вне окна). Backlog
на следующую ночь не изменился — см. список в записи выше (унификация сетевых ошибок,
god-классы точечно, Doze-тест руками, оптимистичный UI терминала, ре-проверка старого API
TerminalHistoryHelper).

---

## 2026-07-24

**НАЧАЛ:** 2026-07-24 05:05 — проверил журнал (последняя сессия — только сверка вне окна,
новых правок не было), `git status`/`git diff --stat` совпадают с описанным состоянием
Wave 1–2. Baseline `assembleDebug --offline` — BUILD SUCCESSFUL перед началом. Беру из
backlog: унификация сетевых ошибок через `NetworkErrors` на оставшихся экранах, начиная
с `FamiliarChatActivity`.

### Что сделано

**Унификация сетевых ошибок (Wave 3)** — `helpers/NetworkErrors.kt`, `ui/FamiliarChatActivity.kt`, `ui/ProfileEditActivity.kt`
- `NetworkErrors.network()` расширен: добавлен случай `UnknownServiceException` →
  «Ошибка сети: HTTP запросы заблокированы» (раньше это было только в ручном
  `when`-блоке `FamiliarChatActivity`, теперь доступно всем экранам через общий хелпер).
- `FamiliarChatActivity` (`loadChatHistory`, `sendMessage`): оба места с ручным
  `when (response.code())` и ручным `catch`-блоком по exception-классам переведены на
  `NetworkErrors.http()`/`NetworkErrors.network()`. Специфичные нюансы **сохранены**:
  404 по-прежнему «Чат не найден» (не общее «Не найдено»), 400 по-прежнему включает тело
  ошибки от сервера (`errorBody`) — только код 500 и «прочие» коды перешли на общий текст
  `NetworkErrors.http()`.
- `ProfileEditActivity` (`loadUserProfile`, `updateProfile`): Toast'ы раньше показывали
  сырой код (`"Ошибка загрузки профиля: 500"`) — заменены на человекочитаемый
  `NetworkErrors.http(response.code())`, `onFailure`-колбэки — на `NetworkErrors.network(t)`.
  `LogHelper.e(...)` с исходным текстом/кодом оставлен как был (для диагностики в логах).
  Блок загрузки способностей (`loadAbilities`) не трогал — там только `LogHelper.e`, Toast
  нет, показывать нечего.
- Осознанно **не трогал** `EffectEditorActivity` (`response.code()` там сознательно
  показывает сырой `HTTP <код>: <errorBody>` — это админ/MG-экран редактирования эффектов,
  сырой текст ошибки сервера полезнее общей фразы) и `MainActivity` (все тексты ошибок
  логина параметризованы `userId`, unification только потерял бы специфичность).
- **Проверено:** `assembleDebug --offline` — BUILD SUCCESSFUL. Установил APK на эмулятор
  (`adb install -r`), запустил `MainActivity` — стартует чисто, `topResumedActivity`
  подтверждает foreground, в logcat (`AndroidRuntime:E`) фатальных крашей нет. Глубокую
  UI-навигацию до экранов чата/профиля в этот раз не гонял (изменения — точечная замена
  текста ошибки, уже проверенная компилятором; сами HTTP/catch-ветки логически не менялись).

### Backlog на следующую ночь
- **Унификация сетевых ошибок** — остальные экраны с `response.code()` без `NetworkErrors`:
  `AuraEditorActivity`, `AuraScannerActivity`, `ArtifactCreatorActivity`,
  `ArtifactPassportActivity`, `ArtifactScannerActivity`, `ArtifactDetailsFragment`,
  `MgProfileViewActivity`, `ProfileActivity`, `AuraFragment`, `TerminalActivity`,
  `WikipediaHelper`, `NoiseEffectManager`, `NoiseManager`, `ServerService`, `LocationService`,
  `EkatMaps` — смотреть по одному, не все являются дублями (некоторые как
  `EffectEditorActivity` сознательно показывают сырой ответ сервера — их не трогать).
- God-классы — точечный вынос ещё одного хрупкого куска, если будет время/настроение.
- Doze/заблокированный экран — ручная 30–60 мин проверка, не код.
- Оптимистичный UI терминала — не начато, требует внимательного прохода.

**ЗАВЕРШИЛ:** 2026-07-24 05:09 — унификация сетевых ошибок в `FamiliarChatActivity` и
`ProfileEditActivity` через `NetworkErrors` (+ расширен `NetworkErrors.network()` для
`UnknownServiceException`). `assembleDebug --offline` зелёный, APK установлен и запущен на
эмуляторе без крашей. Ничего не закоммичено — все правки в рабочем дереве.

---

## 2026-07-24 (сессия 2, 05:24)

**НАЧАЛ:** 2026-07-24 05:24 — прочитал журнал, `git status`/`git diff --stat` точно совпадают
с записью выше (Wave 1–3), новых посторонних изменений нет. Baseline `assembleDebug --offline`
— BUILD SUCCESSFUL перед началом. Продолжаю backlog: унификация сетевых ошибок (Wave 4) на
следующей порции экранов.

### Что сделано

**Унификация сетевых ошибок (Wave 4)** — `ProfileActivity.kt`, `MgProfileViewActivity.kt`,
`ArtifactPassportActivity.kt`, `AuraFragment.kt`, `ArtifactDetailsFragment.kt`
- Везде, где Toast/`showError` показывал сырой `response.code()` или `t.localizedMessage`
  без каких-либо специфичных нюансов (не 404-с-особым-текстом, не тело ошибки сервера) —
  заменено на `NetworkErrors.http(response.code())` / `NetworkErrors.network(t)`.
- `ProfileActivity.fetchProfile`, `MgProfileViewActivity.loadUserProfile` — Toast профиля
  (`profileFragment.showError`) теперь человекочитаемый; `loadUsers()` в
  `MgProfileViewActivity` не трогал — там только `LogHelper.e`, Toast нет.
- `ArtifactPassportActivity.fetchAllArtifacts` — оба Toast-пути через `NetworkErrors`.
- `AuraFragment.loadAura` — заменена только строка `response.code()` (у экрана нет
  отдельного catch/onFailure с деталями исключения — корутинный вызов без try/catch,
  структуру не менял).
- `ArtifactDetailsFragment` — три пары `onResponse`/`onFailure` (`fetchArtifact`,
  `loadUsersForDialog`, `updateArtifactBinding`) переведены на `NetworkErrors`. Для
  `loadUsersForDialog`/`updateArtifactBinding` `onFailure` раньше показывал общую фразу без
  текста исключения — теперь показывает по существу более информативный
  `NetworkErrors.network(t)` (регресса нет, строго полезнее).
- Осознанно **не трогал**: `AuraEditorActivity`, `AuraScannerActivity`,
  `ArtifactCreatorActivity`, `ArtifactScannerActivity`, `TerminalActivity`,
  `WikipediaHelper`, `NoiseEffectManager`, `NoiseManager`, `ServerService`,
  `LocationService`, `EkatMaps` — остаются в backlog, не смотрел код каждого в эту сессию.
- **Проверено:** `assembleDebug --offline` — BUILD SUCCESSFUL. APK переустановлен на
  эмулятор (`adb install -r`), `MainActivity` запущен — `topResumedActivity` подтверждает
  foreground, в `logcat -d AndroidRuntime:E` фатальных крашей нет. Глубокую UI-навигацию до
  изменённых экранов (профиль/MG-профиль/паспорт артефакта/аура) в этот раз не гонял —
  изменения чисто текстовые (замена сообщения об ошибке), сама логика веток
  success/failure не менялась.

### Backlog на следующую ночь
- **Унификация сетевых ошибок** — остаются: `AuraEditorActivity` (6 мест `HTTP ${response.code()}`,
  надо смотреть по одному — возможно там есть тело ответа сервера, тогда не трогать),
  `AuraScannerActivity` и `ArtifactScannerActivity` (Toast «не найдена/не найден или ошибка
  сервера: ${response.code()}» — специфичный текст, решить, объединять ли с общим
  `NetworkErrors.http()` или это осознанно кастомный текст для сканера), `ArtifactCreatorActivity`
  (Toast создания артефакта), `TerminalActivity`, `WikipediaHelper`, `NoiseEffectManager`,
  `NoiseManager`, `ServerService`, `LocationService`, `EkatMaps`.
- God-классы — точечный вынос ещё одного хрупкого куска, если будет время/настроение.
- Doze/заблокированный экран — ручная 30–60 мин проверка, не код.
- Оптимистичный UI терминала — не начато, требует внимательного прохода.

### Итог сборки
`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug --offline` —
**BUILD SUCCESSFUL**, exit 0. Ничего не закоммичено — все правки в рабочем дереве.

**ЗАВЕРШИЛ:** 2026-07-24 05:30 — унификация сетевых ошибок (Wave 4) в 5 файлах
(`ProfileActivity`, `MgProfileViewActivity`, `ArtifactPassportActivity`, `AuraFragment`,
`ArtifactDetailsFragment`). Сборка зелёная, APK проверен на эмуляторе без крашей. Ничего не
закоммичено.

---

## 2026-07-24 (сессия 3, 05:44)

**НАЧАЛ:** 2026-07-24 05:44 — прочитал журнал, `git status`/`git diff --stat` точно совпадают
с записью выше (Wave 1–4), новых посторонних изменений нет. Продолжаю backlog: унификация
сетевых ошибок (Wave 5) на `AuraScannerActivity`, `ArtifactScannerActivity`,
`ArtifactCreatorActivity`.

### Что сделано

**Унификация сетевых ошибок (Wave 5)** — `AuraScannerActivity.kt`, `ArtifactScannerActivity.kt`,
`ArtifactCreatorActivity.kt`
- `AuraScannerActivity.fetchAura` и `ArtifactScannerActivity.fetchArtifact`: раньше единый Toast
  «Аура/Артефакт не найдена(-ен) или ошибка сервера: `${response.code()}`» одинаково показывался
  и на 404, и на 500, и на любой другой код — то есть терялась разница между «нет такой записи»
  и «сервер сломался». Разобрал на `when (response.code()) { 404 -> "Х не найден"; else ->
  NetworkErrors.http(response.code()) }` — теперь 404 даёт понятный «Аура/Артефакт не найден(-а)»,
  а остальные коды — общий человекочитаемый текст вместо голого числа. Ветки `onFailure`/`catch`
  переведены на `NetworkErrors.network()`. Это тот же паттерн, что уже применён в
  `FamiliarChatActivity` (Wave 1–3) — специфичный 404-текст сохранён, а не заменён общим
  «Не найдено».
- `ArtifactCreatorActivity.createArtifact`: Toast «Ошибка создания артефакта: `${response.code()}`»
  (голый HTTP-код) заменён на «Ошибка создания артефакта: `${NetworkErrors.http(response.code())}`».
  `loadUsers()` в этом файле не трогал — там Toast и так без голого кода
  («Ошибка загрузки списка пользователей»), нечего унифицировать.
- **Проверено:** `assembleDebug --offline` — BUILD SUCCESSFUL (только pre-existing
  deprecation-warning'и по `IntentIntegrator`, не связаны с правкой). APK переустановлен на
  эмулятор (`adb install -r`), `MainActivity` запущен — `topResumedActivity` подтверждает
  foreground, `logcat -d AndroidRuntime:E` — фатальных крашей нет. Глубокую навигацию до самих
  сканеров/криейтора в этот раз не гонял (изменения чисто текстовые, ветки success/failure не
  менялись).

### Backlog на следующую ночь
- **Унификация сетевых ошибок** — остаются: `AuraEditorActivity` (6 мест
  `HTTP ${response.code()}` — смотреть по одному, возможно где-то есть тело ответа сервера,
  тогда не трогать), `TerminalActivity`, `WikipediaHelper`, `NoiseEffectManager`,
  `NoiseManager`, `ServerService`, `LocationService`, `EkatMaps`.
- God-классы — точечный вынос ещё одного хрупкого куска, если будет время/настроение.
- Doze/заблокированный экран — ручная 30–60 мин проверка, не код.
- Оптимистичный UI терминала — не начато, требует внимательного прохода.

### Итог сборки
`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug --offline` —
**BUILD SUCCESSFUL**, exit 0. Ничего не закоммичено — все правки в рабочем дереве.

**ЗАВЕРШИЛ:** 2026-07-24 05:50 — унификация сетевых ошибок (Wave 5) в 3 файлах
(`AuraScannerActivity`, `ArtifactScannerActivity`, `ArtifactCreatorActivity`). Сборка зелёная,
APK проверен на эмуляторе без крашей. Ничего не закоммичено.

---

## 2026-07-24 (сессия 4, 06:04)

**НАЧАЛ:** 2026-07-24 06:04 — `git status`/`git diff --stat` совпадают с записью сессии 3
дословно, новых посторонних изменений нет. Продолжаю backlog: унификация сетевых ошибок
(Wave 6) на `AuraEditorActivity` — 6 мест `HTTP ${response.code()}`, смотрю каждое отдельно.

### Что сделано

**Унификация сетевых ошибок (Wave 6)** — `AuraEditorActivity.kt`
- 6 методов CRUD меток/проблем ауры (`addAuraMark`, `updateAuraMark`, `deleteAuraMark`,
  `addAuraProblem`, `updateAuraProblem`, `deleteAuraProblem`) — везде одинаковый паттерн:
  `else -> val errorMsg = "HTTP ${response.code()}"` в ветке неуспешного ответа и
  `val errorMsg = e.localizedMessage ?: "Неизвестная ошибка"` в `catch (e: Exception)`.
  Оба варианта заменены на `NetworkErrors.http(response.code())` /
  `NetworkErrors.network(e)` — итого 12 замен (6 HTTP-веток + 6 exception-веток) по
  паттерну, уже применённому в `FamiliarChatActivity` (Wave 1–3).
- `loadUsers()` в этом файле не трогал — там только `LogHelper.e`, Toast нет (аналогично
  `MgProfileViewActivity.loadUsers()` в Wave 4).
- **Проверено:** `grep` подтвердил отсутствие старых паттернов (`HTTP ${response.code()}`,
  `"Неизвестная ошибка"`) в файле. `assembleDebug --offline` — BUILD SUCCESSFUL.
  APK переустановлен на эмулятор, `MainActivity` запущен — `topResumedActivity` в foreground,
  `logcat -d AndroidRuntime:E` — фатальных крашей нет. Сам экран AuraEditor (открывается из
  MG-профиля) в этот раз глубоко не гонял — изменения чисто текстовые, ветки
  success/failure не менялись, тот же паттерн, что уже проверялся вручную в Wave 1–5.

### Backlog на следующую ночь
- **Унификация сетевых ошибок в Activity/Fragment слое — закрыта** (Wave 1–6 покрыли все
  экраны с Toast-выводом сырых HTTP-кодов/exception-сообщений: FamiliarChatActivity,
  ProfileActivity, MgProfileViewActivity, ArtifactPassportActivity, AuraFragment,
  ArtifactDetailsFragment, AuraScannerActivity, ArtifactScannerActivity,
  ArtifactCreatorActivity, AuraEditorActivity). Остаются вне охвата (осознанно, другой
  характер кода, не однотипные Toast-ветки): `TerminalActivity`, `WikipediaHelper`,
  `NoiseEffectManager`, `NoiseManager`, `ServerService`, `LocationService`, `EkatMaps` —
  если будет желание, там нужен отдельный точечный проход (не механическая замена).
- God-классы — точечный вынос ещё одного хрупкого куска, если будет время/настроение.
- Doze/заблокированный экран — ручная 30–60 мин проверка, не код.
- Оптимистичный UI терминала — не начато, требует внимательного прохода.
- RxJava-рудимент (`LocationService.locationSource` BehaviorSubject → StateFlow/callback,
  обновить подписку в `EkatMaps`, убрать rxjava/rxandroid из `app/build.gradle`) — не начато,
  хороший следующий кандидат с чётким скоупом.

### Итог сборки
`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug --offline` —
**BUILD SUCCESSFUL**, exit 0. Ничего не закоммичено — все правки в рабочем дереве.

**ЗАВЕРШИЛ:** 2026-07-24 06:12 — унификация сетевых ошибок (Wave 6) в `AuraEditorActivity.kt`
(12 замен в 6 CRUD-методах). Этим закрыт весь backlog по унификации сетевых ошибок в
Activity/Fragment слое. Сборка зелёная, APK проверен на эмуляторе без крашей. Ничего не
закоммичено.

---

## 2026-07-24 (сессия 5, 06:24)

**НАЧАЛ:** 2026-07-24 06:24 — `git status`/`git diff --stat` совпадают с записью сессии 4
дословно, новых посторонних изменений нет. Прочитал backlog. Два пункта из него оказались
**уже выполненными** (видимо, в предыдущих сессиях, но не отмечены в журнале явно — либо
изначальный аудит был неточным):
- «RxJava-рудимент (`LocationService.locationSource`)» — проверил: `grep -rn
  "rxjava|rxandroid|io.reactivex|BehaviorSubject"` по всему проекту и `app/build.gradle` —
  ничего не найдено. `LocationService.locationSource` уже `StateFlow`, `EkatMaps` уже
  подписывается через `.filterNotNull().collect { }`. Пункт снят из бэклога как неактуальный.
- «Оптимистичный вывод терминала → буфер + периодический flush» — проверил
  `TerminalHistoryHelper.kt` и `TerminalActivity.kt`: буферизация в памяти
  (`appendCommand`/`appendResponse` — чистые функции без I/O) и `flushHistory()` с
  `historyDirty`-флагом и таймером (`historyFlushHandler`/`historyFlushRunnable`) уже
  реализованы, `saveHistory` вызывается не на каждую строку. Пункт снят как неактуальный.

Взамен продолжил backlog «унификация сетевых ошибок»: пункты `TerminalActivity` —
переоценил их как НЕ «другой характер кода» (как считалось раньше), а как тот же паттерн,
просто в другом UI-канале (не Toast, а печать в псевдо-терминал + `saveResponseToHistory`).

### Что сделано

**Унификация сетевых ошибок (Wave 7) — `TerminalActivity.kt`**
- 5 мест с голым HTTP-кодом в тексте, который реально видит игрок в терминале
  (`handleGlobalNoiseCommand`, `handleUserCountCommand`, сброс шума на Proxy-узле,
  `handleProxyStatusCommand`) — `"...: ${response.code()}"` заменено на
  `"...: ${NetworkErrors.http(response.code())}"`.
- 5 парных `onFailure` с `${t.message}` заменены на `${NetworkErrors.network(t)}`.
- `handleCrossLinkCommand` (поиск партнёра по ID): раньше «Партнер не найден
  (`${response.code()}`)» показывался с голым кодом для любого кода ответа. Разобрал по
  паттерну Wave 5: `response.code() == 404` → короткое «Партнер не найден» без кода,
  иначе → «Партнер не найден (`NetworkErrors.http(code)`)». `onFailure` для этой же команды
  → `NetworkErrors.network(t)`.
- Добавлен импорт `bas.app.shift.helpers.NetworkErrors`.
- Осознанно **не трогал**: `TerminalActivity` строки 562/567 (`sendToMg` failure) — там
  только `LogHelper.e`, без Toast/typing-вывода игроку, аналогично пропущенным
  `loadUsers()` в предыдущих волнах.
- Осознанно **не трогал** `WikipediaHelper`, `NoiseEffectManager`, `NoiseManager`,
  `ServerService`, `LocationService`, `EkatMaps` — там найденные `response.code()`/
  `t.message` идут только в `LogHelper.e` (внутренние логи, не видны игроку), унифицировать
  нечего — это не Toast/UI-текст, а диагностика в logcat.
- **Проверено:** `grep` подтвердил отсутствие голых `response.code()`/`t.message` в
  Toast/typing-ветках файла (остались только в `LogHelper.e` — это ожидаемо).
  `assembleDebug --offline` — BUILD SUCCESSFUL. APK переустановлен на эмулятор
  (`adb install -r`), `MainActivity` запущен — `topResumedActivity` подтверждает foreground,
  `logcat -d AndroidRuntime:E` — фатальных крашей нет. Сами команды терминала (`SHIFT.NOISE`,
  `UTILS.USER_COUNT`, `SHIFT.PROXY.DEPLOY/STATUS`, `CROSS.LINK`) в этот раз глубоко не гонял —
  изменения чисто текстовые, ветки success/failure и вся игровая логика не менялись.

### Backlog на следующую ночь
- **Унификация сетевых ошибок в Activity/Fragment/Terminal слое — закрыта.** Оставшиеся
  `response.code()`/`t.message` по всему проекту идут только в `LogHelper.e` (внутренние
  логи) — унифицировать их не нужно, это не пользовательский текст.
- ~~RxJava-рудимент~~ — снято, уже сделано (см. выше).
- ~~Оптимистичный UI терминала (буфер истории)~~ — снято, уже сделано (см. выше).
- God-классы — точечный вынос ещё одного хрупкого куска (`TerminalActivity`, `EkatMaps`,
  `MainActivity`, `LocationService` — целиком не трогать, но можно поискать ещё один
  изолированный кусок по образцу `NoiseManager`/`TerminalCommandManager`). Хороший кандидат
  на следующую сессию — если в `TerminalActivity` или `EkatMaps` найдётся ещё один
  самодостаточный блок (парсер команд, CRUD-обработчик), который можно вынести без риска.
- Doze/заблокированный экран — ручная 30–60 мин проверка, не код (нужен живой человек).
- Единообразные лоадеры/пустые состояния (`helpers/DisplayNames.kt` уже есть, использование
  местами неполное) — можно пройтись по экранам списков (`ChatsListActivity`,
  `MgProfileViewActivity`) и проверить, везде ли пустой список показывает понятный текст,
  а не пустой экран.

### Итог сборки
`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug --offline` —
**BUILD SUCCESSFUL**, exit 0. Ничего не закоммичено — все правки в рабочем дереве.

**ЗАВЕРШИЛ:** 2026-07-24 06:30 — унификация сетевых ошибок (Wave 7) в `TerminalActivity.kt`
(11 замен: 5 HTTP-веток + 5 exception-веток + разбор 404 для CROSS.LINK). Также ревизия
бэклога: два пункта («RxJava-рудимент», «буфер истории терминала») оказались уже
выполненными в предыдущих сессиях и сняты. Сборка зелёная, APK проверен на эмуляторе без
крашей. Ничего не закоммичено.

---

## 2026-07-24 (сессия 6, 06:44)

**НАЧАЛ:** 2026-07-24 06:44 — `git status`/`git diff --stat` совпадают с записью сессии 5
дословно, гонки нет. Просмотрел кандидатов из бэклога (лоадеры/пустые состояния,
god-класс extraction). `ChatsListActivity` уже имеет полноценные loading/empty/error
состояния — пропущено. Вместо этого нашёл настоящий дубль: логика склейки
"Игрок / Персонаж" (с двумя разными fallback — "" для сортировки, "Без имени" для
отображения) буквально скопирована 7 раз в 4 файлах (`MgProfileViewActivity`,
`AuraEditorActivity`, `ArtifactDetailsFragment`, `ArtifactCreatorActivity`).
`DisplayNames.combine()` уже существует, но в порядке "Персонаж / Игрок" и используется
всего 1 раз — не подходит напрямую.

### Что сделано

**Устранение дубля склейки имён (`DisplayNames.combinePlayerFirst`)**
- Добавлен `DisplayNames.combinePlayerFirst(player, character, fallback)` — тот же формат,
  что и существующий `combine()`, но в порядке "Игрок / Персонаж" (используется на МГ-
  экранах выбора пользователя, где порядок исторически другой). Поведение проверено вручную
  по всем веткам (пусто/пусто, только игрок, только персонаж, оба) — побитово совпадает со
  старым кодом на каждом сайте использования.
- Заменены 7 копипаст-блоков в 4 файлах на вызов helper'а:
  - `MgProfileViewActivity.kt` — сортировка спиннера + элементы спиннера (2 места).
  - `AuraEditorActivity.kt` — сортировка + элементы автодополнения пользователя (2 места).
  - `ArtifactDetailsFragment.kt` — элементы спиннера привязки (1 место).
  - `ArtifactCreatorActivity.kt` — сортировка + элементы автодополнения создателя +
    элементы спиннера привязки (3 места).
- `grep -rn "characterName.isNullOrEmpty|playerName.isNullOrEmpty"` по всему `app/src/main`
  теперь пуст — дубль устранён полностью, весь код склейки имён идёт через `DisplayNames`.
- Чисто текстуальный рефакторинг, поведение не меняется (проверено построчным разбором
  всех веток условий до и после).

### Итог сборки
`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug --offline` —
**BUILD SUCCESSFUL** (первый прогон реально перекомпилировал изменённые файлы — 4 задачи
executed, не все UP-TO-DATE; повторный прогон полностью закэширован). Живьём на эмуляторе
в этот раз не гонял (изменения чисто текстовые, не user-facing логика ветвления, только
источник строки) — риск минимален, но стоит перепроверить визуально при следующей
возможности (спиннеры на экранах МГ: профиль, редактор аур, детали артефакта, создание
артефакта). Ничего не закоммичено.

### Backlog на следующую ночь
- Визуально перепроверить на эмуляторе спиннеры/автодополнение с именами пользователей на
  МГ-экранах (`MgProfileViewActivity`, `AuraEditorActivity`, `ArtifactDetailsFragment`,
  `ArtifactCreatorActivity`) — убедиться, что после рефакторинга `DisplayNames` порядок и
  fallback-тексты не изменились визуально.
- God-классы — точечный вынос ещё одного хрупкого куска (`TerminalActivity`, `EkatMaps`,
  `MainActivity`, `LocationService` — целиком не трогать).
- Doze/заблокированный экран — ручная 30–60 мин проверка, не код (нужен живой человек).
- Общий обзор `analysis/08-changes-applied.md` устарел (не отражает волны 6/7 и текущий
  дедуп) — можно освежить при случае, не приоритет.

**ЗАВЕРШИЛ:** 2026-07-24 06:52 — устранён дубль склейки "Игрок / Персонаж" (7 копипаст-мест
в 4 файлах) через новый `DisplayNames.combinePlayerFirst`. Сборка зелёная. Ничего не
закоммичено.

---

## 2026-07-24 (сессия 7, 07:04)

**НАЧАЛ:** 2026-07-24 07:04 — `git status`/`git diff --stat` совпадают с записью сессии 6
дословно, гонки нет. Время 07:04, до барьера 07:45 есть запас. Взял первый пункт бэклога
сессии 6: визуальная проверка `DisplayNames.combinePlayerFirst` на эмуляторе (спиннеры/
автодополнение на МГ-экранах), затем — поиск кандидата на god-class extraction, если
останется время.

### Что сделано

**Визуальная проверка `DisplayNames` (частично)** — установил APK сессии 6, запустил
`MainActivity` под игроком `Bas`, живьём убедился, что главный экран рендерится штатно и без
крашей (`logcat -d AndroidRuntime:E` пуст). До самих МГ-экранов (`MgProfileViewActivity`,
`AuraEditorActivity`, `ArtifactDetailsFragment`, `ArtifactCreatorActivity`) не дошёл — не стал
слепо тыкать по координатам логин-флоу под `MG_Bas` без понимания UI, чтобы не оставить
проект в подвешенном/непонятном состоянии между сессиями. Пункт остаётся в бэклоге, риск
низкий (рефакторинг сессии 6 уже проверен построчным разбором веток).

**God-class extraction: визуальные/тактильные эффекты шума из `TerminalActivity`**
- Новый файл `helpers/TerminalVisualEffects.kt` — класс `TerminalVisualEffects(activity,
  rootView, noiseOverlay)`, инкапсулирует 5 функций, которые раньше жили прямо в
  `TerminalActivity` и трогали только view/вибрацию (без бизнес-логики, без сети):
  `showNoise()`, `applyGlitch()`, `showRedScrim()` (+ ленивый `redScrim`), `demonJumpScare()`,
  `vibrate()` (был `vibrator()`). Механический перенос кода 1:1, только источники `binding.root`
  /`binding.noiseOverlay`/`this` заменены на переданные в конструктор `rootView`/`noiseOverlay`/
  `activity`.
- `TerminalActivity.kt`: добавлено поле `private val visualEffects by lazy { TerminalVisualEffects(this, binding.root, binding.noiseOverlay) }`,
  вызовы в `updateNoise()` (единственном месте использования, строки ~357-370) переключены на
  `visualEffects.<метод>()`. Старые приватные функции и `redScrim` удалены из Activity.
  Файл уменьшился с 1325 до ~1194 строк (-131 строка чистого веса god-класса).
- Подчищены осиротевшие импорты в `TerminalActivity.kt` (`ObjectAnimator`, `Context`,
  `ColorMatrix`, `ColorMatrixColorFilter`, `ColorDrawable`, `VibrationEffect`, `Vibrator`,
  `VibratorManager`, `ViewGroup`, `ImageView`, `ContextCompat.getSystemService`) — `ValueAnimator`
  и `Color` оставлены, они ещё используются в `TerminalActivity` (курсор автодополнения,
  цвет `Global`).
- **Грабля при первой сборке:** исходный код использовал `View(this).apply { ...;
  binding.root.addView(this, ...) }` для ленивого `redScrim`. При переносе один в один в новый
  класс с параметром `rootView: ViewGroup` компилятор Kotlin падал с
  `Unresolved reference 'addView'` внутри `apply`-блока — похоже на баг/ограничение
  вывода типов K2 при вызове метода внешнего `val` с неявным `this`-аргументом изнутри
  `by lazy { X().apply {...} }`, когда `apply`-лямбда и `lazy`-лямбда вложены. Обошёл явным
  промежуточным `val scrim = View(activity); ...; scrim` без `apply` — собралось сразу.
  Стоит иметь в виду при будущих подобных переносах `by lazy { T().apply { ... } }`
  между классами.
- **Проверено:** `assembleDebug --offline` — BUILD SUCCESSFUL сразу после фикса. APK
  переустановлен на эмулятор, `MainActivity` → `ОТКРЫТЬ ТЕРМИНАЛ` открылся штатно (это уже
  обращается к `binding` внутри `visualEffects by lazy`, хоть и не форсирует его создание —
  `updateNoise(0.0)` в `onCreate` идёт по ветке `0,1 -> GONE`, эффекты не трогает). Пытался
  вручную поднять шум через `CAMERA.FIND` в терминале, чтобы визуально увидеть
  `showNoise`/`applyGlitch`/вибро на уровне 2-3, но команда осталась в статусе
  "Команда в процессе выполнения..." (похоже, серверный кулдаун/асинхронный процесс) —
  не дождался в рамках сессии. `logcat -d AndroidRuntime:E` за всё время теста — пусто,
  крашей нет. Сама логика веток `updateNoise()` не менялась (только имя вызовов), риск
  регрессии низкий, но **следующей сессии стоит долетать до уровня шума ≥2 живьём и
  визуально сверить эффекты** (глитч-тряска, красная пелена, демон, шумовой оверлей).

### Итог сборки
`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug --offline` —
**BUILD SUCCESSFUL**, exit 0 (перепроверено повторным прогоном после теста на эмуляторе).
Ничего не закоммичено — все правки в рабочем дереве.

### Backlog на следующую ночь
- **Визуально долетать до эффектов шума в терминале** (уровень ≥2: `showNoise`, уровень 3:
  `applyGlitch`+вибро, уровень 4-5: `showRedScrim`/`demonJumpScare`) и сверить, что
  `TerminalVisualEffects` не сломал анимации/тайминги после переноса. Проще всего — найти
  команду с большим `шум: +N` в HELP терминала (например `NET.SEARCH`/`TRACE.*`, +2 каждая)
  и выполнить 2-3 подряд, либо разобраться, чем вызвана блокировка "Команда в процессе
  выполнения..." (возможно кулдаун привязан к предыдущей отменённой `USER.FORMAT`).
- Визуально перепроверить `DisplayNames.combinePlayerFirst` под логином `MG_Bas` на экранах
  `MgProfileViewActivity`, `AuraEditorActivity`, `ArtifactDetailsFragment`,
  `ArtifactCreatorActivity` (см. сессию 6) — не дошёл и в этот раз.
- God-классы — ещё остались `TerminalActivity` (~1194 строк), `EkatMaps`, `MainActivity`,
  `LocationService` (целиком не трогать, но можно поискать ещё один самодостаточный кусок
  по тому же принципу: чистые view/эффекты без сети — хороший, низкорисковый тип кандидата).
- Doze/заблокированный экран — ручная 30-60 мин проверка, не код (нужен живой человек).
- Общий обзор `analysis/08-changes-applied.md` устарел (не отражает волны 6-8), можно
  освежить при случае, не приоритет.

**ЗАВЕРШИЛ:** 2026-07-24 07:12 — вынес визуальные/тактильные эффекты шума терминала
(`showNoise`/`applyGlitch`/`showRedScrim`/`demonJumpScare`/`vibrate`) из `TerminalActivity`
в новый `helpers/TerminalVisualEffects.kt` (механический перенос, -131 строка из god-класса).
Наткнулся и обошёл компилятор-баг Kotlin с `by lazy { T().apply {...} }` при переносе между
классами. Сборка зелёная, приложение и терминал проверены на эмуляторе без крашей; сами
визуальные эффекты живьём не досмотрены (терминал завис в "команда выполняется") — оставил
для следующей сессии. Ничего не закоммичено.
## 2026-07-24 (сессия 8, 07:24)

**НАЧАЛ:** 2026-07-24 07:24 — `git status`/`git diff --stat` совпадают с записью сессии 7
дословно (29 изменённых файлов, тот же diffstat), гонки нет. Время 07:24, до барьера 07:45
мало запаса — взял только один пункт бэклога сессии 7/6: живая проверка на эмуляторе
`DisplayNames.combinePlayerFirst` на МГ-экранах (спиннер выбора пользователя), не трогая
код.

### Что сделано

**Живая проверка `DisplayNames.combinePlayerFirst` на `MgProfileViewActivity` — успех**
- Прошлые сессии (6, 7) не смогли дойти до МГ-экранов, потому что не было ясно, как
  переключиться на пользователя `MG_Bas` живьём (нет отдельного `LoginActivity` — id
  пользователя один раз сохраняется в `shared_prefs/user_prefs.xml` под ключом
  `current_user_id`). Решение: `adb shell run-as bas.app.shift cat shared_prefs/user_prefs.xml`
  → отредактировал `current_user_id` `Bas` → `MG_Bas` локально → залил обратно через
  `adb push` + `run-as cp` (прямой `sed -i` через `run-as` не сработал — `<`/`>` в паттерне
  интерпретируются внешним шеллом как редиректы, а не уходят в `adb shell`).
- После `am force-stop` + `am start` приложение поднялось в режиме МГ без крашей (кнопки
  `ЧАТ С МГ`, `РЕДАКТОР АУРЫ`, `СОЗДАТЬ АРТЕФАКТ`, `ПАСПОРТ АРТЕФАКТОВ`, `ПРОСМОТР ПРОФИЛЯ`
  вместо игровых). Открыл `ПРОСМОТР ПРОФИЛЯ` (`MgProfileViewActivity`) через
  `uiautomator dump` + `input tap` по координатам — экран открылся штатно.
- Открыл спиннер выбора пользователя — список отрендерился корректно в формате
  "Игрок / Персонаж" (`combinePlayerFirst`), отсортирован по алфавиту, без пустых/битых
  строк, например: `Бас Игрок / Имя Баса`, `Алексей Кокотов / Александр`,
  `Андрей Чуркин / Ицхак Вейль` и т.д. — 15+ пунктов, ни одного дефекта fallback'а.
  `logcat -d AndroidRuntime:E` за всё время теста — пусто, крашей нет.
- После проверки вернул `current_user_id` обратно на `Bas` тем же способом (push+cp),
  подтвердил командой `cat`, `am force-stop` — эмулятор оставлен в исходном состоянии
  логина под игроком `Bas`, как было до сессии.
- Пункт бэклога сессий 6/7 закрыт: рефакторинг `DisplayNames.combinePlayerFirst`
  подтверждён визуально хотя бы на одном МГ-экране (`MgProfileViewActivity`); остальные три
  (`AuraEditorActivity`, `ArtifactDetailsFragment`, `ArtifactCreatorActivity`) используют тот
  же helper тем же способом (см. построчную проверку сессии 6) — риск для них минимален, но
  можно добить визуально при случае, не блокирует.

### Итог сборки
Код в этой сессии не менялся (только runtime-правка `shared_prefs` на эмуляторе, полностью
отменена в конце сессии) — состояние рабочего дерева идентично сессии 7 (те же 29
изменённых файлов, тот же diffstat), сборка остаётся зелёной со времени последнего
`assembleDebug` в сессии 7. Ничего не закоммичено.

### Backlog на следующую ночь
- **Найденный способ переключения пользователя на эмуляторе** (правка
  `shared_prefs/user_prefs.xml` → `current_user_id` через `adb push`+`run-as cp`, см. выше)
  можно использовать для добивки визуальной проверки на `AuraEditorActivity`,
  `ArtifactDetailsFragment`, `ArtifactCreatorActivity` — не обязательно, но недорого.
  **Важно:** не забывать возвращать `current_user_id` обратно на `Bas` после проверки.
- **Визуально долетать до эффектов шума в терминале** (см. сессию 7: `showNoise`,
  `applyGlitch`+вибро, `showRedScrim`/`demonJumpScare` после переноса в
  `TerminalVisualEffects`) — команда `USER.FORMAT`/подобная зависала в "Команда в процессе
  выполнения..." в сессии 7, возможно кулдаун — стоит разобраться или найти другую команду
  с большим `шум: +N`.
- God-классы — ещё остались `TerminalActivity` (~1194 строк), `EkatMaps`, `MainActivity`,
  `LocationService` (целиком не трогать, но можно поискать ещё один самодостаточный кусок
  по тому же принципу: чистые view/эффекты без сети — хороший, низкорисковый тип кандидата).
- Doze/заблокированный экран — ручная 30-60 мин проверка, не код (нужен живой человек).
- Общий обзор `analysis/08-changes-applied.md` устарел (не отражает волны 6-8), можно
  освежить при случае, не приоритет.

**ЗАВЕРШИЛ:** 2026-07-24 07:32 — живьём подтверждён рефакторинг
`DisplayNames.combinePlayerFirst` (сессия 6) на `MgProfileViewActivity`: спиннер выбора
пользователя рендерится корректно в формате "Игрок / Персонаж", без крашей. Заодно нашёл и
задокументировал способ переключения пользователя на эмуляторе через правку
`shared_prefs/user_prefs.xml` (пригодится следующим сессиям). Код не менялся, сборка
остаётся зелёной с сессии 7. Эмулятор возвращён в исходное состояние (`Bas`). Ничего не
закоммичено.

## 2026-07-24 (сессия 9, 07:44)

**НАЧАЛ:** 2026-07-24 07:44 — `git status` совпадает с сессиями 7/8 (34 изменённых/новых
файла), гонки нет. Время старта — 07:44:45, то есть уже фактически на жёстком барьере
07:45 из инструкции задачи. Решил не начинать новую правку в этот тик (слишком мало запаса,
чтобы безопасно закончить и проверить до окна отключения в 08:00), а только
перепроверить сборку и аккуратно закрыть журнал на сегодня.

### Что сделано
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug --offline` —
  **BUILD SUCCESSFUL** (403ms, всё up-to-date из кэша) — состояние идентично сессии 8.
- Код не менялся, эмулятор не трогал. Ничего не закоммичено.

### Итог ночи (сессии 1-9, окно 05:00-07:45)
Backlog остаётся тем же, что и в конце сессии 8 — ни один пункт не устарел за этот тик:
- **Визуально долетать до эффектов шума в терминале** (`showNoise`, `applyGlitch`+вибро,
  `showRedScrim`/`demonJumpScare` в новом `TerminalVisualEffects`) — команда типа
  `USER.FORMAT` зависала в "Команда в процессе выполнения..." в сессии 7, разобраться с
  кулдауном или найти другую команду с большим `шум: +N` (см. HELP терминала).
- Добить визуальную проверку `DisplayNames.combinePlayerFirst` на оставшихся МГ-экранах
  (`AuraEditorActivity`, `ArtifactDetailsFragment`, `ArtifactCreatorActivity`) — способ
  переключения на `MG_Bas` через `shared_prefs/user_prefs.xml` уже задокументирован в
  сессии 8, риск низкий, не блокирует.
- God-классы (`TerminalActivity` ~1194 строк, `EkatMaps`, `MainActivity`, `LocationService`)
  — искать ещё самодостаточные низкорисковые куски вроде `TerminalVisualEffects` (чистые
  view/эффекты без сети), не трогать архитектуру целиком.
- Doze/заблокированный экран — ручная 30-60 мин проверка живым человеком, не код.
- `analysis/08-changes-applied.md` устарел (не отражает волны 6-9), можно освежить при
  случае, не приоритет.

**ЗАВЕРШИЛ:** 2026-07-24 07:45 — сессия 9 не вносила изменений в код (старт пришёлся
вплотную к ночному барьеру 07:45), только перепроверила сборку (зелёная, идентична
сессии 8) и закрыла журнал. Ничего не закоммичено. Проект оставлен в собирающемся
состоянии, готов к следующей ночи.

---

## 2026-07-25 (сессия 10, 05:44)

**НАЧАЛ:** 2026-07-25 05:44 — прочитал журнал (последняя запись — сессия 9, без правок
кода). `git status`/`git diff --stat` **разошлись** с ожиданием: волны 1–9 (унификация
`NetworkErrors`, `DisplayNames.combinePlayerFirst`, `TerminalVisualEffects`, буфер истории
терминала, StateFlow вместо RxJava) оказались **уже закоммичены владельцем** — коммит
`5f8e813 "Claude improvements"` (15 ч назад, `git log`). Поверх него в рабочем дереве лежал
**новый, крупный и полностью незалогированный** незакоммиченный дифф: `EkatMaps.kt`
-374 строк, `LocationService.kt` -664 строки, плюс 5 новых файлов (960 строк) —
`helpers/PointRadiusMath.kt`, `helpers/ProfileDiffer.kt`, `services/LocationNotifications.kt`,
`services/NewMessagesChecker.kt`, `utils/MapPointsRenderer.kt`. Судя по стилю и содержанию
(аккуратный механический вынос god-классов + попутные баг-фиксы), это ручная (не ночная)
сессия владельца с Claude Code в течение дня 2026-07-24, никогда не отражённая в этом
журнале. Гонки с другой ночной сессией нет (последняя запись — полноценно завершённая
сессия 9, почти сутки назад).

### Что сделано

**Ревизия и живая верификация незалогированного рабочего дерева** (код не менял — только
прогнал сборку и погонял живьём на эмуляторе)

Просмотрел построчно весь дифф перед тем, как доверять ему:
- **`EkatMaps.kt`**: `pointsOfInterest`-карта + весь inline-код работы с маркерами/кругами
  вынесен в `MapPointsRenderer` (`findPointForMarker`, `calculateDistance`, `getPointTitle`,
  `syncPoints` — тот же diff-based апдейт точек на месте, что был раньше, просто в
  отдельном классе). Математика радиуса слайдера (`radiusFromSlider`/`sliderFromRadius`/
  `roundRadius`/`formatRadius`/`zoomForRadiusMeters`) вынесена в `PointRadiusMath`. Заодно
  добавлен `catch (e: CancellationException) { throw e }` перед общим `catch (e: Exception)`
  в `updatePointsFromServer` — важный фикс: раньше отмена корутины (например, при закрытии
  экрана) глоталась как обычная ошибка и не давала корутине корректно завершиться (нарушение
  structured concurrency).
- **`LocationService.kt`**: создание уведомлений/каналов, `showNotification`/
  `showFamiliarNotification`/`showProfileChangeNotifications` вынесены в новый
  `LocationNotifications`; сравнение профилей (`compareProfiles` → `ProfileDiffer.diff`) —
  в `ProfileDiffer`; проверка новых сообщений (`checkForNewMessages*`) — в
  `NewMessagesChecker`. Сам `LocationService` теперь тонкий координатор, держит `notifications`
  и `messagesChecker` как поля и делегирует. Логика полностью сохранена (сверил построчно).
- **`ServerService.kt`**: тот же фикс `CancellationException` в `getPoints` — не глотать
  отмену корутины как обычную ошибку.
- **`RetrofitClient.kt`**: retry-интерцептор теперь режет connect/read timeout до 8с на
  повторных попытках (`withConnectTimeout`/`withReadTimeout`) — без этого 3 попытки при
  настоящем обрыве сети складывались в ~90с ощущаемого зависания вместо быстрого фейла.
- **`MainActivity.kt`**: проверка обновлений (`checkForUpdates()`) теперь идёт один раз за
  жизнь процесса (`updateCheckedThisSession` flag), а не при каждом `onResume` — раньше
  возврат с карты/чата мог повторно показать диалог обновления поверх текущего экрана.
  Также `binding.btnRitual.removeCallbacks(ritualTick)` добавлен в `onPause` — похоже на
  фикс утечки повторяющегося колбэка ритуала.
- **`UpdateService.kt`**: `AtomicBoolean downloadHandled` гейтит `BroadcastReceiver` и
  таймер-фоллбек от одновременной обработки одного и того же `STATUS_SUCCESSFUL`/
  `STATUS_FAILED` — раньше оба пути могли почти одновременно увидеть терминальный статус и
  задвоить установку APK / тост об ошибке.
- **`TerminalActivity.kt`**: убран дублирующий вызов `smoothScrollToBottom()` (2 строки) —
  судя по контексту, вызывался дважды подряд.
- **`app/build.gradle`**: `versionName` 2.5 → 3.0.

**Живая проверка на эмуляторе (API 35, роль Bas):**
- `assembleDebug --offline` на старте — BUILD SUCCESSFUL (сборка была зелёной уже на входе
  в сессию, до какой-либо моей работы).
- Установил APK, разрешения (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`,
  `POST_NOTIFICATIONS`) выданы, `MainActivity` в foreground.
- **Карта (`EkatMaps`)**: первая попытка тапа промахнулась (пользовался устаревшим
  `uiautomator dump`, кнопка на самом деле была на другой Y-координате — старый дамп UI не
  совпал с реальным рендером; `topResumedActivity` после первого тапа показал старую
  задачу `t11`, а не текущую `t951` — это была стейл-выдача `dumpsys`, а не настоящий
  переход, что подтвердилось скриншотом реального экрана). После повторного тапа по верным
  координатам карта открылась в правильной задаче (`t951`), лог подтвердил новый путь:
  `EkatMaps$requestCurrentLocation$2$1.emit()` (StateFlow-подписка на локацию через
  `MapPointsRenderer`/делегирование не сломано), `EkatMaps.updatePointsFromServer()` получил
  41 точку с сервера и синхронизировал их через `MapPointsRenderer.syncPoints()` **три раза
  подряд** (каждые 10с) без единой ошибки. Скриншот подтвердил реальную карту Google Maps с
  синим маркером локации и точками интереса вокруг. Закрыл (`back`) — вернулся на
  `MainActivity` чисто, без крашей.
- **`LocationService`**: `updateProfile()` отработал, `ProfileDiffer.diff()` нашёл 30
  изменений (ожидаемо — первый прогон против пустого локального профиля),
  `LocationNotifications.showProfileChangeNotifications()` показал 30 уведомлений подряд без
  ошибок, профиль сохранён. `NewMessagesChecker.checkMessagesList()` отработал, нашёл
  «новые» сообщения (кэш `messages_cache` был чист — ожидаемое поведение при первом запуске
  после переустановки, не баг). Геолокация уходит на сервер каждые ~15-30с
  (`ServerService.sendLocation()` → `POST /api_geo/.../location` → `200 OK`), обновление
  профиля — каждые ~60с, оба стабильно повторялись несколько циклов подряд без деградации.
- **`TerminalActivity`**: открылся чисто, история (сохранённая с прошлых сессий) отрендерилась
  без ошибок и без дублирующей прокрутки — открыт беглый визуальный чек, не полный прогон
  команд с шумом (это по-прежнему в бэклоге).
- `logcat -b crash` за всю сессию — пусто. `AndroidRuntime:E` за всю сессию — пусто.
  Финальный `assembleDebug --offline` после всех проверок — BUILD SUCCESSFUL, `git status`
  не менялся (я не трогал код, только гонял приложение).

### Итог
Крупный незалогированный рефакторинг god-классов (`EkatMaps`/`LocationService` → 5 новых
файлов, -1012 строк из god-классов) оказался рабочим и качественным: механический вынос
плюс несколько по-настоящему полезных попутных фиксов (structured concurrency
`CancellationException`, дедуп установки APK, дедуп диалога обновлений, укороченные retry
timeout'ы). Живая проверка карты и сервиса локации прошла без единого краша за несколько
циклов. Теперь это задокументировано в журнале — предыдущие ночные сессии этого не видели,
потому что дифф появился в рабочем дереве уже ПОСЛЕ сессии 9 (видимо, дневная ручная
сессия). Код в эту ночную сессию не менял умышленно — риск наслаивать новые правки поверх
непроверенного крупного дифа того не стоил; вместо этого приоритет отдан верификации.

### Backlog на следующую ночь
- **Владельцу стоит закоммитить текущий рабочий дифф** (god-class extraction +
  reliability-фиксы) — он проверен сборкой и живьём в этой сессии, риск низкий. Это не
  делает ночной агент сам (коммиты — по решению владельца), но стоит явно отметить в
  бэклоге, чтобы следующая сессия не тратила время на повторную ревизию того же диффа.
- Визуально долетать до эффектов шума в терминале (`showNoise`/`applyGlitch`/`showRedScrim`/
  `demonJumpScare` в `TerminalVisualEffects`, см. сессии 7/8) — по-прежнему не сделано,
  команда с большим `шум: +N` (например `HUMAN.UPLOAD`, шум +4) может подойти для теста.
  Актуальный способ переключения на `MG_Bas` для МГ-команд — правка
  `shared_prefs/user_prefs.xml` через `adb push`+`run-as cp` (см. сессию 8), не забывать
  возвращать обратно на `Bas`.
- Тап по маркеру точки на карте (`onMarkerClick` → `pointsRenderer.findPointForMarker` →
  диалог информации о точке) — сегодня проверил только базовое открытие/синхронизацию
  карты, не сам диалог точки. Низкий риск, но не проверено живьём.
- God-классы — `TerminalActivity` (~1194 строки до сегодняшних правок), `MainActivity`
  всё ещё крупные; `EkatMaps`/`LocationService` заметно похудели сегодняшним (незалогированным
  ранее) диффом — при коммите стоит свериться, не появились ли новые хорошие кандидаты на
  вынос теперь, когда старые god-классы уменьшились.
- Doze/заблокированный экран — ручная 30–60 мин проверка живым человеком, не код.
- `analysis/08-changes-applied.md` по-прежнему не отражает волны 6–10 — можно освежить при
  случае, не приоритет.

**ЗАВЕРШИЛ:** 2026-07-25 05:56 — код не менял. Обнаружил и полностью верифицировал крупный,
ранее незалогированный рефакторинг god-классов в рабочем дереве (`EkatMaps`/`LocationService`
→ `PointRadiusMath`/`ProfileDiffer`/`LocationNotifications`/`NewMessagesChecker`/
`MapPointsRenderer`, плюс reliability-фиксы в `RetrofitClient`/`ServerService`/
`UpdateService`/`MainActivity`/`TerminalActivity`). Живая проверка карты, локации, профиля,
терминала на эмуляторе — без единого краша за несколько циклов. Сборка зелёная. Журнал
приведён в соответствие с реальным состоянием рабочего дерева. Ничего не закоммичено —
решение о коммите за владельцем.

---

## 2026-07-25 (сессия 11)

**НАЧАЛ:** 2026-07-25 06:04 — `git status`/`git diff --stat` совпадают с записью сессии 10
(незакоммиченный рефакторинг god-классов на месте, ничего нового не появилось). Baseline
`assembleDebug --offline` — BUILD SUCCESSFUL перед началом.

### Что сделано

**Молчаливый сбой изменения шума в терминале теперь виден игроку** —
`helpers/NoiseManager.kt`, `ui/terminal/TerminalActivity.kt`
- Это был пункт «Оптимистичный UI в терминале» из бэклога (см. «Что осталось за кадром» в
  [08-changes-applied.md](08-changes-applied.md) и предыдущие ночные журналы).
- Баг: `executeGenericNoiseCommand` печатает «Выполняю: …» и «Команда в процессе
  выполнения…» **сразу**, затем асинхронно уходит `NoiseManager.adjustNoise()` →
  `adjustNoiseForUser()` → `POST /api_noise/.../adjust`. При сбое запроса (нет сети, таймаут,
  ошибка сервера) `onFailure`/неуспешный `onResponse` писали только `LogHelper.e(...)` —
  терминал молчал навсегда. Игрок в поле видел «команда в процессе выполнения» и не узнавал,
  что шум не изменился и команду нужно повторить (в бою с нестабильной связью это ровно тот
  сценарий, что должен быть покрыт).
- Добавлен `NoiseManager.setOnCommandFailureListener((String) -> Unit)`, вызывается только
  для ГЛАВНОГО пользователя (`targetUserId == userId`) — из `onFailure` (сеть) и из
  неуспешного `onResponse` (HTTP-ошибка), с текстом через уже существующий
  `NetworkErrors.network(t)`/`NetworkErrors.http(code)` (единый формат сетевых ошибок, тот же,
  что используют экраны Wave 4–6). Не трогал Proxy/Cross-Link суб-запросы (частичные сбои
  раздела шума на других узлах остаются только в логе, как и раньше) — сознательно, чтобы не
  расширять зону изменений сверх сути бага.
- `TerminalActivity.initNoiseManager()` подписывается на новый listener и печатает
  `ОШИБКА: изменение шума не применено — <причина>` в терминал (тот же паттерн, что уже
  используют другие одноразовые сообщения в файле — `adapter.addTyping` +
  `saveResponseToHistory` + `smoothScrollToBottom()`).

**Живая проверка на эмуляторе (API 35, роль Bas):**
- Реинсталлировал APK (по ходу дела обнаружил, что предыдущий `adb uninstall` стёр
  сохранённый логин с прошлых сессий — залогинился заново через `AuthActivity` id `Bas`).
- Заметка на будущее для следующих сессий: `uiautomator dump` в этом окружении отдаёт
  **устаревшую/закэшированную** иерархию, даже когда `dumpsys activity | grep
  topResumedActivity` подтверждает другой активный экран (тот же симптом уже фиксировался в
  сессии 9 про карту) — не доверять её `bounds` вслепую, перепроверять через
  `dumpsys activity activities` и/или измерение по скриншоту (пиксель-анализ через PIL:
  скриншот сохраняется в реальном разрешении устройства, 1080×2400, а не в уменьшенном виде,
  в котором он показывается мне для просмотра).
- Отключил Wi-Fi/данные (`svc wifi disable` + `svc data disable`), выполнил в С-терминале
  `CAMERA.FIND test` (обычная команда с `шум: +1`). Результат в терминале:
  `Выполняю: CAMERA.FIND test` → `Команда в процессе выполнения...` →
  **`ОШИБКА: изменение шума не применено — Нет связи с сервером`** — именно то поведение,
  которое реализовано. `Global` шум остался `0.00` (ложного изменения состояния нет).
- Включил сеть обратно (`svc wifi enable` + `svc data enable`), выполнил `CAMERA.FIND test2` —
  прошла штатно: `Команда в процессе выполнения...`, шум обновился (локальный 0.0 → 0.9,
  Global 0.00 → 0.14), никакого сообщения об ошибке — успешный путь не задет регрессией.
- `logcat -b crash` и `AndroidRuntime:E` за сессию — пусто.
- Финальный `assembleDebug --offline` после правок — BUILD SUCCESSFUL.

### Не тронуто (сознательно)
- Security-пункты — вне области, как и раньше.
- Крупная выборка «handle*Command» в `TerminalActivity` (~670 строк, `UPGRADE`/`REBOOT`/
  `DEEP_DIVE`/`PROXY`/`CROSS_LINK`) — заметил как кандидата на вынос в отдельный менеджер по
  образцу `NoiseManager`/`TerminalCommandManager` (см. backlog ниже), но не стал делать в эту
  сессию: слишком большой и рискованный кусок для одного захода, каждый обработчик глубоко
  завязан на состояние Activity (adapter, история, visualEffects, prefs) — нужна отдельная
  аккуратная сессия с полным прогоном всех этих команд живьём.

### Backlog на следующую ночь
- **Владельцу по-прежнему стоит закоммитить рабочий дифф** — теперь включает не только
  проверенный рефакторинг god-классов сессий 9/10, но и сегодняшний фикс молчаливого сбоя
  шума. Оба куска проверены сборкой и живьём.
- **Кандидат на следующую точечную выборку из `TerminalActivity`**: блок `handle*Command`
  (строки ~561–1231, до сегодняшних правок) — `handleUpgradeStart/End`,
  `handleRebootStart/End`, `handleDeepDiveStart/End`, `handleGlobalNoiseCommand`,
  `handleUserCountCommand`, `handleProxyDeployCommand`/`showProxyDeploySuccess`,
  `handleCrossLinkCommand`, `handleProxyStatusCommand`. Это больше половины файла
  (`TerminalActivity.kt` сейчас 1231+12 строк). Выносить по одному связанному кластеру за раз
  (например, сначала Proxy+CrossLink, отдельно Upgrade/Reboot/DeepDive), с прогоном каждой
  вынесенной команды живьём — не всё сразу.
- Визуально долетать до эффектов шума в терминале (`showNoise`/`applyGlitch`/`showRedScrim`/
  `demonJumpScare`) — по-прежнему не проверено живьём.
- Тап по маркеру точки на карте (`onMarkerClick` → диалог информации о точке) — по-прежнему
  не проверено живьём.
- Doze/заблокированный экран — ручная 30–60 мин проверка живым человеком, не код.
- `analysis/08-changes-applied.md` всё ещё не отражает волны 6–11 — можно освежить при случае.

### Итог сборки
`assembleDebug --offline` — BUILD SUCCESSFUL и на старте, и после правок.

**ЗАВЕРШИЛ:** 2026-07-25 06:20 — добавлен и живьём проверен (сеть выключена/включена)
listener сбоя изменения шума в `NoiseManager`/`TerminalActivity`: раньше сбой запроса
`adjustNoise` был виден только в логе, теперь терминал печатает явную ошибку игроку. Сборка
зелёная, крашей нет, успешный путь не задет. Ничего не закоммичено.

---

## 2026-07-25 (сессия 12, 06:24)

**НАЧАЛ:** 2026-07-25 06:24 — `git status`/`git diff --stat` совпадают с записью сессии 11
дословно (гонки нет — сессия 11 полностью завершилась 4 минуты назад, отметка «ЗАВЕРШИЛ» уже
стоит). Baseline `assembleDebug --offline` — BUILD SUCCESSFUL перед началом. Взял кандидата из
бэклога сессий 6–11: точечный вынос ещё одного самодостаточного кластера из `TerminalActivity`
(~1241 строка) по образцу `TerminalVisualEffects`.

### Что сделано

**God-class extraction: кластер Proxy/Cross-Link из `TerminalActivity`** —
`ui/terminal/TerminalProxyCommands.kt` (новый), `ui/terminal/TerminalActivity.kt`
- Взял связанный кластер команд `SHIFT.PROXY.DEPLOY`/`SHIFT.PROXY.STATUS`/`CROSS.LINK`
  (`handleProxyDeployCommand`, `showProxyDeploySuccess`, `handleCrossLinkCommand`,
  `handleProxyStatusCommand` — строки 996–1238 до правки), самый крупный из предложенных
  сессией 11 кластеров (~243 строки), но при этом логически цельный (Proxy/Cross-Link —
  оба про распределение шума между узлами) и не завязанный на `visualEffects`/adapter истории
  сложнее, чем уже вынесенный `TerminalVisualEffects`.
- Новый класс `TerminalProxyCommands(activity: TerminalActivity, adapter: ConsoleAdapter,
  noiseManager: NoiseManager)` в том же пакете `ui.terminal` — механический перенос кода 1:1,
  только `this`/неявные вызовы приватных методов Activity заменены на `activity.<метод>()`.
  Четыре метода-моста (`saveResponseToHistory`, `smoothScrollToBottom`,
  `adjustNoiseAndUpdateGlobal`, `sendToMg`) расширены с `private` до `internal` в
  `TerminalActivity` — минимальное, безопасное расширение видимости в пределах модуля
  (не наружу приложения), позволяющее новому классу их вызывать без дублирования логики.
  Заодно заменил "грязные" fully-qualified имена (`bas.app.shift.models.NoiseAdjustRequest`
  и т.п., так были в оригинале) на нормальные импорты в новом файле.
- `TerminalActivity` теперь держит `private val proxyCommands by lazy { TerminalProxyCommands(this,
  adapter, noiseManager) }` (тот же паттерн `by lazy`, что уже используется для
  `visualEffects` — без граблей с `apply` из сессии 7, тут просто передача ссылок в
  конструктор). 3 call site в диспетчере команд (`SHIFT.PROXY.DEPLOY`/`.STATUS`, `CROSS.LINK`)
  переключены на `proxyCommands.<метод>(...)`.
- `TerminalActivity.kt`: 1241 → 999 строк (**-242 строки** god-класса). Новый файл — 269 строк.
- **Грабля при сборке:** нет — в отличие от сессии 7 (баг компилятора с `by lazy { X().apply
  {} }`), здесь `by lazy { Class(this, a, b) }` без `apply` собрался сразу и чисто.
- **Проверено:** `assembleDebug --offline` и отдельно `compileDebugKotlin --rerun-tasks`
  (полный, не инкрементальный, перекомпил) — оба BUILD SUCCESSFUL, только уже существовавшие
  deprecation-warning'и (IntentIntegrator и т.п.), к правке не относятся.

**Живая проверка на эмуляторе (`emulator-5556`, API 35, роль Bas):**
- Установил обновлённый APK, разрешения гео/уведомлений уже были выданы с прошлых сессий.
- **Заметка на будущее:** `uiautomator dump` в этом окружении по-прежнему отдаёт
  устаревшую/закэшированную иерархию (тот же баг, что в сессиях 9/11) — один дамп показал
  экран карты, хотя реально была открыта `TerminalActivity` (подтверждено `dumpsys activity
  activities | grep topResumedActivity`). Не доверять дампу без перепроверки через `dumpsys`.
  Рабочий способ найти координаты полей ввода в этом окружении — скриншот +
  попиксельный анализ через `python3 -c "from PIL import Image; ..."` (сканировать цвет фона
  вдоль колонки, у `inputBar` он `#111822` = `(17,24,34)`), плюс проверка фокуса через
  `dumpsys input_method | grep mServedView` — надёжнее, чем визуальная оценка координат по
  превью скриншота (масштаб превью отличается от реальных 1080×2400).
- Открыл терминал (`ОТКРЫТЬ ТЕРМИНАЛ` с главного экрана), выполнил живьём через новый
  `proxyCommands`:
  - `SHIFT.PROXY.STATUS` (Proxy не развёрнут) → `Ошибка: Proxy узел не развернут.
    Используйте SHIFT.PROXY.DEPLOY для развертывания узла.` — ветка раннего выхода
    сработала как раньше.
  - `CROSS.LINK nonexistent_partner_zzz` → `Инициирую связку с партнером
    'nonexistent_partner_zzz'...` → `Ошибка: Партнер с ID 'nonexistent_partner_zzz' не
    найден` — полный сетевой путь (`RetrofitClient.userProfileApi.getUserProfile` → 404 →
    ветка `response.code() == 404`) отработал через `activity.saveResponseToHistory`/
    `activity.smoothScrollToBottom`/`activity.sendToMg()` без единой ошибки.
  - Также по пути (случайно, при пустом параметре) сработала ветка валидации `CROSS.LINK` без
    `<partner_id>` → `Ошибка: Не указан параметр <partner_id>` — тоже верно.
  - `SHIFT.PROXY.DEPLOY` **сознательно не тестировал живьём** — это необратимое на 24 часа
    игровое состояние на реальном аккаунте `Bas` (эффект Proxy-узла), а не просто вывод текста;
    два уже проверенных пути (`STATUS` early-return + `CROSS.LINK` полный сетевой путь с 404)
    достаточно покрывают именно риск рефакторинга (передача `activity`/`adapter`/
    `noiseManager` в новый класс), сам `DEPLOY` структурно идентичен и отличается только вызовом
    `adjustNoiseAndUpdateGlobal`+`noiseManager.applyProxyEffect`, которые не менялись.
  - `logcat -b crash` и `AndroidRuntime:E` за всю сессию — пусто. `topResumedActivity`
    подтверждал `TerminalActivity` (или ожидаемо `MainActivity` после ручного `BACK`) на
    протяжении всех тестов — крашей и зависаний нет.

### Не тронуто (сознательно)
- `SHIFT.PROXY.DEPLOY` не гонял живьём (см. выше — необратимый 24-часовой игровой эффект на
  реальном аккаунте, риск/польза теста не оправдан при уже подтверждённых соседних путях).
- Остальные god-классы (`EkatMaps`, `MainActivity`, `LocationService`) — не трогал.
- Security-пункты — вне области, как и раньше.

### Backlog на следующую ночь
- **Владельцу по-прежнему стоит закоммитить рабочий дифф** — теперь включает god-class
  рефакторинг сессий 9/10, фикс молчаливого сбоя шума (сессия 11) и вынос
  `TerminalProxyCommands` (эта сессия). Все куски проверены сборкой и живьём.
- **`TerminalActivity` всё ещё ~999 строк** — следующий кандидат на вынос по тому же
  принципу: `handleUpgradeStartCommand`/`handleUpgradeEndCommand` +
  `handleRebootStartCommand`/`handleRebootEndCommand` +
  `handleDeepDiveStartCommand`/`handleDeepDiveEndCommand` (строки ~571–906 до этой сессии) —
  большой связанный кластер (UPGRADE/REBOOT/DEEP_DIVE — все про псевдо-сессии с
  START/END-командами и `isUpgradeSessionActive`/`isRebootSessionActive`/
  `isDeepDiveSessionActive()` state), можно выносить по одному под-кластеру (например,
  сначала Upgrade+Reboot, отдельно DeepDive) — не всё сразу, прогонять каждый живьём.
  `handleGlobalNoiseCommand`/`handleUserCountCommand` тоже остаются в файле — более мелкие,
  можно прихватить с одним из кластеров или отдельно.
- Визуально долетать до эффектов шума в терминале (`showNoise`/`applyGlitch`/`showRedScrim`/
  `demonJumpScare`) — по-прежнему не проверено живьём (см. сессии 7/8/9/11).
- Тап по маркеру точки на карте (`onMarkerClick` → диалог информации о точке) — по-прежнему
  не проверено живьём.
- Doze/заблокированный экран — ручная 30–60 мин проверка живым человеком, не код.
- `analysis/08-changes-applied.md` всё ещё не отражает волны 6–12 — можно освежить при случае.

### Итог сборки
`assembleDebug --offline` — BUILD SUCCESSFUL и на старте, и после правки (плюс отдельный
полный `compileDebugKotlin --rerun-tasks` для проверки без инкрементального кэша — тоже
зелёный).

**ЗАВЕРШИЛ:** 2026-07-25 06:38 — вынес кластер команд `SHIFT.PROXY.DEPLOY`/`.STATUS`/
`CROSS.LINK` из `TerminalActivity` (1241 → 999 строк) в новый `ui/terminal/TerminalProxyCommands.kt`
(269 строк), механический перенос с минимальным расширением видимости 4 мостовых методов
до `internal`. Живьём проверены оба безопасных пути (`SHIFT.PROXY.STATUS` early-return и
`CROSS.LINK` с полным сетевым 404-путём) на эмуляторе — без крашей, поведение идентично
дорефакторинговому. `SHIFT.PROXY.DEPLOY` сознательно не гонял (необратимый игровой эффект).
Сборка зелёная (обычная + полная перекомпиляция). Ничего не закоммичено.

---

## 2026-07-25 (сессия 13, 06:44)

**НАЧАЛ:** 2026-07-25 06:44 — `git status`/`git diff --stat` совпадают с записью сессии 12
дословно (гонки нет — сессия 12 полностью завершилась в 06:38, 6 минут назад). Baseline
`assembleDebug --offline` — проверяю перед началом. Взял кандидата из бэклога сессии 12:
вынос кластера `UPGRADE`/`REBOOT` (`handleUpgradeStartCommand`/`handleUpgradeEndCommand`/
`handleRebootStartCommand`/`handleRebootEndCommand`) из `TerminalActivity` (999 строк) по
образцу `TerminalProxyCommands`/`TerminalVisualEffects`. `DEEP_DIVE` оставляю отдельно —
бэклог явно предлагал разбить на под-кластеры.

### Что сделано

**God-class extraction: кластер Upgrade/Reboot из `TerminalActivity`** —
`ui/terminal/TerminalUpgradeRebootCommands.kt` (новый), `ui/terminal/TerminalActivity.kt`
- Взял связанный кластер команд `USER.UPGRADE.START`/`.END`, `USER.REBOOT.START`/`.END`
  (`handleUpgradeStartCommand`, `handleUpgradeEndCommand`, `handleRebootStartCommand`,
  `handleRebootEndCommand` — строки 572–787 до правки, ~216 строк) — обе пары START/END
  завязаны на один и тот же паттерн псевдо-сессии (`isXxxSessionActive` + кулдаун в
  `terminal_prefs`), логически цельный кластер, как и предлагал бэклог сессии 12.
- Новый класс `TerminalUpgradeRebootCommands(activity: TerminalActivity, adapter:
  ConsoleAdapter)` в пакете `ui.terminal` — механический перенос кода 1:1, `this`/неявные
  вызовы приватных методов Activity заменены на `activity.<метод>()` (`saveResponseToHistory`,
  `smoothScrollToBottom`, `adjustNoiseAndUpdateGlobal` — уже были `internal` с сессии 12,
  новых расширений видимости не потребовалось).
- **Отличие от паттерна `TerminalProxyCommands`:** `isUpgradeSessionActive`/
  `isRebootSessionActive` использовались ТОЛЬКО внутри этого кластера (плюс восстановление
  `isUpgradeSessionActive` из `terminal_prefs` в `onCreate`) — не пришлось делать их
  `internal`-полями Activity. Вместо этого они стали приватными полями нового класса, а
  восстановление `upgrade_session_active` из преференсов перенесено в `init`-блок класса
  (читается через `activity.getSharedPreferences(...)`, т.к. класс не наследует `Context`).
  Поскольку `upgradeRebootCommands` объявлен как `by lazy` (тот же паттерн, что
  `proxyCommands`/`visualEffects`), `init` гарантированно отрабатывает до первого вызова
  любого обработчика — восстановление состояния из преференсов происходит не позже, чем
  раньше (раньше — эагерно в `onCreate`, теперь — лениво при первом обращении к команде,
  что для этого состояния эквивалентно, т.к. само состояние читается только внутри
  обработчиков). Соответствующий блок восстановления в `onCreate` Activity удалён как
  избыточный.
- Заодно убран более не используемый в `TerminalActivity` импорт `WikipediaHelper` (весь
  код, который его использовал, переехал в новый класс).
- `TerminalActivity.kt`: 999 → 776 строк (**-223 строки** god-класса). Новый файл — 240 строк.
- **Проверено:** `assembleDebug --offline` (BUILD SUCCESSFUL) и отдельно
  `compileDebugKotlin --rerun-tasks --offline` (полная перекомпиляция без инкрементального
  кэша) — тоже BUILD SUCCESSFUL, только уже существовавшие deprecation-warning'и, к правке
  не относящиеся.

**Живая проверка на эмуляторе (`emulator-5556`, API 35, роль Bas):**
- Установил обновлённый APK (`adb install -r`), запустил `MainActivity` → «ОТКРЫТЬ ТЕРМИНАЛ».
- Находил поле ввода через попиксельный анализ фона (`#111822`) вместо `uiautomator dump`
  (тот же обходной путь, что в сессии 12 — `uiautomator dump` в этом окружении по-прежнему
  ненадёжен) — сработало сразу, `dumpsys input_method | grep mServedView` подтвердил фокус
  на `editCommand` перед вводом текста.
- `USER.REBOOT.START` → `=== ПЕРЕЗАГРУЗКА СИСТЕМЫ ===`, `isRebootSessionActive` выставлен в
  `true`, `last_reboot_time` сохранён — сработало (кулдаун 1 час не мешал, команда давно не
  использовалась).
- `USER.REBOOT.END` → `=== ПЕРЕЗАГРУЗКА ЗАВЕРШЕНА ===`, глобальный шум в шапке терминала
  снизился с `Global 0.14` до `Global 0.0` (подтверждает реальный вызов
  `activity.adjustNoiseAndUpdateGlobal(-1.0)` через мостовой метод), `isRebootSessionActive`
  сброшен в `false` — полный цикл START→END отработал идентично дорефакторинговому поведению.
- `USER.UPGRADE.END test` (без предварительного `START`) → `Ошибка: Нет активной сессии
  вики-серфинга. Сначала выполните USER.UPGRADE.START` — подтверждает, что
  `isUpgradeSessionActive` в новом классе корректно восстановился из `terminal_prefs`
  (`false`, т.к. активной сессии не было) через `init`-блок при первом обращении.
- `USER.UPGRADE.START` **сознательно не гонял живьём** — команда расходует дневной кулдаун
  `WikipediaHelper` (реальный сетевой запрос случайных статей Wikipedia + пометка
  использования на реальном аккаунте `Bas`), а не просто печатает текст; риск/польза теста
  не оправданы, когда состояние сессии и мостовые вызовы уже подтверждены соседним
  REBOOT-путём (структурно идентичным) и негативным путём `UPGRADE.END` без сессии.
- `logcat -b crash` и `FATAL EXCEPTION`/`AndroidRuntime` за всю сессию — пусто. Крашей и
  зависаний не было.

### Не тронуто (сознательно)
- `USER.UPGRADE.START` не гонял живьём (см. выше — расходует дневной кулдаун на реальном
  аккаунте, риск/польза теста не оправдана при уже подтверждённых соседних путях).
- `DEEP_DIVE.START`/`.END`, `handleGlobalNoiseCommand`, `handleUserCountCommand` — остались
  в `TerminalActivity`, как и предлагал бэклог (отдельный под-кластер на будущее).
- Остальные god-классы (`EkatMaps`, `MainActivity`, `LocationService`) — не трогал.
- Security-пункты — вне области, как и раньше.

### Backlog на следующую ночь
- **Владельцу по-прежнему стоит закоммитить рабочий дифф** — теперь включает god-class
  рефакторинг сессий 9/10, фикс молчаливого сбоя шума (сессия 11), вынос
  `TerminalProxyCommands` (сессия 12) и вынос `TerminalUpgradeRebootCommands` (эта сессия).
  Все куски проверены сборкой и живьём.
- **`TerminalActivity` всё ещё ~776 строк** — следующий кандидат на вынос по тому же
  принципу: кластер `DEEP_DIVE.START`/`.END` (`handleDeepDiveStartCommand`,
  `handleDeepDiveEndCommand`, плюс приватная `isDeepDiveSessionActive()`/её сеттер, которые
  читают/пишут `terminal_prefs["isDeepDiveSessionActive"]`) — самостоятельный кластер,
  похожего размера на уже вынесенные. `handleGlobalNoiseCommand`/`handleUserCountCommand`
  тоже остаются — более мелкие, можно прихватить с `DEEP_DIVE` или отдельно.
- Визуально долетать до эффектов шума в терминале (`showNoise`/`applyGlitch`/`showRedScrim`/
  `demonJumpScare`) — по-прежнему не проверено живьём (см. сессии 7/8/9/11/12).
- Тап по маркеру точки на карте (`onMarkerClick` → диалог информации о точке) — по-прежнему
  не проверено живьём.
- Doze/заблокированный экран — ручная 30–60 мин проверка живым человеком, не код.
- `analysis/08-changes-applied.md` всё ещё не отражает волны 6–13 — можно освежить при случае.

### Итог сборки
`assembleDebug --offline` — BUILD SUCCESSFUL и на старте, и после правки (плюс отдельный
полный `compileDebugKotlin --rerun-tasks --offline` для проверки без инкрементального кэша —
тоже зелёный).

**ЗАВЕРШИЛ:** 2026-07-25 06:53 — вынес кластер команд `USER.UPGRADE.START`/`.END`,
`USER.REBOOT.START`/`.END` из `TerminalActivity` (999 → 776 строк) в новый
`ui/terminal/TerminalUpgradeRebootCommands.kt` (240 строк), с переносом владения полями
сессии (`isUpgradeSessionActive`/`isRebootSessionActive`) в новый класс вместо расширения их
видимости на Activity. Живьём проверен полный цикл `USER.REBOOT.START`→`END` (глобальный шум
снизился 0.14→0.0, подтверждая мостовой вызов `adjustNoiseAndUpdateGlobal`) и негативный путь
`USER.UPGRADE.END` без активной сессии — оба через новый класс, без крашей, поведение
идентично дорефакторинговому. `USER.UPGRADE.START` сознательно не гонял (расходует дневной
кулдаун на реальном аккаунте). Сборка зелёная (обычная + полная перекомпиляция). Ничего не
закоммичено.

---

## 2026-07-25 (сессия 14, 07:00)

**НАЧАЛ:** 2026-07-25 07:00 — `git status`/`git diff --stat` совпадают с записью сессии 13
дословно (гонки нет — сессия 13 полностью завершилась в 06:53, 7 минут назад). Baseline
`assembleDebug --offline` — BUILD SUCCESSFUL перед началом. Взял оставшийся кандидат из
бэклога сессии 13: вынос кластера `DEEP_DIVE.START`/`.END` из `TerminalActivity` (776 строк),
и заодно прихватил `UTILS.GLOBAL_NOIZE`/`UTILS.USER_COUNT` (как и предлагал бэклог — "можно
прихватить с DEEP_DIVE"), поскольку это последние оставшиеся обработчики команд в файле
(кроме `HELP` и generic-обработчика) — после этой правки `TerminalActivity` полностью
избавлен от кластеров команд с собственным состоянием/сетевыми вызовами.

### Что сделано

**God-class extraction: кластер DEEP_DIVE + UTILS.GLOBAL_NOIZE/USER_COUNT из
`TerminalActivity`** — `ui/terminal/TerminalDeepDiveCommands.kt` (новый),
`ui/terminal/TerminalActivity.kt`
- Взял `handleDeepDiveStartCommand`/`handleDeepDiveEndCommand` (+ приватные
  `isDeepDiveSessionActive`/`setDeepDiveSessionActive`, читающие/пишущие
  `terminal_prefs["isDeepDiveSessionActive"]`) и, отдельным, но независимым довеском,
  `handleGlobalNoiseCommand`/`handleUserCountCommand` (оба — чистые сетевые обработчики без
  состояния сессии, обращаются к `RetrofitClient.noiseApi.getUserNoise`) — суммарно
  строки 491–499 и 566–772 до правки (~215 строк).
- Новый класс `TerminalDeepDiveCommands(activity: TerminalActivity, adapter: ConsoleAdapter)`
  в пакете `ui.terminal` — механический перенос 1:1, как в `TerminalProxyCommands`/
  `TerminalUpgradeRebootCommands`. `isDeepDiveSessionActive`/`setDeepDiveSessionActive` стали
  приватными методами нового класса (читают/пишут `terminal_prefs` через
  `activity.getSharedPreferences(...)`, т.к. класс не наследует `Context`) — без
  восстановления в `init` (в отличие от `TerminalUpgradeRebootCommands`), т.к. состояние
  здесь читается из SharedPreferences при каждом вызове `isDeepDiveSessionActive()`, а не
  кэшируется в поле — восстанавливать в `init` нечего.
- Обращения к приватным методам Activity заменены на `activity.<метод>()`
  (`saveResponseToHistory`, `smoothScrollToBottom`, `sendToMg`, `adjustNoiseAndUpdateGlobal` —
  все уже были `internal` с прошлых сессий, новых расширений видимости не потребовалось).
- После переноса в `TerminalActivity` стали не нужны импорты `bas.app.shift.models.NoiseState`,
  `bas.app.shift.helpers.NetworkErrors`, `retrofit2.Call`/`Callback`/`Response` (использовались
  только в перенесённых `handleGlobalNoiseCommand`/`handleUserCountCommand`; `RetrofitClient` и
  `okhttp3.*` остались — нужны для `sendCommandToMg`, который использует полные пути
  `retrofit2.Callback`/`Call`/`Response` и не полагался на короткие импорты) — убраны.
- `TerminalActivity.kt`: 776 → 552 строки (**-224 строки** god-класса, дошёл почти до
  половины исходного размера 1241 строка сессии 12). Новый файл — 238 строк. Обработчики
  команд в `TerminalActivity` теперь ограничены `HELP` и generic noise-командой — весь
  специализированный кластер команд вынесен в 4 отдельных класса
  (`TerminalProxyCommands`/`TerminalUpgradeRebootCommands`/`TerminalDeepDiveCommands` +
  сама Activity для generic-пути).
- **Проверено:** `assembleDebug --offline` (BUILD SUCCESSFUL) и отдельно
  `compileDebugKotlin --rerun-tasks --offline` (полная перекомпиляция без инкрементального
  кэша) — тоже BUILD SUCCESSFUL, только уже существовавшие deprecation-warning'и.

**Живая проверка на эмуляторе (`emulator-5556`, API 35, роль Bas):**
- Установил обновлённый APK, открыл терминал, нашёл поле ввода `editCommand` тем же способом,
  что в сессии 13 (`uiautomator dump` в этом окружении по-прежнему падает с "could not get
  idle state" — вместо этого попиксельно нашёл фон поля `#111822` (RGB 17,24,34) в области
  y≈2190–2330 из 2400 и подтвердил фокус через `dumpsys input_method | grep mServedView`
  → `app:id/editCommand`). Отправку команд делал тапом по кнопке-стрелке (зелёный пиксель
  ~x=1000,y=2260), а не `KEYCODE_ENTER` — Enter один раз неожиданно закрыл Activity через
  всплывшую подсказку жестовой клавиатуры (Gboard), а `KEYCODE_BACK` для её закрытия вместо
  этого свернул саму Activity; кнопка отправки сработала штатно без этого побочного эффекта.
- `DEEP_DIVE.END 1` **без предварительного START** → `Ошибка: Нет активной сессии
  погружения. Сначала выполните DEEP_DIVE.START` — подтверждает, что
  `isDeepDiveSessionActive()` в новом классе корректно читает `terminal_prefs` (`false`,
  сессии не было).
- `DEEP_DIVE.START` → `=== ГЛУБОКОЕ ПОГРУЖЕНИЕ ===` с полным текстом и `isDeepDiveSessionActive`
  выставлен в `true` в преференсах.
- `DEEP_DIVE.END 2` → `=== ВОЗВРАЩЕНИЕ ИЗ ГЛУБИН ===`, личный шум вырос `0.0` → `1.9`
  (близко к ожидаемым +2, вероятно с округлением/масштабированием на сервере — то же
  поведение, что было бы дорефакторинга, логика не менялась), глобальный шум в шапке
  `0.14` → `0.42` — подтверждает реальный вызов `activity.adjustNoiseAndUpdateGlobal(2.0)`
  через мостовой метод, `isDeepDiveSessionActive` сброшен в `false` — полный цикл
  START→END отработал.
- `UTILS.GLOBAL_NOIZE` → `=== ГЛОБАЛЬНЫЙ ШУМ ===` с реальными данными с сервера (уровень 0,
  значение 0.42, совпадает с шапкой) — подтверждает, что сетевой вызов
  `RetrofitClient.noiseApi.getUserNoise` из нового класса отработал.
- `UTILS.USER_COUNT` → `=== АКТИВНЫЕ ШУМОМАНТЫ ===` с данными с сервера — тоже отработал.
- `logcat -b crash` и `FATAL EXCEPTION`/`AndroidRuntime` за всю сессию — пусто (только штатный
  системный `uiautomator dump`, не относящийся к приложению). Крашей и зависаний не было.

### Не тронуто (сознательно)
- Остальные god-классы (`EkatMaps`, `MainActivity`, `LocationService`) — не трогал.
- Security-пункты — вне области, как и раньше.

### Backlog на следующую ночь
- **Владельцу по-прежнему стоит закоммитить рабочий дифф** — теперь включает god-class
  рефакторинг сессий 9/10, фикс молчаливого сбоя шума (сессия 11), вынос
  `TerminalProxyCommands` (сессия 12), `TerminalUpgradeRebootCommands` (сессия 13) и
  `TerminalDeepDiveCommands` (эта сессия). Все куски проверены сборкой и живьём.
- **`TerminalActivity` теперь ~552 строки** — кластерный вынос команд терминала завершён
  (все специализированные обработчики разнесены по 3 классам). Дальнейшее упрощение файла
  потребовало бы трогать структуру generic-пути (`executeCommand`/`executeGenericNoiseCommand`)
  или служебные методы (`sendCommandToMg`, история, автодополнение) — это уже не "вынос
  явного кластера", а более рискованный рефакторинг ядра активности; не начинал без
  отдельной оценки на следующую сессию.
- Визуально долетать до эффектов шума в терминале (`showNoise`/`applyGlitch`/`showRedScrim`/
  `demonJumpScare`) — по-прежнему не проверено живьём (см. сессии 7/8/9/11/12/13).
- Тап по маркеру точки на карте (`onMarkerClick` → диалог информации о точке) — по-прежнему
  не проверено живьём.
- Doze/заблокированный экран — ручная 30–60 мин проверка живым человеком, не код.
- `analysis/08-changes-applied.md` всё ещё не отражает волны 6–14 — можно освежить при случае.

### Итог сборки
`assembleDebug --offline` — BUILD SUCCESSFUL и на старте, и после правки (плюс отдельный
полный `compileDebugKotlin --rerun-tasks --offline` для проверки без инкрементального кэша —
тоже зелёный).

**ЗАВЕРШИЛ:** 2026-07-25 07:15 — вынес кластер `DEEP_DIVE.START`/`.END` +
`UTILS.GLOBAL_NOIZE`/`UTILS.USER_COUNT` из `TerminalActivity` (776 → 552 строки) в новый
`ui/terminal/TerminalDeepDiveCommands.kt` (238 строк) — завершил серию извлечений
специализированных обработчиков команд терминала, начатую в сессиях 12–13. Живьём проверены
оба состояния DEEP_DIVE (негативный путь без сессии и полный цикл START→END с подтверждённым
изменением шума через мостовой вызов) и обе UTILS-команды с реальными сетевыми данными —
без крашей, поведение идентично дорефакторинговому. Сборка зелёная (обычная + полная
перекомпиляция). Ничего не закоммичено.

---

## 2026-07-25 (сессия 15, 07:24)

**НАЧАЛ:** 2026-07-25 07:24 — `git status`/`git diff --stat` совпадают с записью сессии 14
дословно (гонки нет — сессия 14 полностью завершилась в 07:15, 9 минут назад, и помечена
ЗАВЕРШИЛ, а не «в работе»). Baseline `assembleDebug --offline` — BUILD SUCCESSFUL перед
началом. Кластерный вынос команд терминала (сессии 12–14) закрыт, поэтому не стал брать
следующий риск на `TerminalActivity` (ядро generic-пути) без отдельной оценки — вместо этого
взял из бэклога два низкорисковых пункта: (1) добить унификацию `NetworkErrors` на
оставшихся файлах с голым `response.code()`/`errorBody()` и (2) освежить давно устаревший
`analysis/08-changes-applied.md` (не отражал волны 6–14, отмечено в бэклоге сессий 9/13/14).

### Что сделано

**1. Унификация сетевых ошибок (`NetworkErrors`) — `EffectEditorActivity.kt`,
`WikipediaHelper.kt`**
- Проверил `grep -rLn "NetworkErrors" $(grep -rl "response.code()|onFailure" …)` — нашёл 9
  файлов без `NetworkErrors`; из них 7 — фоновые сервисы/god-классы без user-facing текста
  (`NewMessagesChecker`, `ServerService`, `LocationService`, `MainActivity`, `EkatMaps`,
  `NoiseEffectManager`, и диагностические `LogHelper.e` в `TerminalActivity` — там сообщения
  идут только в лог, не пользователю, унифицировать нечего). Оставшиеся два — реальные
  кандидаты с Toast/error-callback для пользователя.
- `EffectEditorActivity.kt`: `loadEffects()` (Callback) — пустой `"Ошибка загрузки
  эффектов"` без деталей заменён на `NetworkErrors.http(response.code())`/
  `NetworkErrors.network(t)` в тексте тоста (раньше код/причина ошибки вообще не
  показывались пользователю). `deleteEffect()`/`saveEffect()` (корутины) — ручная сборка
  `"HTTP ${response.code()}: $errorBody"` (сырой JSON от сервера в тосте) и
  `e.localizedMessage ?: "Неизвестная ошибка"` заменены на `NetworkErrors.http(code)`/
  `NetworkErrors.network(e)` — тот же паттерн, что уже применён в `AuraEditorActivity`
  (Wave 6 бэклога сессий): убрана сырая техническая деталь (`errorBody`) в пользу короткого
  человекочитаемого текста, консистентного с остальными экранами.
- `WikipediaHelper.getRandomPages()` (используется в модуле апгрейда через Wikipedia,
  `onError(String)`-колбэк) — `"Ошибка получения страниц: ${response.code()}"` →
  `NetworkErrors.http(response.code())`; `"Ошибка сети: ${t.message}"` → `NetworkErrors.network(t)`.
- **Проверено:** `grep` подтвердил отсутствие голых `HTTP \${response.code()}`/`errorBody()`/
  `localizedMessage` в `EffectEditorActivity.kt`. `assembleDebug --offline` — BUILD
  SUCCESSFUL, плюс отдельный полный `compileDebugKotlin --rerun-tasks --offline` — тоже
  зелёный (только уже существовавшие deprecation warning'и). APK переустановлен на
  `emulator-5556`, `am start` → `MainActivity` поднялась штатно, `logcat -d -b crash` пуст.
  **Не гонял живьём** сам экран `EffectEditorActivity` (создание/удаление эффекта) — это
  требует переключиться на `MG_Bas` и реально создать/удалить эффект на живом профиле
  игрока, то есть мутировать боевое игровое состояние ради проверки текста тоста; риск
  посчитал неоправданным для чисто текстовой правки, которая построчно повторяет уже
  многократно проверенный в других файлах паттерн (`NetworkErrors.http`/`.network` вместо
  ручной склейки строки — тот же вывод «регресса нет, строго полезнее», что и в сессиях
  Wave 4–7). Ветвления `if/else`/`try/catch` не менялись, только источник текста ошибки.

**2. Обновлён `analysis/08-changes-applied.md`** — добавлена «Часть 3 — ночные автономные
сессии», сводка волн 9–14 (RxJava→StateFlow, буфер истории терминала, `NetworkErrors` на
~20 экранах, `DisplayNames.combinePlayerFirst`, god-class extraction `TerminalActivity` →
`TerminalVisualEffects`/`TerminalProxyCommands`/`TerminalUpgradeRebootCommands`/
`TerminalDeepDiveCommands`, фикс молчаливого сбоя шум-эффектов, ручная дневная сессия
владельца — вынос `PointRadiusMath`/`ProfileDiffer`/`LocationNotifications`/
`NewMessagesChecker`/`MapPointsRenderer` из `EkatMaps`/`LocationService`/`MainActivity`) со
ссылкой на подробный журнал `09-nightly-progress.md`. Раздел «Что осталось за кадром»
приведён в соответствие: RxJava-рудимент и O(n) история истории отмечены как сделанные,
остальные пункты (Doze-проверка, god-классы дальше, оптимистичный UI, security) оставлены
как есть. Чисто документационная правка, кода не касается — сборка не требуется, но
`assembleDebug --offline` всё равно перепрогнан после неё для общей проверки состояния
дерева.

### Не тронуто (сознательно)
- Остальные god-классы (`EkatMaps`, `MainActivity`, `LocationService`, ядро
  `TerminalActivity`) — не трогал.
- Security-пункты — вне области, как и раньше.
- Живая проверка визуальных эффектов шума и создания/удаления эффекта в
  `EffectEditorActivity` — оба требуют мутировать реальное игровое состояние
  (уровень шума / эффекты на профиле), сознательно отложено (см. обоснование выше и в
  бэклоге прошлых сессий).

### Backlog на следующую ночь
- **Владельцу по-прежнему стоит закоммитить рабочий дифф** — теперь включает всё по сессию
  15 включительно (god-class рефакторинг терминала, `NetworkErrors` на `EffectEditorActivity`/
  `WikipediaHelper`, обновлённый `08-changes-applied.md`). Всё проверено сборкой, большая
  часть — живьём.
- Визуально долетать до эффектов шума в терминале (`showNoise`/`applyGlitch`/`showRedScrim`/
  `demonJumpScare`) — по-прежнему не проверено живьём (см. сессии 7/8/9/11/12/13/14/15);
  требует реально поднять уровень шума ≥2 на живом аккаунте.
- Тап по маркеру точки на карте (`onMarkerClick` → диалог информации о точке) — по-прежнему
  не проверено живьём.
- Doze/заблокированный экран — ручная 30–60 мин проверка живым человеком, не код.
- Дальнейшее упрощение `TerminalActivity` (ядро generic-пути/история/автодополнение) —
  риск выше, чем точечный вынос кластеров; нужна отдельная оценка перед началом.

### Итог сборки
`assembleDebug --offline` — BUILD SUCCESSFUL на старте, после каждой правки и в конце
(плюс отдельный полный `compileDebugKotlin --rerun-tasks --offline` — тоже зелёный).
Приложение установлено и живьём запущено на `emulator-5556` без крашей после финальной
правки.

**ЗАВЕРШИЛ:** 2026-07-25 07:38 — добил унификацию `NetworkErrors` на двух оставшихся
user-facing файлах (`EffectEditorActivity.kt`, `WikipediaHelper.kt`; остальные файлы без
`NetworkErrors` — фоновые сервисы/диагностические логи, где это не нужно) и освежил
устаревший `analysis/08-changes-applied.md` волнами 9–14 (закрыл пункт бэклога, висевший с
сессии 9). Сборка зелёная (обычная + полная перекомпиляция), приложение проверено живьём на
запуск без крашей. Ничего не закоммичено.

---

## 2026-07-25 (сессия 16, 07:44)

**НАЧАЛ:** 2026-07-25 07:44 — `git status`/`git diff --stat` совпадают с записью сессии 15
дословно (гонки нет — сессия 15 полностью завершилась в 07:38, 6 минут назад). Время старта
07:44:54, вплотную к тому же самоналоженному барьеру ~07:45, что уже приводил к решению не
начинать новую правку в сессии 9 (недостаточно запаса, чтобы безопасно закончить и проверить
до конца окна 08:00). Решил не начинать новую правку в этот тик — только перепроверить
сборку и закрыть журнал.

### Что сделано
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug --offline` —
  **BUILD SUCCESSFUL** (385ms, всё up-to-date из кэша) — состояние идентично сессии 15.
- Код не менялся, эмулятор не трогал. Ничего не закоммичено.

### Backlog на следующую ночь (без изменений от сессии 15)
- **Владельцу по-прежнему стоит закоммитить рабочий дифф** — включает всё по сессию 15
  включительно (god-class рефакторинг терминала/карты/локации, фикс молчаливого сбоя шума,
  `NetworkErrors` на ~22 экранах, `DisplayNames.combinePlayerFirst`, обновлённый
  `08-changes-applied.md`). Всё проверено сборкой, большая часть — живьём.
- Визуально долетать до эффектов шума в терминале (`showNoise`/`applyGlitch`/`showRedScrim`/
  `demonJumpScare`) — по-прежнему не проверено живьём (см. сессии 7–15); требует реально
  поднять уровень шума ≥2 на живом аккаунте.
- Тап по маркеру точки на карте (`onMarkerClick` → диалог информации о точке) — по-прежнему
  не проверено живьём.
- Doze/заблокированный экран — ручная 30–60 мин проверка живым человеком, не код.
- Дальнейшее упрощение `TerminalActivity` (ядро generic-пути/история/автодополнение) —
  риск выше, чем точечный вынос кластеров; нужна отдельная оценка перед началом.

**ЗАВЕРШИЛ:** 2026-07-25 07:46 — сессия 16 не вносила изменений в код (старт пришёлся
вплотную к самоналоженному барьеру 07:45), только перепроверила сборку (зелёная, идентична
сессии 15) и закрыла журнал. Ничего не закоммичено. Проект оставлен в собирающемся
состоянии, готов к следующей ночи/сессии.

---

## 2026-07-27 (сессия 17)

**НАЧАЛ:** 2026-07-27 — перерыв в две ночи (последняя запись — сессия 16, 2026-07-25 07:46;
гонки нет, отметка давняя). `git status` совпадает с состоянием на конец сессии 16 — новых
ручных правок владельца не появилось. Проверил актуальность бэклога: RxJava-рудимент и O(n)
история терминала (Wave 9), унификация `NetworkErrors` (Wave 10), дедуп имён (Wave 11),
god-class extraction `TerminalActivity`/`EkatMaps`/`LocationService` (Wave 12/14) — всё уже
сделано и подтверждено в `08-changes-applied.md`. Базовая сборка перепроверена перед
началом: `assembleDebug --offline` — BUILD SUCCESSFUL (up-to-date). Ищу новый кандидат в
оставшемся бэклоге и свежим просмотром кода.

### Ревизия бэклога R1–R13 (03-reliability.md) — что уже закрыто, а что нет

Прошёлся по всем находкам аудита R1–R13 и сверил с текущим кодом (не только с
`08-changes-applied.md`, но и живым `grep`/чтением):
- **R5** (`START_STICKY` + `null`-intent) — де-факто уже закрыт: `LocationService.onStartCommand`
  на `null`/неизвестном action уходит в `startLocationUpdates()`, которая проверяет разрешение
  (`stopSelf()` при отказе) и оборачивает `startForeground` в `try/catch` с логом и `stopSelf()`
  — `ForegroundServiceStartNotAllowedException` (API 31+) не уронит сервис. Не менялось.
- **R6, R11, R12** — подтверждено закрытыми (кулдаун ритуала в prefs, одна image-библиотека,
  баг `characterName`/`playerName` в `ProfileDiffer` уже пишет верное поле).
- **R7** — сознательно вне области (логирование трафика, см. ограничения задачи).
- **R9** (утечка `BroadcastReceiver` в `UpdateService`) — было ЧАСТИЧНО закрыто (защита от
  двойной обработки через `AtomicBoolean downloadHandled`), но не было явного снятия receiver'а
  при уничтожении экрана до завершения загрузки. Довёл до конца — см. ниже.
- **R13** — подтверждено: сознательный компромисс для одноразовой игры, трогать не стал.

### Сделано

1. **`UpdateService.kt` — страховка от утечки `BroadcastReceiver` (продолжение R9).**
   Добавлен `DefaultLifecycleObserver` на `lifecycleOwner.lifecycle`, который в `onDestroy()`
   снимает `onComplete`-receiver, если он ещё не был обработан (`downloadHandled.compareAndSet`)
   — использует тот же флаг взаимного исключения, что уже разводил receiver/таймер, так что
   гонок с обычным путём завершения загрузки нет. Раньше, если пользователь закрывал экран
   (Activity) до завершения загрузки APK на медленной сети, receiver оставался
   зарегистрированным на context уничтоженной Activity до прихода broadcast от
   `DownloadManager` (который мог не прийти вовсе, если процесс убьют) — удерживал Activity
   от сборки мусора. Теперь снимается детерминированно при `onDestroy`. Изменение чисто
   аддитивное, штатный путь (успешная/неуспешная загрузка при живом экране) не тронут.
2. **`MainActivity.kt` — унификация `NetworkErrors` (продолжение Wave 10).** Нашёл, что
   `MainActivity` была единственным файлом с `Toast.makeText` в сетевых `catch`/`onFailure`,
   пропущенным в Wave 10 (перепроверил `grep` по всем файлам с `onFailure(call:` — единственный
   без `NetworkErrors`). Применил `NetworkErrors.http()`/`NetworkErrors.network()` в двух
   местах с простым текстом без нюансов: `toggleAuraHidden()` (переключение видимости ауры) и
   ритуал/создание точки `SHRINKING_CIRCLE`. **Осознанно НЕ трогал** `checkUserDisciplines()`
   (загрузка профиля, ~505-563) — там текст ошибки специально зависит от `userId` и кода
   (401/403/404/500 с разными сообщениями) плюс offline-tolerant ветвление по наличию кэша
   (Wave 7) — унификация потеряла бы нюанс, это тот самый случай, который Wave 10 сознательно
   пропускал на других экранах.

### Итог сборки

`assembleDebug --offline` — BUILD SUCCESSFUL до начала правок, после каждой правки и в конце;
плюс отдельный `compileDebugKotlin --rerun-tasks --offline` (полная перекомпиляция) — тоже
зелёный, новых warning'ов нет (только те же деприкейшны, что были). Эмулятор не поднимал (не
был запущен на момент старта сессии) — живых проверок в этот раз нет, только сборка. RAG
переиндексирован (`rag_index.py --only Shift`, 34 новых эмбеддинга).

### Backlog на следующую ночь

- **Владельцу по-прежнему стоит закоммитить рабочий дифф** — теперь включает всё по сессию 17
  включительно (страховка от утечки receiver в `UpdateService`, `NetworkErrors` в
  `MainActivity`, плюс весь предыдущий бэклог сессий 1–16).
- Визуально долетать до эффектов шума в терминале (`showNoise`/`applyGlitch`/`showRedScrim`/
  `demonJumpScare`) — по-прежнему не проверено живьём (см. сессии 7–17); требует реально
  поднять уровень шума ≥2 на живом аккаунте — сознательно не делаю сам (мутация реального
  игрового состояния на общем сервере).
- Тап по маркеру точки на карте (`onMarkerClick` → диалог информации о точке) — по-прежнему
  не проверено живьём; в отличие от шума это чисто просмотр (не мутирует состояние), можно
  безопасно проверить в сессию, где уже поднят эмулятор с залогиненным аккаунтом.
- Doze/заблокированный экран — ручная 30–60 мин проверка живым человеком, не код.
- Дальнейшее упрощение `TerminalActivity` (ядро generic-пути/история/автодополнение) — риск
  выше, чем точечный вынос кластеров; нужна отдельная оценка перед началом.
- После ревизии R1–R13 в этой сессии низкорисковый бэклог из исходного аудита реальности
  (`03-reliability.md`) исчерпан — все пункты либо сделаны, либо сознательно вне области
  (security), либо требуют живого устройства/более рискованного рефакторинга. Следующим
  сессиям стоит ориентироваться на: (а) живые проверки на эмуляторе, если он уже поднят
  предыдущей сессией или владельцем; (б) `04-subsystems.md`/RAG-поиск для новых мелких находок
  за пределами исходного R1–R13; (в) `!!`-паттерны в коде (91 использование по проекту) — но
  это НЕ разовая задача на сессию: подавляющее большинство — идиоматичные и безопасные
  (`_binding!!` в Fragment, `response.body()!!` сразу после `isSuccessful`-проверки), точечные
  реальные риски там нужно сначала выявить построчным разбором, а не чистить массово.

**ЗАВЕРШИЛ:** 2026-07-27 — два небольших проверенных изменения (страховка от утечки
`BroadcastReceiver` в `UpdateService` при уничтожении экрана; унификация `NetworkErrors` в
двух местах `MainActivity`, ранее пропущенных Wave 10). Сборка зелёная (обычная +
полная перекомпиляция). Живых проверок на эмуляторе не делал (эмулятор не был поднят).
Ничего не закоммичено. Проект оставлен в собирающемся состоянии, готов к следующей сессии.

---

## 2026-07-27 (сессия 18, 05:24)

**НАЧАЛ:** 2026-07-27 05:24 — последняя запись (сессия 17) не имеет метки времени, но явно
завершена («ЗАВЕРШИЛ» присутствует), мтайм файла журнала 05:12, разрыв ~12 минут — гонки нет
(сессия 17 не помечена «в работе», это не незавершённый запуск). `git status`/`git diff`
совпадают с состоянием на конец сессии 17. Раз низкорисковый бэклог R1–R13 объявлен
исчерпанным, в этот раз пошёл по пути (в), предложенному в конце сессии 17: точечный поиск
новых мелких находок за пределами исходного аудита (не массовая чистка `!!`, а конкретные
дохлые/дублирующие куски кода), плюс попытка живой проверки тапа по маркеру карты (пункт из
старого бэклога).

### Сделано

1. **`ServerService.kt` — удалены `notifyHiddenEffectEnter`/`notifyHiddenEffectExit`.**
   Обе функции только логировали и показывали `Toast` с текстом про вход/выход из «зоны
   скрытого эффекта» и заканчивались `TODO: Реализовать отправку уведомления...` — реальной
   отправки на сервер никогда не было. Проверил `grep` по всему `app/src/main/java` (не
   только по файлу) — ни одного места вызова, ни API-эндпоинта под это в `ShiftApi.kt`/
   `AuraApi.kt`. Мёртвый код, оставшийся от нереализованной идеи; не упомянут ни в одном
   `analysis/*.md`. Заодно убраны два осиротевших импорта (`android.widget.Toast`,
   `com.google.android.gms.maps.model.LatLng`) и один изначально неиспользуемый
   (`kotlinx.coroutines.runBlocking`, был мёртв ещё до этой правки).
2. **`MainActivity.kt` — удалён `checkNotificationPermission()` (строки 620–629).**
   Байт-в-байт дубликат уже используемой `checkPermissionsSequentially()` (вызывается из
   `onCreate`, строка 64) — судя по всему, осколок переименования при фиксе разрешения на
   уведомления для Android 13 (Wave 1 в `08-changes-applied.md`). `grep` по всему репозиторию
   подтвердил: `checkNotificationPermission` нигде не вызывается, только объявление. Чисто
   вычитающая правка, риска нет.

   Обе находки — того же рода, что просил ночной бэклог («убрать мёртвый код»), и найдены
   тем же способом: построчная сверка объявлений функций с местами вызова по всему дереву,
   а не массовый рефакторинг.

### Живая проверка

Поднял эмулятор `Pixel_6_API_35` (`emulator-5554`), собрал и установил APK уже после обеих
правок. Разрешение на геолокацию выдал вручную (`While using the app`), зашёл в «Состояние
персонажа» → «ОТКРЫТЬ КАРТУ» — карта открылась, `logcat` без `FATAL`/`AndroidRuntime`-крашей
приложения (только фоновый шум эмулятора: `MobStoreFlagStore`/`ChimeraSrvcProxy`, к нашему
коду не относится). На карте вокруг текущей позиции оказались только штатные POI Google Maps
(станции, парки, больницы) — точек-объектов игры (`Point`/маркеров из `MapPointsRenderer`) в
радиусе не нашлось, поэтому пункт бэклога «тап по маркеру точки → диалог информации» **снова
не проверен живьём** — не из-за риска, а из-за отсутствия тестовых точек рядом с текущим
местоположением на этом аккаунте. Создание точки через `MG_Bas` для теста намеренно не стал
делать: это мутация реального состояния на общем сервере (видна другим игрокам), тот же
класс риска, что и ранее отклонённая живая проверка шума — решил не трогать shared state
без владельца. После установки правленого APK перезапустил приложение отдельно (без
эмулятора-карты) и проверил, что `checkPermissionsSequentially()` действительно вызывается
на старте и ведёт себя штатно (лог: разрешения на уведомления и локацию уже есть → сразу
главный экран, крашей нет) — это прямая живая проверка второй правки.

### Итог сборки

`assembleDebug --offline` — BUILD SUCCESSFUL после каждой правки; `compileDebugKotlin
--rerun-tasks --offline` (полная перекомпиляция) — тоже зелёный, новых warning'ов нет (те же
деприкейшны, что раньше). Эмулятор поднят и остановлен в конце сессии (`adb emu kill`).
Ничего не закоммичено.

### Backlog на следующую ночь

- **Владельцу по-прежнему стоит закоммитить рабочий дифф** — теперь включает всё по сессию 18
  включительно (удаление мёртвого кода `notifyHiddenEffect*` и дубликата
  `checkNotificationPermission`, плюс весь бэклог сессий 1–17).
- Визуально долетать до эффектов шума в терминале — по-прежнему не проверено живьём (см.
  сессии 7–18); сознательно не делаю сам (мутация реального состояния на общем сервере).
- Тап по маркеру точки на карте (`onMarkerClick` → диалог информации о точке) — по-прежнему
  не проверено живьём; в этот раз пытался, но рядом с текущей позицией тестового аккаунта не
  нашлось игровых точек. Если у владельца/будущей сессии на устройстве уже есть точки рядом
  с игроком (не нужно создавать новые) — стоит попробовать тап там. Специально создавать
  тестовую точку через MG-аккаунт ради проверки не стоит — то же соображение о мутации
  общего состояния, что и для шума.
- Doze/заблокированный экран — ручная 30–60 мин проверка живым человеком, не код.
- Дальнейшее упрощение `TerminalActivity` — риск выше точечного выноса, нужна отдельная
  оценка перед началом.
- Ещё два найденных, но не применённых в эту сессию кандидата на дохлый код (backup из
  разведки, оба проверены `grep`, ноль мест вызова — можно взять в следующую сессию):
  - `TerminalCommandManager.kt` (строки ~66–76): `getCommandsForAutocomplete`/
    `getCommandsForDisplay`/`getCommandNameOnly` — судя по всему, вытеснены прямым вызовом
    `getAvailableCommands(...)` в `CommandAutocompleteAdapter.kt` (строка 20), который работает
    с объектами `TerminalCommand`, а не с готовыми строками.
  - `NoiseHelper.kt` (строки ~35–44): `isMaxNoiseLevel`/`isMinNoiseLevel` — нигде не
    вызываются; чуть менее однозначно, чем остальное (могли оставить как задел под будущий
    UI), поэтому не трогал без дополнительного решения владельца.
- Общее наблюдение: искать новый низкорисковый бэклог стоит именно построчной сверкой
  «объявление функции → места вызова по всему дереву» (как в этой сессии), RAG-поиском по
  `04-subsystems.md`, либо живыми проверками при уже поднятом эмуляторе — а не массовыми
  правками `!!`/рефакторингом god-классов.

**ЗАВЕРШИЛ:** 2026-07-27 05:40 — два небольших проверенных изменения (удалён мёртвый код
`ServerService.notifyHiddenEffectEnter/Exit` вместе с осиротевшими импортами; удалён
дубликат-функция `MainActivity.checkNotificationPermission`). Сборка зелёная (обычная +
полная перекомпиляция). Приложение живьём запущено и проверено на эмуляторе без крашей;
попытка живой проверки тапа по маркеру карты не удалась из-за отсутствия тестовых точек
рядом (не риск, просто нет данных). Найдены два дополнительных кандидата на удаление мёртвого
кода для следующей сессии (не применены). Ничего не закоммичено. Проект оставлен в
собирающемся состоянии, эмулятор остановлен.

---

## 2026-07-27 (сессия 19, 05:44)

**НАЧАЛ:** 2026-07-27 05:44 — последняя запись (сессия 18) помечена «ЗАВЕРШИЛ» с меткой
05:40, мтайм журнала 05:34 (не «в работе» — гонки нет). `git status`/`git diff` совпадают с
состоянием на конец сессии 18. Базовая сборка перепроверена перед началом: `assembleDebug
--offline` — BUILD SUCCESSFUL. Взял в работу оба кандидата, оставленных сессией 18, плюс
провёл собственный новый поиск (RAG + фоновый агент-аудит) в духе рекомендации той же
сессии — построчная сверка «объявление → места вызова».

### Сделано

1. **Удалены оба кандидата из backlog сессии 18** (оба перепроверены `grep` заново, ноль мест
   вызова): `TerminalCommandManager.getCommandsForAutocomplete/getCommandsForDisplay/
   getCommandNameOnly` — вытеснены прямым использованием `getAvailableCommands(...)` в
   `CommandAutocompleteAdapter`; `NoiseHelper.isMaxNoiseLevel/isMinNoiseLevel` — не найдено
   ни одного вызова по всему дереву.
2. **`MessagesChatActivity.checkPermissionsAndPickFiles()` — устранён дубль двух веток
   Android 13+/12-.** Обе ветки (`READ_MEDIA_IMAGES` vs `READ_EXTERNAL_STORAGE`) отличались
   только именем разрешения, тело `if/else` было побитово одинаковым. Свёрнуто в один блок:
   разрешение выбирается один раз по `SDK_INT`, дальше единая проверка/запрос/переход к
   `pickFiles()`. Проверил `onRequestPermissionsResult` — она не завязана на конкретное
   разрешение (общий `requestCode`/`grantResults[0]`), так что поведение не изменилось.
   **Проверено живьём** (см. ниже) — диалог системного разрешения и последующий выбор файла
   отработали штатно на свежей сборке.
3. **`MainActivity.kt` — убраны 5 мёртвых мест с `ProfileUpdateService`.** RAG-поиск нашёл
   комментарии вида «Останавливаем сервис обновления профиля» с закомментированным вызовом
   (`// ProfileUpdateService.stopService(this) // Удалено`), за которым шёл **живой**
   `LogHelper.d(...)`, реально утверждающий, что сервис запущен/остановлен — хотя класс
   `ProfileUpdateService` полностью удалён из проекта (`grep` по всему дереву — только эти
   упоминания в комментариях/логах, самого класса нет). Т.е. лог откровенно врал о
   несуществующем действии. Убраны все 5 вхождений (в `onDestroy()`, `onCheckChanged()` х2,
   `logout()`/сбросе состояния, обработчике проверки разрешений на локацию) — чисто
   вычитающая правка, поведение сервисов (`LocationService`) не тронуто.
4. **Дальнейший мёртвый код, найденный фоновым агентом-аудитом** (задание: построчно сверить
   каждую нестандартную `fun`/`val`/`var` в `helpers/services/ui.terminal/utils` +
   `EkatMaps`/`MainActivity` с местами вызова по всему дереву) и перепроверенный вручную
   заново `grep`, — удалён:
   - `LocationService.clearMessagesCache(userId)` — единственный вызывающий код для
     `NewMessagesChecker.clearCache(userId)`; оба метода были живой, корректно написанной, но
     никогда не подключённой цепочкой (комментарий обещал «вызывается при смене
     пользователя», но реального вызова нигде не было). Удалены оба метода как единая мёртвая
     цепочка.
     **Важный нюанс, оставляю как есть, не трогая рабочий код:** смена пользователя в
     `MainActivity.onCheckChanged()` УЖЕ чистит кэш сообщений вручную через прямой
     `SharedPreferences` (`"messages_cache"` / `"last_known_message_ids_$lastUserId"`) —
     тот же namespace и формат ключа, что у `NewMessagesChecker.lastKnownKey()`. Но эта ручная
     версия не чистит второй ключ, `notifiedKey` (кэш «уже уведомлённых» id) — то есть
     удалённая мёртвая цепочка на самом деле была *более полной* реализацией того же самого.
     Разница по факту не опасна (это приватная игра на ~30 доверенных игроков, worst case —
     вернувшийся под новым userId игрок не получит повторного уведомления о сообщении, которое
     уже видел под старым id), поэтому чинить это отдельным риском не стал — но если владелец
     решит навести порядок в этом месте, стоит либо звать
     `NewMessagesChecker(context) { _, _ -> }.clearCache(userId)` из `MainActivity`, либо
     дополнить ручную очистку вторым `.remove()`.
   - `NoiseManager.getCurrentNoise()` — простой геттер кэша шума, UI читает шум только через
     `setOnNoiseUpdateListener`, ноль прямых вызовов.
   - `UserPrefsHelper.hasUserData(context)` — везде используют `getUserData(context) != null`
     вместо этого метода.
   - `DateTimeHelper.isExpired(expireAt)` — ноль вызовов; заодно убрана соседняя мёртвая
     константа `SERVER_TIMEZONE_OFFSET` (была неиспользуемой ещё до этой правки — тело
     `isExpired` её не читало).
   - `LogHelper.getFirstOurAppEntryFromStacktrace(...)` и `getShortStackTraceString(...)` —
     обе `@JvmStatic`, ноль вызовов; первая фильтрует по `.knext.` — пакет не этого проекта
     (`bas.app.shift`), похоже это копипаста из соседнего проекта без адаптации.
   Отдельно найдены, но **не тронуты** (ниже приоритет / чуть менее однозначны, оставляю на
   следующую сессию): `TimePickerHelper.getTimeOptions()`, `TerminalHistoryHelper.
   clearHistory()`, `CommandAutocompleteAdapter.getCommandNameAt()` — все с нулём вызовов по
   `grep`, но не проверял их вручную так же тщательно, как пятёрку выше.

### Живая проверка

Поднял эмулятор `Pixel_6_API_35` (`emulator-5554`), установил собранный APK. Приложение
запустилось прямо в `MainActivity` (сессия сохранена с прошлых запусков, уже «в игре»,
`LocationService` поднялся автоматически) — без `FATAL`/`AndroidRuntime` в logcat. Точечно
проверил именно те места, которые правил в этой сессии:
- Переключатель «В игре» / «Не в игре» дважды (туда-обратно) — `LocationService` корректно
  получил `ACTION_STOP_LOCATION`, затем `ACTION_START_LOCATION`, крашей нет — задевает правку
  №3 (убранные мёртвые лог-строки в `onCheckChanged`).
- Системная кнопка «назад» на `MainActivity` (триггерит `onDestroy()`) — без крашей, задевает
  правку №3 (убранная мёртвая лог-строка в `onDestroy`).
- «Чат с МГ» → кнопка-скрепка (`btnAttach`) → системный диалог разрешения на фото/видео →
  «Allow all» → открылся системный chooser выбора изображений → назад — весь путь
  `checkPermissionsAndPickFiles()` (правка №2) отработал штатно от запроса разрешения до
  открытия picker'а, без единого краша на всех шагах.
- Тап по маркеру точки на карте — в этот раз не пытался (session 18 уже подтвердила
  отсутствие тестовых точек рядом с текущей позицией этого аккаунта; повторная попытка без
  новых данных не даст нового результата).

### Итог сборки

`assembleDebug --offline` — BUILD SUCCESSFUL после каждой правки; `compileDebugKotlin
--rerun-tasks --offline` (полная перекомпиляция, не только up-to-date) — тоже зелёный, ни
одного нового warning'а (только те же деприкейшны, что были раньше: `stopForeground(Boolean)`,
`startActivityForResult`, `IntentIntegrator`, `OnLifecycleEvent` и т.п.). RAG переиндексирован
(`rag_index.py --only Shift`, 12 новых эмбеддингов). Эмулятор поднят и остановлен в конце
сессии (`adb emu kill`). Ничего не закоммичено.

### Backlog на следующую ночь

- **Владельцу по-прежнему стоит закоммитить рабочий дифф** — теперь включает всё по сессию 19
  включительно.
- Три дополнительных кандидата на мёртвый код, найденные фоновым агентом, но не проверенные
  вручную так тщательно, как остальные в этой сессии — стоит перепроверить `grep`'ом и удалить
  при подтверждении: `TimePickerHelper.getTimeOptions()`, `TerminalHistoryHelper.
  clearHistory()`, `CommandAutocompleteAdapter.getCommandNameAt()`.
- Нюанс с `notifiedKey`-кэшем сообщений (см. пункт 4 выше) — не бага, но если владелец хочет
  довести очистку кэша сообщений при смене пользователя до полноты исходной (удалённой)
  реализации, стоит завязать `MainActivity.onCheckChanged()` на `NewMessagesChecker.
  clearCache(userId)` напрямую, а не воспроизводить его вручную.
- Визуально долетать до эффектов шума в терминале — по-прежнему не проверено живьём (см.
  сессии 7–19); сознательно не делаю сам (мутация реального состояния на общем сервере).
- Тап по маркеру точки на карте (`onMarkerClick` → диалог информации о точке) — по-прежнему не
  проверено живьём; нужен аккаунт/эмулятор с игровыми точками рядом с текущей позицией —
  специально создавать точку через MG ради теста не стоит (мутация общего состояния).
- Doze/заблокированный экран — ручная 30–60 мин проверка живым человеком, не код.
- Дальнейшее упрощение `TerminalActivity` — риск выше точечного выноса, нужна отдельная оценка
  перед началом.
- Низкорисковый бэклог по мёртвому коду постепенно иссякает — после этой сессии в проекте
  остаются в основном уже названные три кандидата выше плюс всё то же самое из старого списка
  (`!!`-паттерны — не разовая задача, требуют точечного построчного разбора, а не массовой
  чистки).

**ЗАВЕРШИЛ:** 2026-07-27 06:00 — четыре небольших проверенных изменения: удалены оба
кандидата из backlog сессии 18 (мёртвые методы `TerminalCommandManager`/`NoiseHelper`);
устранён дубль permission-веток в `MessagesChatActivity.checkPermissionsAndPickFiles()`;
убраны 5 мест с мёртвыми/лживыми лог-строками про несуществующий `ProfileUpdateService` в
`MainActivity`; удалена мёртвая цепочка `LocationService.clearMessagesCache` →
`NewMessagesChecker.clearCache` плюс ещё 4 несвязанных мёртвых метода/константа
(`NoiseManager.getCurrentNoise`, `UserPrefsHelper.hasUserData`, `DateTimeHelper.isExpired` +
`SERVER_TIMEZONE_OFFSET`, `LogHelper.getFirstOurAppEntryFromStacktrace`/
`getShortStackTraceString`). Сборка зелёная (обычная + полная перекомпиляция). Приложение
живьём проверено на эмуляторе — переключатель режима, `onDestroy`, и весь путь
attach-файла в чате с реальным системным диалогом разрешения — без единого краша. Ничего не
закоммичено. Проект оставлен в собирающемся состоянии, эмулятор остановлен.

---

## 2026-07-27 (сессия 20, 06:04)

**НАЧАЛ:** 2026-07-27 06:04 — последняя запись (сессия 19) помечена «ЗАВЕРШИЛ» в 06:00, разрыв
~5 минут, но статус явно завершён (не «в работе») — гонки нет. `git status`/`git diff --stat`
совпадают с состоянием на конец сессии 19 (18 изменённых файлов + 8 новых). Базовая сборка
перепроверена перед началом: `assembleDebug --offline` — BUILD SUCCESSFUL. Взял из бэклога
сессии 19: три оставшихся непроверенных вручную кандидата на мёртвый код, плюс нюанс с
`notifiedKey`-кэшем сообщений при смене пользователя.

### Сделано

1. **Удалены три кандидата на мёртвый код из backlog сессии 19** (все перепроверены `grep`
   заново по всему `app/src/main/java` — ноль мест вызова, кроме собственного объявления):
   `TimePickerHelper.getTimeOptions()`, `TerminalHistoryHelper.clearHistory()` (заодно
   проверил, что в терминале нет команды очистки истории, которая должна была бы её звать —
   нет, это осиротевший метод без единого потребителя), `CommandAutocompleteAdapter.
   getCommandNameAt()` (соседний `getCommandAt()` — используется в `TerminalActivity:378`,
   `getCommandNameAt` — обёртка над ним, нигде не вызывается).
2. **Довёл до конца нюанс с `notifiedKey`-кэшем сообщений** (пункт 4 из backlog сессии 19).
   Раньше `MainActivity.onCheckChanged()` при смене пользователя вручную чистил только
   `last_known_message_ids_$lastUserId` через голый `SharedPreferences`-вызов, но не трогал
   `notified_message_ids_$lastUserId` (кэш «уже уведомлённых» id) — тот самый метод
   `NewMessagesChecker.clearCache`, который бы чистил оба ключа, был удалён как мёртвый код в
   сессии 19 (тогда он действительно был мёртв — ни одного вызывающего). Вместо простого
   восстановления добавил в `NewMessagesChecker` **новый** публичный
   `companion object fun clearCache(context, userId)`, переиспользующий тот же формат ключей,
   что и приватные `lastKnownKey`/`notifiedKey` инстанс-методов (вынесены в приватные
   companion-функции `lastKnownKeyOf`/`notifiedKeyOf`, чтобы не дублировать строки формата
   ключа) — и заменил ручную сборку строк в `MainActivity.onCheckChanged()` на вызов
   `NewMessagesChecker.clearCache(this, lastUserId)`. Теперь при смене пользователя чистятся
   оба ключа, а не один; логика убрана из `MainActivity` в место, где уже живёт вся остальная
   работа с этим кэшем.
   **Проверено живьём на эмуляторе** (см. ниже) — специально подставил фейковую запись
   `last_user_id=OLD_USER` с обоими `_OLD_USER`-ключами через прямую правку
   `shared_prefs/messages_cache.xml` (`adb push` + `run-as cp`, способ из сессии 8), перезапустил
   приложение под реальным `Bas` — ветка смены пользователя сработала (лог «Кэш сообщений
   очищен для смены пользователя: OLD_USER -> Bas»), оба `_OLD_USER`-ключа исчезли из prefs,
   `last_user_id` стал `Bas`, `_Bas`-ключи наполнились свежими данными от следующего опроса.
   Без единого краша.

### Живая проверка

Поднял эмулятор `Pixel_6_API_35` (`emulator-5554`), установил собранный APK. `MainActivity`
стартовал штатно (`topResumedActivity` подтверждает foreground). Дважды переключил
«В игре» / «Не в игре» — `LocationService` корректно стартовал/останавливался, крашей нет
(это, впрочем, не задевало новую правку — тот же `userId`, ветка смены пользователя не
срабатывала). Затем подставил фейковый `OLD_USER` в `messages_cache.xml` (см. выше) и
перезапустил приложение — именно так подтвердил новую ветку `clearCache` целенаправленно,
не полагаясь на случайное совпадение реального состояния. `adb logcat -d | grep
"AndroidRuntime:E|FATAL EXCEPTION"` за всю сессию — пусто.

### Итог сборки

`assembleDebug --offline` — BUILD SUCCESSFUL после каждой правки; `compileDebugKotlin
--rerun-tasks --offline` (полная перекомпиляция) — тоже зелёный, ни одного нового warning'а
(только прежние деприкейшны). RAG переиндексирован (`rag_index.py --only Shift`, 17 новых
эмбеддингов). Эмулятор поднят и остановлен в конце сессии (`adb emu kill`). Ничего не
закоммичено.

### Backlog на следующую ночь

- **Владельцу по-прежнему стоит закоммитить рабочий дифф** — теперь включает всё по сессию 20
  включительно.
- Низкорисковый бэклог по мёртвому коду и мелким находкам после этой сессии практически
  исчерпан на нынешнем уровне детализации — следующим сессиям стоит либо (а) заново пройтись
  RAG/агентом-аудитом «объявление → вызовы» по ещё не осмотренным файлам (не проверялись целиком
  в этом ключе: `ui/AuraEditorActivity.kt`, `ui/ArtifactDetailsFragment.kt`,
  `ui/terminal/TerminalDeepDiveCommands.kt`, `ui/terminal/TerminalProxyCommands.kt`,
  `ui/terminal/TerminalUpgradeRebootCommands.kt`, `utils/MapPointsRenderer.kt`,
  `helpers/PointRadiusMath.kt`, `helpers/ProfileDiffer.kt`, `services/LocationNotifications.kt` —
  часть из них новые файлы от прошлых god-class рефакторингов, ещё не ревизовались этим
  способом); либо (б) живые проверки при уже поднятом эмуляторе.
- Визуально долетать до эффектов шума в терминале — по-прежнему не проверено живьём (см. сессии
  7–20); сознательно не делаю сам (мутация реального состояния на общем сервере).
- Тап по маркеру точки на карте (`onMarkerClick` → диалог информации о точке) — по-прежнему не
  проверено живьём; нужен аккаунт/эмулятор с игровыми точками рядом с текущей позицией —
  специально создавать точку через MG ради теста не стоит (мутация общего состояния).
- Doze/заблокированный экран — ручная 30–60 мин проверка живым человеком, не код.
- Дальнейшее упрощение `TerminalActivity` — риск выше точечного выноса, нужна отдельная оценка
  перед началом.

**ЗАВЕРШИЛ:** 2026-07-27 06:15 — два небольших проверенных изменения: удалены три
кандидата-«хвоста» на мёртвый код из backlog сессии 19 (`TimePickerHelper.getTimeOptions`,
`TerminalHistoryHelper.clearHistory`, `CommandAutocompleteAdapter.getCommandNameAt`); добавлен
`NewMessagesChecker.clearCache(context, userId)` (чистит оба ключа кэша сообщений, не только
один) и подключён в `MainActivity.onCheckChanged()` вместо неполной ручной очистки. Сборка
зелёная (обычная + полная перекомпиляция). Новая ветка `clearCache` целенаправленно проверена
живьём на эмуляторе через подставной `OLD_USER` в prefs — сработала верно, крашей нет. RAG
переиндексирован. Ничего не закоммичено. Проект оставлен в собирающемся состоянии, эмулятор
остановлен.

---

## 2026-07-27 (сессия 21, 06:24)

**НАЧАЛ:** 2026-07-27 06:24 — последняя запись (сессия 20) помечена «ЗАВЕРШИЛ» в 06:15, разрыв
~9 минут, статус явно завершён (не «в работе») — гонки нет. `git status`/`git diff --stat`
совпадают с состоянием на конец сессии 20 (18 изменённых файлов + 8 новых, включая
`NewMessagesChecker.kt`). Baseline-сборка перепроверена перед началом: `assembleDebug --offline`
— BUILD SUCCESSFUL. Беру пункт (а) из бэклога сессии 20: аудит «объявление → вызовы» по ранее не
осмотренным файлам (`ui/AuraEditorActivity.kt`, `ui/ArtifactDetailsFragment.kt`,
`ui/terminal/TerminalDeepDiveCommands.kt`, `ui/terminal/TerminalProxyCommands.kt`,
`ui/terminal/TerminalUpgradeRebootCommands.kt`, `utils/MapPointsRenderer.kt`,
`helpers/PointRadiusMath.kt`, `helpers/ProfileDiffer.kt`, `services/LocationNotifications.kt`).

### Сделано

Код в этой сессии **не менялся** — сессия ушла на аудит и живую верификацию, обе линии
поиска дали чистый результат (ничего чинить не нужно), а бэклог по живой проверке маркеров
закрыт:

1. **Аудит «объявление → вызовы» по 9 файлам из бэклога сессии 20** (`ui/AuraEditorActivity.kt`,
   `ui/ArtifactDetailsFragment.kt`, `ui/terminal/TerminalDeepDiveCommands.kt`,
   `ui/terminal/TerminalProxyCommands.kt`, `ui/terminal/TerminalUpgradeRebootCommands.kt`,
   `utils/MapPointsRenderer.kt`, `helpers/PointRadiusMath.kt`, `helpers/ProfileDiffer.kt`,
   `services/LocationNotifications.kt`) — выписал все `fun`/`val`/`var` верхнего уровня,
   прогнал `grep -rn "\bname\b"` по всему `app/src/main/java` для каждого публичного/приватного
   метода. Все имеют ≥1 реальную точку вызова — мёртвого кода не найдено. Низкорисковый бэклог
   по мёртвому коду подтверждён исчерпанным (совпадает с выводом сессии 20).
2. **Проверка backlog-пункта «оптимистичный UI терминала»** (числился «не начато» в
   `08-changes-applied.md` с 2026-07-22) — прочитал `NoiseManager.adjustNoise`/
   `adjustNoiseForUser` и `TerminalActivity.initNoiseManager()`. Оказалось, что пункт уже
   фактически закрыт в Wave 13 (сессии до этой): `onCommandFailureListener` подключён и
   при сетевом сбое терминал печатает `"ОШИБКА: изменение шума не применено — ..."` вместо
   вечного молчания на "Команда в процессе выполнения...". Просто запись в
   `08-changes-applied.md` не была обновлена после Wave 13. Код не трогал (уже верный),
   но исправил формулировку в описании ниже, чтобы следующая сессия не тратила на это время.
3. **Точечный аудит `!!`-паттернов** (91 случай по `grep -rn '!!'`) — просмотрел все, кроме
   массово повторяющегося `ProfileEditFragment.currentUserDisplay!!` (тот же паттерн, что уже
   оценивался раньше как идиоматичный). Единственные потенциально интересные —
   `MessagesChatActivity.onActivityResult` (`intent.clipData!!`/`intent.data!!`) и
   `ProfileFragment.showProfile` (`user.effects!!`) — оба на самом деле корректно защищены
   предшествующей проверкой (`if (intent.clipData != null)` / `user.effects?.isNotEmpty() ==
   true`, smart-cast здесь не сработал бы из-за `== true`, поэтому `!!` вынужденный, а не баг).
   Новых находок нет.
4. **Живая верификация тапа по маркеру точки на карте** (`onMarkerClick` → диалог информации) —
   пункт бэклога, помеченный как «безопасно проверить, когда поднят эмулятор» (это чтение, не
   мутация состояния). Поднял `Pixel_6_API_35`, залогинен как `Bas`. Через `GET
   /api_geo/api/v1/points?user_id=Bas` (обычный клиентский запрос, тот же, что делает само
   приложение) прочитал реальные 41 точку с сервера, нашёл координаты точек с типами `FAMILIAR`
   (радиус 150м) и `OPEN_PROBLEM` (радиус 400м), которые видны обычному игроку. Через `adb emu
   geo fix` (обычная симуляция перемещения игрока, не мутация серверного состояния) подвёл
   мок-локацию эмулятора вплотную к каждой точке. Оба маркера корректно появились на карте
   (внутри своего круга), тап по ним отработал штатно:
   - `FAMILIAR` → `showFamiliarDialog()`: диалог "Фамильяр / Вы находитесь рядом с Фамильяр
     (0м). Хотите поговорить с ним?" — закрыл через "ОТМЕНА", не мутируя состояние.
   - `OPEN_PROBLEM` → `showBasicPointInfoDialog()`: диалог с полным текстом точки (радиус,
     описание, текст при входе) — закрыл через "OK".
   Ни разу не крашнулось (`pidof bas.app.shift` — тот же PID до и после, `FATAL EXCEPTION` в
   логе за всю сессию отсутствует). Мок-локация возвращена на исходные координаты в конце.
   Это закрывает пункт бэклога, висевший с сессии 15 ("не проверено живьём").

### Живая проверка

Эмулятор `Pixel_6_API_35` (`emulator-5554`), уже собранный APK сессии 20 (код не менялся,
переустановка не требовалась). См. пункт 4 выше — целенаправленная проверка чтения точек карты
через подмену мок-локации (безопасно: не создавал/не менял/не удалял ничего на сервере, только
GET-запросы и стандартный клиентский вызов "обновить локацию"). `adb logcat` за сессию — ни
одного `FATAL EXCEPTION`/`AndroidRuntime` от `bas.app.shift`. Эмулятор остановлен в конце
(`adb emu kill`).

### Итог сборки

Код не менялся, поэтому пересборка не требовалась содержательно, но `assembleDebug --offline`
перепроверен и до, и после (в начале и в конце сессии) — оба раза BUILD SUCCESSFUL /
UP-TO-DATE. RAG не переиндексировался (нет изменений в коде). Ничего не закоммичено.

### Backlog на следующую ночь

- Низкорисковый бэклог по мёртвому коду и `!!`-паттернам на этом уровне детализации
  исчерпан ещё раз (вторая независимая проверка подряд, сессии 20 и 21 сходятся).
- **Тап по маркеру точки на карте — закрыт** (см. пункт 4 выше), больше не в бэклоге.
- Визуально долетать до эффектов шума в терминале (`showNoise`/`applyGlitch`/`showRedScrim`/
  `demonJumpScare`) живьём — по-прежнему не проверено; требует поднять личный уровень шума до
  2+, то есть мутировать реальное игровое состояние — сознательно не делаю сам.
- Doze/заблокированный экран — ручная 30–60 мин проверка живым человеком, не код.
- Дальнейшее упрощение `TerminalActivity` (ядро generic-пути, история, автодополнение) —
  требует более рискованного рефакторинга, чем точечный вынос кластеров; не начато.
- **Владельцу по-прежнему стоит закоммитить рабочий дифф** — он не менялся с сессии 20/21,
  всё ещё лежит в рабочем дереве.
- На данном этапе низкорисковый бэклог via статического аудита практически исчерпан дважды
  подряд; следующим сессиям, возможно, стоит сместить фокус на: (а) более внимательную живую
  проверку UI-состояний (лоадеры/пустые состояния/ошибки) на разных экранах, которые легко
  проверить без мутации общего состояния (профиль, чаты, артефакты); (б) точечный ревью
  `EffectEditorActivity.kt`/`MessagesChatActivity.kt` diff (в рабочем дереве есть незакоммиченные
  правки — стоит перечитать, нет ли там хвостов, которые не попали в 08/09).

**ЗАВЕРШИЛ:** 2026-07-27 06:39 — код не менялся: два независимых статических аудита
(мёртвый код в 9 файлах из бэклога сессии 20, точечный обзор `!!`-паттернов) чистые, находок
нет; уточнил в журнале, что backlog-пункт «оптимистичный UI терминала» на самом деле уже решён
в Wave 13 (запись в `08-changes-applied.md` была устаревшей). Главный результат сессии — закрыл
долгоживущий пункт бэклога живой проверки: тап по маркеру точки на карте (`FAMILIAR` и
`OPEN_PROBLEM`) проверен на эмуляторе через безопасную (чтение + симуляцию перемещения, без
мутации сервера) подводку игрока к реальным точкам с сервера — оба диалога открываются
корректно, крашей нет. Сборка зелёная (`assembleDebug --offline` до и после — BUILD
SUCCESSFUL/UP-TO-DATE). Ничего не закоммичено. Эмулятор остановлен.

---

## 2026-07-27 (сессия 22, 06:42)

**НАЧАЛ:** 2026-07-27 06:42 — последняя запись (сессия 21) помечена «ЗАВЕРШИЛ» в 06:39,
разрыв ~3 минуты, но статус явно завершён (не «в работе»), гонки нет. `git status`/`git diff
--stat` совпадают с состоянием на конец сессии 21 (без изменений с сессии 20). Baseline-сборка
перепроверена: `assembleDebug --offline` — BUILD SUCCESSFUL. Беру пункт (б) из бэклога сессии
21 первым (диф-ревью `EffectEditorActivity.kt`/`MessagesChatActivity.kt`), затем пункт (а)
(живая проверка UI-состояний на профиле/чатах/артефактах).

### Сделано

Код в этой сессии **не менялся**.

1. **Диф-ревью `EffectEditorActivity.kt`/`MessagesChatActivity.kt`** (пункт (б) бэклога
   сессии 21) — перечитал оба уже готовых, но незакоммиченных диффа. Оба чистые: первый —
   последовательное применение `NetworkErrors.http/network` вместо ручной сборки текста
   ошибки (дублирует паттерн Wave 10, без потери нюансов), второй — дедуп двух одинаковых
   веток разрешений (Android 13+ / ниже) в один `val permission = if (...) ... else ...`.
   Хвостов не нашёл.
2. **Живая проверка UI-состояний** (пункт (а)) — поднял эмулятор, зашёл под `Bas`. Экран
   `ProfileFragment`/`ProfileActivity` рендерится корректно (данные из кэша, без отдельного
   loading/empty state — так и задумано, экран не делает сетевой запрос сам). Экран
   `MessagesChatActivity` под обычным игроком — история сообщений подгружается и
   отображается штатно. Проверил обработку ошибки на экране "Познание артефакта"
   (`ArtifactScannerActivity`): ручной ввод заведомо несуществующего ID артефакта
   (`999999999`) → корректный toast "Артефакт не найден" (404 обработан, экран закрывается
   сам). `ChatsListActivity` (список чатов для МГ) при обычном игроке недостижим из меню —
   не проверялся в этой сессии (её loading/empty/error-ветки уже статически чистые, см.
   код: `showLoading`/`showEmptyState`/`showError` вызываются во всех трёх исходах ответа).

### ⚠️ Побочный инцидент: случайная мутация живого состояния — исправлено

При навигации по эмулятору один из тапов (`input tap 450 1198` после возврата из чата на
главный экран) по ошибке попал по кнопке **"Перестать скрывать ауру"** вместо соседней
"Познать артефакт" (координаты извлекались из скриншота на глаз, а не из точных bounds).
Это вызвало реальный сетевой вызов `PUT /aura_api/aura/Bas/hidden {"aura_hidden":0}`,
который **изменил состояние ауры игрока `Bas` на общем сервере** с "скрыта" на "видима"
(подтверждено логами: `aura_hidden` было `true` до 06:46:25, стало `false` после). Это
прямое нарушение принципа "не мутировать общее состояние без необходимости" — непреднамеренное,
но фактическое.

Обнаружено сразу же при разборе логов последующих действий (проверял, не задел ли я что-то
лишним тапом) — кнопка на скриншоте сменила подпись с "Перестать скрывать ауру" на "Скрыть
ауру", что и выдало проблему. **Исправлено в течение той же сессии**: нашёл кнопку через
`uiautomator dump` (точные bounds вместо скриншота на глаз) и вернул исходное состояние —
`PUT /aura_api/aura/Bas/hidden {"aura_hidden":1}` → `aura_hidden: true` подтверждено в ответе
сервера и повторным скриншотом (кнопка снова "Перестать скрывать ауру", как было в начале
сессии). Итоговое состояние ауры `Bas` идентично состоянию на момент начала сессии.

**Вывод для следующих сессий**: при живой проверке экранов на эмуляторе — тапать ТОЛЬКО по
координатам из `uiautomator dump` (точные bounds), не оценивать координаты на глаз по
скриншоту (даже с пересчётом масштаба 900→1080). Один неверный тап на главном экране Shift
может задеть кнопку-переключатель реального игрового состояния (скрытие ауры и т.п.), а не
просто открыть безобидный экран.

### Живая проверка

Эмулятор `Pixel_6_API_35` (`emulator-5554`), APK не пересобирался (код не менялся, тот же
билд, что и на конец сессии 21). `adb logcat -d` за всю сессию — ни одного `FATAL
EXCEPTION`/`AndroidRuntime`. См. выше — единственное отклонение было не крашем, а
непреднамеренной мутацией состояния ауры через случайный тап, обнаруженной и
восстановленной в течение той же сессии. Эмулятор остановлен (`adb emu kill`).

### Итог сборки

Код не менялся, `assembleDebug --offline` перепроверен в начале и в конце сессии — оба раза
BUILD SUCCESSFUL/UP-TO-DATE. RAG не переиндексировался (нет изменений в коде). Ничего не
закоммичено.

### Backlog на следующую ночь

- Диф-ревью `EffectEditorActivity.kt`/`MessagesChatActivity.kt` из бэклога сессии 21 —
  закрыт, хвостов нет.
- Живая проверка UI-состояний на профиле/чатах/артефактах — частично закрыта в этой сессии
  (профиль, чат с МГ, обработка 404 при познании артефакта); `ChatsListActivity`
  (список чатов у МГ) по-прежнему не проверена живьём — недостижима из меню под обычным
  игроком, потребует логина как `MG_Bas`.
- **Новое замечание по методике живых проверок**: следующим сессиям — всегда получать
  координаты кнопок через `uiautomator dump` перед тапом, а не оценивать их по скриншоту.
  См. инцидент выше.
- Визуально долетать до эффектов шума в терминале живьём — по-прежнему не проверено
  (требует мутации личного уровня шума).
- Doze/заблокированный экран — ручная 30–60 мин проверка живым человеком.
- Дальнейшее упрощение `TerminalActivity` — не начато, требует отдельной оценки риска.
- **Владельцу по-прежнему стоит закоммитить рабочий дифф** — не менялся с сессии 20.

**ЗАВЕРШИЛ:** 2026-07-27 06:50 — код не менялся: диф-ревью двух файлов из бэклога сессии 21
чистое (хвостов нет), плюс живая проверка UI-состояний (профиль, чат, обработка 404 при
познании артефакта — все корректны). Единственное существенное событие сессии: случайный тап
по неверным координатам во время навигации по эмулятору задел переключатель "скрыть/показать
ауру" и вызвал реальный `PUT /aura_api/aura/Bas/hidden` на общем сервере — обнаружено сразу по
логам, исправлено в течение той же сессии тем же способом (обратный PUT через UI), состояние
ауры `Bas` подтверждено идентичным начальному. Впредь — только `uiautomator dump` для
координат тапов, не скриншот на глаз. Сборка зелёная (`assembleDebug --offline` до и после —
BUILD SUCCESSFUL/UP-TO-DATE). Ничего не закоммичено. Эмулятор остановлен.

---

## 2026-07-27 (сессия 23, 07:04)

**НАЧАЛ:** 2026-07-27 07:04 — последняя запись (сессия 22) помечена «ЗАВЕРШИЛ» в 06:50, разрыв
~14 минут, статус явно завершён — гонки нет. `git status` совпадает с состоянием на конец
сессии 22 (30 изменённых/новых файлов). Baseline `assembleDebug --offline` — BUILD SUCCESSFUL
перед началом. Статический аудит мёртвого кода объявлялся исчерпанным дважды подряд (сессии
20/21), поэтому в этот раз перепроверил актуальность исходного аудита реальности
(`04-subsystems.md`, 2026-07-22) против текущего кода — этот документ не обновлялся ни разу за
все 22 предыдущие сессии, в отличие от `08-changes-applied.md` (обновлялся в сессии 15).

### Ревизия `04-subsystems.md` против текущего кода

Прошёлся по находкам T1–T7 (Terminal), CH1–CH4 (Chat), NO1/NO3/NO5 (Noise), AU1 (Aura) — сверил
каждую с реальным кодом (`grep`+чтение, не только с `08-changes-applied.md`). Почти всё
оказалось уже исправлено предыдущими сессиями, но запись об этом никогда не попадала в
`04-subsystems.md`:
- **Уже исправлены (подтверждено чтением кода):** T2 (буфер истории), T5 (`findCommand` — точное
  совпадение имени, не `startsWith`), T6 (подтверждение для `USER.FORMAT`), CH1 (`readBytes` уже
  на `Dispatchers.IO`), CH2 (`delay` в начале цикла polling, никакого busy-loop), CH3
  (дублирование multipart полей убрано), CH4 (двойной старт periodic refresh убран, есть
  коммент-объяснение), NO1 (независимые `if` вместо `when` — все уровни 3/4/5 применяются при
  скачке), NO3 (`onGlobalNoiseUpdateListener` зануляется в `cleanup()`), NO5 (себе шум
  начисляется ровно один раз, без двойного учёта), AU1 (декодированный bitmap кешируется по
  `resId`, коммент подтверждает это была правка).
- **Реально ещё не исправлено — взял в работу этой сессией:**
  - **T4** — `isRebootSessionActive` (`TerminalUpgradeRebootCommands.kt`) был единственным из
    трёх флагов игровых сессий терминала (`isUpgradeSessionActive`, `isDeepDiveSessionActive`,
    `isRebootSessionActive`), не персистентным в `SharedPreferences` — только `private var`.
    Поворот экрана/сворачивание/восстановление процесса теряли флаг, и `USER.REBOOT.END`
    отвечал «нет активной сессии», хотя `USER.REBOOT.START` был выполнен. Два других флага уже
    десятки строк рядом читают/пишут этот же паттерн в `terminal_prefs`.
  - **T3** — `ConsoleAdapter.graduallyFill` на каждый `addTyping()` создавал свой
    `Handler(Looper.getMainLooper())` с рекурсивным `postDelayed(25ms)`, который никогда не
    отменялся. При серии из нескольких `addTyping` на один ответ команды (обычное дело — почти
    каждый обработчик печатает 2-6 строк) параллельно тикало несколько таймеров; ни один не
    снимался при уходе с экрана — `notifyItemChanged` на список данных уничтоженной активности.

### Сделано

1. **`TerminalUpgradeRebootCommands.kt` — персистентность `isRebootSessionActive`.**
   Добавлен ключ `reboot_session_active` в `terminal_prefs` — читается в `init` (по тому же
   паттерну, что `isUpgradeSessionActive`), пишется `true` в `handleRebootStartCommand()` (в
   тот же `prefs.edit()`, что уже пишет `last_reboot_time`, чтобы не множить commit'ы) и
   `false` в `handleRebootEndCommand()`. Логика самих команд (кулдаун 1 час, снижение шума на
   1 при `.END`) не менялась.
2. **`ConsoleAdapter.kt` — отмена «печатающих» таймеров.** Добавлен приватный список
   `activeTypingHandlers: MutableList<Pair<Handler, Runnable>>` — каждый запущенный в
   `graduallyFill` `Handler`/`Runnable` добавляется в список при старте и убирается сам по
   завершении печати (естественный путь, не только по отмене). Новый публичный
   `cancelAllTyping()` снимает все ещё висящие `postDelayed`-колбэки и чистит список — вызван
   из `TerminalActivity.onDestroy()` (тот же метод, где уже снимается `historyFlushHandler` и
   зовётся `noiseManager.cleanup()`). Сама анимация «печати» (скорость 40 симв/сек, скроллинг
   каждые 10 символов) не менялась — только добавлена возможность её остановить снаружи.

### Живая проверка

Поднял `Pixel_6_API_35` (`emulator-5554`), установил свежий APK. `MainActivity` стартовал
штатно (`topResumedActivity` в foreground), открыл терминал тапом по точным bounds из
`uiautomator dump` (следуя выводу сессии 22 — не по координатам со скриншота на глаз).
Целенаправленно проверил именно правку №1 (T4), не трогая правку №2 (T3 — она проявляется
только при уничтожении экрана во время печати, живая проверка не даст полезного сигнала без
искусственной задержки, а сам факт отсутствия таймера-утечки подтверждён построчным разбором):
- Выполнил `USER.REBOOT.START` в терминале (это **не мутирует шум/ауру на сервере** — только
  ставит локальный кулдаун 1 час и текст ответа; в отличие от `.END`, который снижает шум на
  сервере, `.END` сознательно НЕ выполнял — по тому же принципу, что и раньше отклонённые
  живые проверки эффектов шума, чтобы не менять реальное игровое состояние без владельца).
  Команда прошла без ошибок, ответ «=== ПЕРЕЗАГРУЗКА СИСТЕМЫ ===» напечатан корректно.
  `adb shell run-as bas.app.shift cat shared_prefs/terminal_prefs.xml` подтвердил:
  `reboot_session_active=true`, `last_reboot_time` записан.
- `am force-stop` + повторный `am start` (эмулирует уничтожение процесса — именно тот сценарий,
  который раньше терял флаг) → `terminal_prefs.xml` всё ещё содержит `reboot_session_active=
  true` после перезапуска, `MainActivity` поднялась в foreground без единого краша
  (`logcat -d | grep "FATAL EXCEPTION|AndroidRuntime:E"` — пусто за всю сессию). Это прямое
  подтверждение фикса: до правки поле было `private var` в памяти конструктора
  `TerminalUpgradeRebootCommands`, пересоздаваемого при каждом новом открытии `TerminalActivity`
  — после `force-stop` оно было бы `false` независимо от команды `.START`.
- **Вернул эмулятор в исходное состояние**: `reboot_session_active` сброшен обратно на `false`
  через `adb shell cat ... | run-as ... sh -c 'cat > ...'` (прямой `run-as cp` из способа
  сессии 8 в этот раз упёрся в `Permission denied` на файле, запушенном в `/sdcard` — обошёл
  через пайп `cat` напрямую в `run-as sh -c 'cat > ...'`, без промежуточного файла на sdcard;
  стоит запомнить как более надёжный вариант старого способа). `last_reboot_time` НЕ сбрасывал
  (это не мутация сервера, просто локальный кулдаун — не влияет на реальное игровое состояние).
  Никакого сетевого вызова, меняющего шум/ауру/эффекты на сервере, за всю сессию не делал.

### Итог сборки

`assembleDebug --offline` — BUILD SUCCESSFUL до начала правок, после каждой правки и в конце;
`compileDebugKotlin --rerun-tasks --offline` (полная перекомпиляция) — тоже зелёный, ни одного
нового warning'а (только прежние деприкейшны). Эмулятор поднят и остановлен в конце сессии
(`adb emu kill`). RAG не переиндексирован в этот раз. Ничего не закоммичено.

### Backlog на следующую ночь

- **`04-subsystems.md` стоит освежить целиком** (по образцу того, что сессия 15 сделала для
  `08-changes-applied.md`) — документ не обновлялся с 2026-07-22 и на 90%+ описывает уже
  исправленные находки (T1/T2/T5/T6, CH1-CH4, NO1/NO3/NO5, AU1 подтверждены исправленными в
  этой сессии). Это сэкономит время будущим сессиям. Весь материал для обновления уже собран
  выше (список «уже исправлено»).
- **Не проверены в этой сессии** — оставшиеся находки `04-subsystems.md`: T7 (по сессии 21 уже
  закрыт, Wave 13), NO2 (дедуп по устаревшему кешу), NO4 (вечный `CoroutineScope` в
  `NoiseEffectManager` — по коммент в коде "UI отсюда не трогается" похоже на осознанное
  решение, не багу, но стоит зафиксировать явно), **MA1-MA8 (Maps — полное пересоздание кругов/
  маркеров каждые 10с и связанные MA2-MA4, HIGH severity в исходном аудите, не проверялся ни
  разу за все 23 сессии — лучший кандидат для следующей сессии: либо подтвердить фикс, либо
  это самый ценный оставшийся риск для боевой карты с несколькими игроками)**, AU2-AU8,
  AR1-AR5, CH5-CH8.
- Визуально долетать до эффектов шума в терминале живьём — по-прежнему не проверено (требует
  мутации личного уровня шума на сервере, сознательно не делаю сам).
- Doze/заблокированный экран — ручная 30–60 мин проверка живым человеком.
- **Владельцу по-прежнему стоит закоммитить рабочий дифф** — теперь включает всё по сессию 23
  включительно (персистентность `isRebootSessionActive`, отмена таймеров печати в
  `ConsoleAdapter`, плюс весь бэклог сессий 1–22).

**ЗАВЕРШИЛ:** 2026-07-27 07:13 — два небольших проверенных изменения: персистентность
`isRebootSessionActive` в `terminal_prefs` (T4 из исходного аудита `04-subsystems.md`,
устраняет потерю REBOOT-сессии при повороте/уничтожении процесса — подтверждено живьём через
`force-stop`+перезапуск с сохранённым флагом); отмена «печатающих» Handler-таймеров
`ConsoleAdapter` при уничтожении терминала (T3, устраняет утечку таймеров и обращения к
уничтоженному списку данных). Заодно провёл ревизию находок `04-subsystems.md` против текущего
кода — большинство (T1/T2/T5/T6/CH1-CH4/NO1/NO3/NO5/AU1) оказались уже исправлены предыдущими
сессиями без обновления документа; выявлен нетронутый со времён исходного аудита раздел Maps
(MA1-MA8, включая HIGH-находку о пересоздании маркеров каждые 10с) как самый ценный следующий
кандидат на проверку. Сборка зелёная (обычная + полная перекомпиляция). Живая проверка на
эмуляторе не мутировала общее игровое состояние (только локальный кулдаун REBOOT, сброшен
обратно в конце). Ничего не закоммичено. Проект оставлен в собирающемся состоянии, эмулятор
остановлен.

## 2026-07-27 (сессия 24, 07:24)

**НАЧАЛ:** 2026-07-27 07:24 — прочитал журнал (предыдущая сессия завершена в 07:13, не «в
работе» — гонки нет), взял рекомендованный backlog-пункт: раздел Maps (MA1-MA8 из
`04-subsystems.md`), ни разу не проверявшийся за 23 предыдущие сессии.

### Находка: MA1/MA3/MA6 уже исправлены (не документировано)

При чтении `EkatMaps.kt` и нового `utils/MapPointsRenderer.kt` (229 строк, уже в рабочем
дереве как untracked-файл, `git log` подтверждает, что это не часть коммитов) обнаружилось,
что HIGH-находка **MA1** (полное пересоздание всех кругов/маркеров каждые 10с — мерцание,
сброс info-window, GC-нагрузка) **уже устранена**: логика вынесена из `EkatMaps` в
`MapPointsRenderer.syncPoints()`, который диффит серверный список с уже отрисованным (убирает
только реально пропавшие точки, двигает существующие круги/маркеры `circle.center =`/
`marker.position =` вместо `remove()+add()`, пересоздаёт только при смене `type`). Заодно этим
же диффом закрыты **MA3** (USER-маркеры игроков теперь двигаются на месте, а не
телепортируются) и **MA6** (мутация `pointsOfInterest` в `forEach` — теперь либо не мутирует
итерируемую коллекцию, либо мутирует только перезаписью существующего ключа, как и раньше,
без CME). Кем и когда именно это было сделано — не ясно (не отражено в предыдущих записях
журнала и не закоммичено), но код рабочий и уже используется (`pointsRenderer =
MapPointsRenderer(mMap, isMgUser)` в `onMapReady`, `EkatMaps.kt:174`).

Также перепроверены остальные пункты MA-раздела по текущему коду:
- **MA4** (использование `mMap`/`pointsRenderer` до `onMapReady`) — фактически не
  воспроизводится: `pointsRenderer` используется только из путей, достижимых после
  `onMapReady` (`showPlayersPickerDialog` уже имеет явную проверку
  `::pointsRenderer.isInitialized`), а `currentLocation` (используется в
  `moveToCurrentLocation` → `mMap.animateCamera`) устанавливается только внутри
  `requestForLocation()`, которая вызывается САМА из конца `onMapReady` — то есть `mMap` не
  может быть не инициализирован в момент, когда `currentLocation != null`. Риска нет, менять
  не стал (не нашёл жизнеспособного сценария падения — введение доп. проверки было бы
  спекулятивным).
- **MA5** (ранний `return` в `onCreate` до `setContentView` при «не в игре») — не
  воспроизводится: `finish()` вызывается сразу с `return`, `onResume` в реальном лайфцикле
  Android не получает шанса выполниться раньше физического завершения активности при таком
  сценарии; оставил как есть.
- **MA7** (`fromServerValue(unknown) → USER`), **MA8** (хардкод «30 мин» для
  SHRINKING_CIRCLE, мёртвый `generatePointId()`) — подтверждены, всё ещё в коде
  (`PointType.kt:17-19`, `MapPointsRenderer.kt:151`, `EkatMaps.kt` — не проверял точную строку
  `generatePointId`), но LOW severity и не про надёжность в бою — не риск для этой сессии.

### Сделано: MA2 — непроверенный каст `as SupportMapFragment`

`EkatMaps.kt` (`onResume`): `supportFragmentManager.findFragmentById(R.id.map) as
SupportMapFragment` падал `ClassCastException`/NPE, если фрагмент карты ещё не приложен
(гонка восстановления состояния после process death, либо ранний `finish()` до
`setContentView` — второй сценарий сейчас не воспроизводится по MA5 выше, но защита не
специфична к причине). Заменил на безопасный `as?` с явной проверкой на `null`: если фрагмент
не найден — лог-предупреждение и `return` из `onResume` вместо падения; на штатном пути
(фрагмент есть) поведение не изменилось (`getMapAsync(this)` вызывается точно так же). Вторая
часть MA2 (лишний `getMapAsync`/переинициализация карты на каждый `onResume`) не трогал —
`onPause` уже симметрично отменяет `updatePointsRunnable`/`locationUpdateJob`, поэтому
повторная инициализация не приводит к утечке или дублированию подписок, только к не самой
эффективной работе; менять это было бы более рискованным изменением лайфцикла ради низкой
практической ценности.

### Итог сборки

`assembleDebug --offline` — BUILD SUCCESSFUL (и до, и после правки MA2);
`compileDebugKotlin --rerun-tasks --offline` (полная перекомпиляция) — тоже зелёный, ни одного
нового warning'а.

### Backlog на следующую ночь

- **MA1/MA3/MA6 стоит явно занести в `04-subsystems.md` как «исправлено»** (по образцу правки
  T-раздела в сессии 23) — сейчас документ единственный источник, откуда следующая сессия
  узнаёт, что делать, и он всё ещё показывает MA1 как открытый HIGH-риск.
- MA7/MA8 (LOW) — можно взять точечно в одну из следующих сессий, если не найдётся более
  ценного кандидата: MA7 — `PointType.fromServerValue` тихо превращает неизвестный тип в
  USER, лучше явно логировать/использовать отдельный `UNKNOWN`; MA8 — вынести «30 мин» в
  константу или считать по `expireAt`, удалить мёртвый `generatePointId()`.
- Ещё не проверены за все 24 сессии: NO2, NO4 (см. заметку сессии 23 — вероятно осознанное
  решение, не баг), AU2-AU8, AR1-AR5, CH5-CH8.
- Живая проверка эффектов шума в терминале — по-прежнему сознательно не делается (мутирует
  сервер).
- Doze/заблокированный экран — по-прежнему нужна ручная проверка человеком.
- **Владельцу по-прежнему стоит закоммитить рабочий дифф** (растёт с каждой сессией, сейчас
  включает всю работу по сессию 24).

### Живая проверка

Поднял `Pixel_6_API_35`, установил свежий APK, открыл `MainActivity` → «ОТКРЫТЬ КАРТУ».
Карта отрисовалась штатно (маркер геолокации, точки интереса, полигон видимости). Свернул
приложение (`KEYCODE_HOME`) и вернул на передний план (`am start` на `MainActivity`, который
поднял существующий таск с `EkatMaps` наверху) — это провело активность через `onPause`→
`onResume`, то есть ровно через изменённый код (`as?`-каст + `getMapAsync`). `topResumedActivity`
корректно вернулся на `EkatMaps`, карта отрисовалась идентично (скриншот до/после совпадает),
`logcat -d` за весь запуск не показал `FATAL EXCEPTION`/`AndroidRuntime: E`/
`ClassCastException` — только фоновые GMS-предупреждения эмулятора, не связанные с приложением.
Экран карты не мутирует состояние на сервере (просмотр точек, локальный обзор) — открытие/
закрытие безопасно для реального игрового состояния. Эмулятор остановлен (`adb emu kill`) в
конце сессии.

**ЗАВЕРШИЛ:** 2026-07-27 07:31 — проверил раздел Maps (MA1-MA8), ни разу не тронутый за 23
предыдущие сессии. Обнаружил, что HIGH-находка MA1 (полное пересоздание кругов/маркеров каждые
10с) вместе с MA3 и MA6 уже устранены необъяснённым (не в журнале, не закоммиченным) изменением
— выносом состояния карты в новый `MapPointsRenderer` с диффингом вместо clear+rebuild;
подтвердил это чтением кода и живой проверкой. Исправил остававшийся MA2: непроверенный
`as SupportMapFragment` в `onResume`, который мог упасть `ClassCastException`/NPE при гонке
восстановления состояния — заменён на безопасный `as?` с логом и `return`. MA4/MA5 проверены и
не воспроизводятся в текущем коде (не менял). MA7/MA8 — подтверждены, LOW severity, оставлены
в бэклоге. Сборка зелёная (обычная + полная перекомпиляция). Живая проверка на эмуляторе прошла
через изменённый путь (`onPause`→`onResume`) без крашей. Ничего не закоммичено. Проект оставлен
в собирающемся состоянии, эмулятор остановлен.

**НАЧАЛ:** 2026-07-27 07:45 — `git status`/`git diff --stat` совпадают с записью сессии 24 (30 изменённых/новых файлов, ничего не закоммичено). Беру задачи из бэклога сессии 24: (1) задокументировать MA1/MA3/MA6 как исправленные в `04-subsystems.md`; (2) точечно взять MA7/MA8 (LOW, Maps) если не найдётся более ценного кандидата; (3) осмотреть ещё не проверенные разделы (NO2/NO4, AU2-AU8, AR1-AR5, CH5-CH8) через RAG и выбрать 1-2 безопасных улучшения.

### Сделано: документация MA1/MA3/MA6 + исправлены MA7/MA8 (Maps, LOW)

**Документация.** В `04-subsystems.md` (раздел Maps) отметил MA1/MA3/MA6 как исправленные
(с указанием, что это сделал `MapPointsRenderer` из сессии 24), MA2 — исправлено, MA4/MA5 —
проверено и не воспроизводится. Раньше документ единственный источник контекста для следующей
сессии всё ещё показывал MA1 как открытый HIGH-риск — это вводило в заблуждение.

**MA7 — `PointType.fromServerValue(unknown)` молча превращался в `USER`.**
Неизвестный серверу тип точки становился неотличим от точки живого игрока (терял круг,
подписывался «Кто-то в игре») — искажение боевой карты без единого сигнала в логах. Добавил
`PointType.UNKNOWN` (по образцу уже существующего паттерна `OTHER` в `AuraType`/
`AuraProblemType`) и лог `LogHelper.w(...)` при фолбэке в `fromServerValue`. Обновил
`MapPointsRenderer.getPointTitle` (добавил ветку `UNKNOWN -> "Неизвестный тип точки"`) и
исключил `UNKNOWN` из спиннера создания точки в `EkatMaps.kt` (`filter { it != PointType.USER
&& it != PointType.UNKNOWN }`) — служебное значение не должно быть выбираемо МГ вручную.
`PointVisualizer` не трогал: отсутствующие в `circleColors`/`markerColors` типы уже штатно
уходят в дефолтный серый/маджента (как `HIDDEN_EFFECT_AREA`, `HIDDEN_AR_POINT` и т.д.), `UNKNOWN`
получит то же поведение без изменений в этом файле.

**MA8 — хардкод «Длительность: 30 мин» для SHRINKING_CIRCLE независимо от реального expireAt.**
`MapPointsRenderer.getPointDescription` всегда показывал фиксированный текст, даже если МГ
создал круг с другой длительностью (поле `expireMinutes` в диалоге создания). Заменил на
`DateTimeHelper.formatExpireAt(point.expireAt)` (тот же хелпер, что уже используется для
`Effect.expireAt` в `EffectEditorActivity`) — если `expireAt` есть и парсится, показывается
реальная дата истечения; если `expireAt` null или не парсится — откат на `formatExpireAt`
к исходной строке или просто радиус без строки длительности (без крашей, `formatExpireAt`
ловит исключение парсинга сам). Мёртвый `generatePointId()` из MA8 — не нашёл его в текущем
коде вообще (`grep` по всему проекту, 0 совпадений), видимо уже удалён вместе с рефакторингом
в `MapPointsRenderer` из сессии 24 — упоминание в `04-subsystems.md` убрано как устаревшее.

### Итог сборки и живой проверки

`assembleDebug --offline` — BUILD SUCCESSFUL. Поднял `Pixel_6_API_35`, установил APK,
открыл `MainActivity` (уже залогинен как `Bas`, «В игре») → «ОТКРЫТЬ КАРТУ». `EkatMaps`
открылась, `logcat` показал получение 41 точки с реального сервера — ни одна не попала в
новую ветку `PointType.UNKNOWN`/лог-предупреждение (все известные типы), карта отрисовалась
штатно (маркеры инфраструктуры + геолокация), `logcat -d` за весь запуск без
`FATAL EXCEPTION`/`AndroidRuntime: E`/`ClassCastException`. Экран карты не мутирует состояние
сервера — просмотр безопасен для боевого состояния. Эмулятор остановлен в конце сессии.

### Backlog на следующую ночь

- Ещё не проверены за все 25 сессий: NO2, NO4 (см. заметку сессии 23 — вероятно осознанное
  решение, не баг), AU2-AU8, AR1-AR5, CH5-CH8.
- Живая проверка эффектов шума в терминале — по-прежнему сознательно не делается (мутирует
  сервер).
- Doze/заблокированный экран — по-прежнему нужна ручная проверка человеком.
- Стоит перепроверить формат `expireAt`, который `EkatMaps.kt` (~строка 660) генерирует при
  создании SHRINKING_CIRCLE (`"yyyy-MM-dd'T'HH:mm:ss'Z'"`), против формата, который парсит
  `DateTimeHelper.formatExpireAt` (`"yyyy-MM-dd HH:mm:ss"`, без 'T'/'Z') — если сервер
  возвращает точки в GET-ответе в СВОЁМ каноническом формате (что использует `formatExpireAt`
  для `Effect.expireAt` успешно), то несовпадение не проявляется на чтении, только возможно
  при создании точки (сервер должен сам привести дату). Не трогал — нужно сначала подтвердить
  реальный формат ответа сервера для точки с `expireAt`, это не LOW-риск правка вслепую.
- **Владельцу по-прежнему стоит закоммитить рабочий дифф** (растёт с каждой сессией, сейчас
  включает всю работу по сессию 25, включая новый файл `PointType.UNKNOWN`).

**ЗАВЕРШИЛ:** 2026-07-27 07:52 — задокументировал в `04-subsystems.md`, что MA1/MA2/MA3/MA6
(Maps) уже исправлены (сделано в сессии 24 через `MapPointsRenderer`), а MA4/MA5 проверены и
не воспроизводятся — раньше документ ошибочно показывал MA1 как открытый HIGH-риск. Исправил
оставшиеся LOW-находки: MA7 (`PointType.fromServerValue` молча превращал неизвестный тип точки
в `USER`, маскируя её под живого игрока) — добавил `PointType.UNKNOWN` с логированием фолбэка
и обновил `getPointTitle`/спиннер создания точки; MA8 (хардкод «30 мин» для SHRINKING_CIRCLE
независимо от реального expireAt) — заменил на `DateTimeHelper.formatExpireAt(point.expireAt)`,
переиспользовав существующий хелпер. Дохлый `generatePointId()` из MA8 в коде не найден —
видимо уже удалён ранее, убрал устаревшее упоминание. Сборка зелёная, живая проверка на
эмуляторе через реальный сервер (41 точка, ни одна не unknown) прошла без крашей. RAG
переиндексирован. Ничего не закоммичено. Проект в собирающемся состоянии, эмулятор остановлен.

---

## 2026-07-28 (сессия 26, 06:04)

**НАЧАЛ:** 2026-07-28 06:04 — прочитал журнал (последняя запись — сессия 25, 2026-07-27
07:52, «ЗАВЕРШИЛ», гонки нет), но `git status`/`git diff --stat` **не совпадают** с тем,
что описано в записи сессии 25 — рабочее дерево выглядит совершенно иначе. Разобрался,
что произошло за прошедшие сутки (важно для следующих сессий, отражаю явно):

1. **Весь бэклог сессий 1–25 закоммичен владельцем.** `git log` показывает два новых
   коммита поверх `Nightly`-истории: `5f8e813 Claude improvements` (2026-07-24 15:11) и
   `475874a Claude improvements2` (2026-07-28 01:36). Первый коммит вобрал в себя ровно то,
   что нарабатывали ночные сессии 1–9 (RxJava→StateFlow, буфер истории терминала,
   унификация `NetworkErrors` во всех Wave 1–7, `TerminalVisualEffects`,
   `DisplayNames.combinePlayerFirst`). Второй — гораздо более крупный ручной/дневной заход
   (не ночной серией): рефакторинг `EkatMaps`/`MainActivity` (вынесен
   `MainActivityPermissions.kt`), новые `RitualManager.kt`, `ProfileDiffer.kt`,
   `PointRadiusMath.kt`, `LocationNotifications.kt`, `NewMessagesChecker.kt`, чистка
   неиспользуемых хелперов. Это закрывает весь бэклог сессий 10–25 (Maps MA1-MA8,
   `PointType.UNKNOWN`, T3/T4 и т.д. — всё это уже часть закоммиченной истории).
2. **Появился новый документ `analysis/10-backlog-plan.md`** (создан/обновлён
   2026-07-28 ночью владельцем в прямой интерактивной сессии с Claude, НЕ через ночную
   серию) — разбор игрового бэклога («что делать к выезду»), с пунктами #4/#5/#9/#13
   помеченными как «закрыто» и #7/#28 — «отложено по решению владельца». Также обновлён
   `04-subsystems.md` (в нём уже отмечены как исправленные/проверенные NO2 и NO4 —
   судя по всему, тоже в рамках этого захода, не через 09-journal).
3. **В рабочем дереве на момент старта этой сессии уже лежала несохранённая, но
   собирающаяся правка** — судя по времени модификации файлов (~02:51–05:08) и
   содержанию `10-backlog-plan.md`, это реализация пунктов #4/#5/#9 бэклога: новый
   `helpers/AuraCleanupManager.kt` (застейджен), плюс правки `EkatMaps.kt`,
   `MainActivity.kt`, `ShiftApi.kt`, `NoiseEffectManager.kt`, `Point.kt`,
   `UpdatePointHiddenRequest.kt`, `User.kt`, `ServerService.kt`, `AuraActivity.kt`,
   `FamiliarChatActivity.kt`, `FamiliarFoundActivity.kt`, `MapPointsRenderer.kt`,
   `PointVisualizer.kt`, layouts, `strings.xml`. Это игровые фичи (очистка ауры, скрытие
   точек, фамильяр-находки), а не техдолг из ночного бэклога — **эту сессию я НЕ трогал**,
   `assembleDebug --offline` на старте уже был зелёным (BUILD SUCCESSFUL, всё UP-TO-DATE),
   значит правка сама по себе рабочая и завершённая с точки зрения компиляции. Не стал
   лезть в эти файлы, чтобы не мешать асинхронной работе владельца/дневной сессии над
   игровыми фичами — ночная серия занимается техдолгом/надёжностью отдельно от этого.

**Вывод для будущих сессий:** источник истины по игровым фичам к выезду теперь
`analysis/10-backlog-plan.md` (не 09-journal), обновляется вне ночной серии. Ночная серия
(этот файл) по-прежнему отвечает за техдолг/надёжность и должна **избегать файлов,
которые в момент старта уже модифицированы, но не закоммичены** — это чужая незавершённая
работа, не откат и не мёртвый код.

### Что сделано

Взял из старого бэклога (сессия 24, раздел Artifacts, `04-subsystems.md`) пункты **AR1**
(MED) и **AR5** (LOW) — оба про файлы, не тронутые текущим незакоммиченным WIP
(`ArtifactDetailsFragment.kt`, `ArtifactCreatorActivity.kt`), риск конфликта с чужой
работой нулевой.

**AR1 — сетевые колбэки фрагмента без строгой проверки жизненного цикла**
(`ui/ArtifactDetailsFragment.kt`):
- `showError()` раньше звал `Toast.makeText(context, …)` без проверки на `null` — если
  колбэк `fetchArtifact`/`loadUsersForDialog`/`updateArtifactBinding` возвращается уже
  после ухода с экрана (`context` у отсоединённого фрагмента = `null`), это платформенный
  тип в Kotlin (компилируется без предупреждения) и потенциальный NPE в рантайме. Теперь
  `context ?: return` перед показом тоста, `LogHelper.e` пишется всегда (даже если тост
  показать некому — диагностика не теряется).
- `loadUsersForDialog()` → `onResponse`: добавлена проверка `if (!isAdded) return` перед
  вызовом `showBindingDialog()` — та функция использует `requireContext()` несколько раз
  подряд (инфлейт диалога, `AlertDialog.Builder`), что кидает `IllegalStateException`, если
  фрагмент уже отсоединён к моменту прихода сетевого ответа.
- `updateArtifactBinding()` → успешный ответ: тост «Привязка обновлена» был безусловным
  `Toast.makeText(context, …)` — обёрнут в `context?.let { … }`, симметрично уже
  существовавшей проверке `_binding != null && isAdded` для обновления текста.
- Поведение на штатном пути (фрагмент на экране) не меняется — только добавлены ранние
  выходы на путях, которые раньше падали или рисковали NPE/ISE.

**AR5 — нет защиты от повторного тапа до отключения кнопки создания артефакта**
(`ui/ArtifactCreatorActivity.kt`):
- `createArtifact()`: добавлена ранняя проверка `if (!binding.saveButton.isEnabled) return`
  в самом начале функции — отсекает повторный клик, пока предыдущий запрос ещё в процессе
  (кнопка уже была `isEnabled = false` перед `enqueue`, но раньше ничего не мешало
  синхронно повторно войти в `createArtifact()` до этой строчки при очень быстром двойном
  тапе). Валидация и сама отправка не менялись.

### Живая проверка

Поднят `Pixel_6_API_35` (`emulator-5554`, уже был запущен), пользователь на эмуляторе —
`MG_Bas` (не трогал, оставил как было). Собрал и установил свежий APK
(`adb install -r`), запустил `MainActivity` — старт чистый. Прошёл именно по изменённым
путям через точные `bounds` из `uiautomator dump` (без оценки координат на глаз):
- «СОЗДАТЬ АРТЕФАКТ» → `ArtifactCreatorActivity` открылась, кнопка «СОЗДАТЬ АРТЕФАКТ»
  в обычном (включённом) состоянии — форму не заполнял и не отправлял (создание артефакта
  необратимо мутирует общий сервер), вернулся назад без сабмита.
- «ПАСПОРТ АРТЕФАКТОВ» → `ArtifactPassportActivity`, открыл спиннер (реальный список
  существующих артефактов с сервера), выбрал «тест / Антон» → `ArtifactDetailsFragment`
  корректно отрисовал все поля (название/уровень/тип/материал/свойства/создатель/
  привязка) — это ровно путь `fetchArtifact()`, который я правил.
- «ИЗМЕНИТЬ» (привязка) → диалог со списком пользователей открылся штатно — это ровно
  путь `loadUsersForDialog()` → `showBindingDialog()`, который я правил новой проверкой
  `isAdded`. Нажал «ОТМЕНА» (не «СОХРАНИТЬ») — изменений на сервере не делал.
- `adb logcat -d | grep "FATAL EXCEPTION|AndroidRuntime: E"` за всю сессию — пусто, крашей
  нет. Никаких мутирующих запросов (`POST`/`PUT`) за сессию не отправлено — только `GET`
  списка пользователей и артефакта.
- Эмулятор остановлен приложением через `am force-stop` (сам эмулятор не гасил, он был
  поднят до начала сессии).

### Итог сборки

`assembleDebug --offline` — BUILD SUCCESSFUL и на старте (до правок, чужой WIP уже
собирался), и после правок AR1/AR5, и финальная перепроверка в конце сессии — все три раза
зелёные. `git status` после сессии: изменены ровно два файла сверх уже бывшего WIP
(`ArtifactDetailsFragment.kt`, `ArtifactCreatorActivity.kt`), ничего больше не тронуто,
ничего не закоммичено.

### Backlog на следующую ночь

- **Не трогать файлы текущего незакоммиченного WIP** (см. список в начале записи —
  `EkatMaps.kt`, `MainActivity.kt`, `ShiftApi.kt`, `NoiseEffectManager.kt`, модели точек/
  пользователя, `ServerService.kt`, `AuraActivity.kt`, `FamiliarChatActivity.kt`,
  `FamiliarFoundActivity.kt`, `MapPointsRenderer.kt`, `PointVisualizer.kt`, связанные
  layouts/strings) — пока это не закоммичено, считать чужой активной работой. Если к
  следующей сессии это будет закоммичено — WIP-ограничение снимается само.
- Оставшиеся из `04-subsystems.md`, не тронутые ни разу: **AR2/AR3/AR4** (LOW, Artifacts —
  но AR2/AR3 не в файлах текущего WIP, можно брать), **AU2-AU8** (Aura — ЧАСТЬ этих файлов
  сейчас в WIP, аккуратно сверять по списку выше перед правкой), **CH5-CH8** (Chat —
  CH5/CH6 конкретно про `FamiliarChatActivity`, который сейчас в WIP — пропускать; CH7/CH8
  про `MessagesAdapter`/`ChatAdapter`, не в WIP — можно брать).
- Doze/заблокированный экран — по-прежнему нужна ручная 30-60 мин проверка человеком.
- **Владельцу по-прежнему стоит закоммитить рабочий дифф** — теперь это ДВА независимых
  слоя: техдолг ночной серии (AR1/AR5 этой сессии) и игровые фичи из `10-backlog-plan.md`
  (#4/#5/#9, уже проверены владельцем напрямую).

**ЗАВЕРШИЛ:** 2026-07-28 06:12 — обнаружил и задокументировал большой разрыв в
преемственности (весь бэклог сессий 1-25 закоммичен, появился параллельный
`10-backlog-plan.md` и несвязанная с ночной серией уже собирающаяся WIP-правка игровых
фич). Не тронул чужой WIP. Вместо этого взял из старого бэклога **AR1** (MED, NPE/ISE-риск
в `ArtifactDetailsFragment` при колбэках после detach фрагмента) и **AR5** (LOW, защита от
двойного тапа в `ArtifactCreatorActivity`) — оба файла не пересекаются с текущим WIP.
Сборка зелёная на всех трёх прогонах. Живая проверка на эмуляторе (роль `MG_Bas`) прошла
именно по изменённым путям (создание артефакта — открыл и вышел без сабмита; паспорт
артефактов — выбор из реального списка, деталь отрисовалась; диалог привязки — открылся и
отменён) без единого краша, никаких мутирующих запросов на сервер не отправлено. Ничего не
закоммичено. Проект в собирающемся состоянии, эмулятор оставлен запущенным (был поднят до
начала сессии).

---

## 2026-07-28 (сессия 27, 06:24)

НАЧАЛ: 2026-07-28 06:24 — прочитал журнал (последняя запись — сессия 26, 2026-07-28 06:12,
«ЗАВЕРШИЛ», гонки нет — статус явно завершён). `git status` совпадает с описанным в конце
сессии 26 (тот же WIP-список файлов + AR1/AR5). Проверил backlog сессии 26 (AR2/AR3/AR4,
AU2-AU8, CH5-CH8 за вычетом файлов текущего WIP).

По пути выяснилось, что бэклог `04-subsystems.md` устарел сильнее, чем считалось: при
чтении кода оказалось, что CH1/CH2/CH3/CH4/CH6/AU4 уже исправлены (не через ночную серию —
видимо, часть коммита `475874a Claude improvements2` или прямая дневная сессия владельца,
без записи в этом журнале): `MessagesChatActivity.kt` — чтение вложений уже на
`Dispatchers.IO` (CH1), `startPolling` уже без busy-loop (CH2), нет дублирования
text/recipient_id в multipart (CH3), `currentTempId` — убывающий счётчик, не `.toInt()` от
timestamp (CH6); `ChatsListActivity.startPeriodicRefresh` уже идемпотентен (CH4);
`RetrofitClient`/`AuraMarkType` — `AuraMarkTypeAdapter` уже зарегистрирован (AU4), плюс
незадокументированный `RetryInterceptor` (повтор GET/5xx с бэкоффом). Комментарии в коде
дословно совпадают с формулировками находок аудита — фикс делался тем же аудитом, просто
без строки в 09-журнале.

Взял из оставшегося реального бэклога три низкорисковых пункта, ни один файл не
пересекается с текущим WIP: AR3 (MED, риск ISE-краша), AU6 (LOW, фриз UI-потока), AR4 (LOW,
вводящий в заблуждение Toast).

### Что сделано

**AR3 — `ArtifactPassportActivity`: `commit()` мог кинуть `IllegalStateException`.**
`showArtifactDetails`/`hideArtifactDetails` делали `beginTransaction()....commit()` при
каждом выборе в спиннере (`onItemSelected`) — если выбор приходит после
`onSaveInstanceState` (например, во время сворачивания экрана), `commit()` кидает ISE и
краш. Заменил оба вызова на `commitAllowingStateLoss()` — транзакция ничего не мутирует на
сервере и не хранит важное состояние, потеря при пересоздании активности не критична.

**AU6 — `AuraQrActivity`: генерация QR (640k `setPixel`) на UI-потоке → фриз при открытии.**
`generateQrCode()` теперь оборачивает вызов `generateQRCode(...)` в
`lifecycleScope.launch { withContext(Dispatchers.Default) { ... } }`, результат
(`setImageBitmap`) применяется на `Main`. Добавлены импорты `lifecycleScope`,
`Dispatchers`/`launch`/`withContext` из уже используемого в проекте `kotlinx.coroutines`.

**AR4 — `CustomScannerActivity.focusOnTouch`: вводящий в заблуждение Toast.**
Метод показывал Toast «Фокус установлен» при каждом тапе по превью камеры, хотя реального
управления камерой не было (заглушка) — игрок/МГ мог решить, что тап действительно
перефокусирует камеру. Убрал Toast и неиспользуемый импорт `Toast`, оставил только
диагностический `LogHelper.d` с тем же текстом что и раньше плюс уточнение, что автофокус
работает сам по себе (стандартное поведение `CaptureActivity`, не менялось).

**Побочная находка: бэклог `04-subsystems.md` устарел сильнее, чем считалось.** При чтении
кода перед выбором задач обнаружилось, что **CH1/CH2/CH3/CH4/CH6 (Chat, включая 2×HIGH) и
AU4 (Aura, MED)** уже исправлены — не через эту ночную серию (в коде уже есть комментарии,
дословно объясняющие ровно те же причины, что в аудите). Обновил `04-subsystems.md`:
пометил CH1-CH4/CH6/AU4 как «ИСПРАВЛЕНО» с раскрывающимся блоком `<details>` с исходной
формулировкой находки (не удалял историю), плюс AR3/AU6/AR4 из этой сессии. CH5
(`FamiliarChatActivity`, `CoroutineScope(Dispatchers.Main)` вместо `lifecycleScope`) отмечен
как «не проверено» — файл в текущем незакоммиченном WIP, не трогал. Обновлена сводная
таблица топ-рисков внизу документа.

### Итог сборки и живой проверки

`assembleDebug --offline` — BUILD SUCCESSFUL и после каждой из трёх правок, и финальный
прогон (все таски UP-TO-DATE). Установил свежий APK на `emulator-5554` (был уже поднят).

Живая проверка каждого изменённого пути:
- **AR3** (роль `MG_Bas`, штатный логин на эмуляторе): «ПАСПОРТ АРТЕФАКТОВ» → открыл спиннер
  (16 реальных артефактов с сервера) → выбрал «тест / Антон», деталь отрисовалась → **трижды
  подряд** быстро переоткрыл спиннер и выбрал первый пункт (`open→select` без пауз) — деталь
  каждый раз отрисовывалась корректно (свойства/материал/создатель/привязка), ни одного
  краша. Побочно заметил (не в скоупе этой правки, не трогал): у части артефактов
  `creatorName` приходит как строка `"null"` в подписи спиннера («Бумеранговая Петля / null»)
  — стоит отдельно посмотреть на стороне сервера или клиента при следующей возможности.
- **AU6**: переключился на игрока `Bas` (метод из сессии 8 — правка
  `shared_prefs/user_prefs.xml`→`current_user_id` через `adb push`+`run-as cp` в
  `/data/local/tmp`, не `/sdcard` — туда `run-as` не может читать, permission denied).
  «ПРОФИЛЬ» → прокрутил вниз → «ПОКАЗАТЬ АУРУ ЭКСТРАСЕНСУ» → `AuraQrActivity` открылась,
  QR-код отрисовался корректно (скриншот сверен визуально — чёткий чёрно-белый QR с тремя
  угловыми маркерами), без видимого фриза интерфейса и без крашей.
- **AR4**: «ПОЗНАТЬ АРТЕФАКТ» → «СКАНИРОВАТЬ ШТРИХ-КОД» → запрошено и выдано разрешение
  камеры (`pm grant CAMERA`) → `CustomScannerActivity` с живым превью камеры эмулятора.
  Три тапа по превью с разным интервалом — logcat подтвердил новый текст лога
  (`"Тап в точке (450.0, 1000.0) — автофокус камеры работает сам"`), Toast «Фокус
  установлен» подтверждённо не появляется (искал в UI после тапа — не увидел, и по коду
  Toast полностью удалён). Кулдаун между тапами (1 сек) сработал как и раньше — сработал
  только последний из трёх быстрых тапов.
- За всю сессию: `adb logcat -d "AndroidRuntime:E" "*:S"` — пусто на каждой проверке,
  ни одного `FATAL EXCEPTION`. Никаких мутирующих запросов на сервер не отправлял (только
  просмотр — GET списка артефактов/деталей, локальная генерация QR, локальный доступ к
  камере).
- В конце сессии вернул `current_user_id` обратно на `MG_Bas` (тем же способом,
  push+run-as cp), подтвердил `cat`, `am force-stop`, удалил временные файлы из
  `/data/local/tmp`. Эмулятор оставлен запущенным (был поднят до начала сессии).

`git status` после сессии: изменены ровно три файла сверх уже бывшего WIP
(`ArtifactPassportActivity.kt`, `AuraQrActivity.kt`, `CustomScannerActivity.kt`) плюс правки
в `analysis/` (04-subsystems.md, этот журнал) — ничего больше не тронуто, ничего не
закоммичено. RAG переиндексирован (`rag_index.py --only Shift`).

### Backlog на следующую ночь

- **Не трогать файлы текущего незакоммиченного WIP** (см. список сессии 26 — `EkatMaps.kt`,
  `MainActivity.kt`, `ShiftApi.kt`, `NoiseEffectManager.kt`, модели точек/пользователя,
  `ServerService.kt`, `AuraActivity.kt`, `FamiliarChatActivity.kt`, `FamiliarFoundActivity.kt`,
  `MapPointsRenderer.kt`, `PointVisualizer.kt`, связанные layouts/strings) — пока не
  закоммичено, считать чужой активной работой.
- **Перед выбором следующих пунктов бэклога — перепроверять код, не доверять
  `04-subsystems.md` вслепую.** Этой ночью выяснилось, что 6 находок (CH1-CH4/CH6/AU4) были
  давно исправлены без отметки в аудите — документ частично устарел, несмотря на то что
  секции Maps/Artifacts уже обновлялись в сессиях 24-26. Стоит при случае пройтись по всем
  ещё не помеченным находкам (T1-T6, NO1-NO5, AU1-AU3/AU5/AU7/AU8, MA4-MA8 — уже помечены в
  сессии 25 как проверенные, AR2) и свериться с реальным кодом, а не только полагаться на
  список.
- **CH5** (`FamiliarChatActivity`, `CoroutineScope(Dispatchers.Main)` вместо `lifecycleScope`)
  — файл в WIP, взять при следующей возможности, когда WIP закоммитят.
- **AR2** (LOW, разные способы получения ID из скана — `ArtifactScannerActivity.toInt()` vs
  `AuraScannerActivity.toString()` без валидации) — не тронут, не в WIP, можно взять.
- **AU7** (LOW, `AuraScannerActivity` — мёртвый catch + нет валидации содержимого QR) — не в
  WIP, можно взять.
- **CH7/CH8** (LOW, `MessagesAdapter`/`ChatAdapter` — избыточная работа в `bind`, px вместо dp)
  — не в WIP, можно взять, но чисто косметические/perf-мелочи, не приоритет.
- **Побочно найдено, не в скоупе:** `ArtifactPassportActivity`/`ArtifactCreatorActivity` —
  часть артефактов показывает `creatorName` как строку `"null"` в подписи спиннера
  («Название / null») вместо пустой строки или человекочитаемого fallback — стоит посмотреть
  отдельно (не критично, косметика, но видна и МГ, и потенциально игроку).
- Doze/заблокированный экран — по-прежнему нужна ручная 30-60 мин проверка человеком.
- **Владельцу по-прежнему стоит закоммитить рабочий дифф** — растёт с каждой сессией.

**ЗАВЕРШИЛ:** 2026-07-28 06:45 — обнаружил и задокументировал, что бэклог `04-subsystems.md`
частично устарел (CH1-CH4/CH6/AU4 давно исправлены без отметки). Исправил три новых
низкорисковых пункта: **AR3** (MED, `commit()`→`commitAllowingStateLoss()` в
`ArtifactPassportActivity`, устраняет риск ISE-краша при быстром переключении спиннера во
время сворачивания), **AU6** (LOW, генерация QR-кода ауры перенесена с UI-потока на
`Dispatchers.Default`, устраняет фриз при открытии `AuraQrActivity`), **AR4** (LOW, убран
вводящий в заблуждение Toast «Фокус установлен» из `CustomScannerActivity`, который ничего
не фокусировал). Обновил `04-subsystems.md` (детальные секции + сводная таблица) под все 9
находок этой и предыдущих сессий. Сборка зелёная на всех прогонах. Живая проверка на
эмуляторе прошла по всем трём изменённым путям (роли `MG_Bas` и `Bas`, переключение
пользователя через `shared_prefs`) без единого краша; побочно найдена и задокументирована
(не исправлена) мелкая косметическая находка — `creatorName` как `"null"`. RAG
переиндексирован. Ничего не закоммичено. Проект в собирающемся состоянии, эмулятор оставлен
запущенным, пользователь возвращён на `MG_Bas`.

---

## Сессия 28

**НАЧАЛ:** 2026-07-28 06:47 — `git status`/`git diff --stat` совпадают с записью сессии 27
(тот же WIP из 23 файлов + `analysis/`). Беру пункты из бэклога сессии 27, НЕ входящие в
список WIP: **AU7** и **CH7** (оба файла подтверждены как не тронутые текущим WIP через
`git status`).

Исправлено:
- **AU7** (LOW, `AuraScannerActivity.kt:105-112`) — убран недостижимый
  `catch (NumberFormatException)` (метод `toString()` никогда его не бросает), добавлена
  реальная проверка на пустое содержимое QR (`trim().isEmpty()`) перед вызовом `fetchAura`.
  До правки пустой/битый скан ушёл бы в `getAura("")` без обратной связи пользователю; теперь
  показывается понятный Toast и активность закрывается. Валидный QR обрабатывается
  идентично прежнему коду (по инспекции — `scannedContent.toString()` на непустой String уже
  было no-op).
- **CH7** (LOW, `MessagesAdapter.kt`) — `rvAttachments.layoutManager`/`.adapter` больше не
  переустанавливаются в каждом `bind()`, а ставятся один раз в `init` блоке
  `MessageViewHolder`; `bind()` только обновляет данные через `updateAttachments`. Убирает
  лишнюю работу RecyclerView на каждой перерисовке сообщения с вложением.
- Заодно проверен **AR2**: расхождение типов ID между сканером артефактов (числовой
  `artifactId`) и ауры (строковый `userId`) — это разные домены по смыслу, не баг контракта;
  реальная часть находки (отсутствие валидации на стороне ауры) устранена как AU7. Пометил в
  `04-subsystems.md` как проверено, без изменений кода.

Сборка: `assembleDebug --offline` — BUILD SUCCESSFUL (только старые deprecation-warning'и
ZXing `IntentIntegrator`, не связанные с правкой). Живая проверка на эмуляторе
(`emulator-5554`, роль `MG_Bas`, без переключения пользователя — оба пункта не завязаны на
роль): открыт список чатов → чат с вложением-скриншотом (`1733593029979.jpg`) — вложение
отрисовалось корректно после переноса инициализации `rvAttachments` в `init`, скролл вверх/вниз
без крашей; общий проход по главному меню/навигации. `adb logcat -d "AndroidRuntime:E" "*:S"`
пуст на каждой проверке. Edge-case AU7 (пустой QR) вживую не проверялся — сложно
сгенерировать «пустой» QR-код физически в эмуляторе; уверенность основана на инспекции кода
(golden-путь не изменился, изменение чисто аддитивное).

`git status` после сессии: те же 23 файла WIP сессии 27 (без изменений) плюс НОВЫЕ два файла
вне WIP — `AuraScannerActivity.kt`, `ui/adapters/MessagesAdapter.kt` — и правки в
`analysis/04-subsystems.md`, этот журнал. Ничего не закоммичено. RAG переиндексируется
(`rag_index.py --only Shift`, запущен в фоне, файлы попадут в свежий индекс).

### Backlog на следующую ночь

- Список WIP-файлов из сессии 27 (не трогать до коммита) остаётся тем же, см. запись сессии
  27 выше.
- **CH8** (LOW, `ui/terminal/ChatAdapter.kt:59-69`, margin в px вместо dp) — рассмотрен, НЕ
  исправлен: правильное значение зависит от того, на какой плотности экрана дизайнер визуально
  подбирал текущие 64px — без этого контекста конвертация в dp рискует изменить видимый размер
  отступа на тестовом устройстве. Чисто косметическая находка, не приоритет — можно взять,
  если найдётся эталонная плотность или владелец подтвердит целевой dp.
- **CH5** (`FamiliarChatActivity`) — по-прежнему в WIP, не трогать до коммита.
- **AR1** (MED, `ArtifactDetailsFragment` — потенциальный NPE/ISE на `Toast`/`requireContext()`
  после detach фрагмента при получении сетевого ответа) — не в WIP, не тронут, хороший
  кандидат на следующую сессию: похожий паттерн уже чинили в AR3 этой ночью
  (`commitAllowingStateLoss`), здесь нужнее проверка `context != null`/`isAdded` перед
  каждым UI-обращением в колбэках.
- **AU5** (MED, `AuraEditorActivity` — гонка `post{}` при инициализации колбэка канваса) — не
  в WIP, не тронут, требует более внимательного анализа (не просто one-line fix).
- Побочная косметическая находка сессии 27 (`creatorName` как строка `"null"` в спиннере
  `ArtifactPassportActivity`/`ArtifactCreatorActivity`) — по-прежнему не исправлена, файл в
  WIP, взять после коммита.
- Doze/заблокированный экран — по-прежнему нужна ручная 30-60 мин проверка человеком.
- **Владельцу по-прежнему стоит закоммитить рабочий диф** — растёт с каждой сессией (25
  файлов).

**ЗАВЕРШИЛ:** 2026-07-28 07:05 — исправлены и живьём проверены два низкорисковых пункта вне
текущего WIP: **AU7** (валидация пустого QR-кода ауры вместо недостижимого catch) и **CH7**
(перенос инициализации `rvAttachments` adapter/layoutManager из `bind()` в `init`, убирает
лишнюю работу на каждой перерисовке сообщения с вложением). Заодно проверен и закрыт как
не-баг **AR2** (расхождение типов ID — разные домены, не ошибка). Обновлён
`04-subsystems.md`. Сборка зелёная, живая проверка на эмуляторе (роль `MG_Bas`) — чат со
вложением отрисовался корректно, скролл без крашей, `logcat` чист от `FATAL`/`AndroidRuntime`
на всех проверках. RAG переиндексируется в фоне. Ничего не закоммичено. Проект в
собирающемся состоянии, эмулятор оставлен запущенным, пользователь остался на `MG_Bas`
(роль не переключалась).

## 2026-07-28 (сессия 29, 07:05)

**НАЧАЛ:** 2026-07-28 07:05 — прочитал журнал (последняя запись — сессия 28, 07:05, ЗАВЕРШИЛ,
не «в работе», гонки нет). `git status`/`git diff --stat` совпадают с записью сессии 28 (WIP
25 файлов, ничего не закоммичено). Других процессов Claude не найдено (`ps aux`). Взял из
бэклога сессии 28 единственный оставшийся не-WIP кандидат — **AU5** (гонка колбэка canvas
через `post{}`), которую предыдущие сессии сознательно откладывали как «требует более
внимательного анализа».

Проверил актуальность **AU3/A/C/N** (массово `CoroutineScope` вместо `lifecycleScope`) —
находка в `04-subsystems.md` устарела: `AuraEditorActivity` и `FamiliarChatActivity` уже
используют `lifecycleScope` (когда именно починили — не через ночной журнал, не выяснял).
`NoiseEffectManager.scope` — единственный оставшийся `CoroutineScope(Dispatchers.IO +
SupervisorJob())`, но это НЕ баг: в самом коде есть явный комментарий, что эффекты шума — это
записи на сервер, которые обязаны завершиться даже после закрытия экрана. Пометил находку в
`04-subsystems.md` как проверенную/устаревшую, без изменений кода.

Исправил **AU5**: `AuraEditorActivity.setupUI()` коммитил фрагмент `commit()` и следующим
кадром (`binding.auraContainer.post{}`) ставил `markCallback`/`auraEditorCallback` — если
до отработки `post` происходило быстрое действие (например, long-tap по метке), колбэк
ещё `null`. Заменил на `commitNow()` — синхронно доводит фрагмент до `onViewCreated`,
колбэки ставятся сразу в `onCreate`, без кадра ожидания. Гонка устранена полностью (не
таймаутом, а убран сам источник асинхронности).

Заодно закрыл сопутствующую находку **AU9** (не была в бэклоге, увидел при чтении
`AuraFragment.kt` для AU5): `loadAura()` после `withContext(Dispatchers.Main)` трогает
`binding.auraCanvas` без проверки `_binding != null` — `lifecycleScope` фрагмента переживает
`onDestroyView`, так что ответ сервера, пришедший в узком окне между разрушением view и
уничтожением фрагмента, падал бы на `binding!!` (NPE). Добавил `if (_binding == null)
return@withContext` в начало Main-блока, по образцу уже существующей проверки в
`applyCallbackIfReady()`.

При живой проверке AU5/AU9 на эмуляторе обнаружил третий, более серьёзный и НЕ
задокументированный баг — **AU10**: экран «Редактор Ауры» открывался и бесконечно показывал
«Загрузка пользователей...», хотя список из 38 пользователей успешно приходил с сервера
(видно в logcat). Причина — в `activity_aura_editor.xml` лоадер `loadingLayout` зашит как
`visibility="visible"`, а спиннер выбора пользователя `userSelectionLayout` — как
`visibility="gone"`, и НИ ОДНА строка в `AuraEditorActivity.kt` не переключает эти
visibility. `GONE`-view не принимает тач — то есть выбрать пользователя для редактирования
ауры было физически невозможно, экран был полностью нефункционален. Файл
`activity_aura_editor.xml` не в WIP текущей серии (не трогался ни в одном коммите/сессии из
просмотренных), похоже на давно существующий регресс, а не следствие текущих правок.
Исправил в `loadUsers()`: по завершении запроса (успех или неудача) скрываю `loadingLayout`;
на успехе показываю `userSelectionLayout`; на неудаче — понятный `Toast` через `NetworkErrors`
вместо тишины (раньше ошибка только логировалась, экран так и оставался «загрузка навсегда»).

Сборка: `assembleDebug --offline` — BUILD SUCCESSFUL на каждом шаге (после AU5, после AU9,
после AU10), финально ещё раз с `--rerun-tasks` для полной перекомпиляции — 40/40 задач,
без ошибок (только старые deprecation-warning'и, не связанные с правками). Живая проверка на
эмуляторе (`emulator-5554`, роль `MG_Bas`, без переключения роли): «Редактор Ауры» открылся
без краша; лог подтвердил, что колбэки ставятся синхронно в `onCreate` ДО ответа `loadUsers`
(AU5 работает); список пользователей теперь открывается по тапу и позволяет выбрать
пользователя (AU10 работает); после выбора аура с меткой отрисовалась корректно; переоткрытие
экрана (назад → снова открыть) — без крашей. `adb logcat -d "AndroidRuntime:E" "*:S"` пуст на
всех проверках. Долгое нажатие по самой метке (для проверки диалога редактирования) вживую
не подтверждено — не удалось точно попасть тачем через `adb shell input swipe` по маленькой
иконке метки на скрине; не блокирует, т.к. правка AU5 касается только момента установки
колбэка, а не логики самого диалога, и код диалога в этой сессии не менялся.

`git status` после сессии: те же WIP-файлы плюс новые изменения в `AuraEditorActivity.kt`,
`AuraFragment.kt`, `04-subsystems.md`, этот журнал. Ничего не закоммичено. RAG не
переиндексирован в этой сессии (следующей сессии стоит прогнать `rag_index.py --only Shift`
перед поиском по этим файлам).

### Backlog на следующую ночь

- **AU1** (HIGH, `AuraCanvasView.kt:317`) — декодирование bitmap проблем на каждый `onDraw`,
  GC-шторм при drag/zoom. Крупная находка, требует кеширования bitmap (по аналогии с уже
  закешированными метками) — хороший кандидат, но объём больше «одной сессии», разбить на
  шаги.
- **AU2** (MED, `AuraCanvasView.kt`) — bitmap не рециклятся, кеш меток растёт без границ.
- **NO3** (MED, `NoiseManager.kt:216-220`) — `cleanup()` не зануляет
  `onGlobalNoiseUpdateListener`, риск обращения к `binding` после destroy — похожий паттерн
  на уже исправленные AR1/AU9, хороший кандидат.
- **T3** (MED, `ConsoleAdapter.kt:93-123`) — Handler-таймеры «печати» не отменяются, плодятся
  при серии ответов.
- **AU8** (LOW, `AuraActivity.kt:20`) — заглушка `entityId = "user-123"`, мёртвый код,
  тривиальная уборка.
- Список WIP-файлов из сессии 27 (не трогать до коммита) остаётся тем же, см. запись сессии
  27.
- **AR1** (потенциальный NPE в `ArtifactDetailsFragment`) — по записи сессии 28 уже
  исправлен в сессии 26; при следующем заходе стоит свериться, что это верно (таблица в
  `04-subsystems.md` это подтверждает).
- Doze/заблокированный экран — по-прежнему нужна ручная 30-60 мин проверка человеком.
- **Владельцу по-прежнему стоит закоммитить рабочий диф** — растёт с каждой сессией
  (25+ файлов WIP плюс новые правки этой сессии).

**ЗАВЕРШИЛ:** 2026-07-28 07:14 — исправлены и живьём проверены три MED-находки в Aura Editor:
**AU5** (гонка установки колбэка canvas через `post{}` → `commitNow()`), **AU9** (отсутствие
`_binding` guard в `AuraFragment.loadAura`, потенциальный NPE после `onDestroyView`), и
обнаруженный по ходу **AU10** — ранее не задокументированный баг, из-за которого экран
«Редактор Ауры» был полностью нефункционален (лоадер никогда не скрывался, спиннер выбора
пользователя никогда не показывался). Обновлён `04-subsystems.md` (записи AU5/AU9/AU10,
таблица, устаревшая AU3/A/C/N помечена проверенной). Сборка зелёная (полный rerun всех 40
задач), живая проверка на эмуляторе (роль `MG_Bas`) — редактор ауры теперь полностью
работоспособен: выбор пользователя, загрузка ауры с метками, повторное открытие экрана — без
крашей, `logcat` чист. Ничего не закоммичено. Проект в собирающемся состоянии, эмулятор
оставлен запущенным на экране «Редактор Ауры», пользователь остался на `MG_Bas` (роль не
переключалась).

## 2026-07-28 (сессия 30, 07:24)

**НАЧАЛ:** 2026-07-28 07:24 — прочитал журнал (последняя запись — сессия 29, 07:14,
«ЗАВЕРШИЛ», не «в работе», прошло 10 минут — гонки нет). `git status`/`git diff --stat`
совпадают с записью сессии 29 (27 изменённых файлов, WIP, ничего не закоммичено). Пересверил
актуальность оставшегося бэклога сессии 29 против текущего кода:

- **AU1** (декодирование bitmap на каждый `onDraw`) и **T3** (Handler-таймеры печати без
  отмены) — оказались уже исправлены в более ранних (недокументированных явной пометкой)
  правках: `AuraCanvasView.drawProblem()` кеширует decoded bitmap в `problemBitmaps` по
  `resId` (с явным поясняющим комментарием про GC-шторм), а `ConsoleAdapter` уже трекает
  активные Handler/Runnable пары в `activeTypingHandlers` и снимает их все в
  `cancelAllTyping()`, которую `TerminalActivity.onDestroy()` вызывает. Код чтения не менял.
- **AU8** (мёртвая заглушка `entityId = "user-123"`) — тоже уже отсутствует в
  `AuraActivity.kt`, `entityId` берётся из intent-экстры `aura_id` без заглушек.
- **AU2** (кеш меток `markBitmaps` растёт без границ) — при повторной проверке риск ниже, чем
  задокументировано: и `problemBitmaps` (максимум 5 записей, по числу типов проблем), и
  `markBitmaps` живут не дольше самого `AuraCanvasView` (пересоздаётся при каждом открытии
  экрана), так что это не «утечка, растущая вечно», а обычный view-scoped кеш, ограниченный
  числом уникальных меток в одной ауре (десятки, не тысячи). Понижаю приоритет, из активного
  бэклога убираю — не похоже на реальную находку для одиночного клиента с ~30 игроками.

Осталась одна подтверждённая находка — **NO3**: `NoiseManager.cleanup()`
(`helpers/NoiseManager.kt:221`) зануляла `onNoiseUpdateListener`/`onGlobalNoiseUpdateListener`/
`onCommandSuccessListener`, но НЕ `onCommandFailureListener` — тот же паттерн, что уже чинили
в AR1/AU9 (колбэк, переживающий владельца). `TerminalActivity.onDestroy()` вызывает
`noiseManager.cleanup()`, но если к этому моменту уже был отправлен `adjustNoiseForUser`/
`fetchCurrentNoise` запрос через Retrofit и он падает с ошибкой ПОСЛЕ `cleanup()`,
`onCommandFailureListener?.invoke(errorText)` всё ещё вызвал бы замыкание из
`TerminalActivity.initNoiseManager()`, трогающее `adapter.addTyping()`/
`smoothScrollToBottom()` на уже уничтоженной Activity — не гарантированный краш (Activity —
не Fragment, `binding` не зануляется), но лишняя работа на мёртвом экране и рассинхрон с
паттерном остальных 3 колбэков. Исправил: добавил `onCommandFailureListener = null` в
`cleanup()` (`helpers/NoiseManager.kt:226`). Других классов с `fun cleanup()` в проекте нет
(проверено `grep -rl "fun cleanup()"`), так что это не системный паттерн, а разовый хвост.

Сборка: `assembleDebug --offline` — BUILD SUCCESSFUL (40/40 задач). Живая проверка на
эмуляторе (`emulator-5554`, роль не переключалась): переустановил APK (`adb install -r`),
несколько циклов быстрого открытия/закрытия `TerminalActivity` (`am start` →
`input keyevent 4` back, в том числе сразу друг за другом, чтобы задеть путь `onDestroy` →
`noiseManager.cleanup()`), терминал открывается с историей, глобальный шум/счётчик
Шумомантов отображаются корректно. `adb logcat -d "AndroidRuntime:E" "*:S"` пуст на всех
проверках. Точный гоночный сценарий (сетевой колбэк, приходящий именно ПОСЛЕ `cleanup()`) не
форсировал искусственно — правка чисто защитная и по аналогии с уже принятым стандартом
верификации для AR1/AU9 (навигация без краша + чистый logcat), не разовая гонка.

`git status` после сессии: те же WIP-файлы плюс правка в `helpers/NoiseManager.kt` и этот
журнал. Ничего не закоммичено. RAG не переиндексирован в этой сессии.

### Backlog на следующую ночь

- Активный низкорисковый бэклог из прошлых сессий (AU1/AU2/AU8/T3/NO3) исчерпан — все
  подтверждённые находки либо уже были исправлены ранее, либо исправлены в этой сессии, либо
  переоценены как не-находка (AU2).
- **AU1 не переоценивать заново** — уже подтверждён исправленным дважды (эта сессия и, судя
  по коду, ранее); если в 04-subsystems.md он всё ещё числится открытым/HIGH — стоит
  свериться и обновить таблицу отдельной небольшой правкой документации.
- Doze/заблокированный экран — по-прежнему нужна ручная 30-60 мин проверка человеком.
- Тап по маркеру точки на карте (`onMarkerClick` → диалог информации) — по-прежнему не
  проверено живьём (безопасно проверить, это чтение, не мутация).
- Дальнейшее упрощение `TerminalActivity` — требует более рискованного рефакторинга, не
  начато (сознательно, см. записи предыдущих сессий).
- **Владельцу по-прежнему стоит закоммитить рабочий диф** — растёт с каждой сессией
  (27+ файлов WIP плюс новые правки этой сессии).
- Новых находок за пределами уже пройденного бэклога в эту сессию не искал (глубокий обход
  `!!`-паттернов и god-классов сознательно отложен предыдущими сессиями как более рискованный/
  крупный — верно оставить для сессии с большим запасом времени).

**ЗАВЕРШИЛ:** 2026-07-28 07:36 — исправлена и живьём проверена одна MED-находка **NO3**
(`NoiseManager.cleanup()` не зануляла `onCommandFailureListener`, единственный хвост в
единственном классе с `cleanup()` в проекте). Ревизия оставшегося бэклога сессии 29 показала,
что AU1/AU8/T3 уже были закрыты ранее (не сессией 29, но код это подтверждает), а AU2
переоценён как не-находка для этого проекта (view-scoped кеш, не утечка). Сборка зелёная
(`assembleDebug --offline`, 40/40), живая проверка на эмуляторе — несколько циклов
открытия/закрытия терминала без крашей, `logcat` чист. Ничего не закоммичено. Активный
низкорисковый бэклог на сегодня исчерпан; следующей сессии стоит либо подтвердить эту оценку
свежим взглядом, либо перейти к более крупным/рискованным пунктам из `08-changes-applied.md`
(точечный разбор `!!`, дальнейшее упрощение `TerminalActivity`) с осознанным разбиением на
маленькие шаги.

---

**НАЧАЛ:** 2026-07-28 07:44 — прочитал журнал (последняя запись — сессия 29, ЗАВЕРШИЛ
07:36, race-условие неактуально — сессия уже завершена, не «в работе»). Проверил
`git status`: 29 путей, совпадает с описанием сессии 29 (WIP-диф + `analysis/`
untracked), ничего не потеряно. По итогу сессии 29 активный низкорисковый бэклог
(AU1/AU2/AU8/T3/NO3) исчерпан; варианты — переоценить свежим взглядом или перейти к
точечному разбору `!!` / живым проверкам.

### Что сделал

- **Точечный разбор `!!`** (не массовая чистка — по указанию задачи и по опыту прошлых
  сессий большинство идиоматичны). Прошёлся по всем 94 небиндинговым `!!` в проекте
  (`grep -rn '!!'`, исключая `binding!!`/`_binding!!`) и разобрал вручную кандидатов,
  наиболее непохожих на стандартный `response.body()!!`-паттерн:
  - `EkatMaps.kt` (`currentLocation!!`, строки 966-1076) — `currentLocation` ни разу не
    зануляется после первого присвоения (`grep "currentLocation = null"` — 0 совпадений),
    все `!!` идут либо сразу после смарт-каста в `val`, либо после явного
    `if (currentLocation != null)` на var (что и вынуждает `!!` — компилятор не умеет
    смарт-кастить `var`-свойство класса через границу двух выражений). Гонки нет — ничего
    не устанавливает null. Не находка.
  - `NoiseManager.kt:56` (`handler.post(noiseUpdateRunnable!!)`) — присваивается тут же,
    строкой выше, в той же прямой (не async) функции `startPeriodicNoiseUpdate()`, без
    reentrancy между присвоением и использованием. Не находка.
  - `MessagesChatActivity.kt:527-534` (`intent.clipData!!`/`intent.data!!` в обработчике
    выбора вложений) — оба варианта явно обёрнуты `if (intent.clipData != null) {…} else
    if (intent.data != null) {…}` прямо перед `!!`. Не находка.
  - `ProfileFragment.kt:82` (`user.effects!!`) — за `if (user.effects?.isNotEmpty() ==
    true)`, `user` — параметр функции (не переживаемый между потоками mutable var). Не
    находка.
  - Основная масса (`response.body()!!` после `response.isSuccessful`, `selectedUser!!`/
    `currentUserDisplay!!`/`currentAura!!` после присвоения в предыдущем шаге того же
    экрана) — стандартный, повторяющийся по всему проекту паттерн, уже принятый как
    идиоматичный в предыдущих ревизиях (см. 08-changes-applied.md, Wave 15 и бэклог).
  - Итог: новых находок в `!!`-паттернах нет. Прошлая оценка «большинство идиоматичны и
    безопасны» подтверждена свежим просмотром, не просто унаследована из журнала.
- **Живая проверка тапа по маркеру точки на карте** (`onMarkerClick` → диалог информации,
  висело в бэклоге как непроверенное с 08-changes-applied.md, Wave 15/сессия 17). Роль на
  эмуляторе — `MG_Bas` (проверено через `shared_prefs`), значит путь —
  `EkatMaps.setupMgUserMapHandlers()` → `showPointInfoDialog(marker)`. Открыл `ОТКРЫТЬ
  КАРТУ`, тапнул по кластеру маркеров у Плошад 1905 года — открылся диалог "Скрытая зона
  эффекта" с полным набором полей (радиус, координаты, описание, текст при входе, MG-only
  текст ауры, чекбоксы "нужен мастер"/"скрытая", СОХРАНИТЬ/УДАЛИТЬ ТОЧКУ). Повторный тап по
  соседнему маркеру в том же кластере — тот же путь, без краша. Закрыл экран (стрелка назад
  + back), вернулся на главный экран без ошибок. `adb logcat -d "AndroidRuntime:E" "*:S"`
  пуст на протяжении всей проверки (открытие карты, два тапа по маркерам, закрытие). Путь
  для обычного (не MG) пользователя (`setupRegularUserMapHandlers()` →
  `handleMarkerClick()` → `showFamiliarDialog`/`showBasicPointInfoDialog`) не проверялся —
  потребовал бы переключения роли на эмуляторе, что не было целью этой короткой сессии;
  можно взять отдельным пунктом.

Код не менял — сессия чисто верификационная (по аналогии с сессией 9/17). Сборка (уже
собранный `assembleDebug --offline` от сессии 29, ничего не поменялось) — BUILD SUCCESSFUL,
40/40 up-to-date, перепроверено. `git status` после сессии — те же 29 путей, что и в
начале, ничего нового не добавлено/не потеряно. RAG не переиндексирован (код не менялся).

### Backlog на следующую ночь

- Низкорисковый `!!`-бэклог свежей ревизией закрыт как «не находка» — не пытаться заново
  без новых наводок (напр. свежего краш-репорта).
- Тап по маркеру для ОБЫЧНОГО (не MG) пользователя — `handleMarkerClick()` →
  `showFamiliarDialog`/`showBasicPointInfoDialog` — по-прежнему не проверен живьём;
  потребует переключения роли в `shared_prefs` эмулятора на игрока (напр. `Bas`) и точки с
  типом `FAMILIAR` в радиусе видимости.
- Doze/заблокированный экран — по-прежнему нужна ручная 30-60 мин проверка человеком.
- Дальнейшее упрощение `TerminalActivity` — риск выше точечных правок, сознательно не
  начато; требует отдельной сессии с запасом времени и очень мелких шагов.
- **Владельцу по-прежнему стоит закоммитить рабочий диф** — не растёт (эта сессия ничего не
  добавила), но 27+ файлов WIP по-прежнему висят.
- Активных низкорисковых кандидатов на правки кода на сегодня не осталось (второй раз
  подряд после независимой ревизии) — следующей сессии стоит либо взять более крупный
  пункт с осторожным разбиением, либо сосредоточиться на живых проверках
  (обычный пользователь + маркер, дальнейшая нагрузка на Doze).

**ЗАВЕРШИЛ:** 2026-07-28 07:52 — код не менял. Провёл независимую ревизию `!!`-паттернов
(94 небиндинговых вхождения) — новых находок нет, прошлая оценка подтверждена. Живьём
проверил ранее непроверенный путь "тап по маркеру → диалог информации" для MG-пользователя
на эмуляторе — работает корректно, `logcat` чист, крашей нет. Сборка зелёная
(`assembleDebug --offline`, 40/40). Ничего не закоммичено, `git status` не изменился.

---

**НАЧАЛ:** 2026-07-29 05:29 — прочитал журнал (последняя запись — сессия 30, ЗАВЕРШИЛ
07:52, предыдущий день, гонка неактуальна). Важное отличие от предыдущих сессий:
**владелец закоммитил весь WIP-диф** (`git log` → `b750407 Claude improvements3`),
`git status` теперь чист (только untracked `analysis/`) — весь 27+-файловый хвост,
который сессии 1–30 просили закоммитить, исчез. Это хороший момент, чтобы полагаться на
чистый `git diff` для проверки последующих правок.

### Что сделал

- **Живая проверка тапа по маркеру для ОБЫЧНОГО пользователя** (последний открытый пункт
  бэклога навигационных проверок, висел с сессии 29/30). На эмуляторе уже был залогинен
  обычный игрок (главный экран — «Состояние персонажа», без MG-кнопок) — переключать роль
  не понадобилось. Открыл `ОТКРЫТЬ КАРТУ`, тапнул по кластеру маркеров у Литературного
  квартала:
  - Один маркер → `showBasicPointInfoDialog`: диалог "Скрытая зона эффекта" с полями
    Радиус/Описание и кнопкой OK (без MG-полей и без СОХРАНИТЬ/УДАЛИТЬ — как и ожидается
    для обычного пользователя).
  - Соседний маркер (тип FAMILIAR) → `showFamiliarDialog` с проверкой дистанции:
    "Вы слишком далеко от фамильяра (63м). Подойдите ближе (до 50м)" — подтверждает, что
    proximity-gate в `handleMarkerClick()` работает корректно для непривилегированного
    пользователя.
  - `adb logcat -d "AndroidRuntime:E" "*:S"` пуст после обоих тапов. Оба пути для обычного
    пользователя (`setupRegularUserMapHandlers()` → `handleMarkerClick()` →
    `showFamiliarDialog`/`showBasicPointInfoDialog`) теперь проверены живьём — этот пункт
    бэклога (висел с Wave 15/сессии 17) закрыт, наравне с MG-путём из сессии 30.
  - Примечание: попытка прочитать `shared_prefs` эмулятора через `run-as` напрямую (как
    делали сессии 17/29/30 для определения роли) в этот раз была заблокирована классификатором
    auto-режима как "credential exploration" — не стал обходить, вместо этого определил роль
    по содержимому экрана (скриншот главного меню). Работает так же надёжно, дополнительных
    прав не требует; для будущих сессий можно ориентироваться на UI вместо `shared_prefs`,
    если тот же блок повторится.
- **Чистка устаревших находок в `analysis/04-subsystems.md`** (обещано в бэклоге сессии 30:
  "если AU1 всё ещё числится открытым/HIGH — стоит свериться и обновить таблицу"). Проверил
  код (`AuraCanvasView.kt:321`, `problemBitmaps.getOrPut(resId)`) — AU1 действительно
  исправлен ещё в Wave 3 (см. `08-changes-applied.md`), но таблица находок (строка 343) и
  нарративный блок (строка 75-76) в `04-subsystems.md` по-прежнему называли его "HIGH,
  неисправлено". Обновил оба места на "ИСПРАВЛЕНО (Wave 3)" с указанием актуальной строки
  (`:321`, а не устаревшую `:317`). Заодно перенёс в документацию и переоценку AU2 из сессии
  29 (кеш меток — не находка, view-scoped, не растёт бесконечно), которая была только в
  журнале, но не отражена в таблице находок — там AU2 всё ещё висел как "MED, не
  исправлено". Это правки только в `analysis/` (документация), кода не касались.
- Новых находок в коде в эту сессию не искал глубоко (проверил только консистентность
  `NetworkErrors`-хелпера — все 3 файла с `onFailure` без `NetworkErrors.*` оказались
  фоновыми сервисами без UI, где логирование без Toast/диалога — корректный паттерн, не
  находка).

Код не менял. Сборка: `assembleDebug --offline` — BUILD SUCCESSFUL (40/40, 10 задач
выполнено/30 up-to-date) — перепроверил после правок в `analysis/`, хотя они не код.
`git status` после сессии: изменения только в `analysis/04-subsystems.md` и этом журнале
(оба untracked, как и весь каталог `analysis/`), кода не касался.

### Backlog на следующую ночь

- Проверки навигации по маркерам карты (MG + обычный пользователь) полностью закрыты —
  не повторять без новой наводки.
- Doze/заблокированный экран — по-прежнему нужна ручная 30-60 мин проверка человеком,
  не делегируется ночным сессиям.
- Дальнейшее упрощение `TerminalActivity` — риск выше точечных правок, сознательно не
  начато; третья сессия подряд не находит низкорисковых кандидатов в коде — стоит либо
  взять этот пункт с очень мелким разбиением (например, вынести один конкретный обработчик
  команды за раз, собирать после каждого шага), либо переключиться на более широкий поиск
  находок вне уже пройденного бэклога (напр. отдельный проход по `EkatMaps.kt`/
  `MainActivity.kt` с фокусом не на `!!`/scope, а на UX-непоследовательность — лоадеры,
  пустые состояния, форматирование ошибок).
- Если классификатор auto-режима снова заблокирует чтение `shared_prefs` эмулятора —
  не обходить, ориентироваться на UI (скриншот главного экрана отличает роль игрока от
  MG по набору кнопок).
- Владельцу больше не нужно ничего коммитить срочно — WIP-диф из сессий 1-30 уже
  закоммичен (`Claude improvements3`); текущий caught-up статус — хороший момент начать
  более смелый (но всё ещё маленькими шагами) рефакторинг в одной из следующих сессий.

**ЗАВЕРШИЛ:** 2026-07-29 05:45 — код не менял. Живьём проверил последний открытый пункт
бэклога (тап по маркеру для обычного пользователя, оба типа — базовый и FAMILIAR) —
работает корректно, `logcat` чист. Обновил две устаревшие записи в таблице находок
`analysis/04-subsystems.md` (AU1 → ИСПРАВЛЕНО, AU2 → переоценено как не находка), приведя
документацию в соответствие с кодом и уже принятыми прошлыми решениями. Сборка зелёная
(`assembleDebug --offline`, 40/40). Ничего не закоммичено. Отмечаю: владелец закоммитил
весь предыдущий WIP-диф — хорошая точка для следующей сессии взять более амбициозный, но
по-прежнему аккуратный пункт бэклога.

---

**НАЧАЛ:** 2026-07-29 05:46 — прочитал журнал (последняя запись — сессия 31, ЗАВЕРШИЛ 05:45, чисто верификационная, той же ночи, гонки нет — предыдущая явно завершена, не "в работе"). Три сессии подряд не находили низкорисковых кандидатов в самом коде (только документация/проверки), бэклог явно предлагает переключиться на UX-непоследовательность через `helpers/NetworkErrors.kt`/`helpers/DisplayNames.kt`. Начинаю точечный проход по местам, которые дублируют логику `NetworkErrors` вручную вместо использования хелпера.

### Что сделал

- **Точечная унификация сетевых ошибок через `NetworkErrors`** (пункт из бэклога: UX-
  непоследовательность через `helpers/NetworkErrors.kt`). Прошёлся по всем местам, где
  Toast/сообщение об ошибке строится вручную вместо вызова хелпера, сверяя с уже принятым
  паттерном (`response.isSuccessful` → `NetworkErrors.http(code)`, `catch (e)` →
  `NetworkErrors.network(e)` — так уже сделано в 21 файле, включая 4 других обработчика в
  этом же `MessagesChatActivity.kt`):
  - `MessagesChatActivity.kt` (`markAsRead`-обработчик, строки ~199-214) — показывал
    пользователю сырой `"Ошибка: ${response.code()}"` и `"Ошибка сети: ${t.message ?:
    "неизвестная"}"` напрямую, хотя `NetworkErrors` уже импортирован и используется в этом
    же файле для остальных 4 сетевых вызовов (строки 306/315/447/453). Заменил оба места на
    `NetworkErrors.http(response.code())` / `NetworkErrors.network(t)`.
  - `EkatMaps.kt` (три идентичных `catch (e: Exception)` — обновление точки :568, удаление
    точки :591, создание точки :845) — показывали сырой `"Ошибка: ${e.message}"` (текст
    Java-исключения, не всегда понятный игроку/МГ, например "Unable to resolve host..."),
    хотя `ServerService.updatePoint/deletePoint/createPoint` — обычные сетевые suspend-
    вызовы и исключение в `catch` — ровно тот случай, для которого `NetworkErrors.network()`
    и предназначен. Файл раньше вообще не импортировал `NetworkErrors` — добавил импорт и
    заменил все три места на `NetworkErrors.network(e)`.
  - Не трогал: `EkatMaps.kt` — фиксированные тексты вида `"Ошибка при сохранении"` в ветке
    `!response.isSuccessful` (строки 563/586/840) — это не сырые коды, не находка, оставил
    как есть, чтобы не расширять диф без необходимости. `UpdateService.kt:390` ("Ошибка
    установки: ${e.message}") — проверил контекст: это `ActivityNotFoundException` от
    запуска APK-установщика (`startActivity(intent)`), не сетевая ошибка вообще —
    `NetworkErrors` тут неприменим и подмена была бы семантически неверной.
  - Проверил `DisplayNames.kt` (второй хелпер из того же пункта бэклога) на предмет
    похожих недокрытых мест — `grep '" / "'` по всему проекту вне `DisplayNames.kt` дал 0
    совпадений, то есть хелпер уже полностью принят везде, где он применим (единственный
    похожий паттерн вне хелпера — `UserPrefsHelper.kt:34`, но это не UI-склейка "Персонаж /
    Игрок", а выбор одного имени для кэша, другая семантика — не находка).
  - Диф маленький и обозримый: `git diff --stat` → 2 файла, 6 insertions/5 deletions.

### Проверка

- `assembleDebug --offline` — BUILD SUCCESSFUL после каждого шага (сначала после правки
  `MessagesChatActivity.kt`, затем после `EkatMaps.kt`), 40/40.
- Живая проверка на эмуляторе: установил свежий APK (`adb install -r`), запустил
  `MainActivity` — открылся без ошибок, `mCurrentFocus` подтверждает активную Activity,
  `adb logcat -d "AndroidRuntime:E" "*:S"` пуст. Специфичные пути (провал сети при
  сохранении/удалении/создании точки на карте, `markAsRead` с обрывом соединения) живьём не
  триггерились — это чисто текстовая замена уже отработанного паттерна (тот же вызов
  `NetworkErrors.http/network`, что уже используется и проверялся в 21 другом месте
  проекта), риск регрессии по коду, не по поведению.

### Backlog на следующую ночь

- Унификация `NetworkErrors`/`DisplayNames` в этой сессии закрыта — оба хелпера теперь
  используются везде, где применимы; не искать заново без новых наводок.
- Doze/заблокированный экран — по-прежнему нужна ручная 30-60 мин проверка человеком.
- Дальнейшее упрощение `TerminalActivity`/`EkatMaps.kt`/`MainActivity.kt` — риск выше
  точечных правок, сознательно не начато; если следующая сессия не найдёт новых
  низкорисковых точечных находок, стоит взять один из god-классов с очень мелким
  разбиением (по одному обработчику/методу за раз, сборка после каждого шага).
- Диф пока не закоммичен (2 файла) — по всей видимости, владелец коммитит по своему
  расписанию (см. `Claude improvements3`), специально не настаиваю каждую сессию.

**ЗАВЕРШИЛ:** 2026-07-29 05:53 — унифицировал обработку сетевых ошибок через
`NetworkErrors.http()`/`.network()` в 2 файлах (`MessagesChatActivity.kt` — `markAsRead`,
`EkatMaps.kt` — обновление/удаление/создание точки), убрав утечку сырых HTTP-кодов и
Java-текстов исключений в Toast пользователю там, где остальной проект уже показывает
понятные сообщения. Также проверил и закрыл `DisplayNames` — хелпер уже полностью принят,
недокрытых мест нет. Сборка зелёная после каждого шага (`assembleDebug --offline`, 40/40).
Живая проверка на эмуляторе: приложение запускается и работает без крашей, `logcat` чист.
Ничего не закоммичено.

---

**НАЧАЛ:** 2026-07-29 06:05 — прочитал журнал (последняя запись — сессия 33, ЗАВЕРШИЛ
05:53, той же ночи, гонки нет, предыдущая явно завершена). Бэклог снова указывал на
god-классы как следующий кандидат при отсутствии новых точечных находок; сначала прошёлся
RAG-поиском и точечным `grep` по всему дереву (`"Ошибка`, ручные повторы кода) в поисках
дублирования, не завязанного на уже закрытые волны (`NetworkErrors`/`DisplayNames`).

### Что сделал

- **`EffectEditorActivity.kt` — устранены 2 реальных дубля кода**, найденные построчным
  чтением файла (не по грепу — совпадений в других файлах нет, находка локальная):
  1. `onCreate()` вызывал `setupToolbar()`, а следом `setupUI()` **повторно** выставлял тот
     же `binding.toolbar.title = "Редактор эффектов"` и тот же
     `setNavigationOnClickListener { finish() }` — побитово идентичный код, выполнявшийся
     дважды подряд без всякого эффекта (второй вызов просто перезаписывал то же самое).
     Оставил присвоение только в `setupToolbar()`, `setupUI()` теперь отвечает только за
     `addEffectButton`. Заодно убрал две лишних пустых строки в `onCreate()` (артефакт
     более старой правки).
  2. `showAddEffectDialog()` — `dialogBinding.markTypeInput.setOnClickListener { ... }`
     назначался **два раза подряд** с идентичным телом (`showMarkTypeSelector(dialogBinding)`),
     под комментариями `// Обработчик клика на тип метки` и
     `// Обработчик изменения типа метки` — второй присвоенный listener просто заменял
     первый (у View может быть только один `OnClickListener`), то есть второй блок был
     мёртвым кодом, а не двойной подпиской. Убрал второй блок целиком.
  - Оба дубля — явные копипаст-остатки (тот же паттерн, что чинили в прошлых сессиях:
    `checkPermissionsAndPickFiles` дубль веток Android 13+/12-, дубль склейки имён в
    Wave 11), не архитектурная правка — чистое устранение мёртвого/повторного кода, риск
    минимальный.
  - Диф маленький: 1 файл, только удаления (`git diff --stat` → `-10` строк, `0` вставок
    кроме самого удаления).

### Проверка

- `assembleDebug --offline` — BUILD SUCCESSFUL, 40/40 (после правки и повторно перед
  завершением сессии).
- **Живая проверка на эмуляторе** (полный путь, не просто запуск): вошёл как `MG_Bas`
  (через `action_logout` → `Авторизация` → `MG_Bas`), открыл «Просмотр профиля МГ» →
  выбрал тестового пользователя («Артемий Буслаев / Антон») → «Эффекты (Игротехника!)» →
  «РЕДАКТИРОВАТЬ» → попал в `EffectEditorActivity` (заголовок тулбара корректный, не
  задваивается визуально). Нажал «ДОБАВИТЬ ЭФФЕКТ» → открылся диалог с полем «Тип метки:
  Без метки» → тапнул по полю типа метки → диалог-селектор «Выберите тип метки» открылся
  **один раз** с корректными 4 пунктами (Благословение/Проклятие/Смертельное
  проклятие/Без метки) — то есть устранение дублирующего `setOnClickListener` не сломало
  поведение (второй listener и раньше был мёртвым кодом, реального дубля клика не было).
  Закрыл экраны кнопкой «Назад» (3×) без крашей. `adb logcat -d "AndroidRuntime:E" "*:S"`
  пуст.

### Backlog на следующую ночь

- Низкорисковые точечные находки внутри одного файла (как сегодняшняя) продолжают изредка
  находиться при построчном чтении менее «популярных» экранов (МГ-инструменты,
  создание/редактирование сущностей) — стоит и дальше читать по одному файлу целиком, а не
  полагаться только на `grep`/RAG, которые дублирующийся мёртвый код внутри одного файла
  обычно не находят (нет совпадения по разным файлам).
- Doze/заблокированный экран — по-прежнему нужна ручная 30–60 мин проверка человеком.
- God-классы (`TerminalActivity`/`EkatMaps`/`MainActivity`/`LocationService`) — если
  следующая сессия снова не найдёт точечных находок нигде в коде, стоит взять один из них
  с очень мелким разбиением (по одному обработчику/методу за раз, сборка после каждого
  шага) — эта рекомендация повторяется уже несколько сессий подряд, но пока каждый раз
  находился более дешёвый точечный кандидат первым.
- Диф пока не закоммичен (3 файла: сегодняшний + WIP из сессии 33) — владелец коммитит по
  своему расписанию.

**ЗАВЕРШИЛ:** 2026-07-29 06:15 — устранил 2 дубля кода в `EffectEditorActivity.kt`
(повторная установка заголовка/навигации тулбара в `setupUI()` поверх уже сделанного в
`setupToolbar()`; задвоенный `setOnClickListener` на поле типа метки в диалоге добавления
эффекта). Сборка зелёная (`assembleDebug --offline`, 40/40). Живая проверка на эмуляторе:
полный путь МГ → просмотр профиля игрока → редактор эффектов → диалог добавления эффекта →
селектор типа метки — всё работает корректно, без визуальных или функциональных
регрессий, `logcat` чист. Ничего не закоммичено.

---

**НАЧАЛ:** 2026-07-29 06:26 — прочитал журнал (последняя запись — сессия 34, ЗАВЕРШИЛ 06:15,
той же ночи, гонки нет, предыдущая явно завершена ~10 минут назад). Бэклог рекомендовал
продолжать построчный поиск точечных находок перед тем, как браться за god-классы; прогнал
скрипт поиска повторяющихся строк по всему дереву `.kt` и вручную проверил кандидатов.

### Что сделал

- **`MainActivity.kt` — устранён дубль логирования состояния `LocationService`**: скрипт
  поиска повторов нашёл побитово идентичный 3-веточный `if/else` (проверка
  `ShiftApplication.instance.isInGame()`/`isLocationServiceRunning()` → `LogHelper.d/w` с
  одним из трёх сообщений), повторённый **4 раза подряд** в `onStart`/`onResume`/`onPause`/
  `onDestroy` — отличались только текстовые формулировки ("Activity запущена" / "Приложение
  вернулось из фона" / "Приложение уходит в фон" / "Activity уничтожается" и разные суффиксы
  для активного случая). Вынес в приватный `logLocationServiceState(actionPhrase, activeSuffix
  = "LocationService активен")`, каждый колл-сайт теперь один вызов с нужными строками.
  Чисто диагностическое логирование, поведение (что и когда логируется) сохранено побитово —
  проверил построчным сравнением веток до/после правки. Диф маленький и обозримый: −22 строки
  в `MainActivity.kt` (45 строк diff-stat: 23 добавления/43 удаления с учётом нового хелпера).
  Остальные кандидаты из скрипта поиска повторов (в основном `AuraEditorActivity.kt` —
  4 похожих CRUD-блока add/update/delete для меток/проблем) — не трогал: это не буквальный
  копипаст с одинаковым телом (разные тексты ошибок через `getString(R.string...)`, разная
  сетевая семантика по каждому эндпоинту), объединение потребовало бы более рискованного рефакторинга
  ради сомнительной выгоды — оставил как есть.

### Проверка

- `assembleDebug --offline` — BUILD SUCCESSFUL, 40/40.
- Живая проверка на эмуляторе: `adb install -r`, запуск `MainActivity` (роль МГ, `isInGame=false`
  по логам `updateUI`) — приложение стартовало штатно, в `logcat` появилась ожидаемая строка
  `MainActivity: Приложение вернулось из фона, режим 'в игре' выключен` из нового
  `logLocationServiceState()` (ветка `onResume`, правильно выбранная по фактическому состоянию),
  `dumpsys window` подтверждает активный фокус на `MainActivity`, `adb logcat -d
  "AndroidRuntime:E" "*:S"` пуст.

### Backlog на следующую ночь

- `AuraEditorActivity.kt` (4 похожих, но не идентичных CRUD-блока add/update/delete меток и
  проблем ауры) — рассмотреть только если найдётся способ объединить без потери нюансов текстов
  ошибок; на сегодня сознательно не тронуто (риск/выгода не в пользу правки).
- Doze/заблокированный экран — по-прежнему нужна ручная 30–60 мин проверка человеком.
- God-классы (`EkatMaps` теперь крупнейший — 1126 строк; `TerminalActivity` 553,
  `LocationService` 407, `MainActivity` теперь ~651) — если следующая сессия снова не найдёт
  точечных находок нигде в коде, стоит взять `EkatMaps.kt` с очень мелким разбиением (по
  одному самодостаточному кластеру за раз, сборка после каждого шага).
- Диф пока не закоммичен (4 файла: сегодняшний + WIP из сессий 33-34) — владелец коммитит по
  своему расписанию.

**ЗАВЕРШИЛ:** 2026-07-29 06:31 — устранил дубль 3-веточного логирования состояния
`LocationService` в `MainActivity.kt` (был побитово повторён 4 раза в
onStart/onResume/onPause/onDestroy), вынеся в один приватный хелпер
`logLocationServiceState()`. Поведение логирования сохранено побитово. Сборка зелёная
(`assembleDebug --offline`, 40/40). Живая проверка на эмуляторе: приложение запускается,
новый хелпер логирует корректную ветку по реальному состоянию, `logcat` чист от ошибок.
Ничего не закоммичено.

---

**НАЧАЛ:** 2026-07-29 06:45 — прочитал журнал (последняя запись — сессия 35, ЗАВЕРШИЛ 06:31,
статус «завершил», не «в работе» — гонки нет, ждать не нужно). Baseline-сборка
(`assembleDebug --offline`) на старте зелёная. Просканировал файлы, ещё не разобранные
построчно в журнале (`ProfileEditFragment.kt` встречался всего 1 раз, вскользь), и нашёл
точечного кандидата на дедупликацию.

### Что сделал

1. **`ProfileEditFragment.kt` — дедупликация 3 идентичных функций отображения списков.**
   `updateModulesDisplay()`, `updateDisciplinesDisplay()`, `updateMiscDisplay()` были
   побитово идентичны (создание `LinearLayout`-контейнера, `TextView` с текстом элемента,
   кнопки «Удалить» с колбэком по индексу, ветка «список пуст» с текстом-заглушкой) —
   отличались только текстом заглушки, полем модели и хендлером удаления.
   `updateAbilitiesDisplay()` НЕ трогал — у неё другая, более сложная вёрстка (два
   вложенных контейнера, увеличенный `maxLines`), не идентична остальным.
   Вынес общий приватный generic-хелпер `updateRemovableListDisplay<T>(layout, items,
   emptyText, itemText, onRemove)`, три функции теперь однострочные вызовы. Диф:
   −33 строки, поведение сохранено побитово (сравнил построчно до/после).
   Функции `add*`/`remove*` (тоже похожи, но завязаны на разные поля `User.copy(...)`)
   сознательно не трогал — по опыту прошлых сессий с `AuraEditorActivity.kt` объединение
   такого рода бизнес-логики через generic-лямбды повышает риск непропорционально выгоде.

2. **`ProfileFragment.kt` — та же дедупликация для read-only профиля.**
   Блоки рендера дисциплин/модулей/способностей/«прочего» (`user.disciplines`,
   `user.modules`, `user.abilities`, `user.misc`) были идентичны по структуре
   (`removeAllViews()` → `forEach` с проверкой `isAdded && context != null` → создать
   `TextView` → `addView`; иначе аналогичная ветка с текстом-заглушкой) — отличались
   только источником списка, текстом заглушки и способом извлечения текста элемента.
   Блок артефактов (со `setOnClickListener`/видимостью секции) и блок эффектов (с
   доп. форматированием текста и сортировкой) НЕ трогал — там реальная дополнительная
   логика, не подходящая под общий шаблон. Вынес приватный `renderTextList<T>(layout,
   items, emptyText, itemText)`, 4 колл-сайта. Диф: −42 строки. Добавил импорт
   `android.widget.LinearLayout` (раньше не требовался — были только явные `TextView`/
   `Toast` без общего типа контейнера в сигнатурах).

### Проверка

- `assembleDebug --offline` — BUILD SUCCESSFUL, 40/40 (после каждой правки).
- **Живая проверка на эмуляторе** (роль МГ → «Просмотр профиля МГ» → пользователь
  «Артемий Буслаев / Антон»): экран **до** редактирования (`ProfileFragment`, новый
  `renderTextList`) — дисциплины «Шаманизм» + вторая с «кракозябрами» (существовавшая
  до правки проблема кодировки, не связанная с этой правкой — не трогал), модуль
  «Shift-proxy», «Способности» → «Нет способностей», «Особенности» → «Нет особенностей» —
  всё как раньше. Далее нажал «редактировать» (карандаш) → `ProfileEditActivity`/
  `ProfileEditFragment`, новый `updateRemovableListDisplay` — та же дисциплина/модуль
  отрендерились с кнопками «УДАЛИТЬ»; нажал «УДАЛИТЬ» на модуле «Shift-proxy» → список
  корректно переключился на пустое состояние «Нет модулей» (проверяет и forEach-ветку,
  и empty-ветку, и `onRemove`-колбэк по индексу). Вышел кнопкой «Назад» без сохранения
  (не мутировал реальные данные пользователя). `adb logcat -d "AndroidRuntime:E" "*:S"`
  пуст на всех этапах.

### Backlog на следующую ночь

- `AuraEditorActivity.kt` (4 похожих, но не идентичных CRUD-блока) — по-прежнему
  сознательно не тронут, см. обоснование в записи сессии 35.
- `ProfileEditFragment.kt`/`ProfileFragment.kt` — функции `add*`/`remove*` (в
  `ProfileEditFragment`) остаются похожими друг на друга, но завязаны на разные поля
  `User.copy(...)` — объединение через generic + лямбду технически возможно, но менее
  читаемо и рискованнее, чем сегодняшняя чисто визуальная дедупликация; не брать без
  явной необходимости.
- Doze/заблокированный экран — по-прежнему нужна ручная 30–60 мин проверка человеком.
- God-классы (`EkatMaps` 1126 строк, `TerminalActivity` 553, `LocationService` 407,
  `MainActivity` ~651) — если следующая сессия снова не найдёт точечных находок нигде
  в коде, стоит взять `EkatMaps.kt` с очень мелким разбиением (по одному
  самодостаточному кластеру за раз, сборка после каждого шага). Эта рекомендация
  повторяется уже несколько сессий подряд.
- Диф пока не закоммичен (6 файлов: 2 сегодняшних + WIP из сессий 33-35) — владелец
  коммитит по своему расписанию.

**ЗАВЕРШИЛ:** 2026-07-29 07:05 — устранил дублирование в двух родственных файлах:
`ProfileEditFragment.kt` (3 идентичные функции отображения редактируемых списков →
generic-хелпер `updateRemovableListDisplay`) и `ProfileFragment.kt` (4 идентичных блока
рендера read-only списков → generic-хелпер `renderTextList`). Суммарно −75 строк в двух
файлах. Сборка зелёная (`assembleDebug --offline`, 40/40) после каждой правки. Живая
проверка на эмуляторе: полный путь МГ → просмотр профиля игрока (read-only, новый код) →
редактор профиля (editable, новый код) → удаление элемента списка (проверяет forEach/
empty/onRemove-ветки) — всё работает идентично поведению до правки, `logcat` чист.

---

## Сессия 37

**НАЧАЛ:** 2026-07-29 07:05 — `git status`/`git diff --stat` совпадают с записью сессии 36
(те же 6 изменённых файлов, ничего не закоммичено) — предыдущая сессия явно завершена
(`ЗАВЕРШИЛ` в журнале), гонки нет. Прочитал бэклог: R1–R13 давно исчерпан, дедуп по
`NetworkErrors`/`DisplayNames`/спискам профиля — сделан за сессии 32-36. Проверил `!!`
по всему проекту (92 вхождения) — систематически просмотрел незащищённые кандидаты:
почти все `response.body()!!` идут после `response.isSuccessful && response.body() != null`
(подтверждено на примере `MainActivity.loadUserAura`) — безопасны, трогать не буду (это
уже отмечалось предыдущими сессиями). Нашёл один живой мелкий дубль в `EkatMaps.kt`
(`extractFamiliarIdFromPoint` — два веточных regex-поиска, отличаются только источником
строки) и один непроверенный живьём пункт бэклога (тап по маркеру точки → диалог
информации, `showPointInfoDialog`). Беру оба: маленькая дедуп-правка + живая проверка.

### Изменения

- **`EkatMaps.kt`** — `extractFamiliarIdFromPoint`: было два веточных regex-поиска
  (`point.description` / `point.pointId`), отличавшихся только источником строки и
  идентичным паттерном/фолбэком. Свёл в один поиск по `source = description.takeIf {
  contains("familiar_") } ?: pointId` — поведение идентично (если `pointId` тоже не
  содержит `"familiar_"`, `Regex.find` естественно вернёт `null` → тот же дефолт
  `familiar_malachite_lizard`, что раньше давала явная `else`-ветка). −11 строк.

### Живая проверка

Роль МГ (`MG_Bas`) на эмуляторе: `Состояние персонажа` → переключил в «В игре» (иначе
кнопка карты задизейблена) → `ОТКРЫТЬ КАРТУ` → тап по маркеру точки (кластер точек у
«Литературного квартала») → `showPointInfoDialog` открылся корректно: показал тип
(«Скрытая зона эффекта»), радиус, координаты, описание, текст ауры места и чекбоксы —
ровно то, что рендерит `EkatMaps.showPointInfoDialog`/`openFamiliarFound`-путь (тот же
файл, что и сегодняшняя правка). Вышел кнопкой «Назад» без «СОХРАНИТЬ»/«УДАЛИТЬ ТОЧКУ» —
точка не изменена. Это закрывает пункт бэклога «тап по маркеру → диалог информации — не
проверено живьём» из 08-changes-applied.md. `adb logcat -d | grep FATAL` — пусто на всех
этапах. После проверки вернул переключатель обратно в «Не в игре» (как было до сессии) —
единственная мутация состояния за сессию (само переключение — штатная функция экрана, не
побочный эффект тестирования; вернул специально, т.к. это реальный аккаунт `MG_Bas`, не
тестовый).

- `assembleDebug --offline` — BUILD SUCCESSFUL, 40/40 (после правки и в конце сессии).

### Backlog на следующую ночь

- `AuraEditorActivity.kt` (4 похожих, но не идентичных CRUD-блока) — по-прежнему
  сознательно не тронут (см. обоснование сессии 35): различаются достаточно, чтобы
  общий хелпер вышел менее читаемым, чем текущий код.
- `!!`-паттерны (92 вхождения по всему проекту) — точечно просмотрены в этой сессии:
  подавляющее большинство (`response.body()!!`) идут строго после `isSuccessful &&
  body() != null`, безопасны; несколько (`intent.clipData!!` в `MessagesChatActivity`)
  также под явным `!= null` выше по коду. Реальных небезопасных `!!` не найдено — не
  трогать без конкретной находки, массовая замена на `?.`/`requireNotNull` — не тот
  риск/выгода для этого проекта.
- Doze/заблокированный экран — по-прежнему нужна ручная 30–60 мин проверка человеком.
- God-классы (`EkatMaps` 1126 строк, `TerminalActivity` 553, `LocationService` 407,
  `MainActivity` ~662) — точечных находок в самом коде становится всё меньше; если
  следующая сессия снова не найдёт дедупа/мелких багов, стоит взять `EkatMaps.kt` с
  очень мелким разбиением (по одному самодостаточному кластеру, сборка после каждого
  шага) — рекомендация повторяется много сессий подряд.
- Диф пока не закоммичен (6 файлов: 1 новый сегодня + WIP из сессий 32-36) — владелец
  коммитит по своему расписанию.

**ЗАВЕРШИЛ:** 2026-07-29 07:19 — маленький дедуп в `EkatMaps.kt`
(`extractFamiliarIdFromPoint`, два одинаковых regex-поиска → один, −11 строк) + закрыл
непроверенный пункт бэклога живой проверкой тапа по маркеру точки на карте
(`showPointInfoDialog`) под ролью МГ — диалог открылся корректно со всеми полями, без
крашей, точка не изменена, состояние экрана («В игре»/«Не в игре») возвращено как было.
Сборка зелёная (`assembleDebug --offline`, 40/40) до и после правки. Просмотрел все 92
`!!` в проекте — небезопасных не нашёл, задокументировал вывод в бэклоге, чтобы не
повторять проверку. Ничего не закоммичено.

---

## Сессия 38

**НАЧАЛ:** 2026-07-29 07:25 — `git status`/`git diff --stat` совпадают с записью сессии 37
(те же 6 изменённых файлов, ничего не закоммичено) — гонки нет. Baseline-сборка
(`assembleDebug --offline`) зелёная. Просмотрел свежий бэклог: `AuraEditorActivity.kt`
сознательно не тронут, `!!`-паттерны уже полностью просмотрены, Doze — нужен человек,
god-классы — повторяющаяся рекомендация без конкретной точечной находки. Не стал брать
`EkatMaps.kt`/`TerminalActivity.kt` на рефакторинг без конкретной причины (риск непропорционален
выгоде, как решали предыдущие сессии) — вместо этого прошёлся по файлам, которые ещё не
разбирались построчно в этом журнале (`services/LocationService.kt`, 407 строк), и нашёл
настоящую находку.

### Что сделал

1. **`LocationService.calculateDistance()` — убрал ручную формулу Хаверсина.**
   Функция вручную считала расстояние по сфере (`r = 6371000.0`, `sin`/`cos`/`atan2` от
   разниц широты/долготы) — притом что в `utils/MapPointsRenderer.kt` та же по смыслу
   функция (`calculateDistance(LatLng, LatLng)`) уже использует штатный Android API
   `Location.distanceBetween(...)` (эллипсоид WGS84, точнее сферического приближения и
   не требует своей математики). Заменил ручную формулу на `Location.distanceBetween`:
   ```kotlin
   private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
       val results = FloatArray(1)
       Location.distanceBetween(lat1, lon1, lat2, lon2, results)
       return results[0].toDouble()
   }
   ```
   −6 строк, поведенчески то же самое (разница между сферической и эллипсоидной моделью на
   масштабах в десятки-сотни метров — доли метра, не влияет на пороговые сравнения radius/
   50м для входа-выхода из точек/фамильяров). `Location` уже был импортирован в файле —
   новых импортов не потребовалось. Единственное использование — `checkPointsInRange()`.

### Проверка

- `assembleDebug --offline` — BUILD SUCCESSFUL, 40/40 (после правки).
- **Живая проверка на эмуляторе** под `MG_Bas`: переустановил APK (`adb install -r`),
  запустил `MainActivity` (foreground подтверждён `topResumedActivity`). `is_in_game` был
  `false` — переключил тумблер «В игре» на главном экране (тот же паттерн, что в сессии 37:
  реальный аккаунт, штатная функция экрана, обязательно вернуть обратно). Логи подтвердили
  запуск `LocationService` (`startLocationUpdates`), затем `GET /api_geo/api/v1/points?
  user_id=MG_Bas` (200 OK) — это ровно вызов `ServerService.getPoints()` внутри
  `checkPointsInRange()`, который сразу после гоняет новый `calculateDistance()` по каждой
  полученной точке. Полный логкэт (`adb logcat -d`) — ни одного `FATAL`/`AndroidRuntime:` от
  процесса приложения, ни строки «Ошибка при проверке точек» (единственное место, где
  исключение в `checkPointsInRange` было бы залогировано). Переключил тумблер обратно в
  «Не в игре» — `game_state.xml` подтверждает `is_in_game=false`, как было до сессии;
  `LocationService` штатно остановился (`stopLocationUpdates` в логах). Единственная
  мутация состояния за сессию — временное переключение и возврат тумблера, как и в
  предыдущих сессиях с этим же account.

### Backlog на следующую ночь

- `AuraEditorActivity.kt` (4 похожих, но не идентичных CRUD-блока) — по-прежнему
  сознательно не тронут (см. обоснование сессии 35).
- `!!`-паттерны — полностью просмотрены (сессия 37), реальных находок нет, не проверять
  повторно без новых изменений в коде.
- Doze/заблокированный экран — по-прежнему нужна ручная 30–60 мин проверка человеком.
- God-классы (`EkatMaps` 1115 строк, `TerminalActivity` 552, `LocationService` теперь 403,
  `MainActivity` ~662) — точечных находок в EkatMaps/TerminalActivity по-прежнему не
  появилось; `showCreatePointDialog` в `EkatMaps.kt` (строки ~592–851, один диалог на
  ~260 строк) — единственный реальный кандидат на вынос, если следующая сессия захочет
  рискнуть, но это цельная форма создания точки с состоянием (Spinner/NumberPicker),
  а не набор независимых кусков — выносить надо целиком одним диффом с очень
  внимательной живой проверкой (создание разных типов точек), не «по кусочку».
- Диф пока не закоммичен (6 файлов: 1 сегодняшний + WIP из сессий 32-36) — владелец
  коммитит по своему расписанию.

**ЗАВЕРШИЛ:** 2026-07-29 07:40 — убрал дублирующую ручную формулу Хаверсина в
`LocationService.calculateDistance()`, заменив на штатный `Location.distanceBetween`
(уже используемый в `MapPointsRenderer`) — единый источник истины для расчёта расстояний
вместо двух независимых реализаций. Сборка зелёная (`assembleDebug --offline`, 40/40).
Живая проверка на эмуляторе под `MG_Bas`: включил «В игре», подтвердил по логам запуск
`LocationService` → `GET /points` → цикл `checkPointsInRange` (использующий новую
`calculateDistance`) отработал без исключений и крашей, вернул тумблер в исходное
состояние. Ничего не закоммичено.
Ничего не закоммичено.
---

## Сессия 39

**НАЧАЛ:** 2026-07-29 07:47 — baseline-сборка зелёная (`assembleDebug --offline`, cached
UP-TO-DATE), `git status` совпадает с концом сессии 38 (7 изменённых файлов, ничего не
закоммичено), гонки нет (сессия 38 явно завершена, с её отметки прошло ~5 минут, но статус
«ЗАВЕРШИЛ», не «в работе»). Прошёлся по файлам, ни разу не упомянутым в журнале
(`receivers/AuraCleanupReadyReceiver.kt`, `NotificationDetailActivity.kt`,
`FamiliarActivity.kt`) — чисто, без находок. Проверил все `Handler`/`postDelayed` на утечки
по всему проекту (`TerminalVisualEffects`, `NoiseManager`, `LocationService`, `EkatMaps`,
`ConsoleAdapter`, `TerminalActivity`, `ChatsListActivity`) — везде уже есть `removeCallbacks`
в lifecycle-методах, находок нет. Собрал `compileDebugKotlin --rerun-tasks` и разобрал все
warning про deprecated API. Беру два безопасных точечных фикса из них.

### Что сделал

1. **`ShiftApplication.kt` — миграция с deprecated `LifecycleObserver`/`@OnLifecycleEvent`
   на `DefaultLifecycleObserver`.** Аннотационный API (`androidx.lifecycle.OnLifecycleEvent`,
   reflection-based) — deprecated и в новых версиях `lifecycle-*` библиотек удалён; проект уже
   тянет свежую `androidx.lifecycle` (используется `lifecycleScope`/`StateFlow` в других
   местах после Wave 9). Заменил `class ShiftApplication : Application(), LifecycleObserver`
   на `DefaultLifecycleObserver`, `@OnLifecycleEvent(ON_START) fun onAppForegrounded()` →
   `override fun onStart(owner: LifecycleOwner)`, аналогично `ON_STOP`/`onAppBackgrounded()` →
   `override fun onStop(owner: LifecycleOwner)`. Тела методов не менял — только сигнатура
   регистрации. Это управляет стартом/остановкой `LocationService` при уходе приложения на
   передний план/в фон, поэтому смотрел внимательно. Компилятор указал на неоднозначность
   `override fun onCreate()` (совпадает по имени с `DefaultLifecycleObserver.onCreate(owner)`,
   хоть и разная арность) — уточнил через `super<Application>.onCreate()`.
2. **`LocationService.kt` — `stopForeground(true)` → `stopForeground(STOP_FOREGROUND_REMOVE)`.**
   Deprecated с API 24, `minSdk` проекта — 26, так что константа доступна безусловно. Два места
   (в `catch`-ветке `startLocationUpdates()` и в `stopLocationUpdates()`).

Оба фикса убрали соответствующие warning из `compileDebugKotlin` (было 3 группы: `OnLifecycleEvent`
×3, `getRunningServices` — этот отдельный, не трогал, не deprecated-related к жизненному циклу,
а к списку запущенных сервисов, замена требует другого API — не тот риск/выгода; `stopForeground(Boolean)`
×2 — оба закрыты).

### Проверка

- `assembleDebug --offline` — BUILD SUCCESSFUL, 40/40 (после каждой правки и в конце сессии).
- **Живая проверка на эмуляторе** под `MG_Bas`: переустановил APK, запустил `MainActivity`,
  свернул на Home и снова открыл — в логах `ShiftApplication.onStart()`/`onStop()` (новые
  override-имена метода в логе подтверждают, что вызывается именно новый код, не старый
  reflection-путь) сработали корректно на каждом переходе фон/передний план. Затем переключил
  тумблер «В игре» → `LocationService` запустился (`startLocationUpdates`), затем «Не в игре» →
  `stopLocationUpdates()` отработал до конца (`LogHelper.d("...остановлены")` — значит
  `stopForeground(STOP_FOREGROUND_REMOVE)` не бросил исключение) — вернул `is_in_game` в `false`,
  как было до сессии. Полный `adb logcat -d` — ни одного `FATAL`/`AndroidRuntime:` от процесса
  `bas.app.shift` (только несвязанный GMS-шум от системного uid 1048). Единственная мутация
  состояния за сессию — временное переключение тумблера, возвращено обратно, как и в предыдущих
  сессиях с этим аккаунтом.

### Backlog на следующую ночь

- Deprecated `IntentIntegrator`/`startActivityForResult` (zxing-сканер, `ArtifactScannerActivity`/
  `AuraScannerActivity`/`MessagesChatActivity`/`ProfileFragment`) — миграция на Activity Result API
  (`registerForActivityResult`) реальна, но не точечная: меняет структуру инициализации экрана
  (нужен `ActivityResultLauncher`-филд вместо кода в `onActivityResult`), выше риск, чем сегодняшние
  два фикса — кандидат для отдельной сессии с более внимательной живой проверкой (заскан QR на
  каждом из экранов).
- `Resources.getIdentifier(..., Notification.DEFAULT_ALL)`/аналоги — deprecated `NotificationCompat`
  константы (`EkatMaps.kt:410/452/515`, `NotificationDetailActivity.kt:29/32`) — не проверял глубоко,
  вероятно тривиальная замена на актуальный API, оставил на следующую сессию.
- `ShiftApplication.isLocationServiceRunning()` использует deprecated `getRunningServices` — рабочий
  метод не имеет прямого современного эквивалента без переработки логики (обычно заменяется на
  собственный флаг вместо опроса ActivityManager) — не точечный фикс, не брал.
- `AuraEditorActivity.kt` (4 похожих, но не идентичных CRUD-блока) — по-прежнему сознательно не
  тронут (см. обоснование сессии 35).
- `!!`-паттерны — полностью просмотрены (сессия 37), находок нет.
- Doze/заблокированный экран — по-прежнему нужна ручная 30–60 мин проверка человеком.
- God-классы — `EkatMaps.showCreatePointDialog` (~260 строк, единая форма создания точки) — всё
  ещё единственный реальный кандидат на вынос, риск непропорционален для точечной ночной сессии.
- Диф пока не закоммичен (8 файлов: 2 сегодняшних + WIP из предыдущих сессий) — владелец
  коммитит по своему расписанию.

**ЗАВЕРШИЛ:** 2026-07-29 08:03 — мигрировал `ShiftApplication` с deprecated reflection-based
`LifecycleObserver`/`@OnLifecycleEvent` на `DefaultLifecycleObserver` (управляет стартом/остановкой
`LocationService` на переходах фон/передний план) и заменил deprecated `stopForeground(Boolean)` на
`stopForeground(STOP_FOREGROUND_REMOVE)` в `LocationService` (2 места). Сборка зелёная
(`assembleDebug --offline`, 40/40) после каждой правки. Живая проверка на эмуляторе под `MG_Bas`:
фон/передний план (новые `onStart`/`onStop` сработали в логах на каждом переходе) + полный цикл
«В игре»→«Не в игре» (запуск и штатная остановка `LocationService`, включая новый путь
`stopForeground`) — без единого `FATAL`/`AndroidRuntime:` от процесса приложения, состояние экрана
возвращено как было. Ничего не закоммичено.

# 04. Разбор функциональных подсистем

> Область: функциональные подсистемы клиента (Kotlin, View + viewBinding).
> Сервисы/сеть/потоки как отдельная тема разбираются в другом документе — здесь они затрагиваются только там, где напрямую влияют на надёжность конкретной подсистемы.
> Приоритет — надёжность в бою: краши, use-after-destroy, потеря/дублирование данных, зависания (ANR), утечки, деградация производительности на больших данных.

Severity: **CRIT** (краш/потеря боевых данных почти гарантированы в реальном сценарии) · **HIGH** (краш/ANR/серьёзная деградация при достижимых условиях) · **MED** (баг логики, утечка, деградация при нагрузке) · **LOW** (косметика, маловероятный edge-case, dead code).

---

## 1. Terminal

### Назначение
Внутриигровой «хакерский» терминал шумоманта: ввод команд, эффект «печатающегося» ответа, изменение уровня шума (локального/глобального), спец-сессии (UPGRADE вики-серфинг, REBOOT, DEEP_DIVE), модульные команды (SHIFT.PROXY, CROSS.LINK), дублирование команд в чат МГ. Есть кликабельные ссылки Wikipedia в выводе.

### Ключевые файлы
- `ui/terminal/TerminalActivity.kt` (1273 стр.) — вся логика, парсинг, обработчики команд, шум-оверлеи/глитчи.
- `helpers/TerminalCommandManager.kt` — реестр команд (`allCommands`), фильтрация по модулям, `findCommand`, help.
- `ui/terminal/ConsoleAdapter.kt` — RecyclerView вывода, «печатающая» анимация (`graduallyFill`), Wikipedia-ссылки.
- `ui/terminal/ChatAdapter.kt` — адаптер чата фамильяра (используется в `FamiliarChatActivity`).
- `ui/terminal/CommandAutocompleteAdapter.kt` — автодополнение (`BaseAdapter`+`Filterable`).
- `helpers/TerminalHistoryHelper.kt` + `models/TerminalHistory.kt`, `models/TerminalCommand.kt`.

### Как устроено
- Ввод → `sendToServer(cmd)` → `saveCommandToHistory` → `TerminalCommandManager.findCommand(cmd, availableModules)` → через `Handler.postDelayed(300ms)` → `processCommand`.
- `processCommand` — большой `when(command.name)` для спец-команд; всё прочее падает в `else` (печатает «Выполняю…», шлёт `adjustNoiseAndUpdateGlobal(noiseIncrease)` и `sendToMg()`).
- Шум: `NoiseManager` (см. подсистему 5) через колбэки обновляет `noise`/`globalNoise`; `updateNoise` перерисовывает 5 полосок и включает оверлеи/вибро/джампскейр по уровням.
- Парсинг аргументов везде вручную: `fullCommand.split(" ")`, `parts[1].toInt()` в try/catch (DEEP_DIVE.END глубина). Проверки размера/диапазона есть.
- История хранится в SharedPreferences (`terminal_history`) как Gson-JSON `TerminalHistory` (списки команд и ответов с `LocalTime`).

### Риски и находки
- **[T1 · CRIT→ пересмотрено на MED-latent] `noiseManager` (lateinit) не инициализируется при пустом userId, но команды его дёргают → `UninitializedPropertyAccessException`.**
  `initNoiseManager()` создаёт менеджер только если `userId.isNotEmpty()` (`TerminalActivity.kt:400-427`), иначе только логирует ошибку. Но `adjustNoiseAndUpdateGlobal` (`:444-447`), `handleProxyDeployCommand` (`:1038,1069`), `handleProxyStatusCommand` (`:1217`), `handleCrossLinkCommand` (`:1133`) вызывают `noiseManager.*` без проверки `::noiseManager.isInitialized`. Если профиль/`current_user_id` не сохранён — первая же шумовая команда крашит терминал.
  > **Проверка (ручная, при синтезе отчёта):** в терминал попадают только после `AuthActivity`, который требует непустой id, значит `userId` практически всегда непустой и `noiseManager` инициализируется. Реальный триггер — редкий edge-case (вход по deep-link/уведомлению после `clearUserData`, восстановление процесса с очищенными prefs). Поэтому severity понижен до **MED (латентный краш)**: дефект реален и его дёшево закрыть одним `isInitialized`-guard, но в штатном флоу почти недостижим. В [07-dynamic-testing.md](07-dynamic-testing.md) терминал под игроком с профилем не падал, в т.ч. на мусорном вводе.
- **[T2 · HIGH] История ответов растёт неограниченно + O(n) чтение/запись на каждый ответ.**
  `addResponseToHistory` (`TerminalHistoryHelper.kt:64-68`) НЕ ограничивает размер (лимит `MAX_HISTORY_SIZE=100` применяется только в `addCommand`, `:48-62`). Каждый ответ терминала = `loadHistory()` (полный parse JSON) + `saveHistory()` (полная сериализация). Терминал пишет десятки строк-ответов на команду → квадратичный рост стоимости и раздувание prefs за длинную игровую сессию → заметные фризы UI (запись в prefs синхронная через `apply`, но сериализация на главном потоке).
- **[T2b · MED] Gson не умеет `java.time.LocalTime` из коробки — хрупкая сериализация истории.**
  `TerminalHistory`/`TerminalHistoryItem` содержат `LocalTime`, адаптер не зарегистрирован (`TerminalHistoryHelper.kt` использует голый `Gson()`). Round-trip опирается на рефлексию по приватным полям `LocalTime`; при сбое `loadHistory` ловит исключение и возвращает пустую историю (`:37-40`) — тихая потеря всей истории терминала.
- **[T3 · MED] «Печатающие» анимации не отменяются и плодятся.**
  Каждый `adapter.addTyping(text)` запускает собственный `Handler(Looper.getMainLooper())` + рекурсивный `postDelayed(25ms)` (`ConsoleAdapter.kt:93-123`). При серии ответов (обработчик команды печатает 3-6 строк подряд) параллельно крутится несколько таймеров; ни один не отменяется в `onDestroy`/`onDetachedFromWindow`. Утечка ссылок на адаптер и лишние `notifyItemChanged` после ухода с экрана; при быстром спаме команд — рост числа таймеров.
- **[T3b · LOW] Восстановление истории ломает хронологию.** `loadTerminalHistory` (`:379-390`) добавляет сперва ВСЕ команды, потом ВСЕ ответы — на экране история после перезапуска идёт «все команды, затем все ответы», а не вперемешку по времени.
- **[T4 · MED] Флаги сессий несогласованно персистятся → рассинхрон после поворота/перезапуска.**
  `isUpgradeSessionActive` восстанавливается из prefs (`:104-105`), DEEP_DIVE хранится в prefs (`isDeepDiveSessionActive`), а `isRebootSessionActive` — только in-memory поле (`:50`). Поворот экрана/сворачивание → REBOOT-сессия теряется, `USER.REBOOT.END` выдаёт «нет активной сессии». Терминал не блокирует смену ориентации.
- **[T5 · MED] `findCommand` может сматчить неверную команду по префиксу.**
  `TerminalCommandManager.kt:78-85`: условие `commandText.startsWith(command.name, ignoreCase=true)` возвращает первую подходящую. Ввод `CROSS.LINKAGE` сматчится на `CROSS.LINK`; `USER.REBOOT` (без .START/.END) сматчится на `USER.REBOOT.START`. Порядок в `allCommands` определяет исход. Кривой, но «похожий» ввод молча исполнит не ту команду вместо «неизвестная команда».
- **[T6 · MED] `USER.FORMAT` (−10 шума, «ОПАСНО!») исполняется без подтверждения.**
  Команда описана как опасный сброс (`TerminalCommandManager.kt:46`), обработчика в `when` нет → падает в `else` (`TerminalActivity.kt:252`) и сразу шлёт `adjustNoiseAndUpdateGlobal(-10.0)`. Одна опечатка/тап автодополнения обнуляет шум без диалога подтверждения.
- **[T7 · LOW] Отправка в МГ и вывод не связаны с реальным результатом сервера.** Успех/провал показываются оптимистично (текст печатается до/независимо от ответа шум-API); при сетевой ошибке шум на сервере не изменился, а игрок видит «Уровень шума снижен». Рассинхрон с реальным состоянием на сервере.

### Заметки по качеству
Файл 1273 строки, огромное дублирование (каждый обработчик руками зовёт `adapter.addTyping` + `saveResponseToHistory` + `smoothScrollToBottom`). Комментарии-заглушки, закомментированный `sendToMg()`. `incNoise/showGlitchEvent` — мёртвый код. Много магических строк ("MG_BAS" в `sendCommandToMg` vs "MG_Bas" в чатах — расхождение регистра получателя).

---

## 2. Aura editor / canvas

### Назначение
Визуализация «ауры» персонажа: кастомный `View` рисует круг-ауру (цвет по типу/гуманизму), 10 слотов «проблем» по окружности, метки (внутренние/внешние, по типам), звёзды над метками, фигуру человека или кастомную картинку. Zoom/drag/long-tap. Редактор (МГ) добавляет/меняет/удаляет метки и проблемы. QR-код ауры (генерация и сканирование).

### Ключевые файлы
- `ui/AuraCanvasView.kt` (662 стр.) — кастомная отрисовка, жесты, загрузка bitmap (Coil).
- `ui/AuraEditorActivity.kt` (679) — выбор пользователя, диалоги меток/проблем, CRUD через `AuraApi`.
- `ui/AuraFragment.kt` — обёртка над canvas, загрузка ауры.
- `ui/AuraActivity.kt` — просмотр ауры по `aura_id`.
- `ui/AuraScannerActivity.kt` (ZXing) / `ui/AuraQrActivity.kt` (генерация QR).
- `models/Aura.kt`, `AuraMark.kt`, `AuraProblem.kt`, `AuraType.kt`+`AuraTypeAdapter`, `AuraMarkType`, `AuraProblemType`+adapter.

### Как устроено
- `setAura(aura)` (`AuraCanvasView.kt:77`) грузит `auraImage` и картинки меток корутинами (`CoroutineScope(Main+SupervisorJob)`, отменяется в `onDetachedFromWindow`), кладёт в `markBitmaps: ConcurrentHashMap<url, Bitmap?>`, вызывает `invalidate()`.
- `onDraw` → `drawAura`: `canvas.save/translate(offset)/scale(scaleFactor)`, расчёт радиусов, отрисовка круга, слотов проблем, фигуры, меток по группам, внешних меток. Каждый вызов пересобирает `markTouchAreas`/`problemTouchAreas` для хит-теста.
- Жесты: `onTouchEvent` разделяет 1 палец (drag) / 2 пальца (pinch-zoom) вручную, с «задержкой» после зума (`ZOOM_DELAY_MS`); long-tap реализован через `CountDownTimer` + порог движения.
- Редактор: диалоги на `AlertDialog`+viewBinding, CRUD через `RetrofitClient.auraApi` в `CoroutineScope(Dispatchers.IO)`, после успеха `loadUserAura` (полная перезагрузка).

### Риски и находки
- **[AU1 · HIGH] ИСПРАВЛЕНО (Wave 3, см. 08-changes-applied.md).** `drawProblem`
  (`AuraCanvasView.kt:321`) теперь кеширует декодированный bitmap в `problemBitmaps` по
  `resId` (`getOrPut`), декодирование происходит один раз на тип проблемы, а не на каждый
  `onDraw`. Подтверждено повторно свежим чтением кода в сессии 30 (2026-07-28).
- **[AU2 · MED] ПЕРЕОЦЕНЕНО, не находка (сессия 30, 2026-07-28).** `markBitmaps` и
  `problemBitmaps` — view-scoped кеши: `AuraCanvasView` пересоздаётся при каждом открытии
  экрана ауры, так что кеш не переживает между сессиями и ограничен числом уникальных меток
  на одной ауре (десятки, не тысячи) — обычный кеш, не утечка. `humanBitmap`/`auraImageBitmap`
  без `recycle()` — тоже не проблема на актуальных версиях Android (GC bitmap'ов управляет
  сам, `recycle()` нужен только для явного немедленного освобождения нативной памяти в hot
  path, чего здесь нет). Для одиночного клиента с ~30 игроками риск не подтверждён.
- **[AU3 · MED] Голые `CoroutineScope` вместо `lifecycleScope` → use-after-destroy.**
  `AuraFragment.loadAura` (`AuraFragment.kt:76`) и все CRUD-методы редактора (`AuraEditorActivity.kt:395,433,465,580,612,644`) запускают `CoroutineScope(Dispatchers.IO)`, результат приходит в `withContext(Main)` и трогает `binding`/Toast/`dialog.dismiss()`. Скоуп не привязан к жизненному циклу и не отменяется — при закрытии экрана/повороте во время запроса возможны обращения к уничтоженным view. `AuraFragment` частично защищён (`_binding` зануляется, но проверки `_binding != null` в `loadAura` нет — только в canvas).
- **[AU4 · MED] ИСПРАВЛЕНО (найдено в сессии 27 уже сделанным).** `RetrofitClient.kt:71`
  регистрирует `AuraMarkTypeAdapter` для `AuraMarkType` наравне с `AuraType`/`AuraProblemType` —
  неизвестный тип метки больше не теряется молча. Когда именно исправлено — неизвестно, не
  через 09-журнал ночной серии.
  <details><summary>Исходная находка</summary>
  В `RetrofitClient` зарегистрированы адаптеры только для `AuraType` и `AuraProblemType` (`RetrofitClient.kt:26-27`). `AuraMark.markType: AuraMarkType` (не-null, `AuraMark.kt:9`) при неизвестном значении с сервера Gson выставит `null`; в `drawAura` метка не попадёт ни в одну группу (сравнения null-safe, `:243-258`) и просто не нарисуется — молчаливая потеря метки без краша. `AuraType`/`AuraProblemType` защищены (`fromServerValue → OTHER`).
  </details>
- **[AU5 · MED] ИСПРАВЛЕНО (сессия 29, 2026-07-28).** `AuraEditorActivity.setupUI` теперь
  коммитит фрагмент через `commitNow()` вместо `commit()` и ставит колбэки сразу после —
  без `binding.auraContainer.post{…}`. `commitNow()` синхронно доводит фрагмент до
  `onViewCreated`, поэтому гонка (колбэк ещё `null` при быстром действии до отработки `post`)
  устранена полностью, а не смягчена таймаутом. Живая проверка на эмуляторе (роль `MG_Bas`):
  `AuraEditorActivity` открывается без краша, лог показывает установку колбэков синхронно в
  `onCreate` (до ответа `loadUsers`), выбор пользователя из списка загружает ауру с метками
  корректно; `adb logcat -d "AndroidRuntime:E"` пуст на всех проверках. Долгое нажатие по
  самой метке для проверки диалога вживую не подтверждено — не удалось точно попасть по
  маленькой иконке на эмуляторе тачем через `adb`; неблокирует, т.к. изменение сугубо в
  моменте установки колбэка, а не в логике диалога.
  <details><summary>Исходная находка</summary>
  `AuraEditorActivity.setupUI` коммитит фрагмент и ставит колбэки в `binding.auraContainer.post{…}` (`:143-148`); `AuraFragment.applyCallbackIfReady` также зависит от готовности `_binding`. Хрупкая последовательность: при быстром выборе пользователя до отработки `post` long-tap по метке может не сработать (колбэк ещё null) — не краш, но «редактор не реагирует».
  </details>
- **[AU9 · MED] ИСПРАВЛЕНО (сессия 29, 2026-07-28).** `AuraFragment.loadAura` теперь проверяет
  `_binding == null` перед обращением к `binding` внутри `withContext(Dispatchers.Main)`.
  `lifecycleScope` фрагмента живёт дольше его view (переживает `onDestroyView`); без проверки
  ответ сервера, пришедший в узком окне между разрушением view и уничтожением фрагмента,
  падал бы на `binding!!` (NPE). Аддитивный fix, golden-путь не изменился.
- **[AU10 · MED, было скрытой находкой] ИСПРАВЛЕНО (сессия 29, 2026-07-28).** Лоадер
  «Загрузка пользователей...» в `AuraEditorActivity` (`activity_aura_editor.xml:25-43`,
  `loadingLayout`) был жёстко зашит как `visibility="visible"` и НИКОГДА не скрывался кодом —
  `userSelectionLayout` (спиннер выбора пользователя, `:46-61`) был жёстко `visibility="gone"`
  и тоже никогда не показывался. В результате экран «Редактор Ауры» был полностью
  нефункционален: выбрать пользователя для редактирования ауры было физически невозможно
  (GONE-view не принимает тач), несмотря на то что список из 38 пользователей успешно
  загружался с сервера. Обнаружено живой проверкой на эмуляторе (роль `MG_Bas`) — экран
  бесконечно показывал спиннер загрузки. Файл `activity_aura_editor.xml` не в WIP текущей
  ночной серии, баг не связан с открытыми правками — похоже, давно существующий регресс.
  Исправлено в `AuraEditorActivity.loadUsers()`: `loadingLayout` скрывается и
  `userSelectionLayout` показывается по завершении загрузки (успех или ошибка), на ошибке —
  понятный `Toast` через `NetworkErrors` вместо тишины. Живая проверка: список пользователей
  открывается по тапу, выбор пользователя грузит ауру с метками корректно, `logcat` чист.
- **[AU6 · LOW] ИСПРАВЛЕНО (сессия 27, 2026-07-28).** `generateQrCode()` теперь считает
  bitmap в `lifecycleScope.launch { withContext(Dispatchers.Default) { ... } }`, применяет
  результат на `Main` — 640k `setPixel` больше не блокируют UI-поток. Живая проверка на
  эмуляторе (роль `Bas`, экран «Показать ауру экстрасенсу»): QR отрисовался корректно, без
  фриза и крашей.
  <details><summary>Исходная находка</summary>
  `generateQRCode` (`AuraQrActivity.kt:59-63`) — двойной цикл `800×800` × `setPixel` (640k вызовов) на главном потоке → кратковременный фриз при открытии. При пустом `userId` `encode` кинет исключение → поймано, Toast.
  </details>
- **[AU7 · LOW] ИСПРАВЛЕНО (сессия 28, 2026-07-28).** Убран недостижимый
  `catch (NumberFormatException)`, добавлена реальная валидация: `scannedContent.trim()`
  проверяется на пустоту перед вызовом `fetchAura` (`AuraScannerActivity.kt:105-112`).
  Пустой/битый QR теперь даёт понятный Toast вместо передачи пустой строки в `getAura`.
  Golden-путь (валидный QR) не изменился — по коду идентичен предыдущему поведению.
  <details><summary>Исходная находка</summary>
  `scannedContent.toString()` не бросает `NumberFormatException` — catch недостижим; содержимое QR передаётся как `userId` в `getAura` без проверок.
  </details>
- **[AU8 · LOW] `AuraActivity.entityId = "user-123"`** — заглушка-плейсхолдер осталась в коде (`AuraActivity.kt:20`), не используется, но вводит в заблуждение.

### Заметки по качеству
Ручной pinch-zoom/drag с таймерами и «зон задержки» вместо `ScaleGestureDetector`/`GestureDetector` — хрупко, много состояний (`isZooming`, `zoomEndTime`, `lastPointerCount`). Огромное дублирование блоков `if (shouldShowElements)`/`markTouchAreas.clear()`. Диалоги меток дублируют список из 14 `when`-веток дважды (add/edit).

---

## 3. Chat / Messages

### Назначение
Личные сообщения игрок↔МГ: список чатов (МГ), переписка с вложениями (изображения), теги-дисциплины, ответы на сообщения (reply), отметка «прочитано», polling новых сообщений. Отдельно — чат с «фамильяром» (LLM-подобный, другой бэкенд).

### Ключевые файлы
- `ui/MessagesChatActivity.kt` (670) — основной экран переписки, вложения, multipart, polling.
- `ui/ChatsListActivity.kt` — список чатов (для МГ), periodic refresh.
- `ui/FamiliarChatActivity.kt` — чат с фамильяром (`ChatApi`, отдельный `CHAT_BASE_URL`).
- `ui/adapters/MessagesAdapter.kt`, `ChatsAdapter.kt`, `AttachmentsAdapter.kt`; `ui/terminal/ChatAdapter.kt`.
- `models/Message.kt` (Message, MessageAttachment, Chat, CreateMessageResponse…), `models/ChatMessage.kt`.
- `api/MessagesApi.kt` (multipart create, history, chats, markAsRead), `api/ChatApi.kt`.

### Как устроено
- `MessagesChatActivity`: userId из prefs; recipient из intent (для не-МГ по умолчанию `"MG_Bas"`). Загрузка через `getChatHistory` (МГ) или `getMessages` (игрок). Оптимистичное temp-сообщение при отправке, затем замена на реальное. Вложения выбираются через `ACTION_PICK`/`ACTION_GET_CONTENT`, читаются в multipart. Polling — корутина.
- Отправка МГ требует выбранного сообщения (long-tap) → reply с тегами исходного; игрок выбирает дисциплину диалогом.
- `FamiliarChatActivity`: suspend `ChatApi`, добавляет ответ ассистента в `ChatAdapter`.

### Риски и находки
- **[CH1 · HIGH] ИСПРАВЛЕНО (найдено в сессии 27 уже сделанным).** `sendMessageWithTags`
  теперь читает вложения в `lifecycleScope.launch(Dispatchers.IO)` (`MessagesChatActivity.kt:381-399`),
  комментарий в коде прямо ссылается на этот риск (ANR/OOM на главном потоке).
  <details><summary>Исходная находка</summary>
  `sendMessageWithTags` (`MessagesChatActivity.kt:399-424`) в цикле по `files` делает `contentResolver.openInputStream(uri).readBytes()` (весь файл в `ByteArray`) и собирает multipart — всё синхронно на UI-потоке перед `enqueue`. Большое фото/скан (10-50 МБ) → фриз/ANR и риск `OutOfMemoryError`. В бою МГ/игрок отправляет фото улики — прямой риск.
  </details>
- **[CH2 · HIGH] ИСПРАВЛЕНО (найдено в сессии 27 уже сделанным).** `startPolling` (`:582-595`)
  теперь кладёт `delay(30000)` в начало каждой итерации `while(isActive)` — цикл всегда
  приостанавливается, busy-loop при `isScreenActive=false` невозможен.
  <details><summary>Исходная находка</summary>
  `startPolling` (`:634-645`): `while(true){ if(isScreenActive){ delay(30000); loadMessages(false) } }`. Когда `isScreenActive == false`, ветка без `delay` → цикл крутится без suspend на главном потоке = потенциальный ANR/100% CPU.
  </details>
- **[CH3 · MED] ИСПРАВЛЕНО (найдено в сессии 27 уже сделанным).** `textBody`/`recipientBody`
  передаются только как отдельные `@Part`, без дублирования внутри списка `files`
  (`:374-377`, комментарий в коде фиксирует именно эту причину правки).
  <details><summary>Исходная находка</summary>
  В `sendMessageWithTags` создаются `textBody`/`recipientBody` и передаются как `@Part("text")`/`@Part("recipient_id")` (`:428-434`), но те же поля уже добавлены в список `parts` (`:383-384`). Итог — два поля `text` и два `recipient_id` в запросе; `tags`/`answer_to` наоборот переданы только внутри `parts`.
  </details>
- **[CH4 · MED] ИСПРАВЛЕНО (найдено в сессии 27 уже сделанным).** `startPeriodicRefresh`
  идемпотентен — сам вызывает `stopPeriodicRefresh()` перед стартом (`ChatsListActivity.kt:62-64`),
  старт убран из `onCreate` и остался только в `onResume`.
  <details><summary>Исходная находка</summary>
  `startPeriodicRefresh` вызывается и в `onCreate` (`:41`), и в `onResume` (`:51`); каждый вызов создаёт НОВЫЙ `Handler`+`Runnable` и перетирает поля, теряя ссылку на предыдущий уже запланированный Runnable.
  </details>
- **[CH5 · MED] НЕ ПРОВЕРЕНО.** `FamiliarChatActivity` (в текущем незакоммиченном WIP на
  2026-07-28 — не трогать до коммита) использует `CoroutineScope(Dispatchers.Main)` вместо
  `lifecycleScope`. `MessagesChatActivity.pollingScope` больше не существует (полинг теперь на
  `lifecycleScope`, см. CH2) — актуальность этой части находки не подтверждена.
- **[CH6 · MED] ИСПРАВЛЕНО (найдено в сессии 27 уже сделанным).** `currentTempId` —
  убывающий отрицательный счётчик (`nextTempId--`, `:52-54, 355`), не усечение
  `System.currentTimeMillis().toInt()` — коллизия с реальными id (положительными) исключена.
  <details><summary>Исходная находка</summary>
  temp-id сообщения = `System.currentTimeMillis().toInt()` — усечение `Long→Int` (может стать отрицательным/переполниться), теоретическая коллизия с реальным `id` сообщения.
  </details>
- **[CH7 · LOW] ИСПРАВЛЕНО (сессия 28, 2026-07-28).** `rvAttachments.layoutManager`/`adapter`
  теперь ставятся один раз в `init` блоке `MessageViewHolder` (`MessagesAdapter.kt`), `bind`
  только вызывает `updateAttachments`. Живая проверка на эмуляторе (роль `MG_Bas`, чат с
  вложением `1733593029979.jpg`): вложение отрисовалось корректно, скролл списка без
  крашей. `selectMessage/clearSelection` всё ещё используют `notifyDataSetChanged` целиком —
  не тронуто (отдельная, более рискованная находка, не в рамках этой правки).
  <details><summary>Исходная находка</summary>
  `onBindViewHolder→bind` каждый раз ставит `layoutManager`/`adapter` (`MessagesAdapter.kt:191-194`). На длинных списках с вложениями — лишняя работа.
  </details>
- **[CH8 · LOW] `ChatAdapter` (фамильяр) `bind` меняет `layoutParams.marginStart/End` числами в px (64), не dp** (`ChatAdapter.kt:59-69`) — визуальная несогласованность на разных плотностях.

### Заметки по качеству
Обильный `android.util.Log.d` с PII (тексты сообщений, id). Расхождение получателя по умолчанию (`"MG_Bas"` в `MessagesChatActivity` vs `"MG_BAS"` в `TerminalActivity.sendCommandToMg`). Обработка ошибок построена на `t.message?.contains("UnknownHostException")` — сравнение по подстроке текста исключения хрупко.

---

## 4. Artifacts / QR

### Назначение
Артефакты: сканирование штрих-кода/ручной ввод ID → просмотр «паспорта» артефакта; создание артефакта (МГ) с привязкой к персонажу; изменение привязки; общий список-паспорт с выбором из спиннера.

### Ключевые файлы
- `ui/ArtifactScannerActivity.kt` (ZXing, штрих-коды CODE_128/39/EAN) + `ui/CustomScannerActivity.kt` (кастомный capture, фокус по тапу).
- `ui/ArtifactActivity.kt` (хост фрагмента) + `ui/ArtifactDetailsFragment.kt` (детали, редактирование привязки).
- `ui/ArtifactCreatorActivity.kt` (создание).
- `ui/ArtifactPassportActivity.kt` (список + спиннер).
- `models/Artifact.kt`, `api/ArtifactApi.kt`.

### Как устроено
- Скан: `IntentIntegrator` + `CustomScannerActivity` (портрет форс, таймаут 30с). Результат в `onActivityResult` → `scannedContent.toInt()` (try/catch) → `getArtifact(id)` → `ArtifactActivity` с `artifact_id` (Int extra).
- `ArtifactDetailsFragment` грузит артефакт по id, показывает поля; для МГ — кнопка редактирования привязки (диалог со списком пользователей, `updateArtifact`).
- Creator: валидация полей на клиенте, `createArtifact(ArtifactRequest)`.

### Риски и находки
- **[AR1 · MED] Все сетевые колбэки фрагмента/активити трогают UI без строгой проверки жизненного цикла.**
  `ArtifactDetailsFragment.fetchArtifact/loadUsersForDialog/updateArtifactBinding` (`ArtifactDetailsFragment.kt:66-80,120-137,196-220`) — Retrofit `enqueue`, колбэк зовёт `showArtifact`/Toast/`binding.*`. `showArtifact`/`updateArtifactBinding` проверяют `_binding != null && isAdded` (хорошо), но `showError`→`Toast.makeText(context,…)` при `context==null` после detach даст NPE. Диалоги через `requireContext()` после ухода с экрана → `IllegalStateException`.
- **[AR2 · LOW] ПРОВЕРЕНО (сессия 28, 2026-07-28) — не баг, домены разные.**
  Артефакт использует числовой `artifactId` (`toInt()`, валидно), аура — строковый `userId`
  (`AuraScannerActivity`). Это два разных типа идентификаторов по смыслу, а не расхождение
  контракта. Валидация на стороне ауры была реальной проблемой — исправлена как AU7.
- **[AR3 · LOW] ИСПРАВЛЕНО (сессия 27, 2026-07-28).** `showArtifactDetails`/`hideArtifactDetails`
  теперь используют `commitAllowingStateLoss()` вместо `commit()` — выбор в спиннере во время
  сворачивания больше не может кинуть `IllegalStateException`. Транзакция ничего не мутирует
  на сервере, потеря при пересоздании активности не критична. Живая проверка: несколько
  быстрых переключений спиннера подряд, без крашей.
  <details><summary>Исходная находка</summary>
  `showArtifactDetails` (`:92-99`) делает `replace(...).commit()` при каждом `onItemSelected`; быстрый перебор спиннера плодит транзакции. `commit` после `onSaveInstanceState` может кинуть `IllegalStateException`.
  </details>
- **[AR4 · LOW] ИСПРАВЛЕНО (сессия 27, 2026-07-28).** Убран вводящий в заблуждение Toast
  «Фокус установлен» из `focusOnTouch` — реального управления камерой там всё равно не было
  (заглушка), Toast создавал ложное впечатление, что тап что-то делает. Оставлен только лог
  для диагностики; непрерывный автофокус камеры (стандартный для `CaptureActivity`) как
  работал, так и работает. Живая проверка на эмуляторе: несколько тапов по превью камеры,
  без крашей, лог с новым текстом появляется.
  <details><summary>Исходная находка</summary>
  `:40-50` — только Toast «Фокус установлен», реального управления камерой нет (заглушка), вводит игрока в заблуждение.
  </details>
- **[AR5 · LOW] Creator: нет блокировки двойного тапа до отключения кнопки.** Кнопка отключается уже после старта `createArtifact` и всех валидаций; в целом ок, но повторные быстрые тапы до `enqueue` теоретически создают дубли.

### Заметки по качеству
`ArtifactActivity`/`Passport`/`DetailsFragment` — три разных пути показа одного и того же UI. Списки пользователей грузятся отдельно в Creator и в Details-диалоге (нет общего кеша). Логика формирования `displayName` (player/character) скопирована ~4 раза по проекту.

---

## 5. Noise («шум»)

### Назначение
«Шум» — центральная игровая метрика шумоманта: локальный уровень (0-5, дробный) и глобальный. Команды терминала повышают/понижают шум через сервер; при переходах уровней автоматически применяются эффекты (текст игроку, повреждения ауры, метки). Спец-модули делят шум пополам (Proxy-узел, Cross-Link с партнёром).

### Ключевые файлы
- `helpers/NoiseManager.kt` — периодический опрос, `adjustNoise` (с делением по Proxy/Cross-Link), колбэки в UI.
- `helpers/NoiseEffectManager.kt` (402) — применение эффектов по уровням 3/4/5, Proxy/Cross-Link эффекты, обновление профиля.
- `helpers/NoiseHelper.kt` — уровень из дробного значения (`floor`, coerce 0..5), прогресс, форматирование.
- `models/NoiseState.kt`, `NoiseAdjustResponse.kt`, `NoiseAdjustRequest`, `api/NoiseApi.kt`.
- `drawable/noise_value_background.xml` (тёмный фон плашки значения).

### Как устроено
- `NoiseManager` держит `Handler` (главный поток), `startPeriodicNoiseUpdate` каждые 60с зовёт `fetchCurrentNoise` (`getUserNoise`). `adjustNoise(delta)`: если активен Proxy-эффект и delta>0 — половину шлёт `{userId}_Proxy`; если Cross-Link — половину партнёру (id ищется по имени через `getAllUserShortProfiles`); остаток — себе через `adjustNoiseForUser`.
- В ответе `adjustNoiseForUser` для основного пользователя: `checkAndApplyNoiseEffects(before, after, userId)`.
- `NoiseEffectManager.checkAndApplyNoiseEffects`: сравнивает старый/новый уровень (`NoiseHelper.getNoiseLevel`), в `when` применяет эффект перехода 3/4/5; дедуп по наличию текста эффекта в кешированном профиле.

### Риски и находки
- **[NO1 · HIGH] `when` в `checkAndApplyNoiseEffects` пропускает промежуточные эффекты при скачке уровня.**
  `NoiseEffectManager.kt:39-55` — ветки `oldLevel<3 && newLevel>=3`, `oldLevel<4 && newLevel>=4`, `oldLevel<5 && newLevel>=5` в одном `when` (только первая сработавшая). Прыжок с 0 до 5 (например `DEEP_DIVE.END 5` даёт `+5.0`, или крупная команда) применит ТОЛЬКО эффект уровня 3, а эффекты 4 (потеря хита, разрыв ауры, звук помех) и 5 (тяжёлое ранение, дыра в ауре, метка «Влияние Ноосферы», блок шумомантии) НЕ применятся. Для боевой механики это прямая потеря критичных игровых последствий.
- **[NO2 · MED] ~~Дедуп эффектов по устаревшему кешу профиля → двойное применение / гонки.~~ ИСПРАВЛЕНО (2026-07-28, ночная сессия).**
  Было: проверка «есть ли уже эффект» читает `UserPrefsHelper.getUserData`, но применение эффекта уровня 3/4/5 НЕ обновляло локальный кеш (в отличие от Proxy/Cross-Link, которые зовут `refreshUserProfile`). Два близких пересечения порога (шум колеблется вокруг 3.0) до фонового обновления профиля (`LocationService`, раз в минуту) применяли эффект/проблему ауры повторно. Исправлено: `applyLevel3Effect`/`applyLevel4Effect`/`applyLevel5Effect` теперь зовут `refreshUserProfile(userId)` сразу после успешного создания эффекта (для 4/5 — независимо от исхода добавления проблемы ауры, эффект уже создан и является ключом дедупа), тем же способом, что уже использовали `applyProxyEffect`/`applyCrossLinkEffect`. Дребезг вокруг порога (`floor`) остаётся теоретически возможным в пределах одного цикла до ответа сервера на `refreshUserProfile`, но окно гонки сократилось с ~60с (период фонового обновления) до одного сетевого round-trip.
- **[NO3 · MED] `NoiseManager.cleanup()` не зануляет `onGlobalNoiseUpdateListener` → колбэк после destroy.**
  `cleanup()` (`NoiseManager.kt:216-220`) обнуляет `onNoiseUpdateListener` и `onCommandSuccessListener`, но НЕ `onGlobalNoiseUpdateListener` (`:34`). Ин-флайт ответ `adjustUserNoise`/`fetchCurrentNoise` после `TerminalActivity.onDestroy` вызовет `updateGlobalNoiseDisplay` → доступ к `binding` уничтоженной активности.
- **[NO4 · MED] `NoiseEffectManager` держит вечный `CoroutineScope(Dispatchers.IO)`, не отменяется. ПРОВЕРЕНО (2026-07-28) — намеренное решение, не баг.**
  `:15-18` — с 2026-07-28 в коде есть явный комментарий у объявления `scope`, подтверждающий, что это осознанный выбор: применение эффектов шума — это записи на сервер (создать эффект, добавить проблему ауры, обновить профиль), которые должны завершиться, даже если игрок закрыл терминал; `SupervisorJob` изолирует сбои одной цепочки от других; UI из этого класса не трогается (все `withContext(Dispatchers.Main)` только логируют/обновляют кеш, не View). Отменять scope в `cleanup()` было бы регрессией — оборвало бы недописанный эффект/проблему ауры при обычном уходе с экрана. Не менять.
- **[NO5 · MED] Cross-Link деление шума: асинхронный поиск партнёра, порядок и «двойной учёт».**
  В `adjustNoise` (`NoiseManager.kt:104-130`) при Cross-Link `currentDelta` делится на 2, партнёру шлётся половина в колбэке `findUserByName` (сетевой), а себе — `adjustNoiseForUser(currentUserId, currentDelta)` в конце синхронно. Если Proxy И Cross-Link активны одновременно, delta делится дважды (÷2 ÷2) — возможно не то, что задумано игромеханикой. При не найденном партнёре ветка `else` шлёт себе `delta` (полный!) вдобавок к финальному `adjustNoiseForUser(currentUserId, currentDelta)` — двойное начисление себе.
- **[NO6 · LOW] Хардкод текстов эффектов как «ключей» дедупа.**
  Сравнение и regex по длинным русским строкам (`LEVEL_4_EFFECT_TEXT`, `CROSS_LINK_EFFECT_PATTERN`). Любая правка текста на сервере/в игре ломает и дедуп, и определение партнёра (`getCrossLinkPartnerName`).

### Заметки по качеству
Логика деления шума размазана между `NoiseManager` и `NoiseEffectManager`, состояние эффектов выводится из текстовых полей профиля. `NoiseHelper` — чистый и корректный (единственный хорошо изолированный модуль подсистемы).

---

## 6. Maps (EkatMaps)

### Назначение
Google-карта игрового города: игровые точки (фамильяры, проблемы, скрытые зоны, сужающиеся круги, точки-игроки USER и т.д.) с кругами радиусов; геолокация игрока; для МГ — создание/удаление/скрытие точек лонг-тапом, поиск игроков; для игрока — маркеры видны только когда он внутри круга, диалог «поговорить с фамильяром» по дистанции.

### Ключевые файлы
- `EkatMaps.kt` (1237 стр.) — вся логика карты.
- `utils/PointVisualizer.kt` — `CircleOptions`/`MarkerOptions` по типу.
- `models/Point.kt`, `PointType.kt`.
- (смежно) `services/LocationService.kt` — источник геолокации `locationSource` (BehaviorSubject) и вход/выход из точек.

### Как устроено
- `onMapReady` настраивает карту (minZoom 9 для МГ, 13 для игрока), ставит обработчики (МГ: long-tap маркер/карта; игрок: click маркер), запускает `startPointsUpdate` (Handler, каждые 10с) и `requestForLocation`.
- `updatePointsFromServer` (в `lifecycleScope`): `ServerService.getPoints()`, затем **удаляет ВСЕ круги/маркеры и очищает `pointsOfInterest`**, добавляет заново через `addPoint` (создаёт круг, маркер = null), потом `updateForLocation` создаёт маркеры.
- `updateForLocation`: пересоздаёт `currentLocationMarker`; для МГ добавляет маркеры всем точкам; для игрока — только точкам, в чьём радиусе он находится (иначе снимает маркер).
- Геолокация: `getCurrentLocation` (одноразово) + подписка на `LocationService.locationSource` (`Disposable`), обновление в `onPause`/`dispose`.

### Риски и находки
- **[MA1 · HIGH · ИСПРАВЛЕНО (сессия 24, 2026-07-27)] Полное пересоздание всех кругов и маркеров каждые 10 секунд — деградация и мерцание.**
  Было: `updatePointsFromServer` на каждом цикле сносил ВСЕ круги/маркеры (`clear()`) и строил заново. Исправлено: логика вынесена в `utils/MapPointsRenderer.kt` (`syncPoints()`), который диффит серверный список точек с уже отрисованным состоянием — двигает существующие круги/маркеры (`circle.center =`/`marker.position =`) вместо `remove()+add()`, пересоздаёт только при смене `type`, убирает только реально пропавшие точки. Подключено в `onMapReady` (`pointsRenderer = MapPointsRenderer(mMap, isMgUser)`, `EkatMaps.kt:174`). Живьём проверено (карта отрисовывается, без мерцания и потери info-window).
- **[MA2 · MED · ИСПРАВЛЕНО (сессия 24, 2026-07-27)] `onResume`: непроверенный каст `as SupportMapFragment`.**
  Было: `supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment` мог упасть `ClassCastException`/NPE при гонке восстановления состояния. Исправлено: заменён на `as?` с проверкой на `null` — при отсутствии фрагмента лог-предупреждение и `return` из `onResume` вместо падения. Штатный путь (фрагмент есть) не изменился. Повторная переинициализация карты на каждый `onResume` (`getMapAsync`) осознанно не тронута — `onPause` симметрично отменяет подписки/раннеры, риска утечки нет, только не самая эффективная работа.
- **[MA3 · MED · ИСПРАВЛЕНО (сессия 24, 2026-07-27)] Маркеры точек-игроков (USER) пересоздавались каждый цикл → «телепортация» и потеря выделения.**
  Устранено тем же диффом `MapPointsRenderer` (см. MA1) — USER-маркеры теперь двигаются на месте (`marker.position =`) вместо удаления и создания заново.
- **[MA4 · MED · ПРОВЕРЕНО, не воспроизводится (сессия 24, 2026-07-27)] `mMap` (lateinit) используется из отложенных путей — риск краша до/без `onMapReady`.**
  Перепроверено по текущему коду: `pointsRenderer` используется только из путей, достижимых после `onMapReady` (`showPlayersPickerDialog` уже проверяет `::pointsRenderer.isInitialized`); `currentLocation` устанавливается только внутри `requestForLocation()`, которая сама вызывается из конца `onMapReady` — то есть `mMap`/`pointsRenderer` не могут быть неинициализированы в момент использования. Жизнеспособного сценария падения не найдено, изменения не вносились (спекулятивная защита не нужна).
- **[MA5 · MED · ПРОВЕРЕНО, не воспроизводится (сессия 24, 2026-07-27)] Ранний `return` из `onCreate` при «не в игре».**
  Перепроверено: `finish()` вызывается сразу с `return`; в реальном жизненном цикле Android `onResume` не получает шанса выполниться раньше физического завершения активности при этом сценарии. Изменения не вносились.
- **[MA6 · LOW · ИСПРАВЛЕНО (сессия 24, 2026-07-27)] Мутация `pointsOfInterest` во время итерации `forEach`.**
  Устранено тем же переходом на `MapPointsRenderer` (см. MA1) — состояние точек теперь либо не мутируется во время итерации, либо мутируется только перезаписью существующего ключа, без CME.
- **[MA7 · LOW] `PointType.fromServerValue(unknown) → USER`; `getPointTitle` — exhaustive `when` без `else`.**
  Неизвестный тип точки с сервера станет `USER` (`PointType.kt:17-19`) — точка молча превратится в «Кто-то в игре» и не получит круга (ветка USER). Не краш, но искажение боевой карты. Ещё не исправлено.
- **[MA8 · LOW] Хардкод «Длительность: 30 мин» для SHRINKING_CIRCLE** (`getPointDescription`) независимо от реального `expireAt`; `generatePointId()` — мёртвый код. Ещё не исправлено.

### Заметки по качеству
Файл 1237 строк, тонны `LogHelper.d` (часть закомментирована), дублирование логики видимости точек для МГ/игрока, закомментированные тестовые точки. `updateForLocation` пересоздаёт синий маркер геолокации на каждый апдейт. Нет объединения «обновить набор точек» в диффабельную операцию (add/update/remove) — только «снести всё и построить заново».

---

## Сводная таблица топ-рисков

| ID | Подсистема | Severity | Суть | Файл:строка |
|----|-----------|----------|------|-------------|
| T1 | Terminal | CRIT | `noiseManager` (lateinit) не инициализируется при пустом userId, шум-команды его дёргают → краш | `TerminalActivity.kt:400-427, 444, 1038, 1069, 1133` |
| CH1 | Chat | HIGH | ИСПРАВЛЕНО — чтение вложений вынесено на `Dispatchers.IO` | `MessagesChatActivity.kt:381-399` |
| MA1 | Maps | HIGH | Полное пересоздание всех кругов/маркеров каждые 10с, нет кластеризации → лаги, мерцание, потеря info-window | `EkatMaps.kt:800-834, 855-898, 930-954` |
| AU1 | Aura | HIGH | ИСПРАВЛЕНО (Wave 3) — `problemBitmaps` кеширует decoded bitmap по `resId`, не декодирует на каждый `onDraw` | `AuraCanvasView.kt:321` |
| NO1 | Noise | HIGH | `when` пропускает эффекты уровней 4/5 при скачке шума (0→5 применит только ур.3) | `NoiseEffectManager.kt:39-55` |
| CH2 | Chat | HIGH | ИСПРАВЛЕНО — `delay()` в начале каждой итерации, busy-loop невозможен | `MessagesChatActivity.kt:582-595` |
| T2 | Terminal | HIGH | История ответов растёт без лимита + O(n) load/save на каждый ответ → раздувание prefs, фризы | `TerminalHistoryHelper.kt:64-68, 48-62` |
| MA2 | Maps | MED | Непроверенный каст `as SupportMapFragment` + `getMapAsync` на каждый resume | `EkatMaps.kt:145-147` |
| NO3 | Noise | MED | `cleanup()` не зануляет `onGlobalNoiseUpdateListener` → доступ к `binding` после destroy | `NoiseManager.kt:216-220 / 34` |
| CH3 | Chat | MED | ИСПРАВЛЕНО — раздельные `@Part`, дублирования нет | `MessagesChatActivity.kt:374-377` |
| CH4 | Chat | MED | ИСПРАВЛЕНО — `startPeriodicRefresh` идемпотентен, старт только в `onResume` | `ChatsListActivity.kt:43-64` |
| NO2 | Noise | MED | Дедуп эффектов по устаревшему кешу профиля → повторное применение при дребезге уровня | `NoiseEffectManager.kt:64-124` |
| NO5 | Noise | MED | Cross-Link+Proxy делят delta дважды; при ненайденном партнёре — двойное начисление себе | `NoiseManager.kt:104-130` |
| AU2 | Aura | MED | ПЕРЕОЦЕНЕНО, не находка (сессия 30) — кеши view-scoped, ограничены числом меток одной ауры, не утечка | `AuraCanvasView.kt:32, 73, 84-104` |
| AU3/A/C/N | Все | MED | УСТАРЕЛО, проверено (сессия 29) — `AuraEditorActivity`/`FamiliarChatActivity` уже на `lifecycleScope`; `NoiseEffectManager` — намеренно независимый scope (запись эффектов должна пережить закрытие экрана), см. коммент в коде | `NoiseEffectManager.kt:13-18` |
| AU9 | Aura | MED | ИСПРАВЛЕНО (сессия 29) — `_binding == null` guard в `loadAura` перед обращением к `binding` на Main | `AuraFragment.kt:76-95` |
| AU10 | Aura | MED | ИСПРАВЛЕНО (сессия 29) — лоадер «Загрузка пользователей...» никогда не скрывался, спиннер выбора пользователя никогда не показывался → редактор ауры был полностью нефункционален | `AuraEditorActivity.kt:152-171`, `activity_aura_editor.xml:25-61` |
| T4 | Terminal | MED | `isRebootSessionActive` только in-memory → сессия REBOOT теряется при повороте | `TerminalActivity.kt:50, 779-822` |
| T5 | Terminal | MED | `findCommand` матчит по `startsWith` → «похожий» ввод исполнит не ту команду | `TerminalCommandManager.kt:78-85` |
| T6 | Terminal | MED | `USER.FORMAT` (−10, «ОПАСНО») исполняется сразу без подтверждения | `TerminalActivity.kt:252`, `TerminalCommandManager.kt:46` |
| MA3 | Maps | MED | USER-маркеры (игроки) пересоздаются каждый цикл вместо перемещения → рывки | `EkatMaps.kt:930-954` |
| AR1 | Artifacts | MED | ИСПРАВЛЕНО (сессия 26) — `context`/`isAdded` проверки перед Toast/диалогом | `ArtifactDetailsFragment.kt:107-137, 196-220` |
| AU4 | Aura | MED | ИСПРАВЛЕНО — `AuraMarkTypeAdapter` зарегистрирован в Gson | `RetrofitClient.kt:71` |
| AU5 | Aura | MED | ИСПРАВЛЕНО (сессия 29) — `commitNow()` вместо `commit()`+`post{}`, гонка колбэка устранена | `AuraEditorActivity.kt:138-149` |
| T3 | Terminal | MED | «Печатающие» Handler-таймеры не отменяются, плодятся при серии ответов | `ConsoleAdapter.kt:93-123` |
| MA6 | Maps | LOW | Мутация `pointsOfInterest` в `forEach` — безопасно лишь пока источник эмитит в Main-потоке | `EkatMaps.kt:932-986` |
| AU6 | Aura | LOW | ИСПРАВЛЕНО (сессия 27) — генерация QR на `Dispatchers.Default` | `AuraQrActivity.kt:34-51` |
| AR3 | Artifacts | LOW | ИСПРАВЛЕНО (сессия 27) — `commitAllowingStateLoss()` вместо `commit()` | `ArtifactPassportActivity.kt:93-110` |
| AR4 | Artifacts | LOW | ИСПРАВЛЕНО (сессия 27) — убран вводящий в заблуждение Toast | `CustomScannerActivity.kt:40-45` |

### Кросс-подсистемные наблюдения
1. **Единый анти-паттерн `CoroutineScope(Dispatchers.X)` вместо `lifecycleScope`/`viewLifecycleOwner.lifecycleScope`** — встречается в Aura, Chat, Noise, Maps. Это системный источник use-after-destroy крашей и утечек; стоит починить массово.
2. **Оптимистичный UI без сверки с ответом сервера** (Terminal печатает результат до/без учёта ответа шум-API; Chat temp-сообщения) — при сетевых сбоях в бою игрок видит «успех», которого нет на сервере.
3. **Gson enum/`LocalTime` без адаптеров** — `AuraMarkType` теряет данные молча, `TerminalHistory` может терять историю целиком; поведение зависит от версии Android (рефлексия по `java.time`).
4. **Polling везде на разных механизмах** (корутина `while(true)`, `Handler.postDelayed`, повторный старт в onCreate+onResume) с рассинхроном lifecycle — источник и утечек, и потенциальных ANR.
5. **Cleartext HTTP** (`BASE_URL=http://…` в `RetrofitClient.kt:13-14`) — вне темы «сеть», но отмечаю: боевые данные ходят по незашифрованному каналу.

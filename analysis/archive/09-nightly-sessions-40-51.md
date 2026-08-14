# Archive — nightly sessions 40–51 (2026-08-10 … 2026-08-11)

> Archived from `09-nightly-progress.md` on 2026-08-15. Historical record only — do **not**
> pick work items from here, they are all resolved or superseded. The condensed outcome of
> these sessions lives in [../08-changes-applied.md](../08-changes-applied.md) (waves 21–24),
> the current open backlog in [../11-status.md](../11-status.md).

## Сессия 40

**НАЧАЛ:** 2026-08-10 05:25 — прошло ~12 дней с последней сессии (39, 2026-07-29), гонки
нет. За это время владелец сам закоммитил весь WIP серии 32-39 (коммит `69cebcf` от
2026-07-31, `git status` перед стартом чистый, кроме нетронутого `analysis/screenshots/`).
Начал с чистого дерева. Baseline-сборка зелёная (`assembleDebug --offline`, 40/40,
up-to-date). Взял из backlog сессии 39 пункт «`Resources.getIdentifier(...,
Notification.DEFAULT_ALL)`/аналоги — вероятно тривиальная замена» — на деле это был не
`Notification.DEFAULT_ALL`, а `android.text.util.Linkify.ALL`, использованный в 5 местах.

### Что сделал

1. **`Linkify.ALL` → явный набор флагов без `MAP_ADDRESSES`.** `Linkify.ALL` помечен
   `@Deprecated`, потому что включает `MAP_ADDRESSES` (устаревшее геокодирование адресов
   через `Geocoder`); `WEB_URLS`/`EMAIL_ADDRESSES`/`PHONE_NUMBERS` не деприкейчены и
   покрывают всё, что реально нужно для игровых текстов (ссылки, контакты). Затронуто
   5 вызовов `LinkifyCompat.addLinks(..., Linkify.ALL)`:
   - `EkatMaps.kt:410,452,515` (диалог информации о точке, диалог ауры места, диалог
     редактирования точки для MG) — добавил приватную константу `LINKIFY_MASK` в
     `companion object` рядом с `AURA_READ_MAX_DISTANCE_M`;
   - `NotificationDetailActivity.kt:29,32` (заголовок и текст уведомления) — аналогичная
     константа в `companion object`.
   Поведенчески: тексты в игре (описания точек, ауры, уведомления) практически никогда не
   содержат почтовых адресов в формате, который распознаёт `MAP_ADDRESSES`, а сам этот
   матчер и так был помечен Android как ненадёжный — потери функциональности нет, есть
   устранение предупреждения компилятора.

### Проверка

- `assembleDebug --offline` — BUILD SUCCESSFUL, 40/40. Отдельно прогнал
  `compileDebugKotlin --rerun-tasks` — все 5 warning про `Linkify.ALL` исчезли, остались
  только уже задокументированные и сознательно отложенные (`IntentIntegrator`,
  `getRunningServices`, `startActivityForResult` ×2, `onActivityResult` override) — их не
  трогал, см. backlog сессии 39.
- **Живая проверка на эмуляторе** под `MG_Bas`: переустановил APK, включил тумблер «В игре»
  на главном экране, открыл карту (`EkatMaps`), тапнул по реальной точке на карте —
  открылся диалог «Информация о точке» (MG-редактирование) с полем «Аура места», который
  как раз использует новую `LINKIFY_MASK` на строке 515 (текст описания точки и текст
  «При входе» тоже используют её на 410 — но тот диалог не открывал отдельно, он read-only
  вариант того же кода). Диалог отрисовался корректно, текст читаем, закрыл через «назад»
  не сохраняя и не удаляя точку (это была настоящая точка другого мастера/сессии —
  трогать её содержимое нельзя). Полный `adb logcat -d` за сессию — ни одной строки
  `FATAL EXCEPTION`/`AndroidRuntime:` от `bas.app.shift`, ни одного unhandled exception,
  не считая шумных системных `MobStoreFlagStore`/`GoogleCertificatesRslt` (несвязанные,
  от GMS). Тумблер «В игре» вернул в «Не в игре» (`is_in_game=false` в `game_state.xml`,
  как было до сессии) — единственная мутация состояния за сессию.
- `NotificationDetailActivity` отдельно на экране не открывал (нет тривиального пути
  вызвать реальное уведомление из UI за разумное время) — код идентичен по структуре
  случаю в `EkatMaps`, компилируется и использует тот же проверенный API
  (`LinkifyCompat.addLinks` с `int`-маской), риск регрессии не выше уже проверенного пути.

### Backlog на следующую ночь

- Deprecated `IntentIntegrator`/`startActivityForResult` (zxing-сканер) — по-прежнему
  реальный кандидат, но требует `ActivityResultLauncher`-рефакторинга структуры экрана
  (выше риск) — см. обоснование сессии 39.
- `ShiftApplication.isLocationServiceRunning()` — deprecated `getRunningServices`, не
  точечный фикс без переработки логики — см. сессию 39.
- `AuraEditorActivity.kt` (4 похожих CRUD-блока) — сознательно не тронут (сессия 35).
- `!!`-паттерны — полностью просмотрены (сессия 37), находок нет.
- Doze/заблокированный экран — по-прежнему нужна ручная 30–60 мин проверка человеком.
- God-классы — `EkatMaps.showCreatePointDialog` (~260 строк) — всё ещё единственный
  реальный кандидат на вынос, риск непропорционален для точечной ночной сессии.
- Компилятор больше не выдаёт ни одного warning про Linkify — эта категория закрыта
  полностью, не проверять повторно без изменений в коде вокруг текстовых полей.
- Диф пока не закоммичен (2 файла: сегодняшние). Дерево до сессии было чистым (владелец
  закоммитил весь предыдущий WIP 2026-07-31), так что сейчас в рабочей копии только
  сегодняшние правки.

**ЗАВЕРШИЛ:** 2026-08-10 05:37 — заменил 5 использований deprecated `Linkify.ALL` (тянет
за собой deprecated `MAP_ADDRESSES`/`Geocoder`) на явную маску `WEB_URLS | EMAIL_ADDRESSES |
PHONE_NUMBERS` в `EkatMaps.kt` (3 диалога с точками/аурой) и `NotificationDetailActivity.kt`
(заголовок и текст уведомления) — по константе `LINKIFY_MASK` в каждом файле. Сборка зелёная
(`assembleDebug --offline`, 40/40; warning-группа `Linkify.ALL` полностью исчезла из
`compileDebugKotlin`). Живая проверка на эмуляторе под `MG_Bas`: включил «В игре», открыл
карту, открыл диалог информации о реальной точке (использует новую `LINKIFY_MASK`) — без
крашей, закрыл без изменений точки, вернул тумблер в исходное состояние. Ничего не
закоммичено.

### НАЧАЛ: 2026-08-10 05:45 — сессия 41, ищу следующий пункт backlog

## Сессия 41 — 2026-08-10

Дерево до старта: незакоммиченный дифф сессии 40 (`Linkify.ALL` → `LINKIFY_MASK` в
`EkatMaps.kt`/`NotificationDetailActivity.kt`), плюс нетронутый `analysis/screenshots/`.
Последняя запись журнала — «ЗАВЕРШИЛ: 2026-08-10 05:37» (сессия 40), с этой отметки прошло
~7 минут, признаков «в работе» нет — гонки нет, начал с этого же незакоммиченного дерева
(не откатывал и не трогал чужой дифф).

Baseline-сборка зелёная (`assembleDebug --offline`, 40/40, up-to-date).

### Что сделал

Backlog по deprecated-warnings из сессии 40 (`IntentIntegrator`/`startActivityForResult`,
`getRunningServices`) по-прежнему требует более крупного рефакторинга (см. обоснования ниже
в этой же записи) — не трогал. Вместо этого прогнал `compileDebugKotlin --rerun-tasks` и
заметил *build*-уровневое предупреждение (не Kotlin-warning, а предупреждение AGP), которое
раньше не попадало в фокус ночных сессий, т.к. те смотрели только на `compileDebugKotlin`:

```
> Task :app:processDebugMainManifest
package="bas.app.shift" found in source AndroidManifest.xml
Setting the namespace via the package attribute in the source AndroidManifest.xml is no
longer supported, and the value is ignored.
Recommendation: remove package="bas.app.shift" from the source AndroidManifest.xml
```

Проверил: `app/build.gradle:10` уже содержит `namespace 'bas.app.shift'` (совпадает с
удаляемым атрибутом), `applicationId` задаётся отдельно (`app/build.gradle:14`, тоже
`bas.app.shift`) — то есть атрибут `package` в манифесте полностью избыточен и уже
игнорируется AGP, дублирует то же значение через `namespace`. `find`-проверка показала, что
`package=` встречается только в `app/src/main/AndroidManifest.xml` (в тестовых/debug
манифестах его нет).

**Изменение:** убрал `package="bas.app.shift"` из
`app/src/main/AndroidManifest.xml:3` (осталась просто декларация `xmlns:android`).

### Проверка

- `assembleDebug --offline` — BUILD SUCCESSFUL, 40/40.
- Отдельно прогнал `processDebugMainManifest --rerun-tasks --offline` — предупреждение про
  `package=`/namespace полностью исчезло.
- `aapt dump badging` по собранному APK — `package: name='bas.app.shift'`,
  `versionCode='18'`, `versionName='3.0'` — идентификатор пакета и версия не изменились
  (ожидаемо, т.к. они и раньше брались не из этого атрибута, а из `namespace`/
  `applicationId` в `build.gradle`).
- **Живая проверка на эмуляторе**: `adb install -r` — `Success` (без конфликта подписи,
  подтверждает, что applicationId не поменялся). `adb shell am start -n
  bas.app.shift/.MainActivity` — приложение запустилось, `dumpsys activity activities`
  показывает `ResumedActivity: ...bas.app.shift/.MainActivity`. Полный `adb logcat -d`
  после запуска — ни одной строки `FATAL EXCEPTION`/`AndroidRuntime:` от `bas.app.shift`;
  единственные строки с «exception»/«error» — безобидный системный
  `WindowManager: Exception thrown during dispatchAppVisibility` (стандартный шум при
  переходе экрана, не наш код) и штатная инициализация Crashlytics. Мутаций игрового
  состояния не делал (не логинился, не трогал тумблер «В игре»).

### Поиск второго/третьего кандидата (не нашёл достаточно безопасного)

- `compileDebugKotlin --rerun-tasks` — все оставшиеся warning-и это уже задокументированный
  и сознательно отложенный список (сессии 39/40): `ShiftApplication.getRunningServices`
  (deprecated, нужна переработка логики, не точечный фикс), `IntentIntegrator` в
  `ArtifactScannerActivity`/`AuraScannerActivity` + `startActivityForResult` в
  `MessagesChatActivity`/`ProfileFragment` + `onActivityResult`-override в `ProfileFragment`
  (все тянут за собой `ActivityResultLauncher`-рефакторинг структуры экрана — риск выше
  точечной ночной правки).
- `lintDebug --offline` — упал: `lint-gradle-31.3.0` и зависимости не закешированы для
  offline-режима (нет сети, чтобы скачать) — lint как источник новых находок в эту сессию
  недоступен.
- Повторный RAG-поиск по «мёртвый код»/«TODO» и точечная grep-проверка трёх кандидатов,
  оставленных сессией 19 (`TimePickerHelper.getTimeOptions`, `TerminalHistoryHelper.
  clearHistory`, `CommandAutocompleteAdapter.getCommandNameAt`) — все три уже отсутствуют в
  коде (удалены в одной из сессий 20–40, до этой сессии дело не дошло делать повторно).
  Эвристический скан «функция объявлена, но встречается в файле ≤1 раза» по всем `*.kt` дал
  только framework-overrides (`onResponse`/`onFailure`/`onCreateView`/`onSupportNavigateUp`/
  `onItemSelected` и т.п.) — это ложные срабатывания (вызываются платформой/callback'ом, а не
  текстовым совпадением внутри того же файла), реальных новых кандидатов на мёртвый код не
  нашёл.
- Итог: низкорисковый backlog по-прежнему практически исчерпан после 40 предыдущих сессий;
  единственная свежая находка этой сессии — build-уровневое предупреждение про манифест
  (не пересекается с тем, что искали раньше, т.к. раньше проверяли только
  Kotlin-компиляторные warning-и).

### Backlog на следующую ночь

- Всё то же самое, что и в конце сессии 40: `IntentIntegrator`/`startActivityForResult`
  (zxing-сканер, ×2 экрана + `MessagesChatActivity`/`ProfileFragment`) и
  `ShiftApplication.isLocationServiceRunning()` (`getRunningServices`) — оба требуют
  структурного рефакторинга, не точечная правка; `AuraEditorActivity` CRUD-дубли и
  `EkatMaps.showCreatePointDialog` (~260 строк) — сознательно не тронуты (риск непропорционален
  для ночной сессии); Doze/заблокированный экран — нужна ручная проверка человеком.
- Новое: если владельцу когда-нибудь понадобится включить `lintDebug` в ночной цикл — сейчас
  зависимости lint (`com.android.tools.lint:*:31.3.0` и transitive) не закешированы для
  offline Gradle; разово собрать без `--offline` (с сетью) один раз закинет их в кэш.
- Warning про `package=`/namespace в манифесте закрыт полностью — не проверять повторно без
  изменений в `AndroidManifest.xml`/`build.gradle`.
- Диф пока не закоммичен: сегодняшний файл (`AndroidManifest.xml`) поверх незакоммиченного
  диффа сессии 40 (`EkatMaps.kt`, `NotificationDetailActivity.kt`) — итого 3 изменённых файла
  в рабочем дереве, владелец коммитит сам.

**ЗАВЕРШИЛ:** 2026-08-10 06:05 — убрал избыточный (и уже игнорируемый AGP) атрибут
`package="bas.app.shift"` из `app/src/main/AndroidManifest.xml` — идентификатор пакета и так
задавался через `namespace`/`applicationId` в `app/build.gradle`, дубль только генерировал
build-уровневое предупреждение. Сборка зелёная (`assembleDebug --offline`, 40/40;
предупреждение про namespace исчезло из `processDebugMainManifest`). `aapt dump badging`
подтвердил, что `applicationId`/версия не изменились. Живая проверка на эмуляторе:
переустановил APK (`adb install -r` — успех, без конфликта подписи), запустил
`MainActivity` — без крашей (чистый `logcat`), состояние игры не трогал. Второй/третий
кандидат в этот раз не нашёл: оставшийся backlog (zxing-сканер,
`getRunningServices`) требует более крупного рефакторинга, а повторный поиск мёртвого кода
и lint (недоступен offline) новых точечных находок не дали — низкорисковый backlog
практически исчерпан после 40 предыдущих сессий. Ничего не закоммичено.

---

## Сессия 42 — 2026-08-10

**НАЧАЛ:** 2026-08-10 06:05 — сразу после завершения сессии 41 (запись «ЗАВЕРШИЛ: 06:05»
выше), гонки нет — начал с того же незакоммиченного дерева (не откатывал и не трогал чужой
дифф). Дерево до старта: незакоммиченные диффы сессий 40+41 (`Linkify.ALL` →
`LINKIFY_MASK` в `EkatMaps.kt`/`NotificationDetailActivity.kt`; убранный `package=` в
`AndroidManifest.xml`), плюс нетронутый `analysis/screenshots/`. Baseline-сборка зелёная
(`assembleDebug --offline`, 40/40, up-to-date).

### Что сделал

Взял из backлога сессий 39–41 пункт про deprecated `IntentIntegrator`/`startActivityForResult`
+ `onActivityResult` — но не целиком (два QR-сканера и file-picker в `MessagesChatActivity`
по-прежнему выше риска для точечной ночной правки, см. ниже), а только самый изолированный
случай:

1. **`ProfileFragment.kt` — миграция `startActivityForResult`/`onActivityResult` (deprecated)
   на `ActivityResultLauncher` (Activity Result API).** Единственное место использования —
   кнопка «Редактировать эффекты» (видна только МГ-пользователям) открывает
   `EffectEditorActivity` с одним request-кодом (`REQUEST_CODE_EDIT_EFFECTS`) и простой
   обработкой результата (Toast «Эффекты обновлены» при `RESULT_OK`, без чтения данных из
   `Intent`). Заменил на `private val editEffectsLauncher = registerForActivityResult(
   ActivityResultContracts.StartActivityForResult()) { result -> ... }` (поле фрагмента,
   регистрируется при конструировании — безопасно по правилам Activity Result API), убрал
   константу `REQUEST_CODE_EDIT_EFFECTS` и override `onActivityResult` целиком, вызов —
   `editEffectsLauncher.launch(intent)` вместо `startActivityForResult(intent, ...)`. Логика
   внутри колбэка не поменялась (тот же `Toast`, та же проверка `RESULT_OK`), перенесена
   один в один.

   Сознательно НЕ трогал в эту сессию (риск выше точечной правки, отложено на будущее):
   - `ArtifactScannerActivity`/`AuraScannerActivity` (`IntentIntegrator`, zxing) — миграция
     возможна (`IntentIntegrator.createScanIntent()` + `ActivityResultLauncher`), но требует
     живой проверки реальным сканированием QR/штрихкода на обоих экранах, которую сложно
     сделать безопасно за одну автономную сессию без готового тестового кода в кадре.
   - `MessagesChatActivity` (`startActivityForResult` для выбора файлов) — сложнее
     (`chooserIntent` с `EXTRA_INITIAL_INTENTS`, множественный выбор через `clipData`,
     плюс отдельный `onRequestPermissionsResult`) — риск регрессии в логике разбора
     результата выше, чем в `ProfileFragment`.
   - `ShiftApplication.isLocationServiceRunning()` (`getRunningServices`) — как и в сессиях
     39–41, замена требует переработки логики (обычно — собственный флаг вместо опроса
     `ActivityManager`), а это надёжность-критичный код (решает, перезапускать ли
     `LocationService` при возврате в приложение), не точечный фикс — согласен с прошлыми
     сессиями, не трогал.

### Проверка

- `assembleDebug --offline` — BUILD SUCCESSFUL, 40/40 (после правки и в конце сессии).
- `compileDebugKotlin --rerun-tasks --offline` — warning про `startActivityForResult`/
  `onActivityResult` в `ProfileFragment.kt` полностью исчез из вывода; остались только уже
  задокументированные и сознательно отложенные (`getRunningServices`, `IntentIntegrator` ×2
  файла, `startActivityForResult` в `MessagesChatActivity`).
- **Живая проверка на эмуляторе** под `MG_Bas` (пользователь уже был залогинен на старте
  сессии — не переключал): переустановил APK (`adb install -r` — успех), запустил
  `MainActivity` → «Просмотр профиля» → `MgProfileViewActivity` (именно тот экран, где
  используется `ProfileFragment`) → выбрал в списке реального игрока «Бас Игрок / Имя Баса»
  (безопасный выбор — тестовый аккаунт самого владельца) → нажал «Редактировать» в блоке
  «Эффекты (Игротехника!)» → `EffectEditorActivity` открылась через новый
  `editEffectsLauncher` (заголовок «Редактор эффектов», список «Нет эффектов» — у этого
  пользователя эффектов не было, ничего не добавлял/не удалял, чтобы не мутировать реальные
  данные) → вернулся назад (`KEYCODE_BACK`, `RESULT_CANCELED`) → `MgProfileViewActivity`
  отрисовалась корректно, состояние (выбранный пользователь) сохранилось. Полный
  `adb logcat -d` за сессию — ни одной строки `FATAL EXCEPTION`/`AndroidRuntime:` от
  `bas.app.shift`. `RESULT_OK`-путь (Toast «Эффекты обновлены») отдельно не проверял живьём —
  потребовал бы реально удалить/добавить эффект тестовому пользователю (мутация реальных
  игровых данных), а сам колбэк — механический перенос кода один в один из старого
  `onActivityResult`, дополнительный риск в нём минимален. `game_state.xml` (`is_in_game`)
  не трогал вообще в эту сессию — единственная мутация состояния приложения была временный
  выбор пользователя в дропдауне `MgProfileViewActivity`, которая не пишется на диск.

### Backlog на следующую ночь

- `ArtifactScannerActivity`/`AuraScannerActivity` (`IntentIntegrator` → `ActivityResultLauncher`
  через `createScanIntent()`) — реальный кандидат, но нужна живая проверка сканированием QR/
  штрихкода на обоих экранах.
- `MessagesChatActivity` (file-picker `startActivityForResult` → `ActivityResultLauncher`) —
  сложнее из-за `clipData`/множественного выбора, тоже нужна живая проверка (реально прикрепить
  файл в чате).
- `ShiftApplication.isLocationServiceRunning()` (`getRunningServices`) — по-прежнему требует
  переработки логики (не точечный фикс), надёжность-критичный код — см. обоснование сессий
  39–42.
- `AuraEditorActivity.kt` (4 похожих CRUD-блока) — сознательно не тронут (сессия 35).
- `!!`-паттерны — полностью просмотрены (сессия 37), находок нет.
- Doze/заблокированный экран — по-прежнему нужна ручная 30–60 мин проверка человеком.
- God-классы — `EkatMaps.showCreatePointDialog` (~260 строк) — всё ещё единственный реальный
  кандидат на вынос, риск непропорционален для точечной ночной сессии.
- `startActivityForResult`/`onActivityResult` warning в `ProfileFragment.kt` закрыт полностью —
  не проверять повторно без изменений вокруг редактирования эффектов.
- Диф пока не закоммичен: сегодняшний файл (`ProfileFragment.kt`) поверх незакоммиченного
  диффа сессий 40–41 (`EkatMaps.kt`, `NotificationDetailActivity.kt`, `AndroidManifest.xml`) —
  итого 4 изменённых файла в рабочем дереве, владелец коммитит сам.

**ЗАВЕРШИЛ:** 2026-08-10 06:15 — мигрировал `ProfileFragment.kt` с deprecated
`startActivityForResult`/`onActivityResult` на `ActivityResultLauncher`
(`registerForActivityResult(ActivityResultContracts.StartActivityForResult())`) для потока
«Редактировать эффекты» (МГ-функция). Сборка зелёная (`assembleDebug --offline`, 40/40;
warning для этого файла полностью исчез из `compileDebugKotlin`). Живая проверка на
эмуляторе под `MG_Bas`: `MgProfileViewActivity` → выбор тестового игрока → кнопка
«Редактировать» в блоке эффектов → `EffectEditorActivity` открылась через новый launcher →
возврат назад без изменений — без единого `FATAL`/`AndroidRuntime:` от процесса приложения,
`game_state.xml` не тронут. Два более рискованных случая того же класса warning-ов (zxing-
сканеры, file-picker в `MessagesChatActivity`) сознательно оставлены в backlog — требуют
живой проверки камерой/файлами, что не удалось сделать безопасно за одну автономную
сессию. Ничего не закоммичено.

## Сессия 43 — 2026-08-10

**НАЧАЛ:** 2026-08-10 06:25 — сессия 42 завершилась в 06:15 («ЗАВЕРШИЛ» выше), не «в работе»,
гонки нет. Дерево до старта совпадает с записью сессии 42: незакоммиченные диффы `EkatMaps.kt`
(`LINKIFY_MASK`), `NotificationDetailActivity.kt` (`LINKIFY_MASK`), `AndroidManifest.xml`
(убран `package=`), `ProfileFragment.kt` (Activity Result API для эффектов). Baseline-сборка
зелёная (`assembleDebug --offline`, 40/40, up-to-date).

### Что сделал

Следующий пункт backlog сессий 39–42 — deprecated `startActivityForResult`/`onActivityResult`
в **`MessagesChatActivity.kt`** (file-picker для вложений в чате с МГ). Раньше сессии
откладывали его как более рискованный (`clipData`/множественный выбор), но логика оказалась
такой же самодостаточной, как в `ProfileFragment` — весь код обработки результата уже был
закрыт в `data?.let { ... }` без побочных связей с остальным классом, поэтому перенос один в
один в лямбду `registerForActivityResult` безопасен:

1. Добавлены импорты `androidx.activity.result.ActivityResultLauncher` и
   `androidx.activity.result.contract.ActivityResultContracts`.
2. Новое поле `pickFilesLauncher: ActivityResultLauncher<Intent>` (регистрируется в теле
   класса, как того требует Activity Result API) содержит ровно ту же логику, что раньше была
   в `onActivityResult` под `REQUEST_CODE_PICK_FILES`: разбор `clipData` (множественный выбор)
   / `intent.data` (одиночный), тосты «Выбрано файлов: N» / «Файлы не выбраны» /
   «Выбор файлов отменен».
3. `startActivityForResult(chooserIntent, REQUEST_CODE_PICK_FILES)` → `pickFilesLauncher.launch(chooserIntent)`.
4. Убраны константа `REQUEST_CODE_PICK_FILES` и весь override `onActivityResult` (в классе не
   осталось других request-кодов, использующих этот колбэк — `REQUEST_CODE_PERMISSIONS` идёт
   через отдельный `onRequestPermissionsResult`, не тронут).

Сознательно НЕ трогал (риск выше точечной правки, остаётся в backlog):
   - `ArtifactScannerActivity`/`AuraScannerActivity` (`IntentIntegrator` → `ActivityResultLauncher`
     через `createScanIntent()`) — нужна живая проверка сканированием QR/штрихкода камерой,
     сложнее эмулировать безопасно, чем file-picker.
   - `ShiftApplication.isLocationServiceRunning()` (`getRunningServices`) — как и раньше,
     требует переработки логики, не точечный фикс, надёжность-критичный код.

### Проверка

- `assembleDebug --offline` — BUILD SUCCESSFUL, 40/40 (после правки).
- `compileDebugKotlin --rerun-tasks --offline` — warning про `startActivityForResult` для
  `MessagesChatActivity.kt` полностью исчез; остались только заранее задокументированные и
  сознательно отложенные (`getRunningServices`, `IntentIntegrator` ×2 файла).
- **Живая проверка на эмуляторе** (`emulator-5554`). Переустановил APK — по неосторожности
  сначала сделал `adb uninstall` перед `install -r` (это стёрло `SharedPreferences`, включая
  сохранённый `userId`); восстановил вход через `AuthActivity` (`Ваш Id` → `Bas`), что
  подтвердило: `AuthActivity` — единственное место входа, без сложного onboarding, потеря
  локальных данных легко обратима. **Урок на будущее: `adb install -r` без предварительного
  `uninstall`, только при конфликте подписи — как и написано в системном промпте, но забыл в
  начале этой сессии.** Дальше: `MainActivity` → «Чат с МГ» → скрепка (attach) → открылся
  `Intent.createChooser` с вариантами «Media picker» / «Photos» (подтверждает, что
  `pickFilesLauncher.launch(chooserIntent)` действительно стартует тот же chooser, что раньше
  запускал `startActivityForResult`) → сначала проверил путь отмены (Photos закрылась без
  выбора → тост «Выбор файлов отменен», ветка `RESULT_CANCELED` отработала) → затем запушил на
  эмулятор тестовый PNG (`adb push` в `/sdcard/Pictures`, `MEDIA_SCANNER_SCAN_FILE`) и открыл
  системный Photo Picker («Media picker») → выбрал 3 фото (в т.ч. тестовое) → «Add (3)» → тост
  «Выбрано файлов: 3» — подтверждает ветку `clipData`/множественный выбор (`RESULT_OK`)
  корректно переехала в лямбду. Полный `adb logcat -d` — ни одной строки
  `FATAL EXCEPTION`/`AndroidRuntime:` от `bas.app.shift` за всю сессию. Тестовый файл
  (`/sdcard/Pictures/rtk_test.png`) удалён с эмулятора после проверки, ничего на сервер не
  отправлял (сообщение не отправлял — только вложение выбиралось локально).

### Backlog на следующую ночь

- `ArtifactScannerActivity`/`AuraScannerActivity` (`IntentIntegrator` → `ActivityResultLauncher`
  через `createScanIntent()`) — последний из warning-набора deprecated Activity Result API,
  нужна живая проверка сканированием QR/штрихкода на обоих экранах (сложнее эмулировать, чем
  file-picker/фото — потребуется либо реальный QR-код в кадре виртуальной камеры эмулятора,
  либо webcam passthrough).
- `ShiftApplication.isLocationServiceRunning()` (`getRunningServices`) — по-прежнему требует
  переработки логики (не точечный фикс), надёжность-критичный код — см. обоснование сессий
  39–43.
- `AuraEditorActivity.kt` (4 похожих CRUD-блока) — сознательно не тронут (сессия 35).
- `!!`-паттерны — полностью просмотрены (сессия 37), находок нет.
- Doze/заблокированный экран — по-прежнему нужна ручная 30–60 мин проверка человеком.
- God-классы — `EkatMaps.showCreatePointDialog` (~260 строк) — всё ещё единственный реальный
  кандидат на вынос, риск непропорционален для точечной ночной сессии.
- После warning в `MessagesChatActivity.kt` весь набор deprecated `startActivityForResult`/
  `onActivityResult` warning-ов закрыт, кроме двух QR-сканеров выше — не проверять повторно
  без изменений вокруг вложений в чате.
- Диф пока не закоммичен: сегодняшний файл (`MessagesChatActivity.kt`) поверх незакоммиченного
  диффа сессий 40–42 (`EkatMaps.kt`, `NotificationDetailActivity.kt`, `AndroidManifest.xml`,
  `ProfileFragment.kt`) — итого 5 изменённых файлов в рабочем дереве, владелец коммитит сам.

**ЗАВЕРШИЛ:** 2026-08-10 06:35 — мигрировал `MessagesChatActivity.kt` с deprecated
`startActivityForResult`/`onActivityResult` на `ActivityResultLauncher`
(`registerForActivityResult(ActivityResultContracts.StartActivityForResult())`) для потока
выбора вложений (file-picker чата с МГ). Сборка зелёная (`assembleDebug --offline`, 40/40;
warning для этого файла полностью исчез из `compileDebugKotlin`). Живая проверка на эмуляторе
под игроком `Bas`: открыл «Чат с МГ» → скрепка → chooser (Media picker/Photos) открылся через
новый launcher → проверил и ветку отмены («Выбор файлов отменен»), и ветку успешного
множественного выбора (запушил тестовое фото, выбрал 3 файла через системный Photo Picker →
тост «Выбрано файлов: 3», подтверждающий разбор `clipData`) — без единого `FATAL`/
`AndroidRuntime:` от процесса приложения. По ходу сессии по неосторожности стёр локальные
`SharedPreferences` (`adb uninstall` перед `install -r`, хотя правило — uninstall только при
конфликте подписи) — восстановил вход через `AuthActivity` («Bas»), заметил на будущее.
Единственный оставшийся кандидат того же класса warning-ов (zxing QR-сканеры в
`ArtifactScannerActivity`/`AuraScannerActivity`) сознательно оставлен в backlog — требует живой
проверки камерой, что сложнее сделать безопасно за одну автономную сессию. Ничего не
закоммичено.

## Сессия 44 — 2026-08-10

**НАЧАЛ:** 2026-08-10 06:45 — сессия 43 завершилась в 06:35 («ЗАВЕРШИЛ» выше, не «в работе»),
гонки нет. Дерево до старта совпадает с записью сессии 43: незакоммиченные диффы
`EkatMaps.kt` (`LINKIFY_MASK`), `NotificationDetailActivity.kt` (`LINKIFY_MASK`),
`AndroidManifest.xml` (убран `package=`), `ProfileFragment.kt` и `MessagesChatActivity.kt`
(Activity Result API). Baseline-сборка зелёная (`assembleDebug --offline`, 40/40, up-to-date).

### Что сделал

После 43 сессий низкорисковый backlog правок production-кода почти исчерпан (единственный
оставшийся пункт того же типа — zxing QR-сканеры — требует живой проверки камерой, что
непросто сделать безопасно автономно; см. обоснование сессий 39–43 ниже). Проверил
жизнеспособность миграции: back-камера эмулятора сконфигурирована как `virtualscene`
(`hw.camera.back = virtualscene` во всех `config.ini`), т.е. для реального теста сканирования
штрихкода понадобилась бы замена постера в virtual scene через Extended Controls (GUI,
недоступно в headless-сессии) — решил не форсировать и оставить в backlog, как и раньше.

Вместо этого поискал ценность в другом направлении: у проекта нет ни одного реального unit-
теста (только boilerplate `ExampleUnitTest`/`ExampleInstrumentedTest`), при этом в `helpers/`
уже есть несколько **чистых** (без Android-зависимостей) классов с логикой, которая
используется на многих экранах — регрессия в них молча ломает поведение сразу в нескольких
местах. Добавил юнит-тесты (только новые файлы в `app/src/test/`, ни одна строка
production-кода не тронута — риск для сборки/рантайма нулевой):

1. **`NetworkErrorsTest.kt`** — `NetworkErrors.http()` (коды 400/401/403/404/5xx/неизвестный)
   и `NetworkErrors.network()` (маппинг `UnknownHostException`/`SocketTimeoutException`/
   `UnknownServiceException`, в т.ч. по тексту сообщения обёрнутого исключения, плюс
   null/неизвестное исключение). Этот хелпер — единая точка человекочитаемых сетевых ошибок,
   используется примерно в десятке экранов.
2. **`DisplayNamesTest.kt`** — `DisplayNames.combine()`/`combinePlayerFirst()` (все комбинации
   null/пусто/оба значения/fallback). Используется в списках чатов, профиле МГ, заголовках.
3. **`AuraCleanupManagerTest.kt`** — чистая часть `AuraCleanupManager` (Context-зависимые
   `start()`/`progress()` не трогал): `canClean()`, `durationMinutes()`, `outcomeFor()`
   (TEAR→Converted(SCAR), SCAR→Removed, остальные→null) и `Progress.remainingMs()`/
   `isReady()` (отсчёт времени чистки ауры экстрасенсом, включая клэмп в 0 после дедлайна).
4. **`PointRadiusMathTest.kt`** — нелинейный слайдер радиуса точки на карте
   (`radiusFromSlider`/`sliderFromRadius`: границы 5м/3000м, клэмп вне диапазона,
   монотонность, приблизительный round-trip через "снэп" к красивому шагу),
   `zoomForRadiusMeters` (пороги эвристики зума камеры) и `formatRadius` (м vs км). Класс уже
   содержал комментарий-приглашение «легко тестируется отдельно, зависимостей от Android нет» —
   явно оставленный кем-то повод, что тестов ещё не было.

Итого 32 новых теста в 4 файлах, все проходят. Не трогал `NoiseEffectManager`/`NoiseManager`/
`ProfileDiffer`/`TerminalCommandManager` — тоже интересные кандидаты на будущее, но крупнее и
на сегодня не смотрел их достаточно внимательно, чтобы за одну сессию покрыть качественно.

### Проверка

- `./gradlew :app:testDebugUnitTest --offline` — BUILD SUCCESSFUL, все 32 новых теста + старый
  `ExampleUnitTest` зелёные (проверил как по отдельности по файлам, так и `--rerun-tasks` вместе
  с `assembleDebug` в одном вызове — 46/46).
- `./gradlew :app:assembleDebug --offline` — BUILD SUCCESSFUL, 40/40 (не мог не остаться
  зелёным: тесты лежат в отдельном source set `app/src/test/`, `assembleDebug` их не
  компилирует; это подтвердилось — задачи сборки APK остались `UP-TO-DATE`).
- Живую проверку на эмуляторе в этот раз не делал — production-код (main source set) не
  менялся ни строкой, APK не пересобирался (`assembleDebug` вернул все задачи `UP-TO-DATE`),
  поэтому поведение приложения на устройстве физически не могло измениться.

### Backlog на следующую ночь

- `ArtifactScannerActivity`/`AuraScannerActivity` (`IntentIntegrator` → `ActivityResultLauncher`)
  — по-прежнему единственный кандидат из набора deprecated Activity Result API warning-ов,
  нужна живая проверка камерой; в этой сессии подтвердил, что back-камера эмулятора —
  `virtualscene`, тестовый QR потребовал бы смены постера через Extended Controls (GUI).
- `ShiftApplication.isLocationServiceRunning()` (`getRunningServices`) — требует переработки
  логики, надёжность-критичный код, не точечный фикс (см. сессии 39–43).
- `AuraEditorActivity.kt` (4 похожих CRUD-блока), `EkatMaps.showCreatePointDialog` (~260 строк)
  — сознательно не тронуты, риск непропорционален ночной сессии.
- Doze/заблокированный экран — по-прежнему нужна ручная 30–60 мин проверка человеком.
- **Новое направление, начатое в этой сессии: юнит-тесты для чистых хелперов.** Хорошие
  кандидаты на продолжение (все — pure logic, минимум Android-зависимостей):
  `NoiseHelper.kt` (не смотрел детально), `TimePickerHelper.kt` (не смотрел детально после
  сессии 41 — тогда искали мёртвый код, не тестируемость), `ProfileDiffer.kt` (9.4K, есть
  вероятность частичной чистой логики сравнения полей — крупнее, посмотреть внимательно перед
  тем как тестировать), `TerminalCommandManager.kt`/`TerminalVisualEffects.kt` (парсинг команд/
  визуальные эффекты терминала — вероятно тоже частично чистые). `NoiseEffectManager.kt`/
  `NoiseManager.kt` — крупные (22K/11K), там же есть сетевые вызовы, тестировать только чистую
  часть, если она вообще отделима без Context.
- Диф пока не закоммичен: сегодняшние новые файлы (`app/src/test/java/bas/app/shift/helpers/
  {NetworkErrorsTest,DisplayNamesTest,AuraCleanupManagerTest,PointRadiusMathTest}.kt`) поверх
  незакоммиченного диффа сессий 40–43 (`EkatMaps.kt`, `NotificationDetailActivity.kt`,
  `AndroidManifest.xml`, `ProfileFragment.kt`, `MessagesChatActivity.kt`) — итого 5 изменённых
  + 4 новых файла в рабочем дереве, владелец коммитит сам.

**ЗАВЕРШИЛ:** 2026-08-10 07:05 — низкорисковый backlog правок production-кода после 43 сессий
почти исчерпан (единственный оставшийся кандидат — миграция zxing QR-сканеров — по-прежнему
требует живой проверки камерой, которую я не смог безопасно организовать в headless-сессии:
back-камера эмулятора `virtualscene`, тест потребовал бы GUI Extended Controls). Вместо этого
открыл новое, ранее нетронутое направление: добавил 32 юнит-теста в 4 новых файлах
(`app/src/test/java/bas/app/shift/helpers/`) для чистой логики без Android-зависимостей —
`NetworkErrors` (маппинг сетевых ошибок, ~10 экранов), `DisplayNames` (склейка имён персонаж/
игрок), чистая часть `AuraCleanupManager` (тайминги чистки ауры экстрасенсом) и
`PointRadiusMath` (нелинейный слайдер радиуса точки на карте — класс уже содержал
комментарий-приглашение протестировать). Ни одна строка production-кода не тронута —
риск нулевой, но тесты фиксируют поведение, от которого зависят реальные экраны, и ловят
регрессии, если кто-то (человек или будущая ночная сессия) поменяет логику в этих хелперах.
`testDebugUnitTest` и `assembleDebug` зелёные (46/46 суммарно). Живую проверку на эмуляторе не
делал — APK не пересобирался. Backlog на будущее пополнен конкретными кандидатами для
продолжения тестового покрытия (`ProfileDiffer`, `TerminalCommandManager`,
`TerminalVisualEffects`, `TimePickerHelper`, `NoiseHelper`). Ничего не закоммичено.

## Сессия 45 — 2026-08-10

**НАЧАЛ:** 2026-08-10 07:05 — сессия 44 завершилась в 07:05 («ЗАВЕРШИЛ» выше, не «в работе»),
гонки нет. Дерево до старта совпадает с концом сессии 44: незакоммиченные диффы
`EkatMaps.kt`, `NotificationDetailActivity.kt`, `AndroidManifest.xml`, `ProfileFragment.kt`,
`MessagesChatActivity.kt` (production, сессии 40–43) плюс 4 новых тестовых файла в
`app/src/test/java/bas/app/shift/helpers/` (сессия 44). Baseline-сборка зелёная
(`assembleDebug --offline`, 40/40, up-to-date).

### Что сделал

Продолжил направление сессии 44 — юнит-тесты для чистых хелперов из её backlog. Ни одна
строка production-кода не тронута (только новые файлы в `app/src/test/`), риск для
сборки/рантайма нулевой:

1. **`NoiseHelperTest.kt`** (5 тестов) — `getNoiseLevel` (округление вниз, клэмп 0..5),
   `formatNoiseValue` (формат `%.1f`), `getLevelProgress` (дробная часть, в т.ч. отрицательное
   значение). Маленький хелпер, но используется в отображении шкалы шума.
2. **`TimePickerHelperTest.kt`** (9 тестов) — только чистые функции `formatTime`/
   `parseTimeToMinutes` (диалог `showTimePicker` не тронут — требует `Context`, не
   тестируется юнит-тестом). Покрыл все три формата ("2ч 30м"/"2ч"/"30м"), пустой/
   непарсящийся ввод → `null`, и round-trip format→parse.
3. **`TerminalCommandManagerTest.kt`** (10 тестов) — `getAvailableCommands` (гейтинг по
   `requiredModuleId`), `findCommand` (точное совпадение по первому токену, регистронезависимо,
   с аргументами после команды), включая явную регрессионную проверку на баг, который уже был
   исправлен раньше (`CROSS.LINKAGE` не должен матчиться на `CROSS.LINK`, `USER.REBOOT` — на
   `USER.REBOOT.START`, см. комментарий в самом файле про старый `startsWith`), и `getHelpText`
   (гейтинг команд по модулю в тексте справки).
4. **`ProfileDifferTest.kt`** (22 теста) — самый крупный из четырёх: `diff()` для всех восьми
   отслеживаемых наборов полей `User` (дисциплины/модули/способности/артефакты/инструмент/
   фамильяр/misc/имена/эффекты) — добавление, удаление, отсутствие изменений при простой
   перестановке порядка (сортировка перед сравнением), особый случай `effects == null`
   эквивалентен `effects == emptyList()`. Плюс `formatMessage()` для всех `ChangeType` (включая
   особую формулировку для `ChangeType.ADDED/REMOVED` с `fieldName == "Эффект"`, плейсхолдер
   «не указан» для `null` при `CHANGED`, и обрезание значения >30 символов). Модели `User`/
   `NamedEntity`/`Ability`/`ShortArtifact`/`Effect`/`AuraType` — чистый Kotlin/Gson без
   Android-зависимостей, фикстуры строятся напрямую в тесте.

Итого 46 новых тестов в 4 файлах (в дополнение к 32 из сессии 44 — суммарно 78 новых +
1 boilerplate `ExampleUnitTest`). Backlog юнит-тестов из сессии 44 теперь пуст: все
предложенные тогда кандидаты (`ProfileDiffer`, `TerminalCommandManager`,
`TerminalVisualEffects`, `TimePickerHelper`, `NoiseHelper`) разобраны — `TerminalVisualEffects`
сознательно пропущен целиком (весь класс работает с `View`/`Activity`/`Vibrator`, чистой логики
для юнит-теста в нём нет).

### Проверка

- `./gradlew :app:testDebugUnitTest --offline --rerun-tasks` — BUILD SUCCESSFUL, все 4 новых
  файла зелёные без единого failure/error (проверил по XML-отчётам: NoiseHelperTest 5/5,
  TimePickerHelperTest 9/9, TerminalCommandManagerTest 10/10, ProfileDifferTest 22/22; плюс
  старые 4 файла сессии 44 и `ExampleUnitTest` тоже зелёные).
- `./gradlew :app:assembleDebug --offline` — BUILD SUCCESSFUL, 40/40 up-to-date (production-код
  не менялся, APK не пересобирался).
- Живую проверку на эмуляторе не делал — как и в сессии 44, main source set не тронут.
- RAG-переиндексация Shift выполнена (`rag_index.py --only Shift`) — 254 файла, 53 новых
  embedding'а.

### Backlog на следующую ночь

- Направление юнит-тестов для чистых хелперов исчерпано в объёме, который просматривался за
  сессии 44–45. Если продолжать дальше — стоит внимательнее посмотреть на `NoiseEffectManager`/
  `NoiseManager` (крупные, 22K/11K, вероятно есть сетевые вызовы вперемешку с чистой логикой —
  нужно сначала аккуратно разделить, что тестируется без `Context`, прежде чем писать тесты) и
  на `AuraCleanupManager`/`AuraEditorActivity` вспомогательные функции сверх того, что уже
  покрыто в сессии 44.
- `ArtifactScannerActivity`/`AuraScannerActivity` (`IntentIntegrator` → `ActivityResultLauncher`)
  — по-прежнему единственный кандидат из набора deprecated Activity Result API warning-ов,
  нужна живая проверка камерой (см. сессии 39–44 — back-камера эмулятора `virtualscene`,
  тест требует GUI Extended Controls).
- `ShiftApplication.isLocationServiceRunning()` (`getRunningServices`) — требует переработки
  логики, надёжность-критичный код, не точечный фикс (см. сессии 39–43).
- `AuraEditorActivity.kt` (4 похожих CRUD-блока), `EkatMaps.showCreatePointDialog` (~260 строк)
  — сознательно не тронуты, риск непропорционален ночной сессии.
- Doze/заблокированный экран — по-прежнему нужна ручная 30–60 мин проверка человеком.
- Диф пока не закоммичен: сегодняшние новые файлы (`app/src/test/java/bas/app/shift/helpers/
  {NoiseHelperTest,TimePickerHelperTest,TerminalCommandManagerTest,ProfileDifferTest}.kt`)
  поверх незакоммиченного диффа сессий 40–44 (5 изменённых production-файлов +
  4 тестовых файла сессии 44) — итого 5 изменённых + 8 новых файлов в рабочем дереве, владелец
  коммитит сам.

**ЗАВЕРШИЛ:** 2026-08-10 07:20 — добавил ещё 46 юнит-тестов в 4 новых файлах
(`app/src/test/java/bas/app/shift/helpers/{NoiseHelperTest,TimePickerHelperTest,
TerminalCommandManagerTest,ProfileDifferTest}.kt`), закрыв весь backlog чистых хелперов,
намеченный сессией 44. Ни одна строка production-кода не тронута — риск нулевой. Самый ценный
файл — `ProfileDifferTest` (22 теста): фиксирует поведение сравнения профилей игрока (диффы
дисциплин/модулей/способностей/артефактов/эффектов и прочих полей + форматирование сообщений
об изменениях для уведомлений), включая не самые очевидные детали текущей реализации (`null`
эквивалентен пустому списку для эффектов, сортировка перед сравнением делает порядок
неважным, обрезание длинных значений >30 символов в тексте уведомления). `TerminalCommandManagerTest`
также фиксирует уже когда-то исправленный баг матчинга команд (точное совпадение по первому
токену, а не `startsWith`) — если кто-то случайно вернёт `startsWith`, тест сразу покраснеет.
`testDebugUnitTest --rerun-tasks` и `assembleDebug --offline` оба зелёные. Живую проверку на
эмуляторе не делал — APK не пересобирался. RAG переиндексирован. Backlog юнит-тестов для
хелперов исчерпан в разумном объёме; на следующую ночь из новых направлений — либо аккуратный
разбор `NoiseEffectManager`/`NoiseManager` на чистую/сетевую части перед тестированием, либо
возврат к ранее отложенным пунктам (zxing QR-сканеры, `getRunningServices`). Ничего не
закоммичено.

## Сессия 46 — 2026-08-10

**НАЧАЛ:** 2026-08-10 07:24 — сессия 45 завершилась в 07:20 («ЗАВЕРШИЛ», не «в работе»), с
её отметки прошло ~4 минуты, но статус явно финальный — гонки нет. Дерево до старта: те же
незакоммиченные production-диффы сессий 40–43 (`EkatMaps.kt`, `NotificationDetailActivity.kt`,
`AndroidManifest.xml`, `ProfileFragment.kt`, `MessagesChatActivity.kt`) + 8 тестовых файлов
в `app/src/test/java/bas/app/shift/helpers/` (сессии 44–45). Взял направление, намеченное
самой сессией 45: аккуратно вынести чистую часть `NoiseEffectManager`/`NoiseManager` перед
тестированием.

### Что сделал

Точечно выделил две чистые (без I/O) функции из сетевых менеджеров шума в
[`helpers/NoiseHelper.kt`](../app/src/main/java/bas/app/shift/helpers/NoiseHelper.kt),
сохранив прежнее поведение 1-в-1:

- `NoiseHelper.thresholdsCrossed(oldLevel, newLevel): List<Int>` — какие пороги эффектов
  (3/4/5) пройдены при изменении уровня шума. Раньше это было три `if` прямо в
  [`NoiseEffectManager.checkAndApplyNoiseEffects`](../app/src/main/java/bas/app/shift/helpers/NoiseEffectManager.kt)
  вперемешку с сетевыми вызовами `checkAndApplyLevelNEffect`. Теперь список порогов считается
  чистой функцией, а `checkAndApplyNoiseEffects` только проверяет `N in crossed`.
- `NoiseHelper.calculateNoiseSplit(delta, hasProxyEffect, hasCrossLinkEffect): NoiseSplit` —
  как дельта шума делится между самим пользователем, Proxy-узлом и Cross-Link партнёром
  (каждый активный эффект отщипывает половину от остатка). Раньше арифметика была размазана
  по [`NoiseManager.adjustNoise`](../app/src/main/java/bas/app/shift/helpers/NoiseManager.kt)
  вперемешку с сетевыми вызовами (`adjustNoiseForUser`, `findUserByName`). Теперь
  `adjustNoise` считает `split` одним вызовом чистой функции и дальше только решает, кому и
  когда отправлять запросы — асинхронный путь поиска Cross-Link партнёра по имени
  (`findUserByName`) сохранён без изменений, включая edge case «партнёр не найден — доля
  возвращается пользователю» и «имя партнёра не распознано — Proxy‑часть всё равно уходит,
  Cross‑Link — нет».
- 9 новых тестов в `app/src/test/java/bas/app/shift/helpers/NoiseHelperTest.kt` (было 5,
  стало 14): `thresholdsCrossed` — большой скачок 0→5 (регрессия старого бага с единственной
  веткой `when`), маленький шаг, отсутствие роста, падение уровня; `calculateNoiseSplit` —
  без эффектов, только Proxy, только Cross-Link, оба эффекта сразу (четверть каждому),
  отрицательная и нулевая дельта (снижение шума не делится).

### Проверка

- `assembleDebug --offline` — зелёо (exit 0).
- `testDebugUnitTest --rerun-tasks` — весь набор зелёный, 88 тестов всего (14 в
  `NoiseHelperTest`, было 5).
- `compileDebugKotlin --rerun-tasks --offline` — без предупреждений компилятора по
  изменённым файлам.
- Живая проверка на эмуляторе (`emulator-5554`): собрал и переустановил APK, запустил
  приложение под уже залогиненным пользователем — падений нет (`FATAL`/`AndroidRuntime`
  не встречались за всю сессию). Открыл `C-терминал` — экран рендерится, `NoiseManager`
  реально стучится на сервер (`fetchCurrentNoise`, периодика раз в минуту), получает
  `0.0/Global 0.00` и не падает — значит переписанный `NoiseManager` компилируется и
  работает в реальном рантайме. Попытка вручную набрать команду через `adb shell input
  text` не задалась (эмулятор не принимал фокус на `EditText`/софт-клавиатуру — похоже,
  специфика этого AVD, не связано с моими правками), так что асинхронный путь
  `adjustNoise`/Cross-Link `findUserByName` вживую не прогнан — но он не тронут по сути
  (структура вызовов идентична, поменялась только арифметика деления, которая теперь
  покрыта юнит-тестами построчно).
- RAG переиндексирован (`rag_index.py --only Shift`).

### Дальше

- Живая проверка `adjustNoise` (Proxy/Cross-Link) на реальных командах терминала —
  стоит сделать вручную (владельцу) или в сессии, где получится завести soft-keyboard
  ввод текста на эмуляторе; логика теперь фиксирована тестами, но end-to-end путь с
  реальными сетевыми ответами сервера не проверялся никогда.
- Оставшиеся кандидаты без изменений: `ArtifactScannerActivity`/`AuraScannerActivity`
  (`IntentIntegrator` → `ActivityResultLauncher`, нужна живая проверка камерой),
  `ShiftApplication.isLocationServiceRunning()` (`getRunningServices`, надёжность-критичный
  код), `AuraEditorActivity.kt`/`EkatMaps.showCreatePointDialog` (крупные CRUD-блоки,
  риск непропорционален ночной сессии), Doze-проверка (нужен человек).
- `NoiseEffectManager`/`NoiseManager` остальная часть (сетевые ветки, `refreshUserProfile`,
  создание эффектов/меток ауры) осознанно не трогалась — это I/O, не под юнит-тесты без
  моков сети, а точечных чистых кусков внутри уже не осталось.

**ЗАВЕРШИЛ:** 2026-08-10 07:33 — вынес 2 чистые функции (`thresholdsCrossed`,
`calculateNoiseSplit`) из `NoiseEffectManager`/`NoiseManager` в `NoiseHelper.kt`, добавил
9 юнит-тестов (14 всего в файле, 88 в сумме по проекту), поведение сохранено 1-в-1. Сборка и
полный набор тестов зелёные, живой смоук на эмуляторе без падений (реальный сетевой вызов
`NoiseManager.fetchCurrentNoise` подтверждён логами). Изменены 2 production-файла
(`NoiseEffectManager.kt`, `NoiseManager.kt`) + `NoiseHelper.kt`, поверх незакоммиченного
диффа сессий 40–43 (5 файлов) и тестов сессий 44–45 (8 файлов) — итого 8 изменённых +
9 новых файлов в рабочем дереве, ничего не закоммичено, владелец коммитит сам.

---

## Сессия 47 — 2026-08-10

**НАЧАЛ:** 2026-08-10 07:44 — сессия 46 завершилась в 07:33 («ЗАВЕРШИЛ»), гонки нет.
`git status`/`git diff --stat` совпадают с записью сессии 46 (8 изменённых + тестовые файлы,
ничего не закоммичено). Baseline `assembleDebug --offline` и `testDebugUnitTest` — зелёные
(92 теста) перед началом правок.

### Что сделал

**1. Проверил вживую блокер сессий 45–46 (soft-keyboard в эмуляторе)** — оказалось, это не
баг проекта, а специфика AVD: `secure show_ime_with_hard_keyboard` не влиял, но простой тап
по полю ввода терминала перед `adb shell input text` даёт фокус и рабочую клавиатуру
(`HELP` успешно набралась и отправилась, ответ пришёл и отрендерился). Раньше сессии
ошибочно считали ввод недоступным — теперь известно, что он работает, просто требует тапа
по `EditText` перед вводом.

**Сознательно НЕ стал** гонять `SHIFT.PROXY.DEPLOY`/`CROSS.LINK` вживую — это реальные
`POST`-запросы к продакшен-серверу `shift96.ru` на живом аккаунте `Bas`: разворачивание
Proxy-узла и Cross-Link — эффекты с реальной 24-часовой длительностью и одноразовым гейтом
(«узел уже развёрнут» при повторной попытке). Ночная автономная сессия не должна необратимо
трогать состояние настоящего квеста без владельца. Прогнал только безопасные команды без
побочных эффектов на сервере сверх обычного (`HELP`, `UTILS.GLOBAL_NOIZE` — обе не пишут
шум, `UTILS.GLOBAL_NOIZE` только читает `GET`).

**2. Точечный рефакторинг** — вынес `TerminalActivity.shouldSkipCommand` (приватная чистая
функция: решает, для каких команд не дублировать ответ в MG-чат) в
[`helpers/TerminalCommandManager.kt`](../app/src/main/java/bas/app/shift/helpers/TerminalCommandManager.kt)
как публичную `shouldSkipMgNotification(command: String): Boolean` — по образцу уже
выделенных `NoiseHelper`/`TerminalCommandManager.findCommand`. Логика не менялась ни на
символ (тот же `when` с `startsWith`), только переехала в объект, который уже тестируется
(`TerminalCommandManagerTest.kt`), и вызов в
[`TerminalActivity.sendToMg()`](../app/src/main/java/bas/app/shift/ui/terminal/TerminalActivity.kt)
обновлён на `TerminalCommandManager.shouldSkipMgNotification(command)`.
Добавил 4 новых теста в `TerminalCommandManagerTest.kt` (было 10, стало 14): Proxy-команды
пропускаются, `USER.*` пропускаются кроме `USER.FORMAT` (тот единственный из группы USER,
о котором MG всё же нужно уведомлять — сброс шума), `UTILS.*`/`SYSTEM.*` пропускаются,
обычные команды (`CAMERA.FIND`, `CROSS.LINK`, `DEEP_DIVE.END`) не пропускаются.

### Проверка

- `assembleDebug --offline` — зелёно (exit 0) до и после правки.
- `testDebugUnitTest --rerun-tasks --offline` — весь набор зелёный, 92 теста всего (было 88,
  +4 новых в `TerminalCommandManagerTest`).
- Живая проверка на эмуляторе (`emulator-5554`, роль `Bas`, реальный бэкенд `shift96.ru`):
  переустановил APK, открыл `C-терминал`, набрал и отправил `HELP` — ответ пришёл и
  отрисовался построчно, без падений. Затем `UTILS.GLOBAL_NOIZE` — прошёл через
  `TerminalCommandManager.findCommand` → `TerminalDeepDiveCommands.handleGlobalNoiseCommand`,
  реальный `GET`-запрос отработал («Текущий уровень глобального шума: 0»), крашей нет
  (`logcat` без `FATAL`/`AndroidRuntime`). Открыл «Чат с МГ» — последнее сообщение от бота
  осталось прежним (`DEEP_DIVE.START`, 07:11), новых «Команда в терминале: …» после
  `HELP`/`UTILS.GLOBAL_NOIZE` не появилось — подтверждает, что рефакторенный
  `shouldSkipMgNotification` действительно не шлёт уведомление для `UTILS.*` (и что `HELP`
  в принципе не вызывает `sendToMg`, как и раньше).
  Замечание не по коду: `adb shell input text` через Gboard иногда подмешивает лишний символ
  (словил ведущую запятую при повторном наборе) — артефакт эмулятора/предиктивного ввода,
  не связан с проектом; терминал корректно показал «Неизвестная команда» без падения.

### Дальше

- Живая проверка `SHIFT.PROXY.DEPLOY`/`CROSS.LINK` (Proxy/Cross-Link ветки `adjustNoise`) —
  технически теперь возможна (клавиатура работает), но осознанно не сделана в автономной
  сессии — мутирует реальное игровое состояние на живом сервере (24ч эффект, одноразовый
  гейт). Стоит сделать владельцу лично или в сессии, где это явно санкционировано.
- Оставшиеся кандидаты без изменений (из сессии 46): `ArtifactScannerActivity`/
  `AuraScannerActivity` (`IntentIntegrator` → `ActivityResultLauncher`, нужна живая проверка
  камерой), `ShiftApplication.isLocationServiceRunning()` (надёжность-критичный код),
  `AuraEditorActivity.kt`/`EkatMaps.showCreatePointDialog` (крупные CRUD-блоки), Doze-проверка
  (нужен человек).
- `TerminalActivity` (553 строк) уже неплохо разгружен предыдущими сессиями
  (`TerminalProxyCommands`/`TerminalUpgradeRebootCommands`/`TerminalDeepDiveCommands`/
  `TerminalCommandManager`/`TerminalHistoryHelper`) — точечных чистых кусков внутри активности
  на сегодня не осталось, дальнейшее вычленение потребует более крупного шага (например,
  `sendToServer`/`processCommand` целиком в отдельный класс), что уже ближе к архитектурному
  изменению, а не точечной правке.

**ЗАВЕРШИЛ:** 2026-08-10 07:56 — вынес `shouldSkipCommand` из `TerminalActivity` в
`TerminalCommandManager.shouldSkipMgNotification` (поведение не менялось), добавил 4 юнит-теста
(92 теста в сумме по проекту). Живьём подтвердил на эмуляторе против реального бэкенда:
терминал печатает и выполняет команды, MG-уведомления корректно пропускаются для
`UTILS.*`/`SYSTEM.*`/`USER.*` (кроме `FORMAT`)/`SHIFT.PROXY*`, крашей нет. Дополнительно
выяснил и задокументировал, что soft-keyboard в эмуляторе работает (нужен тап по полю перед
вводом) — снимает блокер, которым сессии 45–46 объясняли непроверенность Proxy/Cross-Link
путей; сами Proxy/Cross-Link команды сознательно не гонял вживую (реальные мутации на
продакшен-сервере). Изменены 9 production-файлов (добавился `TerminalCommandManager.kt`,
`TerminalActivity.kt` к диффу сессий 40–46) + тестовые файлы, ничего не закоммичено, владелец
коммитит сам.

---

## Сессия 48 — 2026-08-11

**НАЧАЛ:** 2026-08-11 05:04 — сессия 47 завершилась 2026-08-10 07:56 («ЗАВЕРШИЛ»), с тех пор
прошло больше суток — гонки нет. `git status` совпал с записью сессии 47 (10 изменённых
production-файлов + `analysis/09-nightly-progress.md` + тестовые файлы сессий 44–45 +
`analysis/screenshots/`, ничего не закоммичено; последний реальный коммит в истории —
`69cebcf`, 2026-07-31, не связан с ночным диффом). Baseline `assembleDebug --offline` —
зелёный до начала правок.

### Что сделал

Единственный оставшийся кандидат из набора «deprecated Activity Result API», который сессии
39–47 неоднократно откладывали («нужна живая проверка камерой») — миграция
`ArtifactScannerActivity`/`AuraScannerActivity` с устаревшего `IntentIntegrator`/
`onActivityResult` (zxing) на современный `registerForActivityResult`:

- **[`ui/AuraScannerActivity.kt`](../app/src/main/java/bas/app/shift/ui/AuraScannerActivity.kt)**
  и **[`ui/ArtifactScannerActivity.kt`](../app/src/main/java/bas/app/shift/ui/ArtifactScannerActivity.kt)** —
  `IntentIntegrator(this)` → `ScanOptions()` + `registerForActivityResult(ScanContract())`
  (класс `ScanContract`/`ScanOptions`/`ScanIntentResult` уже был доступен офлайн в уже
  подключённой библиотеке `com.journeyapps:zxing-android-embedded:4.3.0`, новая зависимость не
  потребовалась — проверил `javap` по кешированному jar перед правкой, что сигнатуры методов
  совпадают с прежними вызовами `IntentIntegrator` 1-в-1: `setDesiredBarcodeFormats`,
  `setPrompt`, `setCameraId`, `setBeepEnabled`, `setBarcodeImageEnabled`,
  `setOrientationLocked`, `setCaptureActivity`, `setTimeout`, `setTorchEnabled`). Ручной
  `ActivityCompat.requestPermissions`/`onRequestPermissionsResult` (запрос разрешения камеры)
  тоже переведён на `registerForActivityResult(ActivityResultContracts.RequestPermission())` —
  раз уж класс всё равно правился, а старый callback-метод сцеплен с тем же
  `REQUEST_CAMERA_PERMISSION`. `CustomScannerActivity` (наследник `CaptureActivity`,
  используется как `setCaptureActivity`/кастомный фокус по тапу) не тронут — он не зависит от
  `IntentIntegrator`, только указывается по имени класса.
  Убраны неиспользуемые импорты (`ActivityCompat`), поведение (тексты Toast, коды форматов,
  параметры сканера) сохранено 1-в-1.

### Проверка

- `compileDebugKotlin --rerun-tasks --offline` — все предупреждения `IntentIntegrator`/
  `IntentResult is deprecated` (были в обоих файлах, 9+5 строк) пропали; остался только
  ожидаемый и сознательно нетронутый `ShiftApplication.getRunningServices` (см. бэклог).
- `assembleDebug --offline` — зелёно (exit 0).
- `testDebugUnitTest --offline --rerun-tasks` — весь набор зелёный (92 теста, без изменений —
  эти классы не покрыты юнитами, только сборкой/эмулятором).
- Живая проверка на эмуляторе (`emulator-5554`, роль `Bas`, реальный APK переустановлен):
  - `ПРОСМОТР АУРЫ` → `AuraScannerActivity` → `startScanner()` → `CustomScannerActivity`
    (камера уже была разрешена) — открылся без крашей, `back` → корректный колбэк
    `ScanContract` с `contents == null`, тост «Сканирование отменено», `finish()`,
    возврат на главный экран. Логкат чист (`FATAL`/`AndroidRuntime` не встречались).
  - `ПОЗНАТЬ АРТЕФАКТ` → `ArtifactScannerActivity` → кнопка «Сканировать штрих-код» →
    `CustomScannerActivity`, тот же cancel-путь через `back` — тост, `finish()`, без крашей.
  - Отдельно проверил именно новый код permission-launcher: `pm revoke … CAMERA`, повторный
    заход на «Сканировать штрих-код» → системный диалог `Allow Shift to take pictures and
    record video?` (значит `registerForActivityResult(RequestPermission())` действительно
    вызывается), тап «While using the app» → колбэк `granted == true` → `startScanner()`
    сработал автоматически → `CustomScannerActivity` открылся без промежуточных экранов и без
    крашей — путь «запросили разрешение → получили → сразу сканер» идентичен старому
    поведению `onRequestPermissionsResult`.
  - Реальное сканирование штрих-кода/QR (успешный путь до `fetchArtifact`/`fetchAura`) не
    гонял — требует физического QR/штрих-кода в кадре виртуальной камеры эмулятора
    (`virtualscene` backdrop), что сессии 39–44 тоже не осилили; но код успешного пути
    (`result.contents`/`ScanIntentResult.getContents()`) не менялся по сути — раньше брался
    из `IntentResult.contents`, теперь из `ScanIntentResult.contents`, то же поле с тем же
    типом (`String?`), маппинг 1-в-1.
- RAG переиндексирован (`rag_index.py --only Shift`, 254 файла/1724 чанка).

### Дальше

- Единственный оставшийся пробел по этому пункту — живой прогон успешного сканирования
  (реальный QR/штрих-код в кадре), который требует настройки `virtualscene` backdrop в
  Extended Controls эмулятора; можно попробовать в следующей сессии или руками владельцем —
  риск минимален, т.к. код чтения результата не менялся.
- Оставшиеся кандидаты без изменений (из сессий 46–47): `ShiftApplication.
  isLocationServiceRunning()` (`getRunningServices`, надёжность-критичный код, не точечный
  фикс), `AuraEditorActivity.kt`/`EkatMaps.showCreatePointDialog` (крупные CRUD-блоки, риск
  непропорционален ночной сессии), Doze/заблокированный экран (нужна живая 30–60 мин проверка
  человеком), живой прогон `SHIFT.PROXY.DEPLOY`/`CROSS.LINK` (реальные мутации на
  продакшен-сервере, сознательно не гоняется автономными сессиями).
- Низкорисковый бэклог deprecated-API исчерпан — из явных находок original-аудита (R1–R13) и
  «что осталось за кадром» (08-changes-applied.md) на сегодня без изменений остались только
  пункты, требующие либо архитектурного риска, либо живого человека. Дальнейшие сессии могут
  либо поискать новые точечные находки (напр. `!!`-паттерны выборочно, дубли кода), либо
  сосредоточиться на живых проверках вместо новых правок.

**ЗАВЕРШИЛ:** 2026-08-11 05:20 — мигрировал `AuraScannerActivity`/`ArtifactScannerActivity` с
устаревшего `IntentIntegrator`/`onActivityResult` на `registerForActivityResult(ScanContract())`
+ `ActivityResultContracts.RequestPermission()` (zxing-android-embedded 4.3.0, уже в зависимостях,
новых зависимостей не добавлено). Поведение сохранено 1-в-1, все deprecation-предупреждения
компилятора по этим двум файлам исчезли. Сборка и полный набор тестов зелёные. Живая проверка на
эмуляторе против реального бэкенда: оба сканера открываются без крашей, cancel-путь (back →
тост → finish) и новый permission-request-путь (revoke → диалог ОС → grant → автозапуск сканера)
оба подтверждены логкатом без `FATAL`/`AndroidRuntime`. Не проверено вживую только успешное
сканирование реального QR/штрих-кода (нужен virtualscene backdrop в эмуляторе) — код этого пути
не менялся по сути. Изменены 2 production-файла поверх незакоммиченного диффа сессий 40–47
(10 файлов) + тестовых файлов сессий 44–45, ничего не закоммичено, владелец коммитит сам.

---

## Сессия 49 — 2026-08-11

**НАЧАЛ:** 2026-08-11 05:24 — сессия 48 завершилась в 05:20 («ЗАВЕРШИЛ», не «в работе») — гонки
нет, хотя разрыв всего 4 минуты. `git status` совпал с записью сессии 48 (10 изменённых
production-файлов + `analysis/09-nightly-progress.md` + тестовые файлы сессий 44–45 +
`analysis/screenshots/`, ничего не закоммичено). Baseline `assembleDebug --offline` — зелёный
(exit 0) до начала правок.

### Что сделал

Проверил кандидатов из бэклога сессии 48 (RxJava-рудимент в `LocationService`/`EkatMaps`,
`!!`-паттерны, TODO/FIXME, компиляторные deprecation-предупреждения) — RxJava уже нигде не
используется (видимо, убрана в более ранних сессиях, `build.gradle` без `rxjava`/`rxandroid`),
TODO/FIXME в исходниках нет, компиляторных предупреждений не осталось, кроме сознательно
пропускаемого `ShiftApplication.getRunningServices`. Вместо этого нашёл новую точечную находку —
дублирование кода:

**Вынес проверку роли МГ (`userId.startsWith("MG_")`) в `UserRoles.isMg(userId: String?)`** —
паттерн повторялся 12 раз в 4 файлах с идентичной семантикой (случай-чувствительное сравнение
префикса, без прочих условий):
- [`helpers/UserRoles.kt`](../app/src/main/java/bas/app/shift/helpers/UserRoles.kt) (новый файл,
  по образцу уже существующих `DisplayNames`/`NoiseHelper` — маленький stateless-объект с одной
  чистой функцией).
- [`ui/MessagesChatActivity.kt`](../app/src/main/java/bas/app/shift/ui/MessagesChatActivity.kt) —
  8 мест (инициализация, лог, видимость кнопок отправки/прочитано, long-click на сообщении,
  выбор API-эндпоинта загрузки, гейт диалога выбора дисциплины при отправке).
- [`ui/adapters/MessagesAdapter.kt`](../app/src/main/java/bas/app/shift/ui/adapters/MessagesAdapter.kt) —
  3 места (подсветка «своих» сообщений, отображение имени отправителя, скрытие статуса
  прочтения).
- [`services/NewMessagesChecker.kt`](../app/src/main/java/bas/app/shift/services/NewMessagesChecker.kt) —
  2 места (выбор API для поллинга, фильтр уведомлений от МГ).
- [`MainActivity.kt`](../app/src/main/java/bas/app/shift/MainActivity.kt) — 1 место (кнопка «Чат
  с МГ»: список чатов для МГ vs прямой чат для игрока); было `userId?.startsWith("MG_") == true`
  на нестрого-нулевом `String` из `UserPrefsHelper.getUserId` — заменено на `UserRoles.isMg`,
  которая принимает `String?`, поведение то же.
Заодно в [`helpers/UserPrefsHelper.kt`](../app/src/main/java/bas/app/shift/helpers/UserPrefsHelper.kt)
убрал неиспользуемый импорт `android.system.Os.remove` (не вызывался нигде в файле, случайный
мёртвый импорт).
Добавил [`UserRolesTest.kt`](../app/src/test/java/bas/app/shift/helpers/UserRolesTest.kt) —
5 тестов (MG-префикс/обычный id/null/пустая строка/префикс не в начале строки).

### Проверка

- `assembleDebug --offline` — зелёно (exit 0) до и после правки.
- `testDebugUnitTest --offline --rerun-tasks` — весь набор зелёный, 97 тестов всего (было 92,
  +5 новых в `UserRolesTest`).
- `grep -rn 'startsWith("MG_")'` по `app/src/main/java` после правки — ни одного совпадения вне
  комментария в самом `UserRoles.kt` (все места действительно переведены).
- Живая проверка на эмуляторе (`emulator-5554`, реальный APK переустановлен, роль `Bas` уже была
  залогинена с прошлых сессий): открыл главный экран → «ЧАТ С МГ» → корректно попал напрямую в
  `MessagesChatActivity` (не в список чатов — значит `UserRoles.isMg("Bas")` в `MainActivity`
  вернул `false`, как и раньше). История сообщений загрузилась и отрисовалась без крашей.
  По `logcat` подтверждено, что рефакторенная логика реально исполнилась с реальными данными
  с продакшен-бэкенда: `MessagesChat: userId starts with MG_: false`; в `MessagesAdapter.bind`
  для сообщений с `senderId=Bas` — `isFromMG=false, isCurrentUser=true, shouldHighlightAsOwn=true,
  senderName=Вы`; для `senderId=MG_Bas` — `isFromMG=true`; для `senderId=bas` (нижний регистр,
  другой отправитель) — `isFromMG=false` (case-sensitive сравнение сохранено 1-в-1). `logcat`
  чист (`grep -iE "FATAL EXCEPTION|AndroidRuntime"` — пусто) за всю сессию проверки.
  Роль МГ (`UserRoles.isMg(...) == true` ветка в `MessagesChatActivity`/`MessagesAdapter`) живьём
  в этот раз не гонял — потребовал бы перелогина на `MG_Bas` на общем аккаунте; семантика этой
  ветки идентична непроверенной раньше (`if (userId.startsWith("MG_"))` → `if
  (UserRoles.isMg(userId))`), и юнит-тесты покрывают обе ветки `isMg` напрямую.
- RAG переиндексирован (`rag_index.py --only Shift`, 256 файлов/1739 чанков).

### Дальше

- Низкорисковый бэклог из явных находок (deprecated API, RxJava, TODO, компиляторные
  предупреждения) на сегодня исчерпан ещё раз. Следующей сессии стоит либо поискать новые
  точечные дубли кода (напр. похожий паттерн повторного вычисления display-имени вне уже
  вынесенного `DisplayNames`, если такой остался; повторные проверки `NetworkErrors` не через
  helper), либо заняться живыми проверками из старого бэклога: `SHIFT.PROXY.DEPLOY`/`CROSS.LINK`
  (сознательно не гоняется автономно — мутация продакшен-состояния), Doze/заблокированный экран
  (нужен человек), успешное сканирование реального QR/штрих-кода (нужен virtualscene backdrop).
- Живая проверка ветки МГ в `UserRoles.isMg` (перелогин на `MG_Bas`) — можно сделать в сессии,
  где это явно уместно, риска для правки это не создаёт (тесты уже покрывают обе ветки).
- **Владельцу по-прежнему стоит закоммитить рабочий диф** — растёт с каждой сессией (11
  изменённых production-файлов + `UserRoles.kt` новый + тестовые файлы, ничего не закоммичено).

**ЗАВЕРШИЛ:** 2026-08-11 05:34 — вынес повторявшийся 12 раз в 4 файлах паттерн
`userId.startsWith("MG_")` в `UserRoles.isMg(userId: String?)` (новый helper по образцу
`DisplayNames`), обновил все места вызова (`MessagesChatActivity`, `MessagesAdapter`,
`NewMessagesChecker`, `MainActivity`), добавил 5 юнит-тестов (97 тестов в сумме по проекту).
Заодно убрал мёртвый импорт в `UserPrefsHelper.kt`. Поведение сохранено 1-в-1 (case-sensitive
сравнение префикса, без прочих условий). Сборка и полный набор тестов зелёные. Живая проверка на
эмуляторе против реального бэкенда подтвердила через logcat, что рефакторенная логика верно
разделяет игрока/МГ и корректно подсвечивает/маршрутизирует сообщения на реальных данных, крашей
нет. Ветка «МГ» проверена только юнит-тестами (живой перелогин не делал — не нужен для риска
этой правки). Изменения поверх незакоммиченного диффа сессий 40–48: было 10 изменённых
production-файлов, стало 12 (+ `UserRoles.kt` новый) + тестовые файлы сессий 44–45, 49, ничего не
закоммичено, владелец коммитит сам.

---

## Сессия 50 — 2026-08-11

**НАЧАЛ:** 2026-08-11 05:44 — сессия 49 завершилась в 05:34 («ЗАВЕРШИЛ»), разрыв 10 минут — гонки
нет. `git status` совпал с записью сессии 49 (12 изменённых production-файлов + `UserRoles.kt`
новый + тестовые файлы, ничего не закоммичено). Baseline `assembleDebug --offline` — зелёный
(exit 0) до начала правок.

### Что сделал

Проверил кандидатов из бэклога сессии 49 (RxJava, `!!`-паттерны, TODO/FIXME, компиляторные
предупреждения) — всё по-прежнему пусто/непригодно для точечного фикса, ничего нового не
появилось. Вместо этого нашёл новую точечную находку — дублирование форматирования времени
сообщений и ещё одно пропущенное место склейки имён (тот же класс дублей, что чинили в Wave 11 /
сессии 49, но конкретно этот экземпляр туда не попал):

**Вынес форматирование времени сообщений `"yyyy-MM-dd HH:mm:ss" → "HH:mm"` в
`DateTimeHelper.formatMessageTime(createdAt: String)`** (рядом с уже существующим
`formatExpireAt`, та же логика try/catch — при ошибке парсинга возвращает исходную строку как
есть, `date ?: Date()` фолбэк при null-результате парсинга сохранён 1-в-1):
- [`ui/adapters/MessagesAdapter.kt`](../app/src/main/java/bas/app/shift/ui/adapters/MessagesAdapter.kt) —
  приватный `formatTime()` был идентичной копией, теперь однострочная делегация в
  `DateTimeHelper.formatMessageTime`.
- [`ui/adapters/ChatsAdapter.kt`](../app/src/main/java/bas/app/shift/ui/adapters/ChatsAdapter.kt) —
  та же логика была заинлайнена внутри `bind()` (не вынесена даже в приватный метод) — заменена
  на вызов `DateTimeHelper.formatMessageTime`.

Заодно в том же `ChatsAdapter.bind()` заметил пропущенное в Wave 11 место склейки имён —
самодельный `when`-блок "имя персонажа / имя игрока" (`interlocutorName`/`interlocutorPlayerName`
→ `chat.interlocutor` как fallback) с семантикой, идентичной уже существующему
`DisplayNames.combine(character, player, fallback)`. Заменил на прямой вызов. Убрал ставшие
неиспользуемыми импорты `SimpleDateFormat`/`java.util.*` в обоих адаптерах.
Добавил [`DateTimeHelperTest.kt`](../app/src/test/java/bas/app/shift/helpers/DateTimeHelperTest.kt) —
4 теста (валидный timestamp, полночь, непарсящаяся строка → строка как есть, пустая строка).

### Проверка

- `assembleDebug --offline` — зелёно (exit 0) до и после правки.
- `compileDebugKotlin --offline --rerun-tasks` — без новых предупреждений (только сознательно
  пропускаемый `ShiftApplication.getRunningServices`).
- `testDebugUnitTest --offline --rerun-tasks` — весь набор зелёный, 101 тест всего (было 97,
  +4 новых в `DateTimeHelperTest`).
- Живая проверка на эмуляторе (`emulator-5554`, реальный APK переустановлен). Роль `Bas` не может
  открыть список чатов (`ChatsListActivity` только для МГ), поэтому временно переключил
  `current_user_id` на `MG_Bas` через `run-as`/`sed` в `shared_prefs/user_prefs.xml` (та же
  техника, что и в прошлых сессиях), перезапустил процесс, проверил, вернул обратно на `Bas` и
  перезапустил снова в конце — устройство осталось в исходном состоянии.
  - `ЧАТ С МГ` (роль MG_Bas) → `ChatsListActivity` открылся без крашей, список реальных чатов с
    продакшен-бэкенда отрисовался: имена в формате «Персонаж / Игрок»
    (`DisplayNames.combine` через `ChatsAdapter`) и время в `HH:mm`
    (`DateTimeHelper.formatMessageTime`) — оба видны на скриншоте, соответствуют ожиданиям.
  - Тап по чату → `MessagesChatActivity`/`MessagesAdapter` — история сообщений отрисовалась,
    время каждого сообщения в `HH:mm` (тот же `formatMessageTime` теперь используется в обоих
    адаптерах), подсветка своих/чужих сообщений не пострадала.
  - `logcat` (`grep -iE "FATAL EXCEPTION|AndroidRuntime"`) пуст за всю сессию проверки, включая
    возврат роли на `Bas` и повторный запуск `MainActivity`.
  - Побочно потыкал `ОТКРЫТЬ КАРТУ` (роль `Bas`) — точки с бэкенда подтянулись (`GET
    /api_geo/api/v1/points?user_id=Bas` → 200 в логе), но стабильный скриншот самой карты не
    получил (второй тап, видимо, попал по элементу, закрывшему экран обратно на главное меню) —
    это НЕ связано с правками этой сессии (карту не трогал), крашей тоже не было; попытка
    проверить `onMarkerClick` из бэклога осталась незавершённой, можно повторить в следующей
    сессии внимательнее (по одному тапу за раз, со скриншотом между).
- RAG переиндексирован (`rag_index.py --only Shift`, 257 файлов/1750 чанков).

### Дальше

- Повторить попытку живой проверки `onMarkerClick` на карте (тап по маркеру → диалог информации) —
  в этот раз прервалось на этапе навигации, не на самой проверке; нужно по одному действию со
  скриншотом между шагами.
- Низкорисковый бэклог явных дублей снова возможно исчерпан — при следующем заходе стоит либо
  поискать ещё дубли (напр. `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", ...)` в `EkatMaps.kt:788`
  используется только один раз, не дубль — проверено, не трогать), либо переключиться на живые
  проверки (Doze/заблокированный экран нужен человек; `SHIFT.PROXY.DEPLOY`/`CROSS.LINK` живой
  прогон сознательно не гоняется автономно).
- **Владельцу по-прежнему стоит закоммитить рабочий диф** — растёт с каждой сессией (14
  изменённых production-файлов + `UserRoles.kt` новый + тестовые файлы, ничего не закоммичено).

**ЗАВЕРШИЛ:** 2026-08-11 06:00 — вынес дублировавшуюся в `MessagesAdapter`/`ChatsAdapter` логику
форматирования времени сообщений (`"yyyy-MM-dd HH:mm:ss" → "HH:mm"`) в
`DateTimeHelper.formatMessageTime`, заодно заменил пропущенный в Wave 11 инлайн-дубль склейки
имён в `ChatsAdapter` на существующий `DisplayNames.combine`. Добавил 4 юнит-теста (101 тест в
сумме). Сборка и полный набор тестов зелёные, компиляторных предупреждений не прибавилось. Живая
проверка на эмуляторе против реального бэкенда (с временным переключением роли на `MG_Bas` и
возвратом обратно на `Bas`) подтвердила, что список чатов и история сообщений отрисовываются
корректно (имена и время в ожидаемом формате) без крашей. Побочная попытка проверить клик по
маркеру карты не довелась до конца (не связано с правками этой сессии, крашей не было) — осталась
в бэклоге. Изменения поверх незакоммиченного диффа сессий 40–49: было 12 изменённых
production-файлов, стало 14 (+ `UserRoles.kt` новый) + тестовые файлы, ничего не закоммичено,
владелец коммитит сам.

---

**НАЧАЛ:** 2026-08-11 06:05 — сессия 49 завершилась в 06:00 («ЗАВЕРШИЛ»), разрыв 5 минут — гонки
нет (не «в работе»). `git status`/`git diff --stat` совпадают с записью сессии 49 (14 изменённых
production-файлов + `UserRoles.kt` новый + тестовые файлы, ничего не закоммичено).
`assembleDebug --offline` перед стартом — exit 0. Бэклог из 08-changes-applied.md по большей части
исчерпан низкорисковыми находками; в конце сессии 49 остался незавершённым один пункт: живая
проверка `onMarkerClick` на карте (тап по маркеру → диалог информации) — это чтение, не мутация
состояния, можно проверить безопасно. Планирую в этой сессии: (1) довести эту живую проверку до
конца по одному тапу за раз со скриншотом между шагами; (2) если останется время — точечный поиск
новых низкорисковых находок (RAG-поиск дублей/мёртвого кода) поверх уже вычищенного.

**ЗАВЕРШИЛ:** 2026-08-11 06:15 — код в этой сессии не менял (только живая проверка на эмуляторе),
довёл до конца хвост сессии 49: живую проверку `onMarkerClick` на карте.

- Роль `Bas` (обычный игрок), карта открыта, точный тап по собственному маркеру геолокации
  (`HUE_AZURE`, title `"Ваше местоположение"`) — `EkatMaps.setupRegularUserMapHandlers` сработал
  (лог `нажатие по маркеру` → `handleMarkerClick`), корректно не нашёл точку для этого маркера
  (`pointsRenderer.findPointForMarker` не находит его — он не часть `pointsOfInterest`) и тихо
  вышел без диалога — это ожидаемое поведение, не баг.
- Разобрался, почему на карте обычного игрока не видно пинов реальных точек (только круги):
  `MapPointsRenderer.refreshMarkersForLocation` создаёт маркер точки для НЕ-MG пользователя,
  только когда `distance <= point.radius` (строки 96-124) — пока игрок физически не внутри
  круга, маркера просто нет, кликать нечего. А вход в круг триггерит
  `LocationService.onEnterPoint`, который стучится на сервер — то есть безопасно проверить
  `handleMarkerClick` по реальной точке под ролью `Bas` без похода на реальную точку или спуфинга
  GPS (= мутация живого состояния на бэкенде) нельзя. Это архитектурная причина, по которой пункт
  зависал в бэклоге несколько сессий подряд, а не невезение с тапами.
- Обошёл ограничение безопасно: переключился на `MG_Bas` (тот же приём через `run-as`/`sed` в
  `shared_prefs/user_prefs.xml`, что и в прошлых сессиях) — под MG все точки показываются
  безусловно (`isMgUser` ветка в `refreshMarkersForLocation`, без гейта по радиусу), а клик по
  маркеру — чтение, не порождает вход в точку. Тап по реальному пину (`APPROACHING_BITER`,
  "Приближающийся `Фамильяр`", ID `p-68cd6d578fcf3`) → `EkatMaps.showPointInfoDialog()` открылся
  корректно: заголовок, радиус (100.0м), координаты, описание, текст при входе, поле "Аура места"
  и чекбоксы отрисовались с реальными данными точки. **Не нажимал** ни "Сохранить", ни "Удалить
  точку" — это живые продакшен-данные точки, случайное сохранение/удаление было бы необратимой
  мутацией; закрыл системной кнопкой "назад" (не трогает данные, только диалог). Проверил на
  скриншоте после закрытия — карта в исходном состоянии, точка на месте.
- `logcat` (`FATAL EXCEPTION|AndroidRuntime`) пуст за всю сессию, включая оба переключения роли.
- Роль возвращена на `Bas`, процесс перезапущен, устройство осталось в исходном состоянии
  (проверено скриншотом финального главного меню).
- `assembleDebug --offline` до и после сессии — exit 0 оба раза (код не менялся, но так как
  бэклог по коду в основном исчерпан прошлыми сессиями, решил не форсировать правку ради правки —
  см. ниже).
- Посмотрел на кандидата для точечной правки: паттерн `response.body()!!` внутри
  `onResponse`-колбэков после `response.isSuccessful` встречается ~30 раз в ~20 файлах
  (`ProfileEditActivity`, `AuraEditorActivity`, `NoiseManager`, `MainActivity` и др.). Это
  системно единообразный паттерн (Retrofit гарантирует непустое тело при `isSuccessful` для
  ненулевого типа ответа на доверенном своём сервере) — не новая находка, а уже принятый
  во всём проекте стиль; массовая правка ~20 файлов ради `!!` → `?:` была бы недопустимым по
  правилам массовым рефакторингом ради рефакторинга, не решающим реальной проблемы. Не трогал.

### Дальше

- Низкорисковый бэклог по коду (R1–R13, дубли, `NetworkErrors`, god-classes точечно) остаётся
  исчерпанным после ревизии Wave 15 и последующих сессий — новых находок точечным просмотром в
  этот раз не нашлось. Следующей сессии, вероятно, стоит либо новый RAG-заход с другими
  формулировками запроса, либо переключиться на живые проверки, которые ещё не сделаны:
  - Визуально долетать до эффектов шума в терминале (`showNoise`/`applyGlitch`/`showRedScrim`/
    `demonJumpScare`) — нужен реальный уровень шума 2+, сознательно отложено (мутация живого
    состояния).
  - Doze/заблокированный экран — нужен живой человек, 30-60 мин.
- `onMarkerClick` **теперь полностью проверен** (и под `Bas`, и под `MG_Bas`, оба пути живьём,
  без крашей и без мутации живых данных) — можно вычеркнуть из бэклога.
- **Владельцу по-прежнему стоит закоммитить рабочий диф** — не меняется с прошлой сессии: 14
  изменённых production-файлов + `UserRoles.kt` новый + тестовые файлы, ничего не закоммичено.

---

**НАЧАЛ:** 2026-08-11 06:24 — сессия 50 (по счёту после исходного аудита) завершилась в 06:15
(«ЗАВЕРШИЛ»), разрыв 9 минут — гонки нет. `assembleDebug --offline` перед стартом — exit 0.
Проверил свежие компиляторные предупреждения (`compileDebugKotlin --offline --rerun-tasks`) —
пусто, как и раньше. Прошёлся RAG-запросами с другими формулировками (дубли обработки сетевых
ошибок, мёртвый код) — ничего нового не всплыло, весь `onFailure`-код либо уже унифицирован через
`NetworkErrors` (проверил `EffectEditorActivity` — уже полностью на `NetworkErrors`), либо это
фоновые сервисы (`LocationService`, `NewMessagesChecker`) без UI, где логи и так информативны —
трогать не стал. Вместо этого нашёл пробел в тестовом покрытии: `TerminalHistoryHelper`
(вынесен в Wave 9 — буферизация истории терминала с лимитом `MAX_HISTORY_SIZE = 100`) не имел ни
одного теста, хотя содержит чистую (без I/O) логику `appendCommand`/`appendResponse`/`limited` —
именно ту, что чинили ради перфоманса в Wave 9. Пишу тесты на неё.

### Что сделал

Не менял production-код в этой сессии — низкорисковый бэклог по коду остаётся исчерпанным (как и
отмечали сессии 48–49), новых кандидатов на точечную правку RAG-поиском с другими формулировками
не нашлось. Вместо этого закрыл два пробела в тестовом покрытии — обе цели чистые (без Android
`Context`/сети/`View`), поэтому тестируются напрямую JUnit без моков:

- Добавил [`TerminalHistoryHelperTest.kt`](../app/src/test/java/bas/app/shift/helpers/TerminalHistoryHelperTest.kt)
  (6 тестов) — покрывает `TerminalHistoryHelper.appendCommand`/`appendResponse` (вынесены в Wave 9
  как чистая буферизация без I/O): аппенд под лимитом не трогает второй список, ровно на границе
  `MAX_HISTORY_SIZE = 100` ничего не обрезается, за лимитом обрезка со старого конца (`takeLast`)
  и лимиты команд/ответов независимы друг от друга. Раньше этот код (несмотря на то, что чинил
  реальную перфоманс-проблему — O(n) load+save на каждую строку терминала) не имел ни одного
  теста.
- Добавил [`FamiliarDataTest.kt`](../app/src/test/java/bas/app/shift/models/FamiliarDataTest.kt)
  (14 тестов, новый каталог `test/.../models/`) — покрывает `FamiliarData` (`models/Familiar.kt`):
  `getNameById` (известный id, пустая строка → "Нет фамильяра", неизвестный id → сам id как
  фолбэк), `getImageNameById`/`getImageNameByIdWithTime` (суффиксы индекса и `_night`) и
  `isNightTime` (граница окна 2:00 включительно / 10:00 исключительно — как в комментарии кода).
  Это активно используемая игровая логика (иконка/имя фамильяра в `FamiliarActivity`,
  `FamiliarFoundActivity`, `ProfileEditFragment`, `MainActivity`, `LocationNotifications`), тоже
  без единого теста раньше.

### Проверка

- `assembleDebug --offline` — зелёно (exit 0) и до, и после (production-код не менялся).
- `compileDebugKotlin`/`compileTestDebugUnitTestKotlin --offline --rerun-tasks` — без новых
  предупреждений.
- `testDebugUnitTest --offline --rerun-tasks` — весь набор зелёный, 121 тест всего (было 101 на
  старте сессии: +6 `TerminalHistoryHelperTest`, +14 `FamiliarDataTest`).
- Живую проверку на эмуляторе в этот раз не гонял — production-код не менялся, эмулятор поднимать
  не требовалось.
- RAG переиндексирован (`rag_index.py --only Shift`, 259 файлов/1784 чанка).

### Дальше

- Низкорисковый бэклог по коду (правки, а не тесты) по-прежнему исчерпан — три сессии подряд
  (48, 49, эта) точечным просмотром и RAG с разными формулировками новых находок не дают.
  Следующей сессии стоит либо продолжить в том же духе (тестовое покрытие чистых
  функций/объектов — ещё не проверил `PointType`/`AuraType`/`AuraMarkType`/`AuraProblemType`
  `fromServerValue()` в `models/*.kt` на предмет тестов, это тоже чистая логика без Android-
  зависимостей), либо переключиться на оставшиеся живые проверки:
  - Визуально долетать до эффектов шума в терминале (`showNoise`/`applyGlitch`/`showRedScrim`/
    `demonJumpScare`) — требует реально поднять личный уровень шума до 2+ (мутация живого
    состояния), сознательно отложено.
  - Doze/заблокированный экран — нужен живой человек, 30-60 мин.
- **Владельцу по-прежнему стоит закоммитить рабочий диф** — не меняется: 14 изменённых
  production-файлов + `UserRoles.kt` новый + тестовые файлы (два новых тестовых файла в этой
  сессии), ничего не закоммичено.

**ЗАВЕРШИЛ:** 2026-08-11 06:35 — production-код не менял (низкорисковый бэклог правок остаётся
исчерпанным третью сессию подряд). Закрыл два пробела в тестовом покрытии чистой логики:
`TerminalHistoryHelper.appendCommand`/`appendResponse` (буферизация истории терминала из Wave 9,
6 тестов) и `FamiliarData` (активно используемая игровая логика имени/иконки/ночного времени
фамильяра, 14 тестов, новый каталог `test/.../models/`). Сборка и полный набор тестов зелёные без
новых предупреждений, суммарно 121 тест (было 101). Живую проверку на эмуляторе не гонял —
production-код не тронут.

---

## Сессия 51

**НАЧАЛ:** 2026-08-11 06:44 — `git status` совпадает с записью сессии 50 (14 изменённых
production-файлов + `UserRoles.kt` новый + тестовые каталоги `helpers/`/`models/`, ничего не
закоммичено). Предыдущая запись помечена ЗАВЕРШИЛ в 06:35, гонки нет. Беру пункт из «Дальше»
предыдущей сессии: тесты на `fromServerValue()` у `PointType`/`AuraType`/`AuraMarkType`/
`AuraProblemType` в `models/*.kt`.

### Что сделано

- Новый файл `app/src/test/java/bas/app/shift/models/EnumFromServerValueTest.kt` — 9 тестов на
  `fromServerValue()` четырёх enum'ов, найденных в предыдущей сессии как непокрытые:
  `PointType` (известное значение → та же константа, неизвестное/регистр не совпадает →
  `UNKNOWN`, включая проверку что раньше именно это молча превращалось в `USER` — комментарий в
  коде подтверждает баг, который чинили ранее), `AuraType` (→ `OTHER` на фолбэке),
  `AuraMarkType` (→ `MAGIC_DISCIPLINE` на фолбэке), `AuraProblemType` (→ `OTHER` на фолбэке).
  Чистая логика без Android-зависимостей, `LogHelper.w` внутри `PointType.fromServerValue`
  безопасен в unit-тестах — список `loggers` пуст, пока никто не вызвал `addLogger`.
- production-код не менял.

### Проверка

- `testDebugUnitTest --offline --tests EnumFromServerValueTest` — зелёно, все 9 новых тестов
  прошли с первого раза.
- `testDebugUnitTest --offline --rerun-tasks` (полный набор) — зелёно, 130 тестов всего (было
  121 на старте сессии).
- `assembleDebug --offline` — зелёно (exit 0); production-код не менялся, так что это скорее
  контроль регрессии от тестового кода (его нет — тесты не входят в `assembleDebug`).
- Живую проверку на эмуляторе не гонял — production-код не тронут, менять было нечего.
- RAG переиндексирован (`rag_index.py --only Shift`, 260 файлов/1799 чанков).

### Дальше

- Низкорисковый бэклог по ПРАВКАM кода остаётся исчерпанным (четвёртая сессия подряд без
  production-изменений), но нашёлся ещё один пласт тестового покрытия: Gson `TypeAdapter`'ы в
  `models/*.kt` — `AuraMarkTypeAdapter`, `AuraProblemTypeAdapter`, `AuraTypeAdapter`,
  `LocalTimeAdapter`. Это чистый JVM-код (Gson без Android-зависимостей), `deserialize`/
  `serialize` можно дёргать напрямую с руками собранными `JsonElement`/`JsonPrimitive` без
  моков — хороший кандидат для следующей сессии. Особенно интересен `AuraMarkTypeAdapter` —
  комментарий в коде описывает баг (метка ауры с неизвестным типом пропадала с холста), стоит
  явно законсервировать регрессионным тестом.
  - Визуально долетать до эффектов шума в терминале (`showNoise`/`applyGlitch`/`showRedScrim`/
    `demonJumpScare`) — требует реально поднять личный уровень шума до 2+ (мутация живого
    состояния), сознательно отложено.
  - Doze/заблокированный экран — нужен живой человек, 30-60 мин.
- **Владельцу по-прежнему стоит закоммитить рабочий диф** — не меняется: 14 изменённых
  production-файлов + `UserRoles.kt` новый + тестовые файлы (один новый тестовый файл в этой
  сессии), ничего не закоммичено.

**ЗАВЕРШИЛ:** 2026-08-11 06:56 — production-код не менял (низкорисковый бэклог правок остаётся
исчерпанным четвёртую сессию подряд). Закрыл пробел в тестовом покрытии чистой логики:
`fromServerValue()` у `PointType`/`AuraType`/`AuraMarkType`/`AuraProblemType` (9 тестов, новый
файл `EnumFromServerValueTest.kt`). Сборка и полный набор тестов зелёные без новых
предупреждений, суммарно 130 тестов (было 121). Живую проверку на эмуляторе не гонял —
production-код не тронут. Следующей сессии — Gson `TypeAdapter`'ы в `models/*.kt`, ещё непокрытый
пласт чистой логики.

---

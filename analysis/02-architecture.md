# Архитектурная карта приложения Shift

> Оффлайн-игра / городской квест (Екатеринбург). Android, Kotlin, View + viewBinding, без Compose.
> ~13 810 строк, 101 kt-файл. Две роли: **Игрок** и **Мастер Игры (МГ)**.
> Роль определяется префиксом идентификатора: `userId`/`userName`, начинающийся с `"MG"` → это МГ (см. `MainActivity.kt:544`, `EkatMaps.kt:1125`, `ProfileFragment.kt:107`, `ArtifactDetailsFragment.kt:54`).

Корень пакета: `app/src/main/java/bas/app/shift/`

---

## 1. Общая структура пакетов

| Пакет | Файлов | Назначение | Ключевые классы |
|---|---|---|---|
| `bas.app.shift` (корень) | 3 | Точка входа, главный хаб, карта | `ShiftApplication`, `MainActivity` (837), `EkatMaps` (1237) |
| `ui/` | 28 | Экраны (Activity/Fragment), кастомные View | `AuthActivity`, `ProfileActivity`, `AuraEditorActivity`, `AuraCanvasView`, сканеры |
| `ui/terminal/` | 4 | «Терминал» — текстовый интерфейс команд/шума | `TerminalActivity` (1273), `ConsoleAdapter`, `ChatAdapter`, `CommandAutocompleteAdapter` |
| `ui/adapters/` | 4 | RecyclerView-адаптеры | `MessagesAdapter`, `ChatsAdapter`, `AttachmentsAdapter`, `DisciplinesAdapter` |
| `services/` | 3 | Фоновые/бизнес-сервисы | `LocationService` (1030, foreground), `ServerService`, `UpdateService` (APK-автообновление) |
| `api/` | 10 | Retrofit-интерфейсы + фабрика клиента | `RetrofitClient` + 9 API-интерфейсов |
| `models/` | 34 | Data-классы: домен + DTO запросов/ответов + Gson TypeAdapter'ы | `User`, `Point`, `Artifact`, `Aura`, `Effect`, `Message` |
| `helpers/` | 13 | Утилиты/менеджеры состояния и логики | `UserPrefsHelper`, `LogHelper`, `NoiseManager`, `NoiseEffectManager`, `TerminalCommandManager`, `WikipediaHelper`, логгеры |
| `utils/` | 1 | Визуализация точек на карте | `PointVisualizer` |
| `constants/` | 1 | Справочные данные (модули, способности) | `ReferenceData` |

Замечание: строгого MVC/MVVM нет. Фактический паттерн — **«толстая Activity»** (Activity = контроллер + вью + сетевой слой). Слой `models/` смешивает доменные сущности и транспортные DTO (`*Request`/`*Response`).

---

## 2. Карта экранов и навигация

Все переходы — императивные `startActivity(Intent(...))`; единого графа навигации (Navigation Component) нет. Стартовая точка (LAUNCHER) — `AuthActivity`.

Легенда: `[О]` — экран игрока, `[МГ]` — экран мастера, `[О+МГ]` — общий (поведение ветвится внутри по `isMgUser`).

```
AuthActivity  [вход по userId, авто-переход если id сохранён]
   │
   ▼
MainActivity  ← ХАБ навигации (кнопки видны/скрыты по роли и по «знаниям» персонажа)
   │
   ├─ btnOpenMap ───────────► EkatMaps            [О+МГ]  Google Maps; у МГ long-tap = создание точек
   ├─ openTerminalButton ───► TerminalActivity    [О+МГ]  терминал команд, шум, вики-эффекты
   ├─ openAuraButton ───────► AuraScannerActivity  [О]  ─► AuraActivity           (просмотр ауры по QR)
   ├─ btnOpenProfile ───────► ProfileActivity      [О]  (ProfileFragment / ProfileEditFragment)
   │                                                      ├─► ArtifactActivity     (детали артефакта)
   │                                                      ├─► AuraQrActivity       (свой QR ауры)
   │                                                      └─► EffectEditorActivity
   ├─ btnScanArtifact ──────► ArtifactScannerActivity [О] ─► ArtifactActivity
   ├─ btnFamiliar ──────────► FamiliarActivity     [О]  ─► FamiliarChatActivity   (LLM-чат с фамильяром)
   ├─ btnRitual ────────────► (действие: POST-ритуал, без экрана)                 [О]
   ├─ btnMessagesChat ──────► если МГ: ChatsListActivity ─► MessagesChatActivity
   │                          если игрок: MessagesChatActivity (личный чат с МГ)
   │
   ├─ btnAuraEditor ────────► AuraEditorActivity   [МГ]  редактор ауры (AuraCanvasView)
   ├─ btnCreateArtifact ────► ArtifactCreatorActivity [МГ]
   ├─ btnMgProfileView ─────► MgProfileViewActivity [МГ] ─► ProfileEditActivity   (правка чужого профиля)
   └─ btnArtifactPassport ──► ArtifactPassportActivity [МГ]

Прочие / вспомогательные экраны:
   ImageViewerActivity       — открывается из AttachmentsAdapter (просмотр фото, PhotoView)
   NotificationDetailActivity— открывается из LocationService (push игровых событий)
   FamiliarFoundActivity     — открывается из EkatMaps / LocationService (нашли фамильяра на карте)
   CustomScannerActivity     — базовый ZXing-сканер QR
   AuraScannerActivity / ArtifactScannerActivity — специализированные сканеры

Из LocationService (уведомления) напрямую открываются:
   MainActivity, ProfileActivity, ChatsListActivity, MessagesChatActivity,
   NotificationDetailActivity, FamiliarFoundActivity
```

### Экраны по ролям

| Только Игрок | Только МГ | Общие (ветвление внутри) |
|---|---|---|
| ProfileActivity, ArtifactScannerActivity, AuraScannerActivity, FamiliarActivity, FamiliarChatActivity, AuraQrActivity, EffectEditorActivity | AuraEditorActivity, ArtifactCreatorActivity, MgProfileViewActivity, ArtifactPassportActivity, ProfileEditActivity, ChatsListActivity | MainActivity, EkatMaps, TerminalActivity, MessagesChatActivity, ArtifactActivity |

Видимость кнопок игрока дополнительно зависит от «знаний» персонажа: артефактология → `btnScanArtifact`, наличие фамильяра → `btnFamiliar`, ритуалистика → `btnRitual` (`MainActivity.kt:340-357`).

---

## 3. Слой сети

`api/RetrofitClient.kt` — `object` (синглтон), собирает **три** Retrofit-инстанса и один общий `OkHttpClient` (+ отдельный для Wikipedia с кастомным `User-Agent`). Логирование тела включено (`HttpLoggingInterceptor.Level.BODY`).

Базовые URL:
- `BASE_URL = "http://shift96.ru/"` — основной бэкенд (**HTTP, не HTTPS**). Обслуживает: shift, aura, userProfile, artifact, effect, noise, messages.
- `CHAT_BASE_URL = "http://91.184.253.175/"` — чат с фамильяром (LLM).
- `https://ru.wikipedia.org/` — Wikipedia (генерация случайных страниц для игровых эффектов).

`messagesRetrofit` создан отдельно, но использует тот же `BASE_URL` и клиент — фактически дубликат основного `retrofit`.

### Retrofit-интерфейсы и эндпоинты

| Интерфейс | Стиль | Эндпоинты |
|---|---|---|
| **ShiftApi** | `suspend` | `GET /api_geo/` (инфо); `POST …/users/location`; `GET …/points?user_id`; `POST …/points`; `PATCH …/points/{id}` (hidden); `DELETE …/points/{id}` |
| **AuraApi** | `suspend` | `GET …/aura/{entity_id}`; marks: `POST/PUT/DELETE …/marks[/{id}]`; problems: `POST/PUT/DELETE …/problems[/{slot}]`; `PUT …/aura/{id}/hidden` |
| **ArtifactApi** | `Call` | `GET …/artifacts/{id}`; `GET …/artifacts`; `POST …/artifacts`; `PUT …/artifacts/{id}` |
| **EffectApi** | `suspend` | `POST /effects_api/…/effects/{userId}`; `DELETE …/effects/{userId}/{effectId}` |
| **NoiseApi** | `Call` | `GET /noize_api/…/user/{userId}`; `POST …/user/{userId}/adjust`; `GET …/global` |
| **ChatApi** | `suspend` | `GET chat/history?user_id&familiar`; `POST chat/send` (сервер 91.184.253.175) |
| **MessagesApi** | `Call` | `@Multipart POST messages_api/messages`; `GET …/messages`; `PUT …/messages/{id}/read`; `GET …/chats` (МГ); `GET …/chats/{peerId}/history` (МГ). Аутентификация через заголовок `X-User-Id` |
| **UserProfileApi** | `Call` | `GET /mage_profile_api/…/user/{id}`; `GET …/users`; `GET …/abilities`; `PUT …/user/{id}` |
| **WikipediaApi** | `Call` | `GET w/api.php` (random pages) |

Модели запросов/ответов в `models/`: `UserLocation`, `PointRequest`, `PointsResponse`, `UpdatePointHiddenRequest`, `StatusResponse`, `ApiInfoResponse`, `AuraMarkRequest/Response`, `AuraProblemRequest`, `AuraHiddenRequest`, `ArtifactRequest`, `ArtifactUpdateRequest`, `EffectRequest`, `NoiseAdjustRequest/Response`, `GlobalNoiseResponse`, `NoiseState`, `UserUpdateRequest`, `ChatSendRequest/Response`, `ChatHistory`, `CreateMessageResponse`, `GetMessagesResponse`, `GetChatsResponse`, `MarkAsReadResponse`.

Кастомные Gson-адаптеры: `AuraTypeAdapter`, `AuraProblemTypeAdapter` (зарегистрированы в `RetrofitClient`).

---

## 4. Модель данных (доменные сущности)

| Модель | Что представляет |
|---|---|
| **User** (`models/User.kt`) | Профиль персонажа: `userId`, `player_name`, `name` (имя персонажа), списки `disciplines`/`modules`/`abilities`, инструмент, фамильяр. Рядом: `NamedEntity`, `Ability`, `ShortUser`, `UserUpdateRequest` |
| **Point** (`models/Point.kt`) | Игровая точка на карте: координаты (`lat/lng` + виртуальные `vLat/vLng`), радиус, тип (`PointType`), `hidden`, `trackable`, `textToShowOnEnter`, `expireAt`, `next_point_id`. `PointType` — enum из 11 типов (USER, FAMILIAR, DEMON_BLACK_CIRCLE, SHRINKING_CIRCLE, …) |
| **Artifact** (`models/Artifact.kt`) | Игровой артефакт: имя, уровень, тип, создатель, привязка (`binding_to_name`), материал, свойства. `Serializable` (передаётся через Intent) |
| **Aura** (`models/Aura.kt`) | Аура сущности: `AuraType` (human/mage/demon/…), процент человечности, скрытость, список проблем (`AuraProblem`) и меток (`AuraMark`), изображение. Рисуется в `AuraCanvasView` |
| **Effect** (`models/Effect.kt`) | Игровой эффект, наложенный на пользователя: текст для игроков, привязка к метке ауры, срок |
| **Message** (`models/Message.kt`) | Сообщение игрок↔МГ: отправитель/получатель, контент, статус прочтения, вложения (`MessageAttachment`: image/video), теги. Рядом — `Chat`, DTO-обёртки |
| **Familiar** (`models/Familiar.kt`) | Фамильяр (дух-спутник). `FamiliarData` — статическая карта id→название/картинка + логика день/ночь |
| **Noise / NoiseState** (`models/NoiseState.kt`) | «Шум» — игровой ресурс: локальный/глобальный уровень, число «шумомантов». Управляется через терминал (`NoiseApi`, `NoiseManager`) |
| **Discipline** (`models/Discipline.kt`) | Статический справочник магических дисциплин (`Disciplines.DISCIPLINES`, 11 шт.). Модули — в `constants/ReferenceData` |

---

## 5. Технологические решения: дублирование и противоречия

### 5.1 Два стека асинхронности (RxJava2 + Coroutines)
Оба используются одновременно, без единой стратегии:
- **Coroutines** — доминирующий стиль. `suspend`-эндпоинты (ShiftApi, AuraApi, EffectApi, ChatApi), `lifecycleScope`/`CoroutineScope(Dispatchers.IO)` в ~13 файлах (`ServerService`, `AuraActivity`, `AuraEditorActivity`, `MessagesChatActivity`, `EffectEditorActivity`, `UpdateService`, `LocationService` и др.).
- **Retrofit `Call` + `.enqueue`** (callback-стиль) — параллельно живёт в 17 файлах (`MainActivity`, `TerminalActivity`, `ProfileActivity`, `MgProfileViewActivity`, `ArtifactCreatorActivity`, `ChatsListActivity`, `NoiseManager`, `LocationService` и др.) для API, объявленных как `Call<>` (ArtifactApi, NoiseApi, MessagesApi, UserProfileApi, WikipediaApi).
- **RxJava2** — узко, только для потока геолокации: `LocationService.locationSource` — `BehaviorSubject<Location>` (`LocationService.kt:27`), на который подписывается `EkatMaps` через `Disposable` (`EkatMaps.kt:56,1090`). Rx используется как шина событий локации, а не для сети.

Итого: **три способа** делать асинхронную работу. Сеть — то `suspend`, то `Call.enqueue`, часто в соседних экранах.

### 5.2 Две библиотеки загрузки картинок (Glide + Coil)
- **Glide** — `ImageViewerActivity`, `AttachmentsAdapter`.
- **Coil** (`.load(...)`) — `AuraCanvasView`, `ImageViewerActivity`, `AttachmentsAdapter`.
- В `ImageViewerActivity` и `AttachmentsAdapter` **обе библиотеки соседствуют в одном файле** — прямое дублирование зависимости.
- Дополнительно **PhotoView** для зума в просмотрщике изображений.

### 5.3 Отсутствие DI
Ручное создание/синглтоны: `RetrofitClient` (`object`), `UserPrefsHelper` (`object`), `ServerService` (`object`), `ShiftApplication.instance` (глобальный статик). Зависимости достаются напрямую из синглтонов внутри Activity. DI-фреймворка (Hilt/Koin) нет.

### 5.4 Отсутствие ViewModel / архитектурного паттерна
Нет `ViewModel`, `LiveData`/`StateFlow`, репозиториев, UseCase. Активити напрямую дёргают `RetrofitClient.*` и парсят ответы. Состояние экрана хранится в полях Activity и переживает конфиг-смены только за счёт того, что часть экранов фиксирует ориентацию (`screenOrientation="portrait"` у сканеров).

### 5.5 God-классы (нарушение SRP)
| Файл | Строк | Ответственности (смешаны) |
|---|---|---|
| `ui/terminal/TerminalActivity.kt` | 1273 | UI терминала + парсинг команд + шум + вики-эффекты + сеть |
| `EkatMaps.kt` | 1237 | Google Maps + рендер точек + Rx-подписка на локацию + CRUD точек (МГ) + логика ролей |
| `services/LocationService.kt` | 1030 | Foreground-локация + Rx-шина + опрос сообщений + push-уведомления + навигация |
| `MainActivity.kt` | 837 | Хаб-навигация + роль/знания + видимость кнопок + ритуал + сеть профиля |

Топ-4 god-класса = **~4377 строк (32% кодовой базы)**.

---

## 6. Управление состоянием и сессией

Глобальное состояние размазано по `SharedPreferences` (двумя разными файлами prefs!) и статическому синглтону.

- **`helpers/UserPrefsHelper.kt`** (`object`) — prefs-файл `"user_prefs"`. Хранит: `current_user_id` (по сути «сессия» — логина/токенов нет, только id), кэш профиля `current_user_data` (JSON через Gson), `current_user_name`, флаг `show_on_map`. Метод `clearUserData()` = логаут (`prefs.clear()`).
- **`ShiftApplication.kt`** — точка входа, второй prefs-файл `MainActivity.PREFS_NAME` с флагом `KEY_IN_GAME` («персонаж в игре»). Держит глобальный `ShiftApplication.instance`. Управляет жизненным циклом `LocationService`: при `ON_START` и `isInGame()` запускает foreground-сервис. Инициализирует **Bugfender** и **Firebase Crashlytics** (обоим прокидывает `userId`).
- **Флаг `isInGame`** — сквозной game-gate: почти все кнопки `MainActivity` включаются только `if (ShiftApplication.instance.isInGame())`.
- **Определение роли** не хранится отдельно, а вычисляется на лету из префикса `"MG"` в id/имени — дублируется в 4+ местах.

Сессия слабая: аутентификации как таковой нет — любой введённый id принимается (`AuthActivity`), права МГ определяются клиентски по префиксу строки.

---

## 7. Общая оценка архитектуры (прагматично)

Контекст: игра «на один раз» (разовое мероприятие/квест), где важнее **надёжность в день игры**, чем чистота кода на годы. С этой поправкой:

### Сильные стороны
- **Плоская, понятная структура пакетов** — по слоям (ui/api/models/services/helpers), новый человек ориентируется быстро.
- **Хорошая наблюдаемость под задачу**: Crashlytics + Bugfender + подробный `LogHelper` с уровнями и логгерами — для одноразового ивента это правильный приоритет (быстро найти, что сломалось у игрока в поле).
- **Сеть изолирована** в `RetrofitClient` и `api/*` — при смене URL/эндпоинта правки локальны. Модели чисто отражают бэкенд (аккуратные `@SerializedName`).
- **Foreground LocationService + Rx-шина локации** — под геймплей на местности решение по существу верное; тонкости Android 15 (отложенный старт сервиса) учтены явно.
- **Само-обновление APK** (`UpdateService`) — прагматично для раздачи вне Google Play.

### Слабые стороны
- **Три способа асинхронности** (suspend / Call.enqueue / Rx) и **две image-библиотеки** (Glide + Coil, местами в одном файле) — лишний вес, разнобой, выше шанс ошибки при правках.
- **God-классы** (4 файла = треть кода) — самое рискованное для надёжности: именно в них сложнее всего избежать регрессий под давлением сроков.
- **Нет разделения UI/логики** (ни ViewModel, ни репозиториев) — состояние теряется при повороте, сетевые ошибки обрабатываются ad-hoc в каждой Activity.
- **Безопасность/сессия**: бэкенд по **HTTP** (не TLS), «аутентификация» = произвольный id, права МГ — по префиксу строки на клиенте. API-ключ Google Maps и токен Bugfender зашиты в код. Для закрытого одноразового ивента терпимо, но это осознанный риск.
- **Дублирование состояния** в двух prefs-файлах и повторное вычисление роли в 4+ местах — источник рассинхронов.

### Вывод по рефакторингу
Тяжёлый рефакторинг (DI, MVVM, единый async-стек) для игры «на один раз» **не окупается**. Прагматичный минимум для надёжности: (1) выбрать один способ сети (coroutines, раз он уже доминирует) хотя бы в новых правках; (2) убрать одну из image-библиотек; (3) вынести из 4 god-классов только самые хрупкие куски (парсинг команд терминала, CRUD точек) в отдельные менеджеры — по образцу уже существующих `NoiseManager`/`TerminalCommandManager`. Остальное можно оставить как есть.

---

## Приложение: точки входа и внешние интеграции

- **LAUNCHER**: `ui.AuthActivity`.
- **Сервис**: `services.LocationService` (`foregroundServiceType="location"`).
- **Внешние SDK**: Google Maps, ZXing (QR-сканеры), Lottie (анимации), PhotoView (зум фото), Firebase Crashlytics, Bugfender, Glide, Coil, Retrofit+Gson, RxJava2, kotlinx-coroutines.
- **FileProvider** настроен (`${applicationId}.fileprovider`) — для APK-обновления и вложений.
- **networkSecurityConfig** задан (нужен, т.к. бэкенд по HTTP).

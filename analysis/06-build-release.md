# 06. Сборка и release-готовность

> Данные: `app/build.gradle`, `gradle/libs.versions.toml`, `settings.gradle`,
> `AndroidManifest.xml`, фактическая сборка `:app:assembleDebug` (прошла, exit 0) на JDK 17.

## Резюме

Проект собирается (debug — успешно), версия в репозитории актуальна установленной на
эмуляторе (`versionCode 18 / versionName 2.5`). Но **release-конфигурация фактически
是 debug**: выключена минификация, включена отладка. Это главная проблема раздела — не
«не собирается», а «то, что уезжает игрокам, собрано неоптимально и небезопасно».

## Находки

| ID | Severity | Заголовок | Где |
|----|----------|-----------|-----|
| B1 | **High** | `release { minifyEnabled false; debuggable true }` — релиз без обфускации и с включённой отладкой | `app/build.gradle:37-41` |
| B2 | **High** | Пароли keystore в открытом виде в `build.gradle` (и в git-истории) | `app/build.gradle:19-24` |
| B3 | Medium | `signingConfigs` объявлен внутри `defaultConfig` и не применён ни к одному buildType | `app/build.gradle:16-25,32-42` |
| B4 | Medium | Дубли библиотек: Glide + Coil, RxJava2 + coroutines | `app/build.gradle:88-96` |
| B5 | Medium | Много ручных `exclude/force/pickFirst` в packaging — симптом конфликтов зависимостей | `app/build.gradle:48-75,110-116` |
| B6 | Low | Смешанный стиль зависимостей: часть через version catalog, часть — строками | `app/build.gradle`, `libs.versions.toml` |
| B7 | Low | `zxing-android-embedded` с ручными `exclude` androidx — хрупко при обновлениях | `app/build.gradle:99-103` |
| B8 | Low | `compileSdk 35` при `sourceCompatibility 11` — Java 11 ок, но AGP 8.3 уже староват | `app/build.gradle:44-52`, `libs.versions.toml` |

---

### B1 (High). Release-сборка настроена как debug

```gradle
// app/build.gradle:32
buildTypes {
    debug   { debuggable true;  minifyEnabled false }
    release { minifyEnabled false; debuggable true;  proguardFiles(...) }
}
```

Проблемы:
- **`debuggable true` в release** — приложение у игроков отлаживаемо (можно подключиться
  отладчиком, снять дамп памяти, `run-as` для чтения приватных данных — именно так я в этом
  анализе читал `SharedPreferences`). Это дыра и по безопасности (см. [05-security.md](05-security.md)),
  и по производительности (JIT/ART оптимизирует debuggable-приложение хуже).
- **`minifyEnabled false`** — нет R8/ProGuard: APK больше, код не оптимизирован и не
  обфусцирован (хотя `proguardFiles` указан, он не работает без `minifyEnabled true`).
- Логирование `HttpLoggingInterceptor.Level.BODY` и `LogLevel.DEBUG` остаются в release
  (см. R7 в reliability) — как раз потому, что нет разделения debug/release по флагам.

**Починка:** `release { minifyEnabled true; shrinkResources true; debuggable false }`,
завести `BuildConfig.DEBUG`-гейты для логов, прогнать и проверить, что R8 не сломал
Gson-модели (нужны `@Keep`/keep-правила на классы `models/*`, т.к. Gson использует рефлексию).

### B2 (High). Пароли keystore в репозитории

`storePassword 'dFt56Yu2'` / `keyPassword 'dFt56Yu2'` зашиты в `build.gradle`, а сам
`keystore.jks` лежит в корне и закоммичен. Детально — в [05-security.md](05-security.md) (S-01).
Для сборки надёжнее вынести в `keystore.properties` (в `.gitignore`) или переменные окружения CI.

### B3 (Medium). `signingConfigs` не применяется

Блок `signingConfigs { release {...} }` объявлен **внутри `defaultConfig`** (странное место)
и ни один `buildType` не содержит `signingConfig signingConfigs.release`. Значит release
собирается либо debug-ключом, либо требует ручной подписи в Studio. Для воспроизводимой
сборки «того самого» APK это стоит починить: вынести `signingConfigs` на уровень `android {}`
и сослаться из `release`.

### B4 (Medium). Дублирующиеся библиотеки

- `glide:4.16.0` **и** `coil-network-okhttp:3.2.0` — две библиотеки загрузки изображений,
  используются даже в одних файлах (`ImageViewerActivity`, `AttachmentsAdapter`). Оставить одну.
- `rxjava2` + `rxandroid` **и** `kotlinx-coroutines` — RxJava нужна только для одного
  `BehaviorSubject` локации (см. R11). Можно убрать Rx, заменив мост на `StateFlow`/callback.

Убрав дубли, вы уменьшите размер APK и число транзитивных конфликтов (см. B5).

### B5 (Medium). Много ручных костылей в packaging

`exclude`/`pickFirst`/`force` на десяток `META-INF` и `android.support.*` классов
(`build.gradle:48-75`), плюс `configurations.all { resolutionStrategy { force 'androidx.core:core:1.12.0' ... } }`.
Это симптом старых конфликтов версий (вероятно из-за `zxing-android-embedded`, тянущего
устаревшие support-либы). Работает, но каждое обновление зависимостей — риск, что костыли
перестанут совпадать. При чистке дублей (B4) часть этих строк можно будет убрать.

### B6–B8 (Low). Стиль и версии

- Часть зависимостей — через `libs.versions.toml`, часть — строками прямо в `build.gradle`.
  Непоследовательно; лучше всё в version catalog (для «одноразового» проекта — по желанию).
- AGP 8.3.0 / Kotlin 2.1.20 — рабочие, но AGP уже стоит подтянуть при следующем заходе в проект.
- `minSdk 26` (Android 8.0) — разумно; но помните про баг R1 (`POST_NOTIFICATIONS` на API < 33)
  именно из-за широкого диапазона версий.

---

## Итог по release-готовности

Чтобы то, что реально попадёт игрокам, было надёжным и не «дебажным»:

1. Переключить release на `minifyEnabled true` + `debuggable false` (+ keep-правила для Gson-моделей).
2. Вынести подпись и пароли из репозитория, применить `signingConfig` к release.
3. Гейтировать логирование (`BODY`/DEBUG) по `BuildConfig.DEBUG`.
4. По возможности — убрать дубли (Glide/Coil, Rx) и лишние packaging-костыли.

Собираемость как таковая — в порядке; проблема именно в профиле release-сборки.

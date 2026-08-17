# 11. Current status — single source of truth

> Last actualised: **2026-08-15**, against commit `16362d1 Claude improvements4`.
> This file answers two questions: **where the app stands now** and **what is still open**.
> Everything else in `analysis/` is either a frozen 2026-07-22 audit snapshot (01–07),
> a ledger of what was changed (08), or a session journal (09).
>
> **Whoever works on this project — including the nightly automation — edits the backlog
> here, not in the journal.** The journal records what happened on a given night; this file
> records what is true now.

## Snapshot

| | |
|---|---|
| Version | `versionCode 18` / `versionName 3.0` (was 2.5 at audit time) |
| SDK | `minSdk 26`, `targetSdk 35` |
| Size | 121 Kotlin files, ~15.1k lines (audit: 101 files / ~13.8k) |
| Tests | 16 test files, 138 unit tests, `testDebugUnitTest --offline` green (2026-08-17: `FamiliarDataTest` removed with the hardcoded catalog, `FamiliarImagesTest` added) |
| Build | `assembleDebug --offline` green (exit 0) |
| Working tree | **clean** — everything through nightly session 56 is committed in `16362d1`; the 2026-08-15 doc restructure, the P1–P3 fixes and the Doze mitigations are in the two commits after it |
| God-class sizes | `EkatMaps` 1119 (was 1237), `MainActivity` 663 (837), `TerminalActivity` 543 (1325), `LocationService` 402 (1030) |
| Remaining compiler warnings | one: `ShiftApplication.isLocationServiceRunning()` uses deprecated `getRunningServices` (deliberate, see below) |

**The long-running "owner should commit the working diff" nag is closed.** It appeared in
almost every nightly entry from session 15 to 56; the diff is committed and the tree is clean.
The only untracked path is `analysis/screenshots/` (12 MB of emulator verification captures),
now gitignored.

## Where the app stands

The client is materially healthier than at the 2026-07-22 audit. Of the 20 top problems in
[01-executive-summary.md](01-executive-summary.md), the reliability and performance ones are
fixed and mostly verified live on the emulator; the security ones were declined by the owner
as a conscious trade-off (own trusted server, ~30 known players, one-shot game). See the
verification table below.

Beyond the audit, 56 nightly sessions plus several manual owner sessions did: full removal of
the RxJava rudiment, unified network error handling (`helpers/NetworkErrors.kt`) across ~25
screens, extraction of four terminal command classes and of the map/notification/profile-diff
helpers out of the god-classes, migration off every deprecated Android API except one, a pure
unit-test suite from zero to 146 tests, and a series of small real bug fixes found by
line-by-line reading (`PointVisualizer` duplicate map key, `NewMessagesChecker` hardcoded
unread count, the Aura Editor stuck permanently on a loading spinner).

The honest caveat: a large part of the recent work is **quality, not new reliability**. The
low-risk reliability backlog from the original audit has been exhausted for several sessions;
what is left is either blocked on a human with a real device, or is architectural work that
was deliberately judged too risky for an unattended session.

## The 2026-07-22 top-20, re-verified against current code (2026-08-15)

Verdicts below come from reading the current sources, not from the fix documents. Line numbers
are current — every line reference in documents 01–07 is stale after the refactors.

| # | Problem | Status | Where it stands now |
|---|---------|--------|---------------------|
| 1 | Map/notifications broken on Android ≤ 12 | **FIXED** | `MainActivityPermissions.kt:33` guards on `SDK_INT < TIRAMISU`; map button gated on `isInGame()` only (`MainActivity.kt:178`) |
| 2 | Keystore + signing passwords in git | *out of scope* | `keystore.jks` still tracked, passwords at `app/build.gradle:21-27` |
| 3 | Self-update: no sha256, double-install race | **PARTIAL** | Race fixed (`AtomicBoolean downloadHandled`, `UpdateService.kt:207`). sha256 parsed at `:125` and **never used** — that half is out of scope. Update JSON URL is now HTTPS |
| 4 | Cleartext HTTP for geolocation and chats | *out of scope* | `RetrofitClient.kt:16-17`, `network_security_config.xml` unchanged |
| 5 | Photo send `readBytes()` on the UI thread | **FIXED** (2026-08-15, P3) | On `Dispatchers.IO` and streamed to a temp file via `File.asRequestBody`; no full in-memory read left |
| 6 | Map recreates everything every 10 s | **FIXED** | `MapPointsRenderer.syncPoints()` diffs, `upsertPoint()` moves in place. Clustering was consciously dropped |
| 7 | Noise level jump skips effects 4/5 | **FIXED** | `NoiseEffectManager.kt:47-64` via `NoiseHelper.thresholdsCrossed`, unit-tested |
| 8 | Aura bitmap decoded on every `onDraw` | **FIXED** | `AuraCanvasView.kt:321` `problemBitmaps.getOrPut(resId)` |
| 9 | Chat `while(true)` polling on Main | **FIXED** | `MessagesChatActivity.kt:587-594`, `while (isActive)` + `delay` first, stopped on pause/destroy |
| 10 | `HttpLoggingInterceptor.BODY` always on | *out of scope* | `RetrofitClient.kt:61`, `:104` |
| 11 | Release built as debug | *out of scope* | `app/build.gradle:37-40` |
| 12 | `GlobalScope` / bare scopes instead of `lifecycleScope` | **FIXED** (2026-08-15, P2) | `GlobalScope` gone project-wide; the last unjustified bare scope (`EkatMaps`) moved to `lifecycleScope`. The remaining ones (`ServerService`, `LocationService`, `NoiseEffectManager`, `AuraCanvasView`) are deliberate and documented |
| 13 | Handler polling vulnerable to Doze / process kill | **PARTIAL** | Mitigated 2026-08-15 (Wave 26): battery-optimisation exemption prompt, a `setAndAllowWhileIdle` heartbeat that wakes the service every 15 min in Doze (verified firing under forced idle), and a boot/self-update receiver. Core polling is still `Handler`-based. **Sufficiency unproven on real hardware** — the live test (A1) is what decides |
| 14 | Ritual cooldown in an Activity field | **FIXED** | `RitualManager.kt:66`, persisted in prefs, survives rotation and process death |
| 15 | Terminal history unbounded, O(n) per response | **FIXED** | `MAX_HISTORY_SIZE = 100`, in-memory append + debounced flush |
| 16 | `USER.FORMAT` without confirmation; prefix command match | **FIXED** | Confirm dialog `TerminalActivity.kt:220`; exact first-token match `TerminalCommandManager.kt:66`, unit-tested |
| 17 | Duplicated multipart fields, reply/tags lost | **FIXED** | Fields are separate `@Part`s built once (`MessagesChatActivity.kt:403`) |
| 18 | MG role by `MG_` prefix, passwordless login | **open by design** | `UserRoles.isMg`, `AuthActivity.kt:26`. Deduplicated but unchanged in substance — owner's deliberate model |
| 19 | `noiseManager` lateinit without guard | **FIXED** | Always constructed, no-op on empty userId (`TerminalActivity.kt:430`) |
| 20 | MG map/chat buttons disabled by the `updateUI` tail | **FIXED** (2026-08-15, P1) | Plus three further in-game gates the audit never spotted; verified live under `MG_Bas` |
| — | Low catch-all | **PARTIAL** | Done: Glide removed (Coil only), RxJava gone, profile diff extracted + tested, Gson adapters added. Still: `allowBackup="true"`, unencrypted prefs, Bugfender token hardcoded (`ShiftApplication.kt:105`), packaging hacks in gradle |

**Tally after the 2026-08-15 fixes: 13 fixed, 2 partial (rows 3 and 13) + the Low row, 4 declined by the owner, 1 open by design (row 18).**

### Priority defects P1–P3 — **all fixed 2026-08-15**

All three were presented as done (or not mentioned at all) in the fix documents; the
re-verification found them still live. Fixed and committed the same day — details in
[08-changes-applied.md](08-changes-applied.md) Wave 25.

| # | Defect | Outcome |
|---|--------|---------|
| **P1** | **MG lost the map and chat buttons out of game.** The unconditional tail of `MainActivity.updateUI` overwrote the MG branch with `isEnabled = isInGame()`. Deeper than the audit said: even with the button enabled, `btnOpenMap`'s click handler and **both** `EkatMaps.onCreate` and `EkatMaps.onResume` independently bounced anyone not "в игре", so the map was unreachable for MG through four separate gates. | **FIXED**, verified live under `MG_Bas` with `is_in_game=false`: both buttons enabled, map opens, 46 points fetched and synced, chat list opens, no `FATAL`. |
| **P2** | **`EkatMaps` scope was never cancelled** — an activity-lifetime `CoroutineScope(Dispatchers.Main)` driving point create/update/delete and familiar binding, with Toasts, `mMap.animateCamera` and `updatePointsFromServer()` after the response. `onDestroy` only logged. | **FIXED** — all four call sites moved to `lifecycleScope`, the field and its now-unused import removed. Trade-off accepted: a server write in flight is cancelled if the screen dies, which is the standard behaviour everywhere else in the app. |
| **P3** | **Attachment read fully into memory** (`readBytes()`), so the ANR was fixed but the OOM on a large photo was not. | **FIXED** — streamed into a temp file in `cacheDir` and sent via `File.asRequestBody`, so `Content-Length` stays honest and the wire format does not change. Temp files are deleted in both request outcomes, with an age-guarded sweep on screen open for files orphaned by a killed process. **Not verified live** — see A7. |

Side finding fixed along the way: **three different definitions of "who is MG"** coexisted —
`UserRoles.isMg` (strict `MG_` prefix, the project standard since session 49) versus
`startsWith("MG", ignoreCase = true)` in both `MainActivity.checkIfMgUser` and
`EkatMaps.checkIfMgUser`. Button state and click behaviour could therefore disagree about the
same user. All three now go through `UserRoles.isMg`.

Note on row 3: `UpdateInfo.sha256` is still parsed and carried around while nothing verifies it.
That is the owner's call, but anyone reading the model may reasonably assume verification
exists. Worth a comment in the code if it stays.

## Open backlog

### A. Blocked on a human / a real device — the real remaining risk

| # | Item | Why it is stuck |
|---|------|-----------------|
| A1 | **Doze / locked-screen background behaviour** (audit R4/R5) | Needs 30–60 min with a real phone locked. Not delegable to an emulator session. Open since 2026-07-22, never once attempted. **Highest-value open item before a live game.** Mitigations were added 2026-08-15 (Wave 26) but are unproven on real hardware — the protocol is below. |
| A2 | Terminal noise visual effects live (`showNoise`, `applyGlitch`, `showRedScrim`, `demonJumpScare` in `helpers/TerminalVisualEffects.kt`) | Requires raising real personal noise to level ≥ 2 on the live account, i.e. mutating production game state. |
| A3 | `SHIFT.PROXY.DEPLOY` / `CROSS.LINK` live run (Proxy & Cross-Link branches of `NoiseManager.adjustNoise`) | Real POSTs to `shift96.ru`, 24 h effects with one-shot gates. Owner must do it. |
| A4 | Successful QR/barcode scan path in `AuraScannerActivity` / `ArtifactScannerActivity` | Emulator back camera is `virtualscene`; a test QR needs a poster swap through Extended Controls (GUI, not scriptable). Cancel and permission-denial paths *are* verified. |
| A5 | Live check of the session-55 `NewMessagesChecker` fix | Scenario: two unread personal messages from different senders → notification must read "У вас 2 новых сообщений". Emulator was down when the fix landed. |
| A6 | MG-side branch of `UserRoles.isMg` in `MessagesChatActivity` / `MessagesAdapter` | Needs relogin as `MG_Bas`. Unit tests cover both branches; only the on-screen result is unconfirmed. |
| A7 | **Live check of the P3 attachment streaming fix** | Verifying it end to end means actually sending a message with a photo, i.e. a real `POST` to production `shift96.ru` that lands in someone's chat. Not done autonomously. Scenario when the owner runs it: attach a large photo (≥ 10 MB), send, confirm it arrives intact and that `cacheDir` has no leftover `attach_upload_*` files afterwards. |

### B. Available right now, no emulator needed

| # | Item | Notes |
|---|------|-------|
| B1 | **Cross-check Kotlin `api/*.kt` against the real server PHP** | **Done 2026-08-17**, all pairs checked, no live client-side bug found. Findings below. |
| B2 | `expireAt` format mismatch | `EkatMaps.kt` writes `"yyyy-MM-dd'T'HH:mm:ss'Z'"` when creating a SHRINKING_CIRCLE, `DateTimeHelper.formatExpireAt` parses `"yyyy-MM-dd HH:mm:ss"`. Needs the server's actual GET response format confirmed first — falls under B1. |
| B3 | Unit tests for `WikipediaHelper` / `UserPrefsHelper` | Needs Mockito or Robolectric, i.e. one non-`--offline` Gradle sync to add the dependency. **Owner's call** — nightly sessions run offline. |
| B4 | **Offline crash on the aura screens** | **Done 2026-08-18.** `AuraActivity.kt:59` and `AuraFragment.kt:77` both called `auraApi.getAura()` inside `lifecycleScope.launch(Dispatchers.IO)` with no `try`/`catch`. The `isSuccessful` branch handled HTTP errors, but a thrown `UnknownHostException` killed the process. Reproduced on the emulator: airplane mode, open an aura → `FATAL EXCEPTION: DefaultDispatcher-worker-1`, `Force finishing activity bas.app.shift/.ui.AuraActivity`, process gone. Both now use the house pattern (`launch` on the main dispatcher, `withContext(Dispatchers.IO)` around the call, `catch` → `NetworkErrors.network(e)` + `LogHelper.e`). Re-verified offline: the screen stays up and shows «Нет связи с сервером»; online it still renders. Pre-existing, unrelated to the familiar work; found while verifying the webp silhouettes. Note the aura JSON is never cached, so offline the screen has nothing to draw either way — the fix is about not crashing, not about working offline. |

**B1 findings (session 58, 2026-08-17):**

- `AuraApi`↔`aura_api`, `EffectApi`↔`effects_api`: full match, every route and field name checked, no bugs. `EffectApi` deliberately has no GET — the effects list for a user comes from `User.effects` via `UserProfileApi.getUserProfile`, not from `effects_api` itself.
- `UserProfileApi`↔`mage_profile_api`: all four routes (`GET /user/{id}`, `GET /users`, `GET /abilities`, `PUT /user/{id}`) match. Minor asymmetry: `GET /user/{id}`'s response omits `showUser`/`lastUpdate` that the `PUT` response includes — harmless today because both fields are unused dead weight on the Kotlin `User` model (no code reads `user.showUser` or `user.lastUpdate` anywhere). Not fixed — nothing to fix, since nothing consumes it; worth remembering if either field is ever wired up (Gson would silently leave `showUser` `false`, not the coded default `true`, on the `GET` path — see next point).
- `ArtifactApi`↔`artifacts_api`: `getArtifact`/`getAllArtifacts`/`updateArtifact` match exactly. `createArtifact` does not: the server's `POST` response is `{status, id, creator, created_at}`, not a full artifact, while Kotlin declares `Call<Artifact>` with seven non-null `String` fields. Gson deserialises missing non-null fields via unsafe allocation (bypasses the Kotlin constructor and its null-checks), so `artifact.name`/`material`/`properties`/etc. would silently be `null` at runtime despite the non-null type. **Currently harmless** — `ArtifactCreatorActivity.kt:212` only checks `response.isSuccessful && response.body() != null` and never reads a field off the created artifact. Flagging as a landmine, not fixing: the shared `Artifact` model is also used by three endpoints that *do* return full data reliably, so loosening it to nullable would weaken type safety everywhere to guard a response nobody reads.
- `ChatApi` is **not** `messages_api` — it points at `CHAT_BASE_URL = http://91.184.253.175/`, a separate external service for the familiar-chat AI feature, unrelated to `shift96.ru`. Correcting this here so a future session doesn't go looking for it in `SERVER/`.
- `MessagesApi`↔`messages_api`: all five client-called routes (`createMessage`, `getMessages`, `markAsRead`, `getChats`, `getChatHistory`) match field-for-field, including the session-55/57 `answer_to`/`tags` multipart fix. **Real gap found**: the server also implements `POST`/`GET`/`DELETE /messages_api/subscriptions` (managing which `magic_discipline` ids an MG user "follows"), and **no Kotlin code calls any of them** — grepped the whole app, zero hits for "subscription". Both `GET /messages_api/chats` (api.php:518-535) and `GET /messages_api/chats/{peer}/history` (api.php:352-381) short-circuit to an **empty result** for any MG with zero rows in the `subscriptions` table. Moved to open questions below (E) rather than "fixed" — this needs Тари/owner to say whether `subscriptions` rows are seeded manually in the DB (plausible for ~30 known players) or whether the chat-filter-by-discipline feature is simply unreachable from the app. `API Messages.txt` describes an equivalent `master_subscriptions` table, so the feature was clearly designed; whether it was ever wired up client-side is the open question.

**B4 sweep (session 59, 2026-08-18) — every network call in the app, audited for "crashes or
fails silently":**

The B4 crash prompted a project-wide sweep rather than a spot fix. Two scripted passes over
`app/src/main/java/bas/app/shift`: brace-match every `launch`/`async` block and every
`suspend fun`, then flag any that calls one of the 37 methods actually declared in `api/*.kt`
without a `try`/`catch` in the block; separately, flag every `onFailure` whose body shows the
user nothing. Results:

- **Coroutine calls with no `try`/`catch`: 2, both fixed** — `AuraActivity` and `AuraFragment`
  (B4). Everything else in the app already wraps its calls. Re-running the script now reports
  zero, which is the useful outcome: this class of crash is closed, not just the one instance.
- **Other throwers inside coroutines** (`imageLoader.execute`, raw `okhttp` `execute`, file
  IO): zero unprotected. `AuraCanvasView.loadBitmap` already catches.
- **`onFailure` that only logs: 13 sites, 2 fixed.** `MgProfileViewActivity.loadUsers` (the MG
  got an empty player spinner with no explanation) and `ProfileEditActivity.loadAbilities`
  (empty ability list; the fragment then says «Загрузка способностей...» forever) now show
  `NetworkErrors` in a Toast on both the HTTP-error and the exception path. An empty ability
  list cannot corrupt a save — `addAbility` looks the id up in `allAbilities` and no-ops when
  it is missing — so this was a UX hole, not data loss.
- **Left as they are (11 sites).** Five are background workers with no screen to complain to
  (`NoiseManager` ×2, `LocationService`, `NewMessagesChecker` ×2). Five of the six
  `ui/terminal/*` handlers already print the failure into the terminal transcript via
  `adapter.addTyping`, which the audit script did not recognise as user-visible.
  The last one, `TerminalActivity:537`
  (`sendCommandToMg`) is deliberately silent: it mirrors the player's terminal commands to the
  MG chat, and telling the player it failed would expose a mechanic they are not meant to see.

Scripts are throwaway (they lived in the session scratchpad); the method is written down here
because re-deriving it is the expensive part, not re-running it.

### C. Deliberately deferred — judged not worth the risk (do not "fix" without a reason)

- **`EkatMaps.showCreatePointDialog` / `handleMarkerClick` cluster** (~590 lines, `EkatMaps.kt:262-850`) — the only remaining real god-class extraction. Miswired callbacks still compile, so this must be done in one diff with live verification, not in an unattended session.
- **`ShiftApplication.isLocationServiceRunning()` on deprecated `getRunningServices`** — no drop-in replacement; needs its own state flag instead of polling `ActivityManager`. Reliability-critical path, so a spot fix is worse than the warning.
- **`AuraEditorActivity` four CRUD blocks** (add/update/delete × marks/problems) — similar but not identical (different strings, different endpoint semantics); merging judged less readable than the status quo. Decided session 35, upheld since.
- **`ProfileEditFragment.add*/remove*`** — same reasoning, bound to different `User.copy(...)` fields.
- **Further `TerminalActivity` simplification** (generic command path, history, autocomplete) — what is left is architectural, not spot extraction.
- **`ui/terminal/ChatAdapter.kt` 64 px margin instead of dp** — cosmetic; the correct dp value depends on the density the designer eyeballed.
- **`creatorName` rendering as the literal string `"null"`** in artifact spinner labels ("Название / null") — cosmetic.
- **A discipline name renders as mojibake on the profile screen** — encoding issue, pre-existing, never chased down. Cosmetic unless it turns out to be a server encoding bug (would then fold into B1).
- **`lintDebug` cannot run offline** — `com.android.tools.lint:*:31.3.0` is not in the offline Gradle cache. One online build would cache it. Owner's call.

### D. Out of scope by owner decision (security)

Declined deliberately: own trusted server, ~30 known players, single-use game. Listed so nobody
re-opens them as "findings": keystore and signing passwords in the repo, cleartext HTTP,
`debuggable true` + `minifyEnabled false` in release, no sha256 verification on self-update,
`HttpLoggingInterceptor.BODY` always on, MG role decided client-side by the `MG_` prefix.

### E. Blocked on other people (game backlog — see [10-backlog-plan.md](10-backlog-plan.md))

- **#15 chase mechanic** — Лёша (mechanic design) + Тари (does the server cut the point chain per player?).
- **#3 "noise magic breaks the site"** — Женя (endpoints).
- **#25 game-master message feed** — Коля/Тари (broadcast from the admin panel).
- **#7** — deferred by owner decision (whole item, including the `fetchCurrentNoise` fix).
- Open questions to Тари: `assigned_player`/`last_message_time` on points; whether `API Messages.txt` is dead documentation (it describes a completely different API than the one implemented); `API геолокации.txt` is far behind reality.
- **MG chat "subscriptions" (discipline filter) has no client-side management UI** (found session 58, B1). The live `messages_api/subscriptions` endpoints (`POST`/`GET`/`DELETE`, api.php:697-739) are what `GET /messages_api/chats` and `GET /messages_api/chats/{peer}/history` filter on — an MG with zero rows in the `subscriptions` table gets an empty chat list, always. No Kotlin code calls these endpoints. Question for Тари/owner: are `subscriptions` rows seeded directly in the DB for each MG, or is this feature simply unreachable from the app? If the latter, every MG needs a way to pick which disciplines they follow, or the chat list silently stays empty forever regardless of the P1 button-access fix from session 57.

### F. Server-side, for the owner (not fixed autonomously)

- `SERVER/public_html/noize_api/api.php`, route `GET /global` (≈ lines 100–103): returns
  `get_global_noise($pdo)` on the raw 0..10 scale, while `get_user_noise()` used by `/user/{id}`
  normalises to the 0..5 UI scale (`$globalRaw / 2.0`). Harmless today — the client no longer
  has that endpoint (dead `NoiseApi.getGlobalNoise()` removed in session 56) — but it would
  return a doubled value if anything is hung on it later.

## The Doze test protocol (A1) — how to actually run it

Written down so it does not get lost again. It has been the top open item since 2026-07-22 and
has never been executed. Everything below needs a **real phone**; the emulator does not model
deep sleep faithfully (it can be forced into Doze for a smoke test, which is what was done on
2026-08-15, but it never actually suspends the CPU the way hardware does).

**Why it matters.** All background game mechanics live in `LocationService`: it polls location,
asks the server for points, decides whether the player entered a radius, and raises the
notification. The polling loop is `Handler.postDelayed`, which counts `SystemClock.uptimeMillis()`
— and that clock **does not advance while the device is in deep sleep**. So a phone lying still
in a pocket with the screen off can stop checking entirely until someone wakes it. For a field
game where the whole loop is "walk into a zone → get a notification", that is the difference
between the game working and not working.

**What was added on 2026-08-15 (Wave 26).** The mechanism is confirmed working on the emulator
under forced deep idle — the alarm is not deferred by Doze, it fires, and the service does real
network work from inside Doze (details in [08-changes-applied.md](08-changes-applied.md)). What
is still unproven is whether it is *enough*, because an emulator never truly suspends its CPU:
1. A battery-optimisation exemption prompt when the player goes "в игре" (`BatteryOptimization`).
   An exempt app is not subject to Doze network and alarm restrictions at all — this is the
   single biggest lever, and it depends on the player actually tapping "Allow".
2. An `AlarmManager.setAndAllowWhileIdle` heartbeat every 15 min (`LocationHeartbeatReceiver`)
   that wakes the service and runs one point/message check. This alarm type fires in Doze and
   needs no special permission; the system caps it at roughly one firing per 9–15 min, which is
   why the interval is 15.
3. A boot receiver that restarts the service after a reboot or a self-update
   (`BootCompletedReceiver`).

**The protocol (~40 min, plus a second pass):**

1. Install the debug APK on a real phone, log in as a player, grant location **"Allow all the
   time"** (not "only while using"), turn on "В игре". When the exemption dialog appears, tap
   **"Не сейчас"** — the first run must measure the *unexempted* behaviour.
2. `adb logcat > doze-run1.log` and leave it running.
3. Lock the screen and put the phone down **completely still** — Doze requires the device to be
   stationary. Do not touch it for 30–40 minutes.
4. Unlock, stop the log. In the log, check:
   - did the `checkPointsInRange` / `checkForNewMessages` cycles keep ticking, or are there
     30-minute holes;
   - does `LocationHeartbeatReceiver: пульс` appear roughly every 15 min through the sleep —
     this is the mitigation doing its job;
   - is the foreground service still alive at the end.
5. Now grant the exemption (Settings → Battery → Shift → Unrestricted) and repeat steps 2–4 into
   `doze-run2.log`.
6. Compare. The gap between run 1 and run 2 is exactly what the exemption buys, and tells you
   how hard to push players to grant it on game day.

**Also worth testing while you have the phone:** reboot it with "В игре" on and confirm the
service comes back by itself (`BootCompletedReceiver`); and install an update over the top and
confirm the same (`MY_PACKAGE_REPLACED`).

**If run 1 shows holes and run 2 does not**, the conclusion is operational, not code: every
player must grant the exemption before the game starts, and that belongs in the briefing.
**If run 2 also shows holes**, the next code step is moving the polling off `Handler` entirely
onto `setAndAllowWhileIdle` alarms, or shortening the heartbeat and accepting the battery cost.

## Closed directions — do not redo

Each of these was investigated to a conclusion. Re-running them wastes a session.

- **`!!` patterns** — audited three times independently (sessions 21, 30/37, 56), all 92
  occurrences in 20 files. Every one is guarded by a preceding null check; Kotlin simply
  cannot smart-cast `var` fields and callback parameters. No unsafe `!!` exists. Do not redo
  without an actual crash report.
- **Dead code** — swept exhaustively in sessions 18–21 and re-checked in 41. The heuristic
  "declared but ≤1 mention" now yields only framework overrides.
- **`NetworkErrors` / `DisplayNames` coverage** — closed. Remaining raw `response.code()`
  sites are `LogHelper` diagnostics in background services with no user-facing text.
- **Deprecated-API warnings** — `Linkify`, manifest `package=`, `startActivityForResult`,
  `LifecycleObserver`/`@OnLifecycleEvent`, `stopForeground(Boolean)`, zxing `IntentIntegrator`
  all migrated. Only `getRunningServices` remains, intentionally (see C).
- **Handler / `postDelayed` leaks** — swept across `TerminalVisualEffects`, `NoiseManager`,
  `LocationService`, `EkatMaps`, `ConsoleAdapter`, `TerminalActivity`, `ChatsListActivity`;
  all remove callbacks in lifecycle methods.
- **Duplicate keys in `mapOf(...)`** — whole source tree scanned after the `PointVisualizer`
  fix; no other case.
- **TODO/FIXME markers** — none in the sources.
- **RxJava remnants** — gone project-wide, including the gradle dependencies.
- **Map marker tap** (`onMarkerClick` → point info dialog) — verified live under both `Bas`
  and `MG_Bas`, including the proximity gate and the safe degradation on the "your location"
  marker. Struck from the backlog.
- **Retrofit orphan methods** — all interfaces scanned; `NoiseApi.getGlobalNoise` was the only
  one, and it is removed.
- **Line-by-line reading of the remaining files** — near exhaustion. What is unread is mostly
  bare data models, Retrofit interfaces and callback interfaces (`AuraEditorCallback`,
  `AuraMarkCallback`); a poor source of findings. Three real bugs *were* found this way
  (`PointVisualizer`, `MapPointsRenderer`, `NewMessagesChecker`), so it was worth doing — but
  it is spent.
- **False alarms, recorded so they are not re-raised**: the Gson default-enum bug does not
  repeat for `PointType` (`Point.type` is a `String`, Gson never sees the enum); the local
  `currentUserId` shadowing in `ProfileFragment.showProfile` is correct by scope; `AU2`
  bitmap-cache growth is view-scoped and bounded; `MA4`/`MA5` are not reproducible.

## Field notes (techniques and gotchas worth keeping)

**Build / test**
```
cd /home/bas/Shift && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug --offline
```
Add `:app:testDebugUnitTest --offline` for the unit suite, and
`:app:compileDebugKotlin --rerun-tasks --offline` when you want a non-incremental recompile.
Pre-existing deprecation warnings are noise.

**Switching roles on the emulator.** There is no login screen for it — the id lives in
`shared_prefs/user_prefs.xml`, key `current_user_id`. Read with `run-as cat`, edit locally,
push to `/data/local/tmp` (**not** `/sdcard` — `run-as` gets `Permission denied` there), then
`run-as cp`, `am force-stop`, `am start`. **Always restore `Bas` at the end of the session.**
`run-as sed -i` does not work: the outer shell eats `<`/`>`.

**Tap coordinates come from `uiautomator dump` bounds — never eyeballed from a screenshot.**
This rule exists because an estimated tap once hit "Перестать скрывать ауру" and fired a real
`PUT /aura_api/aura/Bas/hidden`, mutating production state (it was detected and reverted in the
same session). If `uiautomator dump` returns a stale hierarchy — which it does in this emulator
— screenshot + per-pixel scan with PIL, and confirm focus with
`dumpsys input_method | grep mServedView`.

**`adb install -r` only.** A stray `adb uninstall` wipes SharedPreferences including the user
id; recoverable through `AuthActivity` ("Ваш Id"), which is the only login entry point, but
avoid it.

**Terminal input on the emulator**: tap the `EditText` first, then `adb shell input text`; send
with the arrow button, not `KEYCODE_ENTER` (Enter triggers a Gboard gesture hint that closes
the Activity). Gboard predictive input occasionally injects a stray leading character.

**Kotlin K2 gotcha**: moving `by lazy { T().apply { … outerVal.addView(this, …) } }` into
another class fails with `Unresolved reference 'addView'`. Workaround: an explicit intermediate
`val` without `apply`.

**A normal player cannot tap a real point marker** without physically entering the radius —
`MapPointsRenderer.refreshMarkersForLocation` only creates the marker when
`distance <= point.radius`, and entering the circle triggers a server call. Under MG all points
render unconditionally and a marker click is read-only. This is why that backlog item hung for
so many sessions.

**Reading `shared_prefs` via `run-as` was once blocked by the auto-mode classifier** as
"credential exploration". Do not try to bypass it — determine the role from a screenshot of the
main screen instead (MG has extra buttons).

## Document map and lifecycle

| File | Kind | Rule |
|------|------|------|
| [00-README.md](00-README.md) | index | update when a document is added or its role changes |
| [01-executive-summary.md](01-executive-summary.md) … [07-dynamic-testing.md](07-dynamic-testing.md) | **frozen** audit snapshot, 2026-07-22 | historical. Do not rewrite findings into "fixed" — current state belongs here in 11. (04 is the exception: subsystem findings were marked up in place during sessions 23–31.) |
| [08-changes-applied.md](08-changes-applied.md) | append-only ledger | one section per wave; add a wave when sessions are rotated out of 09 |
| [09-nightly-progress.md](09-nightly-progress.md) | rolling journal, newest 5–8 sessions | rotate into `archive/` per the rules in its header |
| [10-backlog-plan.md](10-backlog-plan.md) | game-feature backlog (owner + teammates) | owner-maintained; tech debt does **not** go here |
| **11-status.md** (this file) | **living** current state + backlog | every session that opens or closes an item edits this file |
| `archive/` | verbatim old journal | never edited, only appended to as whole files |

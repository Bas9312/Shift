# 11. Current status — single source of truth

> Last actualised: **2026-09-08**, against commit `94aa9d3 Tell the player to report a
> finished chase, note the panel upload` (rebased onto `ac77584 Claude improvements5`).
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
| Size | 126 Kotlin files, ~15.7k lines (audit: 101 files / ~13.8k) |
| Tests | 16 test files, 139 unit tests, `testDebugUnitTest --offline` green (2026-08-17: `FamiliarDataTest` removed with the hardcoded catalog, `FamiliarImagesTest` added) |
| Build | `assembleDebug --offline` green (exit 0), re-confirmed 2026-09-08 after the rebase |
| Working tree | clean; several commits sit **ahead of `origin/master`** and unpushed. **Pushing is the owner's step** — this machine has no credential helper, no `~/.git-credentials` and no `gh`, so an automated session can commit but cannot push. Do not spend time debugging that. (`gradlew.bat` reappears as modified whenever a Windows tool touches it; the diff is line endings only, `git checkout -- gradlew.bat` clears it.) |
| God-class sizes | `EkatMaps` **1234** (was 1119 on 2026-08-15 — the chase and aura-sensing work put ~115 lines back), `MainActivity` 713 (663), `TerminalActivity` 543 (543), `LocationService` 468 (402) |
| Remaining compiler warnings | one: `ShiftApplication.isLocationServiceRunning()` uses deprecated `getRunningServices` (deliberate, see below) |

**Note the god-class row moved the wrong way.** Three of the four numbers grew between
2026-08-15 and 2026-08-20 (`EkatMaps` +115, `LocationService` +66, `MainActivity` +50), because
the chase chain and the aura-sensing FAB were added to the existing classes rather than to new
ones. That was the right call under time pressure — a feature in one diff beats a refactor plus
a feature — but `EkatMaps` is now back to 1234 lines against the 1237 it had at the audit: the
entire extraction gain on that file has been spent. §C's "the only remaining real god-class
extraction" is priced accordingly.

**The long-running "owner should commit the working diff" nag is closed.** It appeared in
almost every nightly entry from session 15 to 56; the diff is committed. Untracked paths are
`analysis/screenshots/` (12 MB of emulator verification captures), `_local/` (machine-local
state, synced outside git) and `.claude/` (agent definitions) — all gitignored except the last.

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
unit-test suite from zero to 146 tests (139 today — `FamiliarDataTest` went out with the
hardcoded catalogue in Wave 27), and a series of small real bug fixes found by
line-by-line reading (`PointVisualizer` duplicate map key, `NewMessagesChecker` hardcoded
unread count, the Aura Editor stuck permanently on a loading spinner).

The honest caveat: a large part of the recent work is **quality, not new reliability**. The
low-risk reliability backlog from the original audit has been exhausted for several sessions;
what is left is either blocked on a human with a real device, or is architectural work that
was deliberately judged too risky for an unattended session.

### What changed since 2026-08-15 (the previous actualisation)

Three weeks in which the centre of gravity moved off the client and onto everything around it.
Details are in [08-changes-applied.md](08-changes-applied.md) Waves 27–29; the short version:

- **Familiar and aura artwork left the APK** (2026-08-17/18, spec in
  [12-familiars-remote-assets.md](12-familiars-remote-assets.md), commit `4ca3304`) — 36 `webp`
  files (~6.3 MB) now come from `shift96.ru` with a disk cache, the hardcoded familiar `Map` is
  gone in favour of a server catalogue, and `users.familiar` is validated against it. A new
  familiar no longer means a new APK on 30 phones.
- **The offline crash on the aura screens** (B4) and **the `isInGame()` default bug** (B5) were
  found and fixed by nightly sessions 59 and 61, the second one still awaiting a live check (A8).
- **A GM web admin panel exists** (`shift96.ru/gm/`, ~2000 lines of PHP), all six planned stages
  shipped plus an independent QA pass whose every finding was fixed the same day, plus a
  chains page added later. Full record in [13-gm-web-admin-plan.md](13-gm-web-admin-plan.md).
  This is what closed the "the app has no UI for X" class of problem without shipping an APK:
  subscriptions, point editing, aura marks, noise, catalogues and chain steering are now all
  editable by a master through a password-protected page instead of through phpMyAdmin.
- **The chase mechanic (#15) was built for real** (2026-08-20) with branching, dead ends and
  server-side progress, and **a psychic can now read the aura of places that are not on the
  map at all** through a "listen to the place" FAB. Both are described in
  [10-backlog-plan.md](10-backlog-plan.md) §0е and §#15.
- **"Аркан Оверфлоу" happened entirely outside this document set** — an in-world Stack Overflow
  on `shift96.ru/arcaneoverflow/` filled by a pipeline of character agents. It is game content,
  not client code, which is why it never appeared here; it now has its own summary in
  [14-arcaneoverflow.md](14-arcaneoverflow.md) so that the analysis folder stops implying the
  client is the whole project.

Two consequences worth stating plainly. First, **the client itself has barely moved on
reliability since 2026-08-15** — B4 and B5 are the only two reliability fixes in three weeks,
and A1 is still untouched. Second, **the panel changed what "blocked" means**: several §E items
were blocked on "the app cannot do this", and the answer turned out to be "so do it from the
panel". Anything still in §E should be re-read with that in mind before it is called blocked.

## The 2026-07-22 top-20, re-verified against current code (2026-08-15)

Verdicts below come from reading the current sources, not from the fix documents. Line numbers
are current — every line reference in documents 01–07 is stale after the refactors.

> **Not re-verified on 2026-09-08.** The 2026-09-08 actualisation updated the snapshot, the
> backlog and the change ledger, but did **not** re-read all twenty rows against the code. Rows
> touching `EkatMaps`, `LocationService` and `MainActivity` are the ones most likely to have
> drifted, since those three files grew during the 2026-08-20 feature work.

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
| ~~A8~~ | ~~Live check of the session-61 `isInGame()` default fix (B5)~~ | **Done 2026-09-08 on `emulator-5554`, passed.** `pm clear` → log in as `bas` → grant notifications and location ("While using the app", i.e. the permissive case) → land on `MainActivity`: `game_state.xml` reads `is_in_game=false`, the segmented control shows **«Не в игре»**, the log says `updateUI - isMgUser: false, isInGame: false`, and `LocationService` received only `ACTION_STOP_LOCATION` with `startForegroundCount=0` — no foreground notification, no tracking. Control case, to prove the test could fail: restoring `is_in_game=true` and relaunching produced `ACTION_START_LOCATION` and `startForegroundCount=1`. Emulator state (user `bas`, in-game) was backed up before and restored after. |
| A9 | **The chase mechanic from a phone, not from the API** | The 2026-08-20 verification ran against the live server (`CHASETEST`: start → fork → dead end → restart → finish, plus the 409 and the idempotent-repeat cases) and it passed end to end. What that run did **not** cover is the client half in the field: whether `ChaseNotifier` actually raises each notification on a device, whether its one-minute dedup really suppresses the double delivery (`/users/location` **and** `/points/{id}/enter` both report the same entry), and whether an entry detected while the screen is off survives to a notification at all. That last one is A1 wearing a different hat. **Attempted 2026-09-08 and blocked on test data, not on the app:** there is no chase chain in the live database to walk, and creating one means writing `quests` and `point_links` — tables `api_geo` deliberately exposes no endpoint for (see [13-gm-web-admin-plan.md](13-gm-web-admin-plan.md) §15), so it is a direct write to the production database or a session in the GM panel. Cheapest unblock: the owner builds a throwaway chain on the «Цепочки» page (start → fork → dead end → finish), which takes a couple of minutes there, and the walk-through can then be driven from the emulator with `adb emu geo fix`. |
| ~~A10~~ | ~~The aura-sensing FAB, live~~ | **Done 2026-09-08 on `emulator-5554`, passed.** Standing at the centre of the "Аура ЕСТЬ" test point (56.83917, 60.6056983): the FAB is there for `bas` (a psychic), the dialog shows exactly one text, and the control point 120 m away — whose aura text literally reads «Этот текст игрок увидеть не должен» — is **not** in it, so the 50 m rule for visible points holds. Moved to the three `POINT_WITH_TEXT` rows at 55.755826, 37.617299, which `MapPointsRenderer` never draws: the FAB read all three (`считано 3 из 43 точек`) and showed their texts separated by `⁂`, with no names and no distances anywhere in the dialog. That is the feature's whole reason to exist — auras of places the player cannot see — confirmed working. **One branch is still unproven:** `auraReadRangeFor()`'s `hidden == 1` half. No hidden point in the live data has an aura text, and the only way to make one was a write to the production database, which was refused. The `POINT_WITH_TEXT` half of the same condition is proven, and the two share one line of code. |

### B. Available right now, no emulator needed

| # | Item | Notes |
|---|------|-------|
| B1 | **Cross-check Kotlin `api/*.kt` against the real server PHP** | **Done 2026-08-17**, all pairs checked, no live client-side bug found. Findings below. |
| B2 | ~~`expireAt` format mismatch~~ | **Re-investigated 2026-08-18, original claim was wrong — see F.** `DateTimeHelper.formatExpireAt`'s parser matches the server's GET format fine; the real bug is that the MG's custom expiry input is dead at three layers and never reaches the server at all. |
| B3 | Unit tests for `WikipediaHelper` / `UserPrefsHelper` | Needs Mockito or Robolectric, i.e. one non-`--offline` Gradle sync to add the dependency. **Owner's call** — nightly sessions run offline. |
| B4 | **Offline crash on the aura screens** | **Done 2026-08-18.** `AuraActivity.kt:59` and `AuraFragment.kt:77` both called `auraApi.getAura()` inside `lifecycleScope.launch(Dispatchers.IO)` with no `try`/`catch`. The `isSuccessful` branch handled HTTP errors, but a thrown `UnknownHostException` killed the process. Reproduced on the emulator: airplane mode, open an aura → `FATAL EXCEPTION: DefaultDispatcher-worker-1`, `Force finishing activity bas.app.shift/.ui.AuraActivity`, process gone. Both now use the house pattern (`launch` on the main dispatcher, `withContext(Dispatchers.IO)` around the call, `catch` → `NetworkErrors.network(e)` + `LogHelper.e`). Re-verified offline: the screen stays up and shows «Нет связи с сервером»; online it still renders. Pre-existing, unrelated to the familiar work; found while verifying the webp silhouettes. Note the aura JSON is never cached, so offline the screen has nothing to draw either way — the fix is about not crashing, not about working offline. |
| B5 | **`isInGame()` default value bug** | **Done 2026-08-19.** `ShiftApplication.isInGame()` defaulted the `game_state`/`is_in_game` pref to `true` when unset, while every other reader of the same key (`EkatMaps` ×2, `LocationHeartbeatReceiver`, `BootCompletedReceiver`) defaults to `false`. On a brand-new install or right after registration, before the player ever touches the "В игре" switch, this made `MainActivity` show the toggle already checked and both `ShiftApplication.onStart` and `checkAndStartLocationService()` try to auto-start background location tracking — not a declined hardening item, a plain wrong default. Fixed to `false`, matching the other four readers. **Not verified live** — no emulator this session, see A8. |

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

- **`EkatMaps.showCreatePointDialog` / `handleMarkerClick` cluster** (~700 lines; `handleMarkerClick` now starts at `EkatMaps.kt:281`, `showCreatePointDialog` at `:694`, with the aura-sensing helpers between them from `:473`) — the only remaining real god-class extraction, and bigger than when it was first deferred. Miswired callbacks still compile, so this must be done in one diff with live verification, not in an unattended session.
- **`ShiftApplication.isLocationServiceRunning()` on deprecated `getRunningServices`** — no drop-in replacement; needs its own state flag instead of polling `ActivityManager`. Reliability-critical path, so a spot fix is worse than the warning.
- **`AuraEditorActivity` four CRUD blocks** (add/update/delete × marks/problems) — similar but not identical (different strings, different endpoint semantics); merging judged less readable than the status quo. Decided session 35, upheld since.
- **`ProfileEditFragment.add*/remove*`** — same reasoning, bound to different `User.copy(...)` fields.
- **Further `TerminalActivity` simplification** (generic command path, history, autocomplete) — what is left is architectural, not spot extraction.
- **`ui/terminal/ChatAdapter.kt` 64 px margin instead of dp** — cosmetic; the correct dp value depends on the density the designer eyeballed.
- **`creatorName` rendering as the literal string `"null"`** in artifact spinner labels ("Название / null") — cosmetic.
- ~~**A discipline name renders as mojibake on the profile screen**~~ — **not a defect. Closed for good 2026-09-08, do not re-open.** The discipline is id 9, Шумомантия, and its mangled name (`ШЖ╫■┐ьЮ≈╒╬м╤нт&╜╓я`) is stored that way **on purpose**: noisemancy is the neomagic discipline that works through the internet, so a corrupted name is the point of it. The owner likes it and has now rejected this "finding" three separate times. It is correct data everywhere it appears — profile screen, forum filter, GM panel. Do not rename the row, do not propose a migration, do not list it as a finding. The only real rule that follows from it: a check for a noisemancer must pass on **both** the garbled name and the plain string `Шумомантия`, which matching on `id == 9` (as `MainActivity.kt:339` already does for the terminal button) satisfies by construction. This was **already** recorded in [13-gm-web-admin-plan.md](13-gm-web-admin-plan.md) §9.1 on 2026-08-18 — it got raised again anyway because §9 of that document is not where anyone looks for "is this a bug". Hence this entry, in the file that is meant to be the single source of truth.
- **`lintDebug` cannot run offline** — `com.android.tools.lint:*:31.3.0` is not in the offline Gradle cache. One online build would cache it. Owner's call.

### D. Out of scope by owner decision (security)

Declined deliberately: own trusted server, ~30 known players, single-use game. Listed so nobody
re-opens them as "findings": keystore and signing passwords in the repo, cleartext HTTP,
`debuggable true` + `minifyEnabled false` in release, no sha256 verification on self-update,
`HttpLoggingInterceptor.BODY` always on, MG role decided client-side by the `MG_` prefix.

### E. ~~Blocked on other people~~ — **nothing is, as of 2026-09-08** (game backlog — see [10-backlog-plan.md](10-backlog-plan.md))

This section existed because four items were waiting on teammates. All four are now resolved,
none of them by the teammate doing the work:

| Was blocked on | Outcome |
|---|---|
| Лёша — chase game design | Questions dropped: they do not change the client (below) |
| Женя — site endpoints for #3 | **Item deleted.** Женя is doing it entirely on the site; the app needs no support for it at all |
| Коля/Тари — GM broadcast feed (#25) | **Deferred by the owner**, not waiting on anyone. Not in this game |
| Тари — three API questions | **Answered by reading the server and querying the live DB** (below). Nothing to ask |

The lesson worth keeping: three of the four were not really blocked, they were unasked. Reading
`SERVER/` and the live schema answered in twenty minutes what had been sitting in the backlog
as "ask Тари" for six weeks.

**One new item took their place, and this one really is someone else's (found 2026-09-08):**

- **The familiar chat answers nothing — the GPT proxy 500s on every send.** The service behind
  `CHAT_BASE_URL` (`91.184.253.175`, calls itself `SHIFT GPT Proxy 1.1.0`) accepts a message,
  **writes it into the history**, and then fails: `POST /chat/send` returns **HTTP 500 in ~0.5 s**
  for every familiar tried (`familiar_weird_compass`, `familiar_fox`, `familiar_mirror`,
  `familiar_earth_cat`) and for more than one `user_id`. Half a second is far too fast to be a
  model timeout — it fails before reaching the upstream, or on the very first call to it
  (expired key, exhausted quota, upstream down are all consistent).
  Everything around it looks healthy and that is the trap: `GET /health` returns `{"ok":true}`
  without touching the upstream at all, `GET /familiars` lists all nine as `configured: true`,
  and `GET /chat/history` works. **Not our bug** — the client sends a correct request and the
  server takes it. The player-visible symptom is the worst possible one: the message appears in
  the chat and the familiar simply never replies, which reads as a broken app.
  `bas`'s own history shows this happening at 14:48 on 2026-09-08, before any of this
  investigation. Needs whoever runs that host; nothing on our side to fix.

**What the server actually said (2026-09-08):**

- **A point with `createdAt` in the future is hidden, not served.** `GET /points` filters on
  `(createdAt <= NOW())` inside the `$baseWhere` shared by both the MG and the player branch
  (`api_geo/api.php:283`), so a future date behaves as scheduled publication. The old "#13 risk"
  does not exist. Note the MG does not see such a point on the map either — only in the panel,
  which reads the table directly. Live count of future-dated points: zero.
- **`assigned_player` / `last_message_time` are the familiar-chat lock.** When a player starts
  talking to a `FAMILIAR` point, the row is stamped with their id and the time
  (`api_geo/api.php:585`); `POST /points/{id}/touch` refreshes it. `releaseExpiredFamiliars()`
  clears any familiar whose last message is older than `FAMILIAR_HOLD_MINUTES = 15`, so a
  familiar frees itself a quarter of an hour after the conversation stops. The panel shows
  «занята: X» and can release one by hand. Types: `varchar(255)` (indexed) and `timestamp`.
- **`API Messages.txt` is a design draft that was never built.** Its whole schema rests on
  `chats` and `master_subscriptions`; neither table exists in the live database. Not stale
  documentation — documentation of a cancelled design. Do not diff code against it.

- ~~**#15 chase mechanic**~~ — **closed completely, 2026-09-08.** Built on 2026-08-20 with branching, dead ends and a panel page; the server cuts the chain per player, so the client keeps no chain state ([10-backlog-plan.md](10-backlog-plan.md) #15). The game-design questions that were still hanging on Лёша (personal vs team progress, whether a finish hands anything out, whether a point may be skipped) were dropped by the owner: none of them change the client, which only has to show that the chase finished, and it does. Not blocked on anyone any more.
- ~~**#3 "noise magic breaks the site"**~~ — **deleted 2026-09-08.** Женя is implementing it on the site; the client needs no terminal commands, no endpoints, nothing. Do not re-add it.
- **#25 game-master message feed** — **deferred by the owner 2026-09-08**, not blocked. Not happening this game. If it ever returns: the client needs a "no replies allowed" mode, `Message` has no flag for it, and it would have to ride on `tags` — agree the tag first.
- **#7** — deferred by owner decision (whole item, including the `fetchCurrentNoise` fix).
- `API геолокации.txt` is far behind reality (radius, hiding, place auras, the chase — all missing from it). Not worth chasing: the code is the source of truth and the discrepancies are catalogued in [10-backlog-plan.md](10-backlog-plan.md) §5.
- ~~**MG chat "subscriptions" (discipline filter) has no client-side management UI**~~ — **closed 2026-08-18 by the GM panel, not by the app.** The open question was "who seeds `subscriptions` rows, since no Kotlin code calls those endpoints, and an MG with zero rows gets a permanently empty chat list". The answer chosen was to stop needing the app for it: `gm/pages/chats.php` renders a GM × discipline matrix, rewrites only the masters carried in the POST, and adds a coverage table that flags disciplines nobody reads and disciplines with a single master. The client is unchanged and does not need to be — shipping an APK to 30 phones to add a settings screen was the worse option. Still worth an actual look before the game: open the coverage table and confirm no discipline is uncovered.

### F. Server-side, for the owner (not fixed autonomously)

- `SERVER/public_html/noize_api/api.php`, route `GET /global` (≈ lines 100–103): returns
  `get_global_noise($pdo)` on the raw 0..10 scale, while `get_user_noise()` used by `/user/{id}`
  normalises to the 0..5 UI scale (`$globalRaw / 2.0`). Harmless today — the client no longer
  has that endpoint (dead `NoiseApi.getGlobalNoise()` removed in session 56) — but it would
  return a doubled value if anything is hung on it later.
- **SHRINKING_CIRCLE custom expiry is dead at all three layers — the whole "expires in N
  minutes" input is decorative** (found 2026-08-18, replaces the old wrong B2 claim above):
  1. **UI**: `EkatMaps.kt`'s `showCreatePointDialog` type-switch sets
     `etExpireMinutes.visibility = View.GONE` in *all three* branches (`FAMILIAR:674-675`,
     `SHRINKING_CIRCLE:685-686`, `else:696-697`) — the minutes field is never shown for any
     point type, including the one it's meant for. Every other field in that switch is
     correctly toggled per type; this looks like a genuine "forgot the `VISIBLE` case" bug,
     not intentional.
  2. **Client model**: even if the field were visible and filled in, `EkatMaps.kt:787-795`
     computes an `expireAt` string from it but never puts it on the request — `PointRequest`
     (`models/PointRequest.kt`) has no `expireAt` field at all, so the value is computed,
     logged, and discarded.
  3. **Server**: even if the client did send `expireAt`, `api_geo/api.php`'s `POST` handler
     (≈ line 304) never reads `$input['expireAt']` — it hardcodes
     `$expireAt = ($type == 'SHRINKING_CIRCLE') ? date('Y-m-d H:i:s', strtotime($createdAt) + 1800) : null`,
     i.e. every SHRINKING_CIRCLE always expires in exactly 30 minutes, full stop.
  Net effect: no MG can ever have made a SHRINKING_CIRCLE last longer or shorter than 30
  minutes through the app, regardless of what they typed — because they were never able to
  type anything in the first place. Not fixed autonomously: fixing only the client would be
  a no-op (server still ignores it) and fixing only the UI visibility would be actively worse
  (shows the MG a working-looking input that silently does nothing). Needs a product decision
  from the owner/Тари — either wire all three layers so the input actually controls expiry,
  or delete the dead input and hardcode the 30-minute assumption client-side too so the UI
  stops lying about it. The original B2 entry ("format mismatch" between
  `DateTimeHelper.formatExpireAt` and the server) was checked and is **not** a real bug —
  the server returns `Y-m-d H:i:s` on GET (MySQL `DATETIME` via `SELECT *`), which is exactly
  what the parser expects.

  **Decided and done, 2026-09-08: the dead input is gone from the app.** The owner chose to
  delete it rather than wire all three layers. `tvExpireLabel`/`etExpireMinutes` are out of
  `dialog_create_point.xml`, the six `View.GONE` lines and the `expireAt` string that was
  computed and thrown away are out of `EkatMaps`, and `point_expire_label`/`point_expire_hint`
  are out of `strings.xml`. The server keeps its hardcoded 30 minutes and the panel remains the
  way to set anything else — which is now stated in a comment at both former call sites, so the
  next reader does not "restore" the field. Nothing else changed: `PointRequest` never carried
  the value, so no request shape moved. The rest of this entry is kept as the record of why.

  **Update 2026-09-08 — the decision got cheaper.** The GM panel's point editor
  ([13-gm-web-admin-plan.md](13-gm-web-admin-plan.md) stage 4) already edits a point's expiry
  directly, so a master who needs a circle to last other than 30 minutes has a working way to
  do it *today*, just not from the phone. That makes "delete the dead input from the app and
  let the panel own expiry" the low-risk option: it costs three lines in `EkatMaps`, removes a
  UI element that has never once worked, and loses no capability anyone actually has. Wiring
  all three layers is still the better answer if MGs are expected to create timed circles
  while walking around the city — which is a question about how the game is run, not about the
  code.

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
| [12-familiars-remote-assets.md](12-familiars-remote-assets.md) | spec, **delivered** 2026-08-18 | closed; kept for the schema and cache reasoning |
| [13-gm-web-admin-plan.md](13-gm-web-admin-plan.md) | plan **and** build log of the GM panel | all six stages shipped, QA pass closed, chains page added — append when the panel changes |
| [14-arcaneoverflow.md](14-arcaneoverflow.md) | overview of the in-world forum and its agent pipeline | game content, not client code; no credentials in it |
| `archive/` | verbatim old journal | never edited, only appended to as whole files |

**Scope note.** 01–11 are about the Android client. 13 and 14 are about the server side, which
stopped being out of scope on 2026-08-18 when the owner authorised direct edits. A reader who
only opens 11 will conclude the project is an Android app that has been quiet for three weeks;
that conclusion is wrong, and this table is where it gets corrected.

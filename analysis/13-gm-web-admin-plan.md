# 13. GM web admin panel — inventory and implementation plan

> Written 2026-08-18 against the live database (`bas931wn_inst1`, MySQL 8.4), the live server
> tree mirrored in `SERVER/`, and the Android client at commit `9671fce`.
> Purpose: give whoever runs the game a way to edit game data without opening MySQL, and give
> a coding agent an unambiguous build order.
>
> Game shape this plan is sized for: **~30 players, ~9 GMs, 4 days, one shot.** Not a product.
> Everything below is chosen for "cheapest thing that removes real pain", not for beauty.
> UI strings are Russian (GMs read them); everything else is English per `CLAUDE.md`.

## 1. The actual problem

Right now a piece of game data lives in one of four states:

1. **Comfortable in the app** — the GM opens a phone screen and edits it (aura marks, profile
   disciplines, map points, chat).
2. **Editable in the app but painful at scale** — fine for one player, miserable for thirty
   (artifacts, marks for every player before the game).
3. **Only in MySQL** — no API, no screen. Somebody has to open a SQL client mid-game.
4. **Nowhere** — no API path, and the app never exposed it (point texts, effect editing).

States 3 and 4 are the ones that will hurt, because during the game the person at the wheel is
not the owner and will not be writing SQL.

## 2. Inventory — every game entity, where it can be edited today

Non-game tables (`inst_*`, InstantCMS) are out of scope. Row counts are live as of 2026-08-18.

| Entity (table) | Rows | App (GM) | HTTP API | Verdict |
|---|---|---|---|---|
| `users` — passport: `player_name`, `name`, `type` | 38 | ✗ | ✗ (dead code exists, §4.3) | **SQL only** |
| `users` — `showUser`, `lat`, `lng`, and `name` on every ping | | phone owns them | overwritten by geo upsert | **not editable at all** (§4.9) |
| `users` — game: `disciplines`, `modules`, `abilities`, `instrument`, `familiar`, `misc` | | ✓ ProfileEdit | `PUT /mage_profile_api/api/v1/user/{id}` | OK |
| `users` — `artifacts` (id list), `effects` (json) | | ✗ | ✗ (only via effects API side-effect) | **SQL only** |
| `users` — creating a new player | | ✗ | implicit, broken (§4.5) | **SQL only, and it is the pre-game bottleneck** |
| `auras` — `type`, `percent_of_humanism`, `aura_image` | 28 | ✗ | ✗ | **SQL only** — and it drives the aura artwork players see |
| `auras` — `aura_hidden` | | ✓ | `PUT /aura_api/aura/{id}/hidden` | OK |
| `aura_marks` | 188 | ✓ AuraEditor | full CRUD, **broken for `ABILITY` marks** (§4.10) | OK, but 30 players on a phone is slow |
| `aura_problems` | 6 | ✓ AuraEditor | full CRUD | OK |
| `points` — create / delete | 78 (32 are ephemeral `USER` pins) | ✓ map long-tap | `POST` / `DELETE` | OK |
| `points` — edit `hidden`, `next_point_id`, `trackable`, `aura_text` | | partly | `PATCH` (these four only) | thin |
| `points` — edit `description`, `textToShowOnEnter`, `lat`, `lng`, `radius`, `expireAt`, `type` | | ✗ | ✗ | **nowhere — a typo means recreating the point** |
| `subscriptions` — which disciplines a GM sees in chat | 29 | ✗ | `POST`/`DELETE` exist, **no client uses them** | **SQL only, and it gates the chat (§4.4)** |
| `abilities` (catalogue) | 22 | ✗ | `GET` only | **SQL only**, renaming has fallout (§4.11) |
| `magic_discipline` (catalogue) | 11 | ✗ | `GET` only | **SQL only**, renaming has fallout (§4.11) |
| `magic_modules` (catalogue) | 33 | ✗ | `GET` only | **SQL only**, renaming has fallout (§4.11) |
| `familiars` (catalogue) | 23 | ✗ | `GET` only | **SQL only** (+ artwork over FTP), see §4.11 |
| `artifacts` | 86 | ✓ creator/editor | `POST` / `PUT`, **no `DELETE` anywhere** | OK per item, painful in bulk; a mistyped artifact could not be removed at all before the panel |
| `effects` | 0 live | ✓ create/delete | `POST` / `DELETE` | **no edit path — only delete and recreate** |
| `noisemancy_global` | 1 | ✗ | `GET` only | **SQL only** — cannot set the world noise mid-game; crons move it (§4.12) |
| `noisemancy_local` | 8 (3 are phantom ids) | terminal (indirect) | `GET`, `POST .../adjust` (delta only) | can nudge, cannot set |
| `messages` / `message_tags` / `attachments` | 1369 / 1369 / 136 | ✓ chat | full | OK; no broadcast (§7 P2) |
| `ritual_data`, `ritual_symbols_completion` | 1 / 30 | ✓ ritual screen | `ritual.php` | OK |
| `quests`, `user_quest_progress`, `point_links` | 0 / 0 / 0 | ✗ | `POST /quests` | was a dead feature; revived 2026-08-20, panel page «Цепочки» (§15) |
| `arcane_*` (Arcane Overflow) | 3/5/5/4 | web page | web page | OK |
| `user_point_visits` | 25 | ✗ | written by server | read-only in panel |

**Short version:** the game's *content* (players, catalogues, point texts, aura identity) is
exactly the part with no editor, while the parts that already have editors are the ones the
players themselves drive.

## 3. Recommendation

Build a **server-rendered PHP panel** at `https://shift96.ru/gm/`, no build step, no framework,
no JS dependencies. (Stages 0 and 1 are live there since 2026-08-18 — see §11. Sources are
mirrored in the gitignored `SERVER/public_html/gm/`; the shared password is in the private
`shift-gm-panel` memory file, not in this repo.) Reasons:

- The backend is already PHP 8.2.28 on beget shared hosting; `arcaneoverflow/index.php` is a
  working precedent for the *shape* (session, CSRF token, PDO, hand-written HTML forms). Note it
  is not fully standalone — it `require`s `../bootstrap.php` and rides the InstantCMS session
  and the `mage_profile_api` PDO. The panel deliberately will not: it needs its own session and
  its own credentials (§10), so copy the patterns, not the includes.
- Any SPA needs a build pipeline and a deploy story on hosting where the deploy is FTP.
- GMs will be on phones in the street. Server-rendered forms degrade gracefully on 3G;
  a React bundle does not.
- A coding agent can write and verify this file-by-file without a toolchain.

`/admin` is **taken** by InstantCMS (returns 200). Use `/gm/`.

## 4. Constraints found in the code — read before writing anything

### 4.1 The APIs have no authentication at all

Every `*_api` endpoint is open to the world; `Access-Control-Allow-Origin: *` on most of them.
The panel does not make this worse — but it means panel security is only about keeping GMs'
hands off each other's browsers, not about protecting the data. The owner accepted this
trade-off for the client already (see `analysis/05-security.md`). One thing worth fixing anyway:
`users_monitor.php` sits at the site root with **no password** and lists every player and their
last-seen time.

### 4.2 The profile API has side effects; direct SQL does not

`put_user_update()` (`SERVER/public_html/mage_profile_api/api.php:123`) does more than write
columns. On change it also rewrites the player's aura:

- `sync_magic_discipline_marks()` — creates/deletes `MAGIC_DISCIPLINE` marks and recomputes
  `number_of_stars` from the module ids (`intdiv(module_id, 10) == discipline_id`);
- `sync_abilities_marks()` — creates/deletes `ABILITY` marks;
- `sync_instrument_link()` / `sync_familiar_link()` — maintain `INSTRUMENT_LINK` / `FAMILIAR_LINK`.

So editing `users.disciplines` straight in SQL silently desynchronises what psychics see in the
aura. **This is the single most important rule in this plan** — see §5.

### 4.3 `patch_user()` is written, wired to nothing

`mage_profile_api/api.php:669` implements a partial update over `ALLOWED_FIELDS` from
`mage_profile_api/config.php`, which is *wider* than the PUT whitelist: `player_name`, `name`,
`type`, `artifacts`, `effects`. The router never dispatches to it — `PATCH` appears only in the
CORS header. Enabling it is ~4 lines and hands us the missing passport fields.

Note it deliberately does **not** run the aura sync, so use it only for passport fields, never
for disciplines/abilities.

### 4.4 A GM with no subscriptions sees an empty chat list

`GET /messages_api/chats` (route at `messages_api/api.php:518`) returns `[]` immediately when the
GM has no rows in `subscriptions` (`:532`).
Current live state:

| GM | Subscribed disciplines |
|---|---|
| `MG_Bas` | 1–11 (everything) |
| `MG_TARI` | 1, 8, 9, 10 |
| `MG_ANTE` | 3, 5, 10, 11 |
| `MG_JOHN` | 2, 6, 10 |
| `MG_LESHA` | 10, 11 |
| `MG_FARASH`, `MG_ISA`, `MG_SASHA` | 10 only |
| `MG_MARINA` | 10, 10 (duplicate row — no unique key on the table) |

Disciplines **4 (Проклятья и Благословения)** and **7 (Ментальная магия)** are covered by
`MG_Bas` alone. Every other discipline except **10** and **11** is covered by exactly two
people — always `MG_Bas` plus one. Take the owner out of the rotation and nine of eleven
disciplines are one absence away from unanswered. There is no UI anywhere to fix this — SQL only.
The same empty-list behaviour also guards `GET /chats/{peer}/history` (`api.php:378`).

### 4.5 Players are auto-created by the geolocation endpoint, half-formed

`POST /api_geo/api/v1/users/location` upserts into `users` with only `userId, name, lat, lng,
showUser` (`api_geo/api.php:141`). Login in the app is "type your id, no password"
(`ui/AuthActivity.kt`). Consequences:

- a typo'd id creates a **new player** with an empty profile;
- there is already a junk row with `userId = ''`, `name = 'Unknown User'`;
- `auras` rows are created lazily on first aura read, defaulting to `type='human'` while
  `users.type` defaults to `'mage'` — the two can disagree from birth.

The panel's "create player" flow must write `users` + `auras` together and then push the game
fields through the API so the marks appear.

### 4.6 Point edit is nearly nonexistent

`PATCH /api/v1/points/{id}` accepts exactly `hidden`, `next_point_id`, `trackable`, `aura_text`
(`api_geo/api.php:439`). `description` and `textToShowOnEnter` — the text a player actually
reads on entering — are create-only. Typos currently cost a delete + recreate, which loses the
chain link and the `user_point_visits` history.

### 4.7 Mark artwork is a URL into a folder we control

`aura_marks.image_url` and `magic_discipline.mark_image_url` point at
`http://shift96.ru/static/images/*.png`, a directory on the same host. The panel can `glob()`
it and offer a picker with thumbnails instead of asking a GM to type a URL on a phone.

Watch the mismatch: mark images are `http://` + `.png`, while `auras.aura_image` uses
`https://` + `.webp` (5 live rows, e.g. `aura_yulia.webp`). The picker must glob
`{*.png,*.webp,*.jpg}` and keep each field's existing scheme, or the aura-artwork picker will
come up empty.

### 4.8 Routing — and why the panel should not need a rewrite rule

The root `.htaccess` sends everything not matching a real file to InstantCMS
(`RewriteCond %{REQUEST_FILENAME} !-f` … `RewriteRule ^(.*)$ ./index.php [L]`), with explicit
bypasses for `aura_api`, `effects_api`, `mage_profile_api`, `artifacts_api`, `messages_api`,
`familiars_api`. `api_geo`, `noize_api` and `aura_api` additionally carry their own per-directory
`.htaccess` with a `RewriteBase`.

The cheapest safe route for the panel: **address every page as a real file with a query string**
— `/gm/index.php?p=players`. The CMS fallback only fires when the target is neither a file
(`!-f`) nor a directory (`!-d`), so a real file under a real directory never reaches it — this
is why `/arcaneoverflow/` works today with no root bypass and no `.htaccess` of its own. The
root `.htaccess` (live, shared, CMS-critical) therefore does not have to be touched at all.
Pretty URLs are not worth that risk.

Two details: every `*_api` directory carries its own `.htaccess` with a `RewriteBase` (that is
how `/api/v1/...` paths resolve to `api.php`), and the root file 301s `/gm/` → `/gm` via the
trailing-slash rule until the directory actually exists. Local and live `.htaccess` are
currently identical.

### 4.9 `showUser`, `lat`, `lng` and `name` are owned by the phone, not the server

`POST /api/v1/users/location` writes `name`, `lat`, `lng`, `showUser` on **every** ping. The
`show` flag comes from a device-local preference (`helpers/UserPrefsHelper.kt:63`), which is
never seeded from the server — the client parses `showUser` off the profile and no code consumes
it. So a panel edit to any of these four columns is reverted seconds later by the player's
phone. The panel must show them **read-only** ("телефон перезапишет"), and "hide a player from
the map" is a thing to ask the player to toggle, not a thing the panel can do. `name` self-heals
only because the client refetches the profile and pushes its own copy back.

### 4.10 The aura API cannot edit `ABILITY` marks

`aura_api/index.php:257` rejects any `PUT .../marks/{id}` on a mark whose type is `ABILITY`
unless `description` is a numeric ability id. But the marks created by the profile sync store the
ability's **Russian text**, not an id (`mage_profile_api/api.php:472`; verified live — mark 174
holds `Переход в Мир духов…`). Since the handler falls back to the stored description when the
request omits it, editing *any* field of those 21 marks returns 400. Consequence for the panel:
`ABILITY` marks are managed through the player's ability list (PUT profile), or by direct SQL —
never through the marks endpoint.

### 4.11 Catalogue rows are joined by name, not only by id

The catalogues look like inert reference data. They are not:

- `sync_magic_discipline_marks()` matches existing marks by **`aura_marks.name` = discipline
  name** (`mage_profile_api/api.php:511-540`). Rename a discipline and every existing mark for it
  is orphaned — it stays in auras under the old name and a duplicate appears on the next sync.
- The client gates features on catalogue **names**: `isExtrasensory` matches `"Экстрасенсорика"`
  (`models/User.kt:60`), the artifact scanner matches module name `"Познание артефактов"`
  (`MainActivity.kt:332`). A rename silently removes buttons from players' phones.
- `familiars.name` and `abilities.description` are **snapshotted** into mark text when the mark
  is created (12 `FAMILIAR_LINK`, 21 `ABILITY` marks live). Editing the catalogue later does not
  refresh them.
- Foreign keys: deleting a discipline cascades into `subscriptions` and `message_tags`; deleting
  a familiar sets `users.familiar` to NULL.

So catalogue editing is P1, not P0, and the panel should warn on rename/delete rather than
quietly writing.

### 4.12 Noise is cron-driven and lives on a different scale than the UI shows

Four cron scripts move noise on their own: `noize_api/auto_increase_noise.php` (global +0.1,
local +0.5), `auto_decrease_noise.php` (global −0.04), `decrease_global_noise_cron.php`
(global −0.5), `decrease_local_noise_cron.php` (local −2).

**Their steps now live in `noize_api/tuning.php`** (added 2026-08-18), which the panel's noise
page edits; each script falls back to its built-in defaults if that file is missing or broken,
so a bad edit degrades to the shipped behaviour rather than to a crashed cron. Two bugs were
fixed while wiring this: `auto_decrease_noise.php` clamped with `min()` and no floor, so the
global value could go negative, and its `--dry-run` printed the *increase* script's numbers.

**Which crons actually run** (measured 2026-08-18 from the server-side logs): `api_geo/cleanup.php`
every 5 minutes and `effects_api/cron_delete.php` every minute — both clearly scheduled. The four
noise scripts write no log, and the global value sat at 0.0 untouched from 2026-07-25 until the
panel changed it, which means **they are not scheduled at all right now**. Whoever runs the game
has to switch them on in the beget panel, or noisemancy will not move on its own.

Scale trap: `noisemancy_*.value` is raw **0..10**, and every consumer halves it to 0..5 before
display (`noize_api/api.php:133`, `users_monitor.php`). A GM typing "5" meaning "level 5" would
actually set level 2.5. Label the field with the raw scale and show the resulting player-facing
level next to it.

## 5. Write paths — the one thing worth getting right

For each entity the panel picks exactly one write path. Getting this wrong is the one way this
project can quietly corrupt game state. Three groups, not two.

### A. Через API — server-side logic exists, never write these tables directly

| What | Call |
|---|---|
| Player game fields: `disciplines`, `modules`, `abilities`, `instrument`, `familiar`, `misc` | `PUT /mage_profile_api/api/v1/user/{id}` — rewrites the aura marks (§4.2) |
| Aura marks, except `ABILITY` ones | `POST/PUT/DELETE /aura_api/aura/{id}/marks[/{mark_id}]` |
| Aura problems (slot allocation) | `POST/PUT/DELETE /aura_api/aura/{id}/problems[/{slot}]` |
| Aura hidden flag | `PUT /aura_api/aura/{id}/hidden` |
| Effects — creates a linked mark and appends to `users.effects` | `POST/DELETE /effects_api/api/v1/effects/{userId}[/{effectId}]` |
| Points create/delete/patch | `POST/PATCH/DELETE /api_geo/api/v1/points[/{id}]` |
| Artifacts | `POST/PUT /artifacts_api/api/v1/artifacts[/{id}]` |
| Player passport (after §8.2) | `PATCH /mage_profile_api/api/v1/user/{id}` |

### B. Прямым SQL — plain rows, nothing else reacts

`auras.type` / `percent_of_humanism` / `aura_image` / `aura_hidden`, `subscriptions`
(de-duplicate on write, §9.2), the initial `INSERT` of a `users` + `auras` pair (§11 lists the traps: all of
`player_name`, `name`, `showUser`, `lat`, `lng` are NOT NULL without defaults), and `ABILITY`
aura marks, which the API refuses (§4.10).

### C. Прямым SQL, но со страховкой — logic lives elsewhere, so warn the GM

| What | The catch | Panel behaviour |
|---|---|---|
| `magic_discipline`, `magic_modules`, `abilities`, `familiars` | rows are joined by **name**, snapshotted into marks, and gate client features (§4.11) | confirm dialog on rename/delete listing what will break; never bulk-rename |
| `noisemancy_global` / `_local` | four crons move it; the UI scale is half the stored one (§4.12) | show raw and player-facing value, plus "кроны продолжат двигать" |
| `users.showUser` / `lat` / `lng` / `name` | phone overwrites on the next ping (§4.9) | read-only, with the reason spelled out |

Calls go out with cURL to `https://shift96.ru/...`, which reaches the same code the phone reaches
(the phone itself still uses `http://` for everything except `familiars_api` —
`api/RetrofitClient.kt:16`; both schemes work). The point is to reuse the endpoint, not to
duplicate its logic. Panel-side helper: `gm/api.php` with `api_get/api_put/api_post/api_patch/
api_delete` returning `[status, body]`, 10 s timeout, and a visible error banner on any non-2xx —
never a silent failure.

## 6. Priorities

Ranked by *pain removed per hour of work*, given a 4-day game with a non-technical GM at the
wheel.

### P0 — without this somebody edits MySQL during the game

| # | Feature | Why it is P0 |
|---|---|---|
| P0.1 | **Players**: list, create, edit passport (`player_name`, `name`, `type`), edit game fields, delete with a real warning (§11) | The whole pre-game data entry, plus every "I typed my id wrong" incident. Only path today is SQL. `showUser`/`lat`/`lng` stay read-only (§4.9). |
| P0.2 | **GM subscriptions**: matrix GM × discipline with checkboxes + a warning for uncovered disciplines | A GM with no subscription is invisible to players and cannot answer. Only path today is SQL. Live data already has a gap (§4.4). |
| P0.3 | **Aura identity**: `type`, `percent_of_humanism`, `aura_image` (picker), `aura_hidden`; marks and problems from a real keyboard (`ABILITY` marks via SQL, §4.10) | Aura type/artwork has no editor anywhere; marks exist in the app but are slow for 30 players. |
| P0.4 | **Points**: table with filters (hide the ephemeral `USER` pins by default, §7), edit texts and geometry, hide/show, delete; needs the extended PATCH (§8.3) | Point text typos are currently unfixable. |
| P0.5 | **Global noise**: show and set, on the raw 0..10 scale with the 0..5 player value beside it (§4.12) | World noise drives the whole noisemancy mechanic and there is no way to set it except SQL. |

### P1 — quality of life, worth it if P0 lands early

| # | Feature | Why |
|---|---|---|
| P1.1 | **Catalogues**: abilities, disciplines, modules, familiars — with rename/delete guards from §4.11 | Abilities get invented mid-game; disciplines have a live data bug (§9.1). |
| P1.2 | **Artifacts**: table view, bulk edit, binding, filter by creator | 86 rows already, with junk (`level = 'тлимлот'`, `type = 'олпш'` and two empty). Bulk work on a phone is misery. |
| P1.3 | **Effects**: list active with countdown, create, extend, revoke | Editing an effect currently means delete + recreate, and expiry is invisible. |
| P1.4 | **Dashboard**: who is online (`users.lastUpdate`), local noise, unread per GM, active points/effects | `users_monitor.php` already does two thirds of this; fold it in behind the password. |

### P2 — only if everything else is done and tested

| # | Feature | Note |
|---|---|---|
| P2.1 | ~~Broadcast message from a GM to all players~~ | **Built 2026-08-18** on the chat page: text + discipline tag + optional attachments, sent to everyone / holders of one discipline / a hand-picked set. `messages_api` has no fan-out, so it is one call per player. |
| P2.2 | Upload new mark artwork through the panel | FTP works today; upload adds a file-permission failure mode. (Chat attachments *are* uploadable — they go through `messages_api`, which already stores them.) |
| P2.3 | Action log (who changed what) | 4-day game, GMs sit in one chat. Skip unless asked. |

**Explicitly not built:** role separation inside the panel, pagination (largest table is 1369
messages), i18n. (`quests` / `user_quest_progress` were skipped here as a dead feature; the
owner revived the mechanic on 2026-08-20 and the page was added then — see §15.)

## 7. Screens

Nine pages, one nav bar, mobile-first dark CSS (GMs outdoors at night).

| Page | Contents |
|---|---|
| `/gm/` dashboard | online/offline players by `lastUpdate`, global noise with a set-field, local noise top, unread counts per GM, active points and effects counts, warning banners from §9 |
| `players` | table: id, player name, character, type, last seen, links; `showUser` shown greyed with a hint; "создать игрока" button |
| `player?id=` | three form blocks: **паспорт** (PATCH: `player_name`, `name`, `type` — enum-validated), **игровое** (PUT — discipline/module/ability checkboxes from the catalogues, instrument, familiar select, misc list), **служебное** (artifacts id list, read-only phone-owned fields, delete player behind a confirmation that names what cascades) |
| `aura?id=` | aura type select, humanism 0–100, artwork picker (`.png`/`.webp` thumbnails from `/static/images`), hidden toggle; marks table with inline edit/delete + add form (mark type select, image picker, name, description, external, stars 0–5) — `ABILITY` rows edited over SQL and labelled as owned by the ability list; problems by slot 0–9 |
| `points` | default filter hides `type='USER'` (32 of 78 rows, recreated on every ping — panel edits to them are pointless); filter by type/hidden; row edit for description, `textToShowOnEnter`, `aura_text`, lat/lng, radius, expire, hidden, trackable, chain target; delete; "release familiar" (clear `assigned_player`); link to Google Maps for the coordinates |
| `refs` | four tabs: abilities (type enum + description), disciplines (name + image picker), modules (id + name, id convention `discipline*10+n`), familiars (id, name, description, kind, variants, `is_listed`, `sort_order`) |
| `artifacts` | table with all fields editable in place, filter by creator/level/type, create form; level and type as selects with an "other" free-text escape |
| `effects` | active effects joined with player names, time left, create (text, TTL or absolute expiry, optional linked mark), revoke |
| `chats` | GM × discipline subscription matrix, coverage warning, per-GM unread counts; (P2) broadcast box |

## 8. Server-side patches (all small, all in `SERVER/`)

> Always download the live file, diff against the local mirror, then upload — Тари may have
> changed it (`CLAUDE.md`).

### 8.1 `gm/.htaccess` — no change to the root file

Because every page is a real file (`/gm/index.php?p=…`, §4.8), the root `.htaccess` stays
untouched. The panel gets its own tiny one:

```apache
RewriteEngine On
RewriteCond %{HTTPS} !=on
RewriteRule ^(.*)$ https://%{HTTP_HOST}/gm/$1 [R=301,L]
```

If pretty URLs are ever wanted, add the bypass to the root file next to the other API ones —
but only then, and diff the live copy first.

### 8.2 `mage_profile_api/api.php` — wire up the existing `patch_user()` — *not done, not needed*

**Decision (2026-08-18): skipped.** The panel writes passport fields in direct SQL instead.
`patch_user()` performs a plain `UPDATE` with no side effects, so the two paths are equivalent —
and skipping it means the live `api.php` that every phone depends on is not touched at all.
Keep the recipe below only for the day the *app* needs a passport endpoint.

In the REST block next to the `PUT /user/{id}` branch:

```php
elseif ($method === 'PATCH' && count($seg) === 2 && $seg[0] === 'user') {
    $restHandled = true;
    patch_user($pdo, $seg[1]);
}
```

Then two guards inside `patch_user()`, because as written it has neither:

1. **Narrow the field set for this route** to `player_name`, `name`, `type`, `artifacts`;
   reject `disciplines`, `modules`, `abilities`, `instrument`, `familiar` with a 400 saying
   "use PUT" (they need the aura sync from §4.2). Note `showUser` is not in `ALLOWED_FIELDS` at
   all, and per §4.9 it should not be.
2. **Validate `type` against the enum** (`human`, `mage`, `creature_of_spirit_world`,
   `creature_of_abyss`, `creature_of_myth`, `creature_of_reality`, `demon`, `angel`, `other`).
   The server runs with an **empty `sql_mode`** — no `STRICT_TRANS_TABLES` — so MySQL silently
   coerces a bad enum value to `''` instead of erroring, and `patch_user()` has no try/catch to
   notice. Same reasoning applies to any enum the panel writes directly (`auras.type`,
   `aura_marks.mark_type`, `aura_problems.problem_type`, `abilities.type`, `points.type`):
   **validate in PHP, never trust the column.**

### 8.3 `api_geo/api.php` — widen the point PATCH — *not done, not needed*

**Decision (2026-08-18): skipped, same reasoning as §8.2.** The panel edits points in direct SQL
and only calls the API for create (which generates the id, the virtual centre and the
`SHRINKING_CIRCLE` expiry) and delete. The single piece of server-side logic on those columns —
the random virtual centre — is reproduced in `gm/pages/points.php::virtual_centre()` and only
recomputed when the real centre or the radius actually changed. `api_geo/api.php`, the file every
phone talks to for its map, stays untouched. Keep the recipe below for the day the *app* needs to
edit point texts.

Add to the `PATCH /api/v1/points/{id}` branch, using the same `array_key_exists` pattern as the
existing four fields: `description`, `textToShowOnEnter`, `radius` (numeric > 0), `lat`, `lng`,
`expireAt` (`null` clears), `assigned_player` (`null` releases a familiar). Leave `type` alone —
changing it invalidates the radius defaults and the virtual-point maths; recreate instead.

Coordinate validation is **not** ±180: `points.lat`/`lng` are `decimal(10,8)`, so anything
≥ 100 does not fit the column (and with the empty `sql_mode` it will be silently clamped, not
rejected). Validate `-99.99999999 … 99.99999999` and reject the rest with a 400. `users.lat`/
`lng` have the same type and the same trap. Yekaterinburg (≈ 56.8 / 60.6) is comfortably inside.

### 8.4 Optional: `noize_api` set endpoint

Not required — the panel can `UPDATE noisemancy_global SET value = ?` directly; the softcap/EMA
logic in `adjust_user_noise()` only applies to *deltas*. What does keep moving the value is the
cron set in §4.12, and an endpoint would not change that. Add one only if the app ever needs it.

## 9. Data problems found while surveying (fix from the panel once it exists)

1. ~~**Discipline 9 has a mojibake name**~~ — **owner's call, 2026-08-18: leave it.**
   `magic_discipline.id = 9` reads `ШЖ╫■┐ьЮ≈╒╬м╤нт&╜╓я` (image `noize_magick.png`, the client
   gates the terminal on `it.id == 9`), and three players — `pavlik`, `anton`, `lina` — carry an
   aura mark with that name. Bas says the garbled text is fine as it is, so **do not "fix" it**.
   If it ever is to be renamed: renaming the catalogue row alone is not enough, because
   `sync_magic_discipline_marks()` returns early unless the *id list* changed (`api.php:486`) —
   the marks need `UPDATE aura_marks SET name = … WHERE mark_type = 'MAGIC_DISCIPLINE'
   AND image_url LIKE '%noize%'`, or a PUT without discipline 9 followed by a PUT with it.
2. ~~**`MG_MARINA` has a duplicated subscription row** (10, 10)~~ — **fixed 2026-08-18** by
   saving the subscription matrix in the panel (29 rows → 28, same effective set). The table
   still has no unique key on `(user_id, discipline_id)`; the panel de-duplicates on every write.
3. **Junk player row** with `userId = ''` / `name = 'Unknown User'` from the geolocation upsert.
4. **Dirty artifact values**: `level` contains `тлимлот` where the docs promise three levels
   (`простой`/`сильный`/`великий`); `type` was never an enum and legitimately holds 17 values
   including combinations like `изменение, стабилизация` — there only `олпш` and two empty
   strings are junk. So: a select for `level`, a free-text-with-suggestions for `type`.
5. **Discipline coverage gap in chat** (§4.4) — decide before the game who covers 4 and 7, and
   who backs up the rest when the owner is busy.
6. ~~**`users_monitor.php` is public**~~ — **fixed 2026-08-18**: replaced by a 302 to `/gm/`,
   original archived in `SERVER/_backups/`.
7. **`noisemancy_local` holds three phantom ids** (`lina_Proxy`, `pavlik_Proxy`, `anton_Proxy`)
   that do not exist in `users` — the table has no FK and `ensure_local_row()` inserts whatever
   id it is handed. `get_user_noise()` reports `noisemancers = COUNT(*)` over the whole table
   (`noize_api/api.php:137`), so the terminal currently overstates the noisemancer count by 3.
   Panel should list them and offer deletion.

## 10. Security

Sized for the same threat model the owner already accepted: a trusted own server, ~30 known
players, one-shot game. The panel is not a bank.

- **One shared password** for all GMs, stored as a `password_hash()` string in `gm/config.php`;
  no accounts, no roles. Login form + `password_verify()` + PHP session.
- **HTTPS only**: `gm/.htaccess` redirects http → https; session cookie `secure`, `httponly`,
  `samesite=Lax`. (The phone client still talks http to the APIs — out of scope here.)
- **CSRF token** on every mutating form, same pattern as `arcaneoverflow/index.php:14`.
- **Login throttle**: 5 failures per IP per 10 minutes in a file counter — enough to stop
  drive-by guessing.
- **Own session name** (`GMSESSID`) so the panel never collides with InstantCMS sessions.
- **Own DB credentials in `gm/db.php`** — do **not** `require` the API configs. Each `*_api/
  config.php` opens its own PDO, hardcodes `$host = 'localhost'`, and `define()`s
  `ALLOWED_FIELDS` with *different* values (`mage_profile_api/config.php:13` vs
  `artifacts_api/config.php:8`); including two of them makes the second `define()` a silent
  no-op and one module quietly inherits the other's whitelist. Copy the credentials, not the file.
- Everything user-visible goes through `htmlspecialchars()`; every query is a prepared statement.

## 11. Build order

Each stage is independently useful and independently verifiable — stop after any of them and
what shipped still works.

| Stage | Deliverable | Files |
|---|---|---|
| **0. Skeleton** ✅ *shipped 2026-08-18* | login, session, CSRF, layout, nav, CSS, DB and API helpers, dashboard with counters/online/warnings | `gm/index.php`, `gm/inc/{config,lib,db,http,auth,layout}.php`, `gm/pages/{login,dashboard}.php`, `gm/assets/style.css`, `gm/.htaccess` (+ `Require all denied` in `inc/` and `pages/`, plus a `GM_ROOT` guard in every include) |
| **1. Players (P0.1)** ✅ *shipped 2026-08-18* | list with search/role filter, create (`users` + `auras` in one transaction), passport edit with aura-type sync, game edit through PUT, artifact list, guarded delete | `gm/pages/players.php`, `gm/pages/player.php` — §8.2 turned out unnecessary |
| **2. Subscriptions + noise (P0.2, P0.5)** ✅ *shipped 2026-08-18* | GM × discipline matrix (rewrites a master's rows only when they changed, which also de-duplicates), coverage table with unread counts per discipline, uncovered/single-master warnings; global noise set on the raw scale with the player-facing level beside it, per-player local noise, reset-all, phantom-row cleanup | `gm/pages/chats.php`, `gm/pages/noise.php` |
| **3. Aura (P0.3)** ✅ *shipped 2026-08-18* | identity (type / humanism / hidden / artwork picker with live preview, `.png` + `.webp`, https for aura art and http for marks), marks CRUD through `aura_api` with `ABILITY` rows read-only and labelled, problems CRUD with slot allocation, type mismatch warning against `users.type` | `gm/pages/aura.php` |
| **4. Points (P0.4)** ✅ *shipped 2026-08-18* | collapsible list with type/text filters (`USER` pins hidden by default and read-only), full edit incl. texts, coordinates, radius, expiry, chain target, hidden/trackable; create through `POST /points`; delete through the API; familiar release | `gm/pages/points.php` — §8.3 **not applied**, see below |
| **5. Catalogues + artifacts + effects (P1.1–P1.3)** ✅ *shipped 2026-08-18* | four catalogue tabs with usage counts and rename/delete guards; artifacts via `artifacts_api` with level select + type datalist, plus a delete the API does not have (direct SQL, also pulls the id out of every `users.artifacts`); effects create/extend/revoke with an optional linked aura mark | `gm/pages/refs.php`, `gm/pages/artifacts.php`, `gm/pages/effects.php` |
| **6. Dashboard (P1.4)** ✅ *shipped 2026-08-18* | five counter tiles, online/offline split, unread per master and per discipline (flagging disciplines nobody reads), map summary by type with expiring points and bound familiars, live effects, global + top local noise, and the warning list with links straight to the page that fixes each one; 60-second meta refresh so it can stay open all game | `gm/pages/dashboard.php` |

**All six stages are live**, and `users_monitor.php` now 302s to `/gm/` (§9.6) — the dashboard
shows everything that page did and more, behind the password.

Rough size: ~1500–2000 lines of PHP + ~200 lines of CSS in total. No stage is architecturally
risky; the risk is entirely in §5 (writing to the wrong path).

**Two things stage 1 must get right, because the schema will not stop you:**

- *Creating* a player: `player_name`, `name`, `showUser`, `lat`, `lng` are all NOT NULL with no
  default — they only get away with it today because `sql_mode` is empty and MySQL substitutes
  `''`/`0`. Fill all of them (lat/lng may be `0`, the phone overwrites them), and insert the
  matching `auras` row in the same transaction so `users.type` and `auras.type` do not disagree
  from birth (§4.5).
- *Deleting* a player: `artifacts.creator_user_id → users.userId` is **ON DELETE CASCADE**, so
  deleting a player destroys every artifact they authored (live: `hesha` 6, `bas` 3, `anti` 2,
  `anton` 1). `subscriptions.user_id` has **no ON DELETE clause**, so deleting a GM who has
  subscriptions fails with an FK error. `messages.recipient_id` is SET NULL and `sender_id` has
  no FK at all — either way the chat history is left dangling. The confirmation dialog must name
  the counts, and the panel should offer "clear subscriptions first" for GMs.

## 12. How to develop and test without breaking the live game

There is no PHP and no Docker on this machine today. Install the CLI once:

```bash
sudo apt install -y php-cli php-mysql
```

Then serve **only the panel directory** against the live database (external MySQL access is
already open — see the `shift-server-ftp` memory file):

```bash
php -S 127.0.0.1:8080 -t /home/bas/Shift/SERVER/public_html/gm
```

Point the document root at `gm/`, not at `public_html/`: PHP's built-in server ignores
`.htaccess` entirely, so every pretty API path (`/mage_profile_api/api/v1/…`, `/aura_api/aura/…`)
404s locally, and serving the whole tree also exposes the InstantCMS `index.php`, which fatals
without the CMS runtime. The panel's own pages are plain files with query strings (§4.8), so they
work fine — and its API calls go to the live host anyway.

Give `gm/config.php` a DB host from an environment variable, defaulting to `localhost` (what
beget needs) and overridable to `bas931wn.beget.tech` for local runs. The API base URL is
`https://shift96.ru` in both cases, since the APIs are public.

Testing rules that matter more than any of the above:

1. **Take a backup before every stage that writes** — `python3 _local/tools/db_dump.py`
   (~30 s, lands gzipped in `SERVER/_backups/`).
2. Test writes against a **throwaway player** (`test_gm_panel`), not a real one, then delete it.
3. Upload only after a local diff of the live file (§8 note).
4. Before the game: a dry run — create a player end to end, check the phone client sees the
   profile, the aura marks, and the chat.

## 13. What this plan deliberately does not solve

- **API authentication** — anyone who knows the URLs can still write to the game. Unchanged by
  this work, and the owner has already accepted it for the client.
- **The app's own gaps** — e.g. the client offers no way to manage subscriptions; the panel
  covers it instead of changing the app, because shipping an APK to 30 phones mid-game is worse.
- **Concurrent editing** — two GMs editing the same player will last-write-win. With 9 GMs in
  one chat this is a social problem, not a technical one.
- **Phone-owned fields** — `showUser`, `lat`, `lng` (§4.9). No panel can hold them; fixing that
  means changing the client so the server is the source of truth. Out of scope, and probably not
  worth an APK rollout for a 4-day game.
- **`ABILITY` aura marks through the API** (§4.10) — the panel routes around the bug with SQL
  instead of fixing `aura_api`. Fixing it properly means deciding whether those marks store an
  id or a text, which is a server-side change with client-side consequences.
- ~~**The `quests` chain feature**~~ — revived on 2026-08-20 with branching and dead ends; the
  panel page and the client work landed together (see §15 and `10-backlog-plan.md` #15).

## 14. QA pass and fixes (2026-08-18)

An independent tester was pointed at the live panel with this plan as the specification and a
`qa_*`-only sandbox. Everything it flagged was reproduced and fixed the same day; the database
was backed up before the run (`SERVER/_backups/bas931wn_inst1-20260818-044701.sql.gz`, plus a
code mirror in `ftp-code-20260818.tar.gz`) and verified identical afterwards.

**Fixed:**

| What was wrong | Fix |
|---|---|
| Every rendered timestamp was 2 h off — MySQL sessions pinned to +05:00 while PHP used the host default, so "online" really meant "pinged within 2 h 05 min" | `date_default_timezone_set()` from a new `timezone` config value, in `index.php` before anything formats a date |
| The `(int)` cast ran before every range check, so `"abc"` became `0` and passed: humanism silently went 100 → 0, same for mark stars, problem slot, familiar sort order | `int_field()` in `inc/lib.php`, used everywhere a number is read from a form |
| The login throttle refused the *correct* password for 10 minutes per IP — all GMs share one password and often one wifi | Over the limit it now adds a 2 s delay instead of refusing; a successful login clears the counter |
| The artifact id list was parsed with `preg_match_all('/\d+/')`: `-1` became artifact 1, `abc` silently wiped the list | `id_list_field()` — every element must be a positive integer, otherwise nothing is saved |
| Catalogue edits reported "saved" for rows that do not exist | Existence check before every UPDATE in `refs.php` |
| Deleting a familiar left its `FAMILIAR_LINK` marks behind; deleting a discipline left marks, orphan modules and dead ids in profiles, and its confirmation counted only subscriptions and tags | Deletes run in a transaction that strips the id from profiles (`strip_id_from_profiles()`), removes the marks and the orphan modules; the confirmation names every count |
| Over-long strings were truncated by MySQL and reported as saved | `too_long()` against the real column widths |
| Confirmation text was built into an inline `onsubmit`; an apostrophe or a newline in a player-supplied name broke the handler, so the button deleted with no prompt | One delegated listener reading `data-confirm`, escaped as an attribute |
| Modules could be granted without their discipline — invisible in the aura, and the client gates features on names | Rejected, with the offending module names listed |
| The subscription matrix rewrote *all* masters, so a truncated submit could silently unsubscribe someone | Only masters carried in the POST are touched; an empty list is refused |
| Any panel write to `users` bumped `lastUpdate` (the column is ON UPDATE CURRENT_TIMESTAMP), and a new player inherited `CURRENT_TIMESTAMP` — both made people look online | `KEEP_LAST_UPDATE` on panel UPDATEs; new players start with `lastUpdate = NULL` |
| `?p=player` with no id opened the junk `userId = ''` row as an editable card | Redirects to the list, which now offers a one-click cleanup of that row |
| `?p=logout` was a GET — any third-party page could log a GM out mid-game | POST + CSRF token |
| `noise` set_local minted a row for any posted id; `points` fell back to "everything" on a garbage `type`; search fed `%`/`_` straight into LIKE; artifact `level` accepted tampered values | Existence check, fallback to the default filter, `like_escape()`, level whitelist |

**Confirmed working and left alone:** auth and CSRF on every mutating form, 403 on `inc/` and
`pages/`, no XSS and no SQL injection, cross-user mark edits rejected, the aura-mark rebuild
through the profile API, artifact delete cleaning profiles.

**Closed too:** `users_monitor.php` (§9.6) now 302s to `/gm/` — it used to serve every player's
name and last-seen time to anyone who knew the URL, including through its `?ajax=` endpoints.
The original file is kept off-server in `SERVER/_backups/users_monitor.php.orig-20260818`; the
redirect is deliberately temporary (302, `no-store`) so the page can be restored without fighting
browser caches.

Nothing from the QA pass is left open.

## 15. Chains page (2026-08-20)

Added when the owner revived the chase mechanic (`10-backlog-plan.md` #15). `index.php?p=quests`,
nav item «Цепочки». All writes are direct SQL: `api_geo` covers only what the phone needs, and
nothing a master does mid-game to a chain has an endpoint.

What the page gives:

- **Create / delete a quest.** Start point is picked from a list, not typed. One quest per start
  point — a second one on the same point is refused, because the entry handler would start both.
  Deleting a quest drops the players' progress but keeps the points and their links.
- **The chain, drawn as a tree** from the start point, with `финиш` / `тупик` / `уже была выше`
  / `точка удалена` badges. The walk stops on a repeat, so a loop (`A → B → A`) renders instead
  of hanging the page — the API deliberately does not forbid loops.
- **Edit the branches**: add or remove a transition from any reachable point, and mark a point
  with no outgoing links as a finish. A point that leads somewhere cannot be a finish, and
  adding a link clears the flag — otherwise a chain would end and continue at the same time.
- **Steering the players**: who stands where, with `идёт` / `прошёл` / `тупик`, and buttons to
  move a player to any point of the chain, count it as done, or reset. This is the part that
  matters in the field: a player who lost the point (dead battery, Doze, twenty metres short)
  otherwise has to be moved from phpMyAdmin.

`points.php` is unchanged: the linear `next_point_id` it already edits is kept in sync with
`point_links` by the API, so a chain built there shows up here and vice versa.

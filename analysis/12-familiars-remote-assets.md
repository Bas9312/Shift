# Spec: server-hosted familiar images + familiar catalog

Status: **complete and verified end to end on the emulator (2026-08-18), including the
offline case.** The aura-silhouette follow-up (§3.6) and the `sync_familiar_link()` naming
defect (§7) were finished the same day.
Date: 2026-08-17
Scope: familiar artwork and catalog move from the APK to `shift96.ru`; aura silhouette
images get the same caching treatment; `users.familiar` becomes a validated reference.

Related: [11-status.md](11-status.md) (current defects), [10-backlog-plan.md](10-backlog-plan.md) (game backlog).

---

## 1. Why

Familiar artwork currently ships inside the APK as 36 `webp` files in `res/drawable/`
(~6.3 MB) and is resolved by name at runtime:

```kotlin
// FamiliarActivity.kt:52
val imageResId = resources.getIdentifier(imageName, "drawable", packageName)
```

Three consequences:

1. A new familiar requires a full APK build and rollout to every player.
2. The catalog of familiars is a hardcoded `Map` in `models/Familiar.kt`, so names live in
   two places (client constant and the value stored in `users.familiar` on the server).
3. `users.familiar` is an unconstrained `varchar(255)`. Nothing validates it. Live data
   already contains a mis-pasted person's name (see §6).

12 new creature image sets were delivered in `newFamiliars/`, which would add roughly
another 8 MB to the APK if bundled the same way.

## 2. Inventory

### 2.1 Existing familiars (9)

IDs are already stored in `users.familiar` and **must not change**:

`familiar_mirror`, `familiar_vaynera_spirit`, `familiar_weird_compass`,
`familiar_gentlemans_tear`, `familiar_dobyvala`, `familiar_abyss_eater`,
`familiar_earth_cat`, `familiar_malachite_lizard`, `familiar_fox`.

Note the inconsistent legacy file naming: `familiar_fox2` but `familiar_mirror_2`. This is
exactly the kind of string-gluing that `getIdentifier` fails silently on (returns 0). The
server layout in §3 removes it.

### 2.2 New familiars (11)

Source images: `newFamiliars/`, 1254x1254 PNG with alpha, 4 per creature
(3 day poses + 1 sleeping/night pose), matching the existing convention.

| Source images | ID | Display name |
|---|---|---|
| 01-04 | `familiar_ping_penguin` | Пингвин Пинг |
| 05-08 | `familiar_five_more_minutes` | Ещё пять минут |
| 09-12 | `familiar_public_wifi` | Кусочек общественного Wi-Fi |
| 13-16 | `familiar_arkady` | Бомж-пророк Аркадий |
| 17-20 | `familiar_bad_advice` | Дух плохого совета |
| 25-28 | `familiar_told_you_raven` | Ворон «Я же говорил» |
| 31, 32, 29, 30 | `familiar_bureaucracy_imp` | Мелкий бес бюрократии |
| 33-36 | `familiar_common_sense_toad` | Жаба здравого смысла |
| 37-40 | `familiar_literal_pigeon` | Голубь-почтальон |
| 41-44 | `familiar_unread_notifications` | Барабашка непрочитанных уведомлений |
| 45-48 | `familiar_lost_things` | Дух потерянных вещей |

Numbering is the alphabetical order of files in `newFamiliars/`, as delivered.

Source-data caveats, confirmed with the owner:

- Images 21-24 are a second take on the same character as 17-20. **Excluded.**
- Group `familiar_bureaucracy_imp` is ordered `31 -> day1`, `32 -> day2`, `29 -> day3`,
  `30 -> night`; the rest follow plain file order.
- Originals `00_09_26.png` and `00_09_36.png` are 1536x1024 instead of 1254x1254 and are
  **replaced** by the two `17 авг. 2026 г., 13_40_20` files (sitting pose -> `day1`,
  standing pose -> `day2`). The oversized originals stay on disk but are skipped by the
  conversion script.

`description` is left `NULL` for now: the ability and weakness text lives in the owner's
Google document and is deliberately not duplicated into the database.

### 2.3 Custom familiars (3, no artwork)

Live values in `users.familiar` that are not catalog IDs, all legitimate:

| User | Character | Value | Kind |
|---|---|---|---|
| `itzhak` | Ицхак Вейль | Глазастик | custom creature |
| `olyakaizer` | Оля Кайзер | Королева Танцпола | custom creature |
| `hesha` | Хеша | Матеюнс Кристина Валерьевна | another player acting as familiar |

All three date from the first game (last profile update September 2025). They are preserved
as catalog rows so nothing breaks, but hidden from the picker (§3.2).

## 3. Design

### 3.1 Static file layout

```
public_html/static/familiars/<familiar_id>/day1.webp
                                          /day2.webp
                                          /day3.webp
                                          /night.webp
```

`static/` is already used for `update.json`, the release APK and `images/`. It is served
directly by Apache: the CMS rewrite rules in the root `.htaccess` only fall through to
`index.php` when the request does not match a real file (`RewriteCond %{REQUEST_FILENAME} !-f`),
and `static/.htaccess` disables the PHP engine there. No routing changes are needed for the
images themselves.

One directory per familiar, with the variant as the filename, replaces the
`id + index + "_night"` string concatenation.

Conversion target: 1024x1024 `webp`, quality 82, lossless alpha, encoder `method=6`.
Measured result: 250-340 KB per new image (the artwork is dense, so quality below 82 buys
almost nothing — q65 saves 16% and visibly degrades), 73-267 KB for the legacy files, which
are copied as-is rather than re-encoded lossy over lossy. **16.8 MB** for all 80 files
(20 familiars x 4).

### 3.2 Database

Character set must be `utf8mb3` to match `users.familiar`
(`utf8mb3_general_ci`). A `utf8mb4` table would raise `Illegal mix of collations` when the
two columns are compared.

```sql
CREATE TABLE familiars (
  id            VARCHAR(255) NOT NULL PRIMARY KEY,
  name          VARCHAR(255) NOT NULL,
  description   TEXT NULL,
  kind          ENUM('creature','player') NOT NULL DEFAULT 'creature',
  variants      VARCHAR(128) NOT NULL DEFAULT 'day1,day2,day3,night',
  image_version VARCHAR(16)  NOT NULL DEFAULT '1',
  is_listed     TINYINT(1)   NOT NULL DEFAULT 1,
  sort_order    INT          NOT NULL DEFAULT 0,
  updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
```

Column semantics:

- `variants` — comma-separated list of available images; empty string means no artwork, so
  the client shows a placeholder. A single-image familiar is a one-row edit, not a schema change.
- `kind` — `player` means a human plays this familiar: no artwork, no AI chat, and the client
  hides the familiar button entirely (§3.4).
- `is_listed` — whether the familiar appears in the profile picker. Custom one-off familiars
  are `0`: still a valid assignment, not offered to new players.
- `image_version` — cache-buster, see §3.3.

Referential integrity, which is the point of the exercise:

```sql
ALTER TABLE users
  ADD CONSTRAINT fk_users_familiar FOREIGN KEY (familiar)
  REFERENCES familiars(id) ON UPDATE CASCADE ON DELETE SET NULL;
```

Two layers guard the field, and both are needed:

1. The API check already written in `mage_profile_api/api.php:209` activates as soon as
   `FAMILIARS_TABLE` is defined in `mage_profile_api/config.php`:

   ```php
   define('FAMILIARS_TABLE', 'familiars');
   ```

   It answers `400 Unknown familiar id`.

2. The foreign key. The `define` only covers the API; the foreign key also covers direct
   edits in phpMyAdmin, which is how the master actually touches data.

**Empty string means "no familiar".** The client's picker offers «Нет фамильяра» as `""`
(`models/Familiar.kt:12`), and the column stores `""` for 13 users historically. Neither the
catalog check nor the foreign key accepts `""`, so `mage_profile_api/api.php` normalises it
to `null` before validation — otherwise clearing a familiar returns `400 Unknown familiar id`.
This was caught by testing right after the `define` went live; see §5 step 5.

### 3.3 Cache busting

Image URLs carry the catalog's `image_version` as a query parameter:

```
https://shift96.ru/static/familiars/familiar_fox/day1.webp?v=ab12cd34
```

Coil keys its cache by the full URL, so bumping `image_version` in the database makes every
client fetch the new artwork without any "clear app data" instructions. Because the version
is in the URL, the files themselves can be served with a long `Cache-Control`.

### 3.4 Catalog API

New endpoint modelled on `artifacts_api` (same `config.php` shape, same PDO usage, same
error format):

```
GET https://shift96.ru/familiars_api/api/v1/familiars
```

```json
{
  "version": "9f3c1a20",
  "base_url": "https://shift96.ru/static/familiars/",
  "familiars": [
    {
      "id": "familiar_fox",
      "name": "Чудо-лиса",
      "description": "…",
      "kind": "creature",
      "variants": ["day1", "day2", "day3", "night"],
      "image_version": "ab12cd34",
      "is_listed": true,
      "sort_order": 10
    }
  ]
}
```

The whole catalog comes in one response — there are 23 rows, pagination would be noise.
`version` is a hash over the rows, so the client can skip re-parsing an unchanged catalog.

`is_listed: false` rows are included: the client needs the display name for a player who
already has that familiar, it just excludes them from the picker.

This requires the one change to a file owned by Тари — an exception in the root `.htaccess`
before the CMS routing, next to the existing ones:

```apache
RewriteCond %{REQUEST_URI} ^/familiars_api [NC]
RewriteRule ^ - [L]
```

Per `CLAUDE.md`, download the live `.htaccess` and diff it before uploading.

### 3.5 Client

| Concern | Location |
|---|---|
| `FamiliarApi` (Retrofit) | new, `api/` |
| `FamiliarCatalog` — memory -> `filesDir/familiars.json` -> network -> bundled bootstrap | new, `helpers/` |
| `FamiliarImages.urlFor(id, variant)` / `prefetch(id)` | new |
| `FamiliarData` | reduced to `isNightTime()` and variant selection by hour |
| `FamiliarActivity.kt:52`, `FamiliarFoundActivity.kt:47` | `getIdentifier` -> `ImageView.load(url)` |
| `ProfileEditFragment.kt:336` | picker reads the catalog, filtered by `is_listed` |
| `MainActivity.kt:207` | familiar button hidden when familiar is null or `kind == "player"` |
| 36 familiar `webp` in `res/drawable/` | deleted (-6.3 MB APK) |

**Image loader.** The app already depends on Coil 3 and uses it for chat attachments
(`AttachmentsAdapter.kt:61`). Two changes:

- Register a singleton `ImageLoader` via `SingletonImageLoader.Factory` in the `Application`,
  with its disk cache in `filesDir/image_cache`, capped at 128 MB. Coil's default cache lives
  in `cacheDir`, which Android is free to purge under storage pressure — unacceptable for a
  game played in the field with no connectivity.
- Drop the ad-hoc `ImageLoader(context)` instance in `AuraCanvasView.kt:34` so aura artwork
  shares the same persistent cache.

**Prefetch.** On login and on familiar change (`ProfileEditFragment.updateFamiliar()`), fetch
the player's own 4 images (0.7-1.4 MB depending on the familiar) into the cache in the
background, failures logged only.
Other familiars — the "familiar found" screen — load lazily.

**Bootstrap.** A small `assets/familiars_bootstrap.json` (IDs and names only, ~1 KB) so a
player whose catalog fetch has never succeeded still sees the familiar's name as text.

### 3.6 Aura silhouettes

The per-character aura silhouette pipeline **already works end to end** and needs no schema
or API change:

- `auras.aura_image` (`TEXT`, nullable) holds an absolute URL; 5 of 26 rows are populated.
- `aura_api/index.php:143` returns it as `aura_image`.
- `models/Aura.kt` parses it into `auraImage`.
- `AuraCanvasView.kt:152` draws `auraImageBitmap ?: humanBitmap`, so a null value correctly
  falls back to the generic `R.drawable.human` silhouette.

What was worth doing, all client-side or file-side:

1. **Done (2026-08-18).** Convert the five silhouettes to `webp`. They were 1024x1024 PNG at
   ~1.4 MB each; at `-quality 82 -define webp:alpha-quality=100` they are 52-77 KB, a 20x
   reduction for 6.9 MB saved across the set. Dimensions and alpha are unchanged, and a
   side-by-side render of the before/after showed no visible difference. The PNGs were left
   on the server for rollback.
2. **Done (2026-08-17).** Route them through the shared `ImageLoader` from §3.5 so they
   persist offline (`AuraCanvasView.kt:37`).
3. **Dropped (owner's call, 2026-08-18).** Prefetching the player's *own* silhouette is
   pointless: a player never looks at their own aura, only at other people's. Prefetching
   everyone else's would mean downloading the whole roster, which is not worth it for 60 KB
   images that the shared cache keeps forever after the first view.
4. Cache busting: when a silhouette is redrawn, upload it under a new filename and update the
   URL in `auras.aura_image`. With five images changing rarely, this is cheaper than a version
   column. The `png` -> `webp` switch was itself a filename change, so it busted the cache for
   free; the same URLs were also moved from `http://` to `https://`.

`aura_problem_*` drawables (5 files, ~250 KB) **stay in the APK**. They are keyed to the
client-side `AuraProblemType` enum, they are small, and they do not change.

## 4. Failure modes

| Condition | Behaviour |
|---|---|
| No network, images cached | Works as before |
| No network, nothing cached | Placeholder image plus the familiar name as text. No blank screen, no crash |
| Catalog endpoint down | Last cached catalog, then the bundled bootstrap |
| Familiar ID missing from the cached catalog | One forced catalog refetch; if it still misses, the ID is displayed as the name |
| Image returns 404 | Coil `error` drawable |
| `variants` empty (custom familiar) | Placeholder plus name; chat still works |
| `kind = 'player'` or familiar is null | Familiar button not shown |

## 5. Rollout order

1. **Done (2026-08-17).** Convert artwork locally, produce `static/familiars/` tree and the
   SQL seed. Scripts: `tools/build_familiar_map.py` (source mapping, reviewable TSV) and
   `tools/prepare_familiars.py` (conversion + `tools/familiars_seed.sql`).
2. **Done (2026-08-17).** Upload `static/familiars/` over FTP — 80 files, additive, the path
   did not previously exist. Verified: 20 directories x 4 files present, spot-checked files
   return HTTP 200 with `Content-Type: image/webp` and sha1 matching the local copies, a
   missing path returns 404. `https://` works for these files, so the client can use it.
3. **Done (2026-08-17).** Data cleanup (§6), then create `familiars`, seed 23 rows, add the
   foreign key. Fresh `users` dump taken first; the cleanup was previewed as a `SELECT`
   before it ran and applied in a transaction. Verified: 0 orphan values, 0 empty strings,
   the foreign key rejects an unknown ID with `ERROR 1452` and accepts a real one.
4. **Done (2026-08-17).** Upload `familiars_api/`; patch the root `.htaccess` (downloaded,
   diffed — the only change is the three added lines — uploaded). Verified after the root
   rewrite change: homepage, `mage_profile_api` and `api_geo` all still answer. Endpoint
   verified: 23 rows, 20 with four variants, 3 without artwork, 1 `kind=player`;
   `ETag`/`If-None-Match` returns `304` with an empty body; `config.php` is `403` from
   outside; unknown route `404`; `POST` `405`. All 80 image URLs built from the catalog
   response return `200`.
5. **Done (2026-08-17).** Define `FAMILIARS_TABLE` in `mage_profile_api/config.php`.
   This immediately broke clearing a familiar (`""` -> `400`), fixed in the same session by
   normalising `""` to `null` in `api.php`. Round-trip verified against the live API on the
   owner's own account: set a familiar, clear it, junk rejected, aura mark created and
   removed, account left in its original state.
6. Verify with `curl`: catalog JSON, one image fetch, and a rejected bad familiar ID.
7. **Done (2026-08-17).** Client changes and drawable deletion. `assembleDebug --offline`
   green, 138 unit tests green, APK inspected: no `familiar_*.webp`,
   `assets/familiars_bootstrap.json` present. **APK size corrected 2026-08-18: 16.6 MB.** The
   20.6 MB written here originally was measured against a stale build; a clean rebuild of the
   same debug variant gives 16.6 MB, which matches the ~5.5 MB of drawables that were removed.
8. **Done (2026-08-18).** Aura silhouettes to `webp` (§3.6) and the `sync_familiar_link()`
   name lookup (§7). `auras` and `aura_marks` dumped first. The five webp files were uploaded
   alongside the PNGs, `auras.aura_image` repointed with a single `REPLACE()` update touching
   exactly 5 rows, and `aura_api/aura/{id}` verified to return the new URL. `api.php` was
   downloaded and diffed before editing, and re-downloaded and diffed after upload.

## 5.1 Live verification (emulator, 2026-08-18)

Run on `Pixel_6_API_35` against the production backend, after freeing disk space.

| Case | Result |
|---|---|
| Fresh install, no local state | `files/familiars.json` written after the background refresh; version `59f9d886`, 23 rows |
| Player with no familiar (`bas`) | Familiar button hidden on the main screen; profile shows «Нет фамильяра». Previously this screen showed someone else's lizard |
| Familiar picker (MG editing a profile) | Lists «Нет фамильяра» + the 20 listed catalog entries, including the 11 new ones. The three hidden custom rows are absent |
| Saving a pick | `PUT` accepted, `users.familiar = familiar_ping_penguin` in the production database |
| Familiar screen | Name and artwork both from the server; the image lands in `files/image_cache/` |
| **Airplane mode, app restarted** | Familiar screen renders the artwork from the disk cache — the offline case this whole design exists for |
| Cleanup | `bas.familiar` returned to `NULL`, `FAMILIAR_LINK` aura mark removed; database back to 23 catalog rows, 38 users, 0 orphans, 0 empty strings |

Already-installed APKs are unaffected — their artwork is bundled. The drawables disappear
only from the next release.

## 6. Data migration

Current state of `users.familiar` (38 rows): 21 empty or null, 14 valid IDs, 3 custom values.

```sql
-- empty string is not a valid foreign key target
UPDATE users SET familiar = NULL WHERE familiar = '';

-- the three custom values become real catalog rows before the FK is added
INSERT INTO familiars (id, name, kind, variants, is_listed, sort_order) VALUES
  ('familiar_glazastik',      'Глазастик',                   'creature', '', 0, 900),
  ('familiar_dancefloor_queen','Королева Танцпола',          'creature', '', 0, 901),
  ('familiar_player_kristina','Матеюнс Кристина Валерьевна', 'player',   '', 0, 902);

UPDATE users SET familiar = 'familiar_glazastik'       WHERE userId = 'itzhak';
UPDATE users SET familiar = 'familiar_dancefloor_queen' WHERE userId = 'olyakaizer';
UPDATE users SET familiar = 'familiar_player_kristina'  WHERE userId = 'hesha';
```

Backup taken before any of this: `SERVER/_backups/bas931wn_inst1-20260817-135919.sql.gz`
(149 tables, gitignored).

## 7. Adjacent defects found

Fixed as part of this work, since the code is being touched anyway:

- **Fixed 2026-08-18.** `mage_profile_api/api.php` — `sync_familiar_link()` wrote the familiar
  **ID** into the aura mark's `description`, so players saw `familiar_malachite_lizard`
  instead of «Малахитовая ящерица». Now a `familiar_display_name()` lookup against the catalog
  table, falling back to the raw ID if the catalog has no such row. 11 existing rows in
  `aura_marks` were backfilled with the same join; one of them (`itzhak`) additionally had
  `name` and `description` swapped by hand and was normalised. Verified live: setting
  `bas` to `familiar_ping_penguin` produced the mark «Связь с фамильяром» / «Пингвин Пинг»,
  and clearing the familiar still deletes the mark.
- `FamiliarActivity.kt:37` — falls back to `familiar_malachite_lizard` when the player has no
  familiar, showing artwork that belongs to someone else. Superseded by hiding the button.

Known and deliberately left alone: aura-mark `image_url` values (`familir_bond.png` — the
typo is in the filename on the server, `instrument_bond.png`, the ability-type icons) are
still `http://`, while the silhouettes are now `https://`. Changing them would rewrite every
mark row for a cosmetic gain; the client loads both fine.

## 8. Out of scope

- Artwork and personality text for `Глазастик` and `Королева Танцпола`. Both players last
  appeared in the first game (September 2025); the owner will decide once the roster for the
  second game is known. The catalog rows exist and work without artwork.
- A master-facing admin UI for the familiars table. phpMyAdmin is sufficient for 23 rows.
- Thumbnail sizes. The picker is text-only today; a second image size can be added when a
  visual picker exists.
- Migrating `static/images/` (ability icons, plane influences) to the same scheme.

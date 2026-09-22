# Point types audit — what the player actually sees

Date: 2026-09-22. Triggered by: a chain built out of "Точка с текстом" in the GM panel was
walked through in the field and **nothing was visible on the map**, even though the "Скрыта"
checkbox was off. Plus a question about the `HIDDEN_AR_POINT` type, since the game has no AR.

## Short answer

Two separate things, neither of them a bug in the strict sense — both are the code doing
exactly what it says, and both are traps for the master:

1. `POINT_WITH_TEXT` is **never** drawn for a player. Not the circle, not the marker. The
   `hidden` flag has nothing to do with it — the filter is on the type
   (`MapPointsRenderer.kt:38` and again `:179`). Its only channel to the player is the
   background push on entering the radius (`LocationService.kt:292`). Worse: that type is
   the **preselected default** in the GM panel's "Новая точка" form
   (`gm/pages/points.php:443`), so a master who never touches the type dropdown gets
   invisible points by default.
2. `HIDDEN_AR_POINT` is dead. Nothing in the app consumes it: `app/.../arcore/arcore/` is an
   empty directory, and the only references to the constant are the enum entry
   (`PointType.kt:15`), a display label (`MapPointsRenderer.kt:147`) and the server's radius
   table. If created, it behaves as a **plain visible point**: grey circle (not in
   `PointVisualizer.circleColors` → `Color.GRAY`), magenta marker (not in `markerColors` →
   `HUE_MAGENTA`), radius 80 m, and the generic `textToShowOnEnter` push. "Hidden" and "AR"
   are both lies in the name.

## Visibility model as it actually stands

For a master (`MG_*`) everything is visible always, with the marker created regardless of
distance (`MapPointsRenderer.refreshMarkersForLocation`). Everything below is about players.

A point reaches the map only if it passes three gates in order:

1. **Server gate** (`api_geo/api.php:323`, `GET /api/v1/points`): expired points, other
   people's `USER` markers, taken `FAMILIAR` points, and **any point that participates in a
   chain** are dropped. A chain link is only returned if it is a quest start, or an open
   transition from the point the player currently stands on in an active quest.
2. **Client type/hidden gate** (`MapPointsRenderer.syncPoints`): `POINT_WITH_TEXT` → never;
   `hidden == 1` → never.
3. **Marker gate** (`refreshMarkersForLocation`): the circle is drawn as soon as the point
   survives gate 2, but the **marker is only created while the player is inside the radius**.
   Outside it the marker is removed again. So at distance, a player sees a coloured circle
   and nothing else — deliberate, per the comment in `PointVisualizer`.

## Per-type table

| Type | GM label | Default R | Circle colour | Marker | What the player gets |
|---|---|---|---|---|---|
| `USER` | Кто-то в игре | 100 | green `#4CAF50` (no circle drawn) | green | Marker only, other players' markers are filtered server-side; only `MG_*` markers survive |
| `FAMILIAR` | Фамильяр | 150 | green `#1CAF50` | rose | Circle + marker in radius; **entry is counted at 50 m, not 150** (`LocationService.kt:243`) |
| `HIDDEN_EFFECT_AREA` | Скрытая зона эффекта | 250 | **grey (fallback)** | **magenta (fallback)** | Fully visible unless `hidden=1` is set by hand — the name promises otherwise |
| `FAKE_FAMILIAR_BITER` | «Фамильяр» (кусачий) | 180 | green `#1CAF50` | rose | Deliberately identical to a real familiar — working as designed |
| `APPROACHING_BITER` | Приближающийся «фамильяр» | 100 | violet `#9C27B0` | violet | Visible |
| `OPEN_PROBLEM` | Открытая проблема | 400 | red `#F44336` | red | Visible |
| `SHRINKING_CIRCLE` | Сужающийся круг | 1000 | `#1FEB3B` (comment says "Желтый", it is green) | cyan | Visible; server shrinks it over 30 min and deletes it |
| `DEMON_BLACK_CIRCLE` | Демон, чёрный круг | 500 | black `#000000` | **magenta (fallback)** | Visible; black marker was presumably intended |
| `APPROACHING_VIRTUAL` | Приближающаяся вирт. проблема | 300 | `#8FEB3B` (comment says "Желтый", it is green) | yellow | Visible |
| `HIDDEN_AR_POINT` | Скрытая AR-точка | 80 | **grey (fallback)** | **magenta (fallback)** | Visible plain point. Dead type, no AR anywhere in the project |
| `POINT_WITH_TEXT` | Точка с текстом | 30 | — | — | **Nothing on the map, ever.** Push notification on entering the radius only |
| `UNKNOWN` | — | — | grey | magenta | Client-side fallback for a type the app does not know; server rejects unknown types with 400 |

Five of the eleven live types fall through to the grey/magenta fallback, which means
`HIDDEN_EFFECT_AREA`, `HIDDEN_AR_POINT` and a genuinely unknown type are visually
indistinguishable on the map.

## Secondary finding: circle centre vs entry centre

The circle is drawn around the **virtual** centre (`vLat`/`vLng`, randomly offset from the
real one by up to `radius` when the point is created, `api.php:469`), but entry is computed
against the **real** centre (`LocationService.checkPointsInRange` uses `point.lat/lng`).
The marker, once it appears, is placed on the real centre too. Consequence: a player can be
inside the drawn circle and get no entry event, and can get an entry event while just
outside the drawn circle. For a 30 m `POINT_WITH_TEXT` this is invisible anyway; for a
400 m `OPEN_PROBLEM` it is a couple of hundred metres of discrepancy. Already noted in
`analysis/10-backlog-plan.md:226`.

## What was changed (same day)

Visibility is now a **server-side contract**. The client no longer decides anything about
whether a point shows up.

**`api_geo/api.php`**
- `POINT_DEFAULT_RADII` is now a global constant and doubles as the list of accepted types.
  `HIDDEN_AR_POINT` is gone from it, so `POST /points` rejects it with 400; `POINT` (100 m)
  is new.
- `ALWAYS_HIDDEN_POINT_TYPES = ['POINT_WITH_TEXT', 'HIDDEN_EFFECT_AREA']`. On `POST` the
  server forces `hidden = 1` for them; on `PATCH` it refuses to clear the flag.
- New column contract `marker_from_afar` accepted on `POST` and `PATCH`.

**`gm/inc/lib.php`, `gm/pages/points.php`**
- `POINT` added and made the create form's default (it was `POINT_WITH_TEXT` — the trap).
- `POINT_WITH_TEXT` relabelled «Скрытая точка с текстом при входе»; `HIDDEN_AR_POINT` gone.
- The «Скрыта» checkbox is ticked and disabled for the always-hidden types, in the create
  form and in every edit form, via `gmHiddenLock()`. Because a disabled checkbox is not
  submitted at all, both the panel's PHP and `api_geo` set the flag themselves — the form is
  only there to show the master there is no choice.
- New checkbox «Булавка издалека» (`marker_from_afar`), disabled whenever the point is hidden.

**Client**
- `MapPointsRenderer.syncPoints` filters on `hidden` and nothing else. The `POINT_WITH_TEXT`
  type check is gone from both `syncPoints` and `addPoint`.
- Marker lifetime moved into `shouldShowMarker()`: always for the master, always when the
  server says `marker_from_afar == 1`, otherwise only inside the radius as before.
  `upsertPoint` recreates a point when that flag flips.
- `EkatMaps.auraReadRangeFor` keys off `hidden` alone.
- `PointVisualizer`'s colour tables are complete — only `UNKNOWN` falls back to grey now, as
  a genuine "the server sent something new" signal.
- `PointType.ALWAYS_HIDDEN` locks the same checkbox in the app's own create dialog.

**`SERVER/migrations/2026-09-22-point-display.sql`** — `ADD COLUMN marker_from_afar`, plus the
data fix below. **Not applied yet.**

## Deployment order — this one matters

Live data as of 2026-09-22: 42 points, of which **19 `POINT_WITH_TEXT` and 3 of the 4
`HIDDEN_EFFECT_AREA` carry `hidden = 0`**. They were invisible only because the old client
threw them away by type. Ship the new APK first and all 22 appear on the players' map.

1. Apply `SERVER/migrations/2026-09-22-point-display.sql` (adds the column, then
   `UPDATE points SET hidden = 1 WHERE type IN ('POINT_WITH_TEXT','HIDDEN_EFFECT_AREA')`).
   Safe against the *old* app: `POINT_WITH_TEXT` was already invisible, and the three effect
   zones becoming hidden is the intended end state.
2. Upload `api_geo/api.php` and `gm/` (diff against live first — Тари may have touched them).
   The panel's edit form writes `marker_from_afar` by direct SQL, so it breaks without step 1.
3. Install the new APK.

Rolling back means putting the old APK back; the column and the `hidden` flags can stay.

## Still open

- `UpdatePointRequest` in the app does not carry `marker_from_afar` — the master's app can
  toggle `hidden`/`trackable`/`aura_text` on an existing point, but not the new flag. Panel
  only, for now.
- The familiar's 50 m entry vs 150 m drawn circle (`LocationService.kt:243`) is untouched.
- Circle drawn around the virtual centre vs entry measured from the real centre — untouched,
  see above.

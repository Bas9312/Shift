# 17. GM panel — a master's first walkthrough, triaged

> Written 2026-09-18, after one of the game masters looked at the live panel
> (https://shift96.ru/gm/) on both a laptop and a phone and sent 15 notes. He explicitly did
> **not** exercise anything that writes ("не знаю насколько можно сейчас там всё шатать"), so
> this is a read-only walkthrough: everything below is either a layout problem, a wording
> problem, or something found by reading the code afterwards.
>
> Companion to [13-gm-web-admin-plan.md](13-gm-web-admin-plan.md), which is the original build
> plan. Same sizing assumptions: ~30 players, ~9 GMs, 4 days, one shot. UI strings are Russian
> because GMs read them; everything else is English per `CLAUDE.md`.

His overall verdict was positive — "оч кайфово, что так много всего через неё можно делать",
usable on a laptop, "скорее возможно" on a phone. The notes are worth taking seriously
precisely because they are a first-contact reading: everything he misread, the next master
will misread too.

## Summary

| # | His note | What it actually is | Status |
|---|---|---|---|
| 1 | Dashboard "Чат" rows are not clickable | True — the neighbouring "Карта" block links, this one was never wired | **fixed** |
| 2 | — | He withdrew it himself | — |
| 3 | "Aura editable here, but breaks if edited in the DB?" | Correct reading; the wording that says so is unclear | **fixed** |
| 4 | Discipline note in chat drifts around | Flex spacer moves it as text length changes | **fixed** |
| 5 | Subscription labels hard to read even on a PC | Rotated vertical headers | **fixed** |
| 6 | Noise terminology is hard | Fair; a glossary covers it | **fixed** |
| 7 | **Raised noise by +1, journal showed nothing** | **Real bug** — panel never wrote to `noise_log` | **fixed** |
| 8 | Can a player get an ability that is not in the catalogue? | Yes, and the catalogue gains a row; no path to it from the player card | **fixed** |
| 9 | Artifacts on hand are entered as bare ids | True — the data for a picker is already loaded | **fixed** |
| 10 | Mobile nav bar is monstrous | 12 items in a wrapping sticky flex row | **fixed** |
| 11 | Dashboard "Карта" table breaks on mobile | Only this one has headers; the rest are label/value pairs | **fixed** |
| 12 | Mobile chat opens invisibly, far below | Card order stacks the player list above the thread | **fixed** |
| 13 | **Attachment vanished a few seconds after picking it** | **Real bug** — 30 s meta refresh on a page full of forms | **fixed** |
| 14 | Subscriptions on mobile are endless blocks | Same matrix, stacked | **fixed** |
| 15 | **Noise journal continues off-screen to the right** | **Real bug** — `table.grid` had no CSS rule at all | **fixed** |
| — | (found while fixing 7) | **Setting a player's noise to its current value crashed the page** | **fixed** |

## Fixed on 2026-09-18

### 13 — the chat page threw away what the master was typing

`pages/chat.php` opened a conversation with `layout_header('Чат', 'chat', 30)`, i.e. a
`<meta http-equiv="refresh" content="30">`. `inc/layout.php` carries the warning that made
this a bug in the first place: *"Never put it on a page with forms."* A meta refresh reloads
unconditionally — it discarded half-written replies and files already chosen in the picker.

It explains both halves of his report. On a phone, choosing a photo out of the gallery easily
takes longer than 30 seconds, so the first attempt reloaded while he was still in the picker
and nothing was attached at all; the second attempt attached, then the next tick wiped it.

Checked and ruled out first: POST size limits are not involved. A 64 MB multipart POST to the
panel is accepted, so `post_max_size` never silently discarded the upload.

Replaced with a JS timer that reloads **only when there is nothing to lose** — no text in any
textarea or text input, no file chosen, no `<details>` open, focus not in a form control, tab
not hidden. The live-chat feel survives, the data loss does not. A hint under the reply box
says so, and says when auto-refresh is paused.

A `visibilitychange` handler tops it up: coming back to a tab that sat in the background
refreshes straight away if a tick was missed, instead of showing a stale thread for up to
another half a minute.

The dashboard keeps its 60 s meta refresh: it is the one page meant to sit open on a laptop
all game, and it has no data entry on it.

### 7 — noise changes made from the panel were invisible in the journal

`pages/noise.php` wrote straight to `noisemancy_global` / `noisemancy_local` in SQL and never
touched `noise_log`. The journal was fed only by the game itself — `noize_api/service.php` and
`system/libs/site_noisemancy.php`. So a master nudging the number by hand moved the world and
left no trace, which made the journal quietly *misleading* rather than merely incomplete: it
reads as the full story of why the noise is what it is.

All five mutating actions now log through one helper (`noise_log_gm()`), tagged
`source = 'gm'`, which shows up in the existing «Откуда» column beside `app` and `site`:

| action | command | notes |
|---|---|---|
| `adjust_global` | `GM.GLOBAL.ADJUST` | filed under the stand-in userId `GLOBAL` |
| `zero_global` | `GM.GLOBAL.ZERO` | `applied` is the whole amount removed |
| `adjust_local` | `GM.LOCAL.ADJUST` | records `local_before` / `local_after` |
| `set_local` | `GM.LOCAL.SET` | `requested` is the absolute target |
| `reset_all_local` | `GM.LOCAL.RESET_ALL` | one row per player actually zeroed |

`noise_log.userId` is `NOT NULL` and the global value belongs to nobody, hence the `GLOBAL`
stand-in. No schema change was needed: `source` is a free `varchar(16)` and every column the
helper writes already existed.

The two 24-hour summaries ("кто нашумел", "чем шумели") now exclude `source = 'gm'`. They
answer *what the players did*, and a master moving a number is not a player making noise. The
detailed journal below them shows everything.

### 15 — four tables ran off the side of the phone

Four tables on the noise page are marked `class="grid"`. There has never been a `table.grid`
rule in `assets/style.css` — the class does nothing. With no scroll container they overflowed
past the viewport, and since the page body does not scroll sideways those columns were
unreachable rather than merely ugly, which is exactly how he described it.

Wrapped in a new `.table-scroll` container that scrolls inside its card. The `?v=` cache
buster on `style.css` was bumped so masters do not get the old stylesheet — it has moved on
with each batch since and now sits at `v=6`. Bump it whenever you touch the stylesheet.

The wide dashboard table (his #11) was a different problem — it squeezed rather than escaped,
and the fix there was the existing `responsive` stacking; see batch C below.

### Bonus — setting a player's noise to the value it already had crashed the page

Found while instrumenting `set_local`, not reported by him. The handler ran an `UPDATE` and,
if it reported zero affected rows, fell through to an `INSERT`. But MySQL reports zero
affected rows in two different cases: the row is missing, **and** the value written equals the
one already stored. `noisemancy_local.userId` is the primary key, so the second case ran an
`INSERT` onto an existing key and died with `1062 Duplicate entry`, surfacing as the panel's
generic "Что-то сломалось".

Verified directly against the live schema before changing anything: an `UPDATE` to an
identical value does report `rowCount() === 0`, and the follow-up `INSERT` does raise 1062.
Replaced with an explicit existence check. "Set this player's noise to 0" when they are
already at 0 is an entirely natural thing for a master to do mid-game, so this was waiting to
go off at the worst time.

### How the fixes were verified

Against the live database, restoring state exactly: global noise `+1` then `−1` (a round trip
back to 0.00; at raw 1 the level players see is still `floor(1/2) = 0`, so nothing visible
moved), and `set_local` on a player at the value they already held — a genuine no-op on the
data that used to be a crash. Both logged correctly; the journal renders the new rows with the
`gm` tag.

The auto-refresh guard was exercised in a real browser, in both directions: an untouched page
reloaded on its own, and a page with a draft in the reply box did not — and its hint switched
to "Автообновление на паузе", which is the proof that the timer actually ran and *chose* not
to reload rather than simply never firing.

Worth knowing if anyone retests this: an automated browser pane keeps the page at
`visibilityState: "hidden"` and freezes it between commands, so the timer barely advances and
the `document.hidden` guard suppresses every tick. The first two attempts at this test looked
like passes and proved nothing — an untouched draft is exactly what a never-running timer also
produces. Overriding `document.hidden` and driving page time forward with busy-waits is what
made the test real.

After deploying, every one of the panel's twelve pages was re-checked for HTTP 200, and the
page's inline script was pulled back off the production server and run through `node --check`.

Two `GM.GLOBAL.ADJUST` rows and one `GM.LOCAL.SET` row from this testing are in `noise_log`.
They are honest records of a real GM action and harmless to leave; delete them if you want a
clean journal before the game starts.

## Answers to his questions

**3 — "Inside the player card: does this mean the aura can be edited here, and if you go
straight into the DB everything breaks?"** He read it right. The player's game fields —
disciplines, modules, abilities, instrument, familiar — are saved through
`PUT /mage_profile_api/api/v1/user/{id}`, and that endpoint also rebuilds the aura marks.
Writing those same columns in SQL updates the profile and leaves the aura behind, so the two
drift apart. The note on the card says this, badly; rewording is in B.

**8 — "What if a player wants an ability that is not in the catalogue?"** It works, and the
catalogue gains a row. Справочники → Способности → the empty form at the bottom creates the
ability with a fresh id, and it can then be granted on the player card. The gap is
discoverability, not capability: the player card offers no hint that this is possible and no
link to get there. Also worth knowing, and currently not said anywhere near the form: ability
text is snapshotted into the aura mark when the mark is made, so editing an ability later does
not rewrite marks already issued.

**6 — noise terminology.** He is right that it is master-facing jargon, and right that it is
somebody else's department. Cheapest useful fix is a one-line glossary in the page header:
raw 0–10 is what is stored, the player is shown half of it, and the level is the whole part of
that half. Renaming things in the UI is not worth it this close to the game.

## Batch B — clarity, also shipped 2026-09-18

Six small changes, none of which touch a write path:

- **#1** Dashboard "Чат" rows are links now, the way the "Карта" rows always were. A master's
  row goes to that master's chat list; a discipline row goes to the chat of a master who
  actually reads it, and names them ("читает MG_Bas и ещё 2"). A discipline nobody reads links
  to Подписки instead, which is the page that fixes it.
- **#9** The artifact field keeps its comma-separated ids — that is still what gets submitted,
  and typing by hand still works — but above it there is now a picker listing all 86 artifacts
  by name and level, with a button that appends the chosen id. Broken ids already held by a
  player are called out in red rather than silently listed.
- **#8** The Способности fieldset now says an ability can be created if it is missing, links
  straight to Справочники → Способности, and warns that editing an ability's text later does
  not rewrite marks already issued.
- **#3** The aura note leads with the answer — the aura is rebuilt for you, and these fields
  must not be edited in SQL because the profile would move and the aura would not.
- **#4** The "Читает дисциплины" line was a flex sibling, so its position slid with the length
  of the list. It is its own block now.
- **#6** A glossary card at the top of the noise page: raw value, what the player sees, level,
  global vs local, what one journal row is, what `app` / `site` / `gm` mean, how decay works.

Verified on the live panel: all thirteen pages still return 200, the picker appends without
duplicating and leaves the database untouched until the form is actually submitted (`bas`'s
artifacts were unchanged after clicking through it), and the inline script pulled back off the
production server passes `node --check`. Stylesheet cache buster is at `v=5`.

Incidentally confirmed while reading the dashboard output: discipline id 9's mangled name
(`ШЖ╫■┐ьЮ≈╒╬м╤нт&╜╓я`) is stored that way in the database on purpose. It is in-world styling
for Шумомантия, not a rendering fault — worth knowing before someone "fixes" it.

## Batches C and D — mobile and the matrix, also shipped 2026-09-18

**The matrix is transposed (#5, #14).** Disciplines are the rows, masters the columns. There
are nine masters and eleven disciplines, and master names are far shorter ("Анте", "Фараш")
than discipline names, so the headers now sit flat — `table.matrix th.vert`, with its
`writing-mode: vertical-rl` and 180° flip, is gone. Above the table there is a master filter
that hides every other column; on a phone it selects the first master on load, which turns a
9×11 grid into a single readable column and answers #14 with the same mechanism.

The checkbox names did not change (`subs[<master>][]` = discipline id), so the save handler was
not touched at all. That mattered for the risky part: **filtering hides columns, and hidden
checkboxes are still submitted** — only `disabled` controls are left out of a POST. Verified in
the browser with the filter on one master: 25 checked boxes sitting in hidden columns, and all
29 checks still present in the form's `FormData`, all nine masters still carried.

**Mobile (#10, #11, #12).** The nav collapses behind a button labelled with the current page,
which took the sticky header from a three-row block down to 64 px; it is script-driven from
`matchMedia`, and the markup starts expanded so scripting-off degrades to today's behaviour.
The dashboard's "Карта" table — the only one on that page with headers and four columns —
gets the existing `responsive` stacking; the others are label/value pairs that already fit, so
they were left alone rather than stacked for the sake of it. On the chat page, an open
conversation is ordered first on a phone with the player list under it and capped at 45vh, so
tapping a player no longer looks like nothing happened.

Checked at 375–577 px: no page scrolls sideways, every wide table is inside a scroll
container, and at 1280 px the nav is flat with all twelve links, the toggle is hidden, the
filter defaults to "все сразу" with all 99 checkboxes visible, and the matrix fits with no
horizontal scrolling.

### One real slip, on live data

Testing the transposed form's save path, I posted the form back unchanged expecting a no-op
and instead got "изменено мастеров: 1" — `MG_LESHA`'s subscription to discipline 11 was gone.

The panel was not at fault. My test harness built the POST body with `'\n'.join(...)`, leaving
no trailing newline, and the shell's `while read` loop silently drops a final unterminated
line. That line was `subs[MG_LESHA][]=11`, so the form arrived genuinely missing one checkbox
and the handler did exactly what it should with what it was given.

Restored immediately and confirmed byte-identical to the pre-test snapshot, then the test was
re-run correctly: "Ничего не изменилось", 29 rows before and after. Worth recording because
the failure mode is so quiet — a truncated POST to this page looks exactly like a master
deliberately unticking a box, and the handler cannot tell the difference. The existing guard
(only rewriting masters named in `masters[]`) is what keeps a truncation from wiping everyone
rather than one row; it is there for a reason and should stay.

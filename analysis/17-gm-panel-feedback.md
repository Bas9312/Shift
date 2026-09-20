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
with each batch since and now sits at `v=9`. Bump it whenever you touch the stylesheet.

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

## The pages he never opened, on a real device

Same pass over `points`, `quests`, `refs`, `artifacts`, `effects` and `aura` — first by
reading them, then by driving the live panel in Chrome on the Android emulator
(`emulator-5554`, 1080×2400 at density 420, so a ~411 px CSS viewport: a real phone width, not
a desktop browser squeezed narrow).

Code pass found little. Every POST form carries a CSRF token. The `UPDATE`-then-`INSERT`
rowCount trap that bit `set_local` exists nowhere else — the other two `INSERT`s into
`noisemancy_*` use `ON DUPLICATE KEY UPDATE`, which is immune. Only the dashboard carries a
meta refresh, and it has no data entry.

What the device found:

- **`refs.php` — the abilities catalogue was unusable on a phone.** "Тип" was pinned at a
  fixed 170 px inside a `.row`, so flex never wrapped and the description textarea got
  whatever was left: about three words wide, with the text clipped. 78 abilities all rendered
  that way. Fixed by stacking `label` children of a `.row` at full width under 760 px, which
  is right everywhere on a phone; buttons in a row are untouched.
- **The mobile header put "Выйти" before the menu.** The batch-C rule gave `order` to the
  toggle and the nav but not to the logout form, which kept the default `0` and jumped to the
  front — so the logout button sat exactly where a thumb reaches for the menu. Every item in
  the bar is ordered explicitly now.
- **`quests.php` had one more untreated table**, the per-player progress grid: four columns,
  the last a row of controls. It renders only once players actually start a chain, i.e. it
  first appears mid-game, which is the worst possible moment to find out it does not fit.
  Given the stacking treatment.
- **"Или укажите точное время справа"** in `effects.php` and `points.php`. The field is below
  on a phone, not to the right. Same class as the chat page's "Выберите чат слева".

What the device confirmed working: the collapsed nav (64 px header, menu names the current
page, opens to twelve links), the transposed subscription matrix (one card per discipline,
filtered to a single master), the noise glossary, the noise journal scrolling inside its card
instead of off-screen, and — the point of the whole exercise — tapping a player in the chat
now puts the conversation, reply box, discipline picker and send button on the first screen,
where before the player list pushed them out of sight.

One thing I called a bug and was not: an artifact badge reading "у anti" looked like clipped
text, so I added `flex: none` to `.badge`. The holder's userId is literally `anti` (Анти).
Reverted — a defensive rule carrying a false explanation is worse than no rule.

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

## Second walkthrough, 2026-09-20

Nikolai went through the panel again, this time exercising it rather than only looking, and
sent ten more notes. Nine are fixed; the tenth needs work outside the panel and is described
at the end.

### The chat thread was rendered upside down (his #2)

`GET /messages_api/chats/{peer}/history` returns messages **newest first**. This page was
written assuming the opposite — the comment on the scroll script says "history is oldest-first,
so open it on the newest message instead of the first one" — so it rendered the oldest at the
bottom and then dutifully scrolled there. A master opening a conversation landed a year deep
in history and had to scroll up through everything to find what they had just sent.

The same wrong assumption picked the reply's default discipline: it walked `array_reverse()`
looking for the player's most recent tagged message and found their **oldest** one instead, so
the tag pre-selected in the reply box could be a year stale.

Fixed by sorting on `created_at` rather than trusting whatever order the API returns, which
also means a future change at the API end cannot flip it back.

### Unread counters (his #1 and #7)

Two separate confusions, neither of them wrong behaviour:

- **Nothing cleared the circle.** There was a "Пометить прочитанными" button, but it sat
  between the reply form and the thread where nobody found it, and replying did not clear
  anything. Answering is when a master considers a question handled, so a reply now marks that
  player's unread messages read and says so in the confirmation. The button stays for "read
  it, not answering".
- **Phone showed 1, desktop showed 12.** Both correct: the list and every counter are filtered
  by the subscriptions of whoever is selected in "Отвечаю как", and that choice lives in the
  session, so two devices drift apart. `MG_TARI` genuinely sees 12 unread from `bas` while
  everyone else sees 1–2. The chat list now says whose eyes it is showing and that the numbers
  are per-master.

### The rest

- **#3** "Только игроки" returned NPCs. NPCs are not a column — they are marked by convention,
  `player_name = 'НПС'` (32 rows). The filter excluded masters and nothing else. Now it
  excludes NPCs too, and there is a "Только НПС" option. Verified against the database: 42
  players, 32 NPCs, 9 masters, 83 total.
- **#5** The level picker offered "тлимлот", because it listed every DISTINCT level in the
  table and one test artifact carries that word. It now offers the three canonical levels plus
  «другое…», and additionally the current artifact's own level when that is custom — so
  editing can never silently change it. The filter dropdown still lists everything present in
  the data, which is what a filter is for.
- **#4, #6** "Create new" forms sat under the whole list on artifacts, points and all four
  catalogue tabs. Moved to the top, collapsed behind a summary, so they cost no space until
  clicked.
- **#8** The player card's module and ability grids (38 and 78 checkboxes) are folded away
  behind a summary showing how many are selected, so artifacts and aura below them are
  reachable without scrolling past everything.
- **#10** My own fault from the previous round: `.table-scroll > table { min-width: 520px }`
  forced *every* table in a scroll box to 520px, including the two three-column noise
  summaries, which are header-only whenever nothing has made noise in 24 hours. Nikolai
  guessed the cause exactly ("МБ потому, что пустые"). Now `width: auto; min-width: 100%`:
  measured at a 411px viewport, the empty summary is 276px in a 276px box and the wide journal
  is 672px in a 357px box and scrolls inside it.

### #9, effects history — done

`effects` rows are deleted outright, by `effects_api/cron_delete.php` on expiry and by
`effects_api/api.php` on manual removal. The table sat at `AUTO_INCREMENT = 162` with zero
rows: 161 effects had come and gone leaving nothing behind, so "что на нём висело час назад"
had no answer at all.

New table `effects_log` (append-only; `event` and `source` are `VARCHAR(16)` rather than
enums, because this server runs with an empty `sql_mode` and would silently coerce a bad enum
to `''`). Four events are recorded:

| event | written by | source |
|---|---|---|
| `issued` | `effects_api/api.php` after create | `gm` when the panel sends it, else `app` |
| `edited` | `gm/pages/effects.php` | `gm` |
| `removed` | `effects_api/api.php` after delete | `gm` when the panel asks, else `app` |
| `expired` | `effects_api/cron_delete.php` | `cron` |

This is the part that had to reach outside the panel, and two rules kept it safe:

- **Log after `commit`, never inside the transaction.** A failing journal insert must not roll
  back the effect a master just hung. `effects_log()` additionally swallows its own errors into
  `error_log`. The cost is that a crash between commit and log loses one row; that is the right
  trade.
- **Read the text before deleting.** Both delete paths now select `textToShowPlayers` (and
  `expireAt`) before the `DELETE`, or the journal would record that something ended without
  being able to say what it was.

The helper lives in `effects_api/config.php` rather than its own file, because both `api.php`
and `cron_delete.php` already require it — one fewer include that can be forgotten on upload.

Panel side: a filterable history table at the bottom of the Эффекты page, newest first, last
100 rows, with a per-player filter. It says in plain text that it only covers 2026-09-20
onwards, since nothing earlier can be reconstructed.

Verified end to end against the live game: issue → edit → remove through the panel logged all
three with `source = gm` (and the `removed` row correctly carried the *edited* text, proving
the pre-delete read works), then an already-expired effect was swept by the real cron and
logged as `expired / cron`. `effects` returned to zero rows and the test entries were removed
from the journal afterwards.

Two things noticed while doing this, neither introduced here:

- `effects_api/cron_delete.php` is a plain file inside a web-served directory, so it can be
  triggered over HTTP by anyone who knows the URL. It only deletes effects that have already
  expired, which is what it does on schedule anyway, so the impact is small — but it is not
  meant to be a public endpoint. Its first line also used to warn on `foreach ($argv …)` when
  reached that way; that is now guarded.
- `users.effects` carries ids of effects that no longer exist for two players (7 ids). Harmless
  in practice: `mage_profile_api/api.php:601` replaces that column with a live query before
  serving a profile, so no player sees it.

## The panel offered to delete a working mechanic

Raised by the owner, not in either list, and the most dangerous thing found so far.

The noise page warned "Записей на несуществующих игроков: 3" and offered a button to delete
them. All three were `<player>_Proxy` rows — and those are the Proxy node mechanic working as
designed: when a player has an active node, half of every noise increase is deliberately
parked on a synthetic row under that name (`noize_api/service.php:204`), which is exactly why
no `users` entry exists for it. Pressing the button would have zeroed the noise sitting on
every proxy in the game, mid-session, with a confirmation that said it was removing junk.

Fixed in both places. The page now labels proxies as proxies and says not to delete them, and
only offers the button for a row that is neither a player nor a proxy — a real leftover from a
deleted player, of which there are currently none. The `DELETE` itself also excludes
`%\_Proxy`, because the form can be submitted without going through the interface. Verified
against the live database: the old query would have removed `anton_Proxy`, `lina_Proxy` and
`pavlik_Proxy`; the new one removes nothing.

### Still open, and it is a balance call rather than a bug

The warning's second claim was true, and that part is not fixed because it should not be
decided here:

- `noize_api/api.php:180` — `noisemancers` is `SELECT COUNT(*) FROM noisemancy_local`, so proxy
  rows are counted as noisemancers in the number the player sees in the terminal. Right now
  that is 8 rather than 5.
- `noize_api/service.php:346` — `$activeCount` counts rows with a recent `last_action_at`, and
  proxy rows get theirs stamped when noise lands on them. That number goes into the crowd
  damping `crowdK / (crowdK + activeCount)`, so proxies make the crowd look bigger and global
  noise grow more slowly.

Whether a Proxy node should read as a noisemancer to players, and whether it should damp the
world's noise, is a design question about how the mechanic is meant to feel. Excluding
`_Proxy` from either query is a one-line change once that is decided.

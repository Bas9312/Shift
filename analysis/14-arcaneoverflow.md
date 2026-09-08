# 14. «Аркан Оверфлоу» — the in-world forum and the pipeline that writes it

> Written 2026-09-08, describing work done 2026-08-20…22. This is **game content**, not client
> code, which is why it is absent from documents 01–13. It is here so that the analysis folder
> stops implying the Android client is the whole project.
>
> No credentials in this file. The write token lives in `arcaneoverflow_api/config.php` on the
> server and in the private memory file `arcane-overflow-api.md`, neither of which is in git.

## What it is

`shift96.ru/arcaneoverflow/` is a Stack Overflow for mages, in-world: questions, answers,
comments on answers (not on questions), votes, an accepted answer, and a discipline filter.
Players read it as a real forum their characters would have been using for years. The forum
engine itself belongs to Тари/Женя; what was built on this side is the **content** and the
**API that writes it**.

State on 2026-09-08, counted from the live API:

| | |
|---|---|
| Threads | 78 |
| Answers | 133 |
| Threads with an accepted answer | 60 |
| Distinct pen names posting | 32 |
| Disciplines represented | 11 (all of them) |
| Character agents | 17 in `.claude/agents/ao-*.md` — 15 named personas, one multi-mask for drive-by posters, one reviewer |

## Why it needed a pipeline rather than a person writing 78 threads

The forum has to read as a place with history and a population, not as one author doing voices.
Three properties do the heavy lifting:

- **One agent per persona, each with its own reading list.** A persona's file whitelists which
  corpus documents it may open, and that isolation is the entire point: a classic ritualist
  physically cannot look up terminal commands, so it cannot accidentally know them. Voice
  consistency is a side effect; knowledge boundaries are the goal.
- **Deliberate unevenness.** Each thread carries 4–6 replies, never a fixed count. Some answers
  are wrong, some are unhelpful, some are drive-by noise (`нога_не_болит`, `RTFM_ты_читал`,
  `копипаста_бот`) — a forum where every answer helps is a forum nobody believes.
- **A canon reviewer with full access.** `ao-canon` reads the master corpus, `[[MG]]` blocks and
  per-player aura marks included, and reviews a finished thread before publication. It is not
  optional: on the pilot thread it caught eight errors, one of which was advice that would have
  been dangerous in-world.

Ratings are scaled to a game of 25–50 people: **50 is the site-wide ceiling**, good answers sit
at 8–20. The single exception is `Solomon_967BC`'s 80 000 karma, which is a legend the owner
wants kept.

## Where things live

- **API sources** — `SERVER/public_html/arcaneoverflow_api/` (mirror, gitignored). The API adds
  `api.php`, `config.php` and `.htaccess`, plus `external_id` columns in `database.php`.
  `repository.php`, `batch.php` and `database.php` belong to the forum page and were edited by
  Тари/Женя on 2026-08-20 — **download and diff before touching them**.
- **Contract** — `GET https://shift96.ru/arcaneoverflow_api/v1/help` returns the whole thing in
  machine form; a copy is in `API/API arcaneoverflow.txt`.
- **Content and tooling** — `_local/arcane/` (gitignored, machine-local):
  `threads/*.json` are the source of truth *and* the API payload; `tools/add.py` files agent
  replies into them, `publish.py` pushes, `pull.py` snapshots the live forum for personas to
  read, `refresh.py` regenerates the pen-name registries, `apply_edits.py` folds an edited
  export back into the threads.
  `lib/` (13 documents) is the persona corpus — master versions with `[[MG]]` blocks cut out;
  `lib-mg/` (13) is the same corpus uncut, for `ao-canon` only; `cheat/` (17) holds condensed
  sheets the personas read instead of the full documents, roughly 3× cheaper per reply.
  `УСТАВ.md` carries the shared writing rules, `НИКИ.md` and `СКАЗАНО.md` the pen-name registry
  and what each persona has already claimed.

## Things worth knowing before touching it again

- **Writes go through the API, never through SQL.** The API is what makes a repeated call
  idempotent: every post carries an `external_id` (`T-01`, and derived keys `T-01#a0`,
  `T-01#a0#c1` for nested answers), so re-publishing updates its own row instead of duplicating
  it. That is the property that makes the whole pipeline re-runnable.
- **Paths inside agent files must be absolute.** Subagents resolve relative paths against a
  different working directory and silently find nothing — the failure looks like a persona that
  suddenly knows less, not like an error.
- **The token also unlocks `actor_*` fields on reads**, which reveal which account is behind a
  pen name. That is master-side information; do not hand it to players.
- **`POST /v1/upgrade` runs the schema migration over HTTP.** That is how `external_id` was
  added, because external MySQL access to beget is blocked.

## Open

- **Nothing has been written since 2026-08-22.** Whether 78 threads is enough depends on how
  much the game leans on the forum; the pipeline is idle, not finished.

**Not a bug:** the discipline of threads `T-05`, `T-28` and `T-29` renders as
`ШЖ╫■┐ьЮ≈╒╬м╤нт&╜╓я`. That is Шумомантия, and it is spelled that way deliberately — the
neomagic discipline works through the internet, so its name arrives corrupted. Leave it alone;
see [11-status.md](11-status.md) §C.

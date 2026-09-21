# Familiar chat proxy and the player–familiar bond

Status: **deployed and verified end to end on 2026-09-21.** The bond loop was exercised on
the live systems with the test account `ChaseTest`: the familiar consented, the row reached
MySQL, the notification reached the masters' chat, a master confirmed the ritual in the GM
panel, and the familiar started answering as bound.

Related: [12-familiars-remote-assets.md](12-familiars-remote-assets.md) (the familiar
catalogue and artwork), [13-gm-web-admin-plan.md](13-gm-web-admin-plan.md) (the GM panel).

---

## 1. Why this had to be rewritten

OpenAI **sunset the Assistants API on 2026-08-26** — a hard cut, no read-only period. The
proxy was built on assistants and threads, so from that date every `/chat/send` answered
500. The database shows the shape of the outage precisely: assistant replies stop on
2025-09-22 (the end of the last game), player messages continue to 2026-09-19, and the eight
most recent ones never got an answer.

Two things were lost with the shutdown and two were not:

- **Lost:** the assistant objects, and with them the familiars' system prompts, which lived
  only in OpenAI's dashboard.
- **Kept:** every message ever exchanged, because the proxy had always mirrored them into
  its own SQLite. 896 messages, 441 of them familiar replies — enough to reconstruct voices
  from, and the reason the rewrite was an inconvenience rather than a disaster.

The lesson is recorded here because it generalises: **anything that only exists inside a
vendor's console is not backed up.** Prompts now live in files, in this repo.

## 2. Shape of the rewrite

`gptFamiliarProxy.py` (deployed as `/opt/gptproxy/app/main.py`) is now stateless with
respect to the vendor. Every reply is one `responses.create` call carrying:

| part | content | changes per request? |
|---|---|---|
| `instructions` | `_core.md` + the familiar's card | no — this is what prompt caching holds |
| `input[0]` | `<state phase="…" />`, role `developer` | only when the phase changes |
| `input[1:]` | the tail of the chat, `HISTORY_TURNS` messages | yes |

Everything the Assistants API used to hold — threads, run polling, run status — is gone,
along with roughly a third of the code. The HTTP contract with the Android client did not
change, so no client release was needed for any of this.

Measured on the live service: ~1150–1500 input tokens per reply of which ~90% come back
cached, ~120 output tokens. A whole game (≈530 replies, the last game plus 20%) costs
**one to two dollars** on `gpt-5.6-terra`, and under ten even on the flagship. Cost is not a
constraint here and should not drive the model choice; the model id is `OPENAI_MODEL` in
`/etc/gptproxy.env`, changing it is a restart.

## 3. The three phases

```
bond_confirmed ?  -> BOUND
consented ?       -> CONSENTED
otherwise         -> NEGOTIATING
```

The familiar decides one thing and one thing only: whether it has agreed. That arrives as a
machine signal, not as text — the model answers under a strict JSON schema
`{reply, consent}`, and `reply` alone is what is stored and shown. **No regex ever looks for
"я согласен".**

`consent = true` is honoured only in `NEGOTIATING`; in any other phase it is logged and
ignored. The model can never reach `BOUND`.

## 4. Where the state lives, and why it is split

This is the part that is not obvious from the code, so it is written down.

**Masters have no access to the proxy and will not get it.** That single constraint decides
the layout: confirmation of the ritual has to be settable where masters actually work, which
is the GM panel. So:

- **consent** is produced by the proxy and stored in its SQLite (`bonds`). Nobody else
  writes it.
- **confirmation** lives in `familiar_bonds` on the game server, written by the panel in
  direct SQL. The proxy only mirrors it.

The mirror is deliberately **one-way**: once the proxy has seen a bond confirmed it records
that forever and stops asking. A player who is bound therefore stays bound even if
shift96.ru is unreachable, and the failure mode of the game server being down is that a
not-yet-bound player waits a little longer, not that a bound player is silently unbound
mid-game.

There is **no public confirm endpoint, on purpose.** One would let anybody declare
themselves bound. The two endpoints the proxy does use —
`GET|POST /familiars_api/api/v1/bonds` — are guarded by `X-Bond-Token`.

## 5. Outgoing side effects never block a reply

Two things happen when a familiar consents: the consent is pushed to the game server, and a
notification is placed in the player's chat with the masters. Neither is allowed to break
the conversation, so both go through a local outbox: the consent row is written first,
atomically, and the sends are flagged and retried by a background loop.

`messages_api` has no idempotency key, so deduplication is ours: a notification is sent once
because `notified_at` is set once. A 4xx answer is treated as permanent and closes the row
with an ERROR in the log — a hopeless row must not be retried forever — while 5xx and
network errors are retried.

The notification is posted exactly the way the client posts player messages
(`X-User-Id: <player>`, `recipient_id: MG_Bas`, `tags: 10`), so it lands in the thread
masters already watch rather than in a new channel nobody opens. Its text is prefixed
`[авто]` so nobody mistakes it for something the player wrote.

## 6. The mirror trap

`familiar_mirror` collapses every player into one shared history key
(`__GLOBAL__MIRROR__`). That is deliberate: the mirror is meant to treat everyone as the
same interlocutor.

**Bond state must never use that key.** It is keyed on the real `user_id`. Key it on the
canonical one and the first player the mirror agrees with binds it for everybody at once.
There is a test for this specifically, and it should stay.

See §8 — the shard cards delivered on 2026-09-21 describe three mirror shards that
explicitly do *not* share memory, which is in tension with the shared history and is an open
question for the owner.

## 7. Prompts

`prompts/_core.md` is the shared instruction — role, canon, the three phases, how to talk,
the answer format, and the `ability_execution` block that defines `MG` / `PLAYER` /
`PASSIVE`. Each familiar is one card file named for its id; files starting with `_` and
`README` are not familiars.

Two findings from testing the first finished card are baked into the core:

- **Disclose the mechanics once.** The first draft said "before consent, state the ability,
  weakness and bond type", and the model read that as *on every message*, reciting the full
  spec twice in a row inside one conversation. Negotiation read like a form. Now it names
  them once and afterwards refers back briefly.
- **One question means one question mark.** The model was gluing two questions with «и» and
  counting it as one.

Card craft, learned the same way: the highest-leverage section is the one naming **what the
familiar compulsively distinguishes** (for the spirit of lost things: чужое / ничьё /
забытое / потерянное). That is where the good lines come from, because it gives the familiar
something to say about anything at all. A list of characteristic words in the speech section
works poorly — the model spreads them evenly and flatly; two or three **example lines** in
voice anchor the register far better.

## 8. Open questions

- **Mirror shards vs shared history.** The delivered cards define three Осколка Зеркала,
  each with its own bearer, sharing a bearers' chat but explicitly *not* each other's
  memories. The proxy currently gives `familiar_mirror` a single shared conversation, which
  is the opposite. Either the card loses the no-telepathy lines, or the mirror becomes a
  per-player chat like everyone else (one constant, `SHARED_FAMILIAR`). Owner's call.
- Three catalogue entries have no card: `familiar_glazastik`,
  `familiar_dancefloor_queen`, `familiar_player_kristina` — the player-specific familiars
  seeded in §12. They answer `400 unknown familiar` until someone writes them.
- The proxy still has **no authentication**: `user_id` is taken from the request body, so one
  player can read another's chat by guessing an id, and the OpenAI key is billable by anyone
  who knows the address. `PROXY_SECRET` exists and is off; turning it on needs a header in
  `RetrofitClient` and stops outsiders but not players impersonating each other. Proper fix
  is a per-player token. Traffic is plain HTTP.

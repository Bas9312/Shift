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

The card delivered on 2026-09-21 originally described three shards that share a bearers'
chat but *not* each other's memories — the opposite of what the storage does. The owner
chose to keep the shared conversation, so the card was rewritten: the three shards are one
reflecting surface, and the mirror is told that it cannot tell which bearer is speaking and
must never attribute or retell. The storage limitation is now part of the character. The
bond stays per bearer regardless — a shared surface is not a shared bond.

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

## 8. The ability a bond grants (2026-09-21)

Until this day a familiar's ability existed only as prose inside its card: confirming a
ritual changed nothing a player could see in their profile. Now the ability is a catalogue
row like any other, and confirming the ritual hands it over.

Three small pieces make that work:

- `abilities.external` (new column, default 0). `sync_abilities_marks` used to hardcode
  `external = 0` on every ABILITY aura mark; it now reads this column. Familiar abilities are
  the only rows with `external = 1`, so they show up as **external** marks — visible to
  whoever reads the aura — while all 75 pre-existing abilities keep behaving exactly as
  before.
- `familiars.ability_id` (new column) — which ability a familiar grants.
- The panel's «Обряд проведён» button now also writes the ability to the player. It goes
  **through `PUT /mage_profile_api/api/v1/user/{id}`**, never SQL, because that endpoint is
  what rebuilds the aura marks. «Снять» revokes both.

The twenty familiar abilities occupy ids **101–120**, deliberately clear of the 1–80 range
the game already used, so a familiar ability is recognisable by its number alone. Types were
assigned by what the ability does: познание for the ones that answer questions (лиса,
зеркало, компас, жаба, ворон-советчик, барабашка, Аркадий, Wi-Fi, плохой совет), защита for
the ones that blunt an attack (ящерица, кошка, Зев Бездны, «Ещё пять минут», ворон),
изменение for the ones that move things (Вайнера, дух вещей), усиление for the healing cup,
прочее for the rest.

Descriptions carry the ability and its cooldown but **not** the familiar's weakness. The
mark is external, and a condition like «раз в сутки обязан утащить ценность» would then be
readable by anyone scanning the player's aura. If that leak is wanted, it is one edit per
description.

Three catalogue familiars have no ability because they have no card: `familiar_glazastik`,
`familiar_dancefloor_queen`, `familiar_player_kristina`. Confirming a bond with them works
and simply reports that there is nothing to grant.

`familiars.description` was empty for all 23 rows and is now filled for those same 20 — what
the creature is, its bond type, its ability with the cooldown, and the price. The Android
client carries the field in `FamiliarCatalogResponse` but never renders it, so today this is
text for masters in «Справочники»; it is written to survive being shown to players later,
since a mage hears the ability, the weakness and the bond type from the familiar itself
before consenting anyway. The abilities tab of that page now sorts `ability_id DESC` — newest
first, which also puts the familiar block at the top.

## 9. The five-hour reservation (2026-09-21)

A consent used to hold nothing. The map point stayed with its player for the ordinary
`FAMILIAR_HOLD_MINUTES = 15` of silence and was then free for anyone — so a player who went
looking for how the ritual is performed lost the point while walking. Now consent reserves
the point for `BOND_RESERVE_HOURS = 5`; if the ritual is not confirmed within that window the
consent is deleted and the point is released.

The sweep lives in `releaseExpiredFamiliars()` in `api_geo`, which already ran on every
`GET /points` and on `/bind`. Player traffic is the clock, so there is still no cron. Order
matters inside it: expired consents are deleted first, then points are released for everyone
whose remaining consent is not pending — which is why the hold query needs no time arithmetic
of its own. A **confirmed** bond deliberately does not hold the point: the familiar is already
in the profile, and holding it forever would starve everyone else.

The proxy caches consent locally, so it has to learn about an expiry it did not cause. On its
periodic check it now also reads `consented`, and clears the local row when the game server
says no. The guard is `pushed_at`: only a consent the server definitely received may be
cleared this way, otherwise an unreachable game server would wipe a fresh consent still
sitting in the outbox. A confirmed bond is never cleared — that one-way rule stands.

One consequence worth knowing: a push rejected with 4xx marks `pushed_at` to stop the retry
loop, so such a consent will later be cleared by this same path. That is consistent — if the
game server refuses to record the bond, the bond does not exist — and the 4xx is in the log.

Both notices in the chat (`CONSENT_NOTICE`, `EXPIRED_NOTICE`) are stored with the `assistant`
role inside `⟪ ⟫`. A real `system` role would mean changing the CHECK constraint, the client
model and the adapter, and shipping an APK for what two brackets already convey.

## 10. One point, one bearer (2026-09-21)

Confirming a ritual now retires the map point the player negotiated through. The rule is
per **point**, not per familiar: the mirror gets three points on the map and therefore three
bearers, the cup gets one and therefore one. How many bearers a creature has is decided by
how many points the masters place, which is where that decision belongs.

The point is not deleted — `expireAt = NOW()` drops it out of `GET /points`, whose base
filter is already `expireAt > NOW() OR expireAt IS NULL`, while the row and its
`assigned_player` stay as the record of who took it. «Снять» reverses it: `expireAt` back to
NULL and the assignment cleared, so a master who confirmed the wrong row can undo it
completely.

Targeting is by `assigned_player`, not by familiar id, which is what keeps the other points
of the same creature on the map. A bond created through «Обряд провели вживую» usually has
no point behind it at all; that path simply reports that nothing on the map changed.

## 11. Open questions

- **The mirror carries the previous game across.** Every other familiar is keyed per player,
  so a new player always starts a clean chat. `familiar_mirror` is not: its shared log holds
  123 messages from 2025-08-30 onwards, and every new conversation opens with the tail of
  the last game already in context. What remains open is a game question, not a technical
  one — whether a mirror that can refer to last game's events is a problem or a feature.

  Two worries about that log were **tested and did not hold up**, so nobody needs to retest
  them. Those old replies come from the lost pre-2026 prompt and are written in a completely
  different voice — an oracle dispensing quoted aphorisms — so the obvious fear is that the
  model imitates forty examples of its own former self instead of following the card. It
  does not: asked point blank for a divination, the mirror answered in the new card's voice
  with no aphorisms at all. The second guess, that the same history was making it terser
  than other familiars, was checked by running the identical card under a throwaway id with
  an empty history; the answer came out just as dry. The reserve is the character — the card
  says "спокойный, внимательный и немного отстранённый" — and not the context.
- Three catalogue entries have no card: `familiar_glazastik`,
  `familiar_dancefloor_queen`, `familiar_player_kristina` — the player-specific familiars
  seeded in §12. They answer `400 unknown familiar` until someone writes them.
- The proxy still has **no authentication**: `user_id` is taken from the request body, so one
  player can read another's chat by guessing an id, and the OpenAI key is billable by anyone
  who knows the address. `PROXY_SECRET` exists and is off; turning it on needs a header in
  `RetrofitClient` and stops outsiders but not players impersonating each other. Proper fix
  is a per-player token. Traffic is plain HTTP.

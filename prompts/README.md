# Familiar prompts

Two kinds of file live here.

**`_core.md`** — the rules every familiar shares: staying in character, what may not be
invented, the three bond phases, and the shape of the answer. Files starting with `_` are
never treated as familiars.

**`<familiar_id>.md`** — one card per familiar. The file name (without extension) **is** the
id the Android client sends in `familiar`, so it must match `familiars.id` on the server and
`users.familiar` exactly:

```
prompts/familiar_mirror.md
prompts/familiar_fox.md
prompts/familiar_lost_things.md
```

The proxy sends `_core.md` + the card as the model's `instructions`, in that order. That
block is identical on every request, which is what makes prompt caching work — so keep
anything that changes per request out of it.

Start a card with `Имя: <отображаемое имя>.` The proxy reads that line for the notification
it sends to the masters; without it the notification falls back to the raw id.
`familiar_lost_things.md` is the worked example to copy.

A familiar with no card does not exist as far as the proxy is concerned: both `/chat/send`
and `/chat/history` answer `400 unknown familiar '<id>'`. Adding a familiar means dropping a
file here; no code change.

The nine legacy ids (must not change, they are stored in `users.familiar`):

`familiar_mirror`, `familiar_vaynera_spirit`, `familiar_weird_compass`,
`familiar_gentlemans_tear`, `familiar_dobyvala`, `familiar_abyss_eater`,
`familiar_earth_cat`, `familiar_malachite_lizard`, `familiar_fox`

The eleven newer ones are listed in [analysis/12-familiars-remote-assets.md](../analysis/12-familiars-remote-assets.md);
the live catalogue currently holds 23.

## Editing during a game

After changing a file, pick it up without restarting the service:

```bash
curl -X POST http://<proxy>/admin/reload-prompts -H "X-Shift-Token: $PROXY_SECRET"
```

An empty or unreadable directory is refused on reload — the previously loaded cards stay in
memory rather than leaving every familiar mute mid-session.

## Note

These files are the familiars' characters. They belong on the proxy host and in this repo,
never inside the APK — players should not be able to read what their familiar is made of.

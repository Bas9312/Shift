# Proxy smoke tests

58 checks over `gptFamiliarProxy.py` — phases, consent, the shared-mirror trap, the outbox,
prompt loading and the error paths. No network and no OpenAI key: the SDK is replaced by
`stub/openai.py` and the game server by a fake inside `smoke.py`.

Needs `fastapi` and `httpx`. There is no `python3-venv` on this machine, so install them
into a directory and point `PYTHONPATH` at it:

```bash
pip3 install --target ./libs fastapi httpx
PYTHONPATH=./stub:./libs python3 smoke.py
```

Exit code is 0 only if every check passed. Run it before uploading `main.py` to the proxy.

The fake game server lets tests drive failures directly: `game.down = True` simulates
shift96.ru being unreachable, `game.status = 400` a permanent rejection, and
`game.confirmed` holds the bonds a master has confirmed.

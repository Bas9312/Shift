"""Minimal stand-in for the openai SDK so the proxy can be smoke-tested offline."""
import json

CALLS = []              # every responses.create(**kwargs) recorded here
NEXT = [("ответ фамильяра", False)]   # (reply, consent) the fake model will produce
RAW = [None]            # set to a string to return it verbatim instead of NEXT
RAISE = [None]


class _Resp:
    def __init__(self, text):
        self.output_text = text
        self.status = "completed" if text else "incomplete"
        self.incomplete_details = None if text else {"reason": "max_output_tokens"}


class _Responses:
    async def create(self, **kwargs):
        CALLS.append(kwargs)
        if RAISE[0] is not None:
            raise RAISE[0]
        if RAW[0] is not None:
            return _Resp(RAW[0])
        reply, consent = NEXT[0]
        return _Resp(json.dumps({"reply": reply, "consent": consent}, ensure_ascii=False))


class AsyncOpenAI:
    def __init__(self, **kwargs):
        self.init_kwargs = kwargs
        self.responses = _Responses()

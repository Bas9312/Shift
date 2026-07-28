# Shift — заметки для Claude

Android-клиент оффлайн-игры/квеста (Kotlin, View + viewBinding, без Compose). Приоритет
владельца — **надёжность в бою**; красота кода вторична. Роли: игрок (id вроде `Bas`),
мастер игры (`MG_Bas`, префикс `MG_`). Полный аудит и внесённые улучшения — в `analysis/`
(начни с `analysis/00-README.md` и `analysis/01-executive-summary.md`).

Сборка: `cd /home/bas/Shift && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleDebug --offline`
Эмулятор: `emulator-5554` (Pixel_6_API_35); adb — `/home/bas/Android/Sdk/platform-tools/adb`.

## Language (since 2026-07-28)

Write **everything in English** unless the owner says otherwise for a specific task: chat
replies, analysis documents, code comments, commit messages. The existing Russian comments
and docs stay as they are — do not retranslate them, just write new text in English.

The one exception is **user-facing Android string resources** (`res/values/strings.xml` and
anything else players read in game): those stay Russian, because they are game text, not
communication with the owner.

## Server side

The backend (PHP + MySQL on beget, served at `shift96.ru`) is owned by Тари, but the owner
authorised direct edits and FTP uploads. A local mirror lives in the project:

- `SERVER/` — mirror of `public_html`; the geo API is `SERVER/public_html/api_geo/api.php`
- `API/` — API documentation as `.txt`

Both are **gitignored** (`SERVER/` contains DB credentials in `config.php`).

FTP: host `bas931wn.beget.tech`, user `bas931wn_claude`; the FTP root **is** `public_html`.
The password is in the private memory file `shift-server-ftp.md`, not in this repo.

**Always download the live file and diff it against the local copy before uploading** — Тари
may have changed it in the meantime. Client-facing base path is
`http://shift96.ru/api_geo/api/v1/...`.

## RAG-поиск по проекту (используй ДО слепого grep/чтения больших областей)

Проект проиндексирован в локальный семантический индекс (эмбеддинги — Ollama
`qwen3-embedding:8b` на GPU, русскоязычный корпус ищется хорошо). Индекс и скрипты живут в
`/home/bas/AIWork/rag/`. Ищет по СМЫСЛУ и возвращает `repo/file:начало-конец` + сам код.

**Искать по Shift:**
```bash
cd /home/bas/AIWork && .venv/bin/python rag/rag_query.py "<запрос своими словами>" --repo Shift
```
Полезные флаги:
- `-k 8` — сколько результатов (по умолчанию 6);
- `--lang kt` — только Kotlin (или `xml`, `gradle`, `md`, …);
- `--docs` — только документация (в т.ч. `analysis/*.md`);
- `--answer` — прогнать локальный LLM (`qwen3.5:9b`) по найденному контексту и дать краткий ответ.

Без `--repo Shift` поиск идёт и по соседним проектам воркспейса AIWork
(`android.next`, `ios.next`, `ndw4`, `ndm`, `Mobile_docs`) — удобно посмотреть, «как сделали
у соседа». Для работы именно по Shift — всегда добавляй `--repo Shift`.

Примеры:
```bash
.venv/bin/python rag/rag_query.py "где считается уровень шума и применяются эффекты" --repo Shift
.venv/bin/python rag/rag_query.py "как обновляются точки на карте" --repo Shift --lang kt -k 8
.venv/bin/python rag/rag_query.py "какие известные риски надёжности" --repo Shift --docs --answer
```

## Переиндексация (после правок кода)

Индекс НЕ обновляется сам при редактировании файлов. Если менял код и хочешь искать по
свежему — переиндексируй (инкрементально, по sha1, считает только изменённое):
```bash
cd /home/bas/AIWork && .venv/bin/python rag/rag_index.py --only Shift
```
Если файл изменился с момента индексации, в выдаче `rag_query.py` результат помечается
`[STALE — переиндексируй]` — это сигнал прогнать индексацию. Полный прогон всех репо
(включая Shift) и так гоняется кроном по понедельникам в 17:00.

## Если поиск не отвечает

Нужна поднятая Ollama: `systemctl --user status ollama` (перезапуск — `systemctl --user restart ollama`).
Устройство RAG, модели и автозапуск подробно описаны в `/home/bas/AIWork/rag/README.md`.
Shift добавлен как «внешний» репозиторий (лежит вне `/home/bas/AIWork`) через
`EXTERNAL_REPOS` в `rag/common.py`.

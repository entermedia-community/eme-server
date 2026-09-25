---
name: opencode-v2-api
description: Use when working with OpenCodeClient.java or any code that talks to a paired OpenCode v2 HTTP server (the "opencode pair" web interface), or when starting/opening the OpenCode web UI locally. Covers the full verified /api/* endpoint surface, Basic auth, request/response envelopes, the SSE event stream wire format and event-type catalog, the v1→v2 migration mapping for OpenCodeClient.java, the fixed-login startup script bin/opencoded.sh (127.0.0.1:49374), and the live-probe recipe for re-verifying the API against a running server.
---

# Skill: opencode-v2-api

The OpenCode **v2** paired server (e.g. `http://127.0.0.1:<port>` from `opencode pair`) exposes a
JSON HTTP API under `/api/*`. There is **no public v2 API spec** — the official `/docs/server/`
page documents v1 only. This skill records the v2 surface reverse-engineered from the web-app JS
bundles and confirmed by live probing (server 2.0.14, verified 2026-09-23).

Primary consumer: `plugins/finder/code/org/entermediadb/mcp/client/OpenCodeClient.java`
(715 lines, still written against the v1 surface — see the migration mapping below).

## Starting the web interface (`bin/opencoded.sh`)

The repo ships `bin/opencoded.sh` for bringing up this server locally:

- Starts the OpenCode **background service** (`opencode service set/start`, not standalone
  `opencode serve`) pinned to **127.0.0.1:49374**.
- Pins a **fixed password** (no rotation): sign-in is always username `opencode` with the
  default in the script, overridable via the `OPENCODE_PASSWORD` env var. The script prints
  the credentials on every run.
- **Idempotent**: it only runs `opencode service set <x>` when the current value differs, so a
  normal run never restarts an already-correctly-configured server.
- Opens the local browser (`xdg-open` → `google-chrome` → `firefox`, best effort).

**Gotcha — self-hosting:** this agent session is hosted by that same service. Any
`opencode service set <x>` **stops the background server**, which kills in-flight tool calls and
makes the session report a restart. When an agent must change service config, run it detached so
the restart cannot take the caller down:

```bash
setsid nohup bash -c 'opencode service set password <pw>; sleep 3; opencode service start' \
  >/tmp/oc-restart.log 2>&1 < /dev/null &
```

## Ground truth and how to re-derive it

1. **Web-app JS bundles** — download the chunks served by the running server (they contain the
   complete typed API client: every path, method, query param, body field, success status, and
   declared error statuses). In this session they were saved to `/tmp/occhunks/`:
   - `health-CIQGyytw.js` — the API client definitions (`server`, `session`, `permission`,
     `form`, ... namespaces) plus the HTTP layer and the SSE iterator.
   - `runtime-CPxMNWhG.js` — event schemas (e.g. `Permission.Request`) and UI handler code
     showing how each event's `data.*` fields are consumed.
2. **Live probing** with curl (recipe at the bottom). Anything uncertain should be re-probed
   before being trusted; the surface can shift between OpenCode releases.
3. Captured event-stream samples from this session: `/tmp/probe-events.txt`,
   `/tmp/probe-events2.txt`; probe session response: `/tmp/probe-session.json`.

## Authentication

- **Basic auth on every `/api/*` route.** Username is literally `opencode`, password is the
  pairing password printed by `opencode pair`:
  ```
  Authorization: Basic base64("opencode:" + <pairing-password>)
  ```
  (Found in bundle: `` btoa(`opencode:${e.password}`) ``.)
- The password **changes on every pairing** — except in this deployment, where `bin/opencoded.sh`
  pins it to a fixed value (see "Starting the web interface" above), so it no longer rotates. It
  is also stored in the `aiserver` table (`serverapikey` field); `OpenCodeClient` reads it from
  there. If you start getting 401s, re-run `bin/opencoded.sh` (or `opencode pair`) and update the
  stored key to match.
- Working curl pattern (replace `<password>`):
  ```bash
  AUTH=$(printf 'opencode:<password>' | base64 -w0)
  curl -s -H "Authorization: Basic $AUTH" http://127.0.0.1:<port>/api/info
  ```

## Global conventions

- **All API routes live under `/api/`.** Any non-`/api/*` path (including old v1 paths like
  `/session`, `/event`, `/global/health`) returns the **SPA HTML page with HTTP 200** — v1-style
  requests silently get HTML instead of a 404. This is the #1 trap when migrating.
- **Every JSON response is wrapped**: `{"data": <payload>}` — including lists
  (`GET /api/session` → `{"data":[...]}`). 204 responses have no body.
- The client HTTP layer sets `content-type: application/json` automatically for any non-void
  body (binary bodies get `application/octet-stream`).
- Each API definition declares a `successStatus` and `declaredStatuses` (e.g. `[400,401,404]`) —
  treat anything outside as unexpected.
- Session IDs are URL-encoded in paths (`encodeURIComponent`), e.g. `/api/session/ses_.../prompt`.

## Endpoint reference (verified)

### Server / health

| Method | Path | Notes |
| ------ | ---- | ----- |
| GET | `/api/info` | 200 → `{"version":"2.0.14","pid":...,"urls":[...],"paths":{"tmp":"/tmp/opencode"}}`. **Replaces v1 `GET /global/health`.** |
| GET | `/api/event` | 200 → SSE stream (see below). |

### Sessions

| Method | Path | Body / query | Success | Notes |
| ------ | ---- | ------------ | ------- | ----- |
| GET | `/api/session` | query: `limit, order, search, parentID, directory, project, subpath, cursor` | 200 | `{"data":[session,...]}`. Session object: `{id, projectID, agent, model:{id,providerID,variant?}, cost, tokens:{input,output,reasoning,cache:{read,write}}, outcome, time:{created,updated,idle?,viewed?}, title, location:{directory}}`. |
| POST | `/api/session` | body: `{id?, title?, agent?, model?, location?, metadata?, permissions?}` | 200 | Returns `{"data":{session}}`. Live-verified with `{title, agent:"build", location:{directory}}`. |
| GET | `/api/session/{sessionID}` | — | 200 | `{"data":{session}}`. |
| DELETE | `/api/session/{sessionID}` | — | 204 | empty. |

### Prompting / execution (the important ones)

| Method | Path | Body / query | Success | Notes |
| ------ | ---- | ------------ | ------- | ----- |
| POST | `/api/session/{id}/prompt` | body: `{id?, text, files?, agents?, skills?, metadata?, delivery?, resume?}` | 200, **immediate** (~26 ms) | **Fire-and-forget.** Replaces BOTH v1 `POST /session/{id}/message` (sync) and `POST /session/{id}/prompt_async`. Returns the user message: `{"data":{"id":"msg_...","sessionID":"...","time":{"created":...},"type":"user","payload":{"text":"..."},"delivery":"steer"}}`. Completion must be detected via SSE events (`session.execution.succeeded|failed|interrupted`) or by polling messages. `delivery` observed values: `"steer"` (default), `"queue"` (pairs with `resume:false`). Declared statuses include 409 (conflict). |
| POST | `/api/session/{id}/interrupt` | query: `{resume?}` | 200 | **Replaces v1 `POST /session/{id}/abort`.** Returns data (not empty). |
| POST | `/api/session/{id}/background` | — | 204 | empty. |
| POST | `/api/experimental/session/{id}/wait` | — | 204 | Blocks until the session goes idle; declared statuses include 503. Useful as a "wait for completion" primitive if you don't want to consume SSE. |

### Messages

| Method | Path | Success | Notes |
| ------ | ---- | ------- | ----- |
| GET | `/api/session/{id}/message` | 200 | `{"data":[...]}` **newest-first**. First item is an idle marker `{id, time, "type":"idle", outcome}`; then the assistant message with `content[]` parts (`{type:"reasoning",text,...}`, `{type:"text",text}`, tool parts); then the user message. |
| GET | `/api/session/{id}/message/{messageID}` | 200 | single message. |

### Permissions (replaces v1 `/session/{id}/permissions/{permissionID}`)

| Method | Path | Body | Success | Notes |
| ------ | ---- | ---- | ------- | ----- |
| GET | `/api/session/{id}/permission` | — | 200 | list pending permission requests. |
| GET | `/api/session/{id}/permission/{requestID}` | — | 200 | single request. |
| POST | `/api/session/{id}/permission/{requestID}/reply` | `{decision, message?}` | **204 empty** | `decision` ∈ `once \| always \| reject` (same enum as v1 reply). Live shape of the triggering event: see `permission.asked` below. |

### Form replies (not needed by OpenCodeClient, for completeness)

- `GET /api/session/{id}/form`, `GET .../form/{formID}`,
  `POST .../form/{formID}/reply` body `{answer}` → 204.

### Other endpoints that exist (not needed by the client)

`/api/session/{id}/fork` (`{before?}`), `/agent`, `/model`, `/move` (`{directory,delivery}`→204),
`/command`, `/synthetic`, `/shell`, `/compact` (`{id?,delivery?}`), `/revert/stage`
(`{messageID,files?}`), `/revert`, `/revert/commit`, `/context`, `/diff`, `/inbox`,
`/inbox/{inboxID}`, `/generate`, `/environment`, `/view`;
`/api/experimental/session/stats` (query `from,to,project,timezone,tools`),
`/api/experimental/session/import`, `/api/experimental/session/{id}/log` (query `after,follow`);
plus `/api/location`, `/api/location/reload`, `/api/pty`, `/api/rpc/{rpcID}/{method}`,
`/api/plugin/update`.

## SSE event stream (`GET /api/event`)

### Wire format

- Standard SSE: frames separated by blank lines; **only `data:` lines carry JSON** — there are no
  `event:` or `id:` lines. Parse each `data:` line as one JSON object.
- The **event type is inside the JSON** (`"type"` field), not on an `event:` line.
- **Heartbeat is a comment line**: `: heartbeat` (no JSON). A parser that only handles `data:`
  lines must skip lines starting with `:`. There is no `server.heartbeat` event in v2.
- The first event after connecting is `{"type":"server.connected","data":{}}`.
- **The stream is global** — it carries events for *all* sessions on the server, including other
  OpenCode sessions running on the same machine. Every session-scoped event has
  `data.sessionID`; filter by your own session id. Some events also carry a top-level
  `"location":{"directory":"..."}` field.

### Event object shape

```json
{
  "id": "evt_...",
  "created": 1790222484303,
  "type": "session.execution.started",
  "location": {"directory": "/home/shanti/git/eme-server"},
  "data": { "sessionID": "ses_..." },
  "durable": {"aggregateID": "ses_...", "seq": 14, "version": 1}
}
```

`created` and `location` are optional (e.g. `server.connected` has neither).

### Event type catalog (observed live + from bundle)

Execution lifecycle for one prompt turn, in order:

1. `session.inbox.enqueued` — `data:{inboxID, sessionID, item:{type:"user",payload:{text},delivery}}`
2. `session.execution.started` — `data:{sessionID}`
3. `session.inbox.delivered` — `data:{sessionID, inboxID}`
4. `session.step.started` — `data:{sessionID, agent, model:{id,providerID}, assistantMessageID, snapshot, started}`
5. `session.reasoning.started` / `session.reasoning.delta` (`data.delta`) / `session.reasoning.ended` — `data:{sessionID, assistantMessageID, ordinal, state?/delta?}`
6. `session.text.started` / `session.text.delta` (`data.delta`) / `session.text.ended` — `data:{sessionID, assistantMessageID, ordinal, text?}`
7. Tool events: `session.tool.input.started`, `session.tool.input.delta`, `session.tool.input.ended`, `session.tool.called` (`data:{..., id, input, executed:false}`), `session.tool.progress`, `session.tool.success`, `session.tool.failed`; shell lifecycle `shell.created` / `shell.exited` / `shell.deleted`
8. `session.step.ended` — `data:{sessionID, assistantMessageID, finish:"stop"|"tool-calls"|..., rawFinish, cost, tokens:{input,output,reasoning,cache:{read,write}}, snapshot, files:[]}`
9. `session.usage.updated`
10. **`session.execution.succeeded`** — `data:{sessionID}` ← the "idle/done" signal

Failure / control events:

- `session.execution.failed` — `data:{sessionID, error:{type, message}}`. **`error` is an object**, not a string (e.g. `{"type":"compaction.failed","message":"Compaction produced no summary"}`). Read `data.error.message`.
- `session.execution.interrupted` — `data:{sessionID}` (after `interrupt`).
- `permission.asked` — `data:{id, sessionID, action, resources?, save?, metadata?, source?, message?}`. The request id to use in the reply URL is `data.id`.
- `permission.replied` — `data:{sessionID, requestID, reply}` with `reply` ∈ `once|always|reject`.
- `server.connected` (first event on connect).
- `form.created` — `data:{form:{id:"frm_...", sessionID, title, metadata?, fields:[...]}}`. **The session id is nested under `data.form`**, not `data.sessionID`. Each field has `{key, type, title?, description?, required?, hidden?, when?}` plus per-type props: `string` (`format?` email|uri|date|date-time, `options?`, `custom?`, `default?`, `placeholder?`, `pattern?`), `number`/`integer` (`minimum?`, `maximum?`, `default?`), `boolean` (`default?`), `multiselect` (`options:[{value,label,description?}]`, `minItems?`, `maxItems?`, `default?`), `external` (`url`, no answer). Reply via `POST /api/session/{id}/form/{formID}/reply` body `{answer:{<key>: string|number|boolean|string[]}}`.
- `form.replied` — `data:{id, sessionID, answer}`; `form.cancelled` — `data:{id, sessionID}`. v2 has no `question.*` events.
- **`question` forms** — when the agent asks a question mid-turn (e.g. via an `ask`/`question` tool call), the resulting form carries `metadata:{kind:"question", tool:{messageID, id}}`, where `tool.messageID`/`tool.id` identify the assistant message and tool-call part that raised the question (so the reply can be correlated back to that tool call). Live-shaped example:
  ```json
  {
    "metadata": {
      "kind": "question",
      "tool": {"messageID": "msg_0d969c158001RHvgmp8pwj2i9n", "id": "2OzRw5iK9A2yOBXtQ6JjKu3hL4Fm4QtC"}
    },
    "id": "frm_0d969e052001Qsr8esvqgU2UX9",
    "sessionID": "ses_f26963f0bffevr2uTvNbV6hkoJ",
    "title": "Questions",
    "fields": [
      {
        "custom": true,
        "type": "string",
        "key": "q0",
        "title": "Favorite color",
        "description": "What's your favorite color?",
        "options": [
          {"label": "Deep teal (Recommended)", "value": "Deep teal (Recommended)", "description": "A rich blue-green, calm and a little moody"},
          {"label": "Warm amber", "value": "Warm amber", "description": "Golden-orange, cozy like late afternoon light"},
          {"label": "Misty lavender", "value": "Misty lavender", "description": "Soft purple-grey, quiet and understated"},
          {"label": "Forest green", "value": "Forest green", "description": "Deep, earthy, very grounding"}
        ]
      }
    ]
  }
  ```
  Note the `string`-type option shape here is `{label, value, description}` — **not** the same key order/shape as `multiselect`'s `{value,label,description?}`, but the same three fields. `custom:true` on a `string` field with `options` means it's a suggested-choices field: the UI should offer the listed `label`/`description` pairs as pickable suggestions but still accept free-text, since the field type is `string` (not `multiselect` or an enum) and the reply value is just the chosen/typed string in `answer.q0`. A "(Recommended)" suffix baked into an option's `label`/`value` is just prompt-authored text, not a protocol field — there is no separate `recommended` flag.

Other types seen: `session.step.failed`, `session.step.streamed`, `session.viewed`,
`session.compaction.started|delta|ended|failed`, `session.btw.error`,
`session.instructions.updated`, `session.shell.started|ended`, `session.websearch.failed`,
`form.replied`, `vcs.branch.updated`, `workspace.status.error`, `server.connect.failed`.

### v1 → v2 event mapping (for OpenCodeClient)

| v1 event | v2 replacement | Notes |
| -------- | -------------- | ----- |
| `server.heartbeat` | *(none)* — SSE comment line `: heartbeat` | Skip lines starting with `:`; do not expect a JSON event. |
| `session.idle` | `session.execution.succeeded` \| `session.execution.failed` \| `session.execution.interrupted` (all `data:{sessionID}`) | v2 has no single idle event; all three end a turn. |
| `session.error` | `session.execution.failed` with `data.error.message` | error is an object `{type,message}`. |
| `permission.updated` | `permission.asked` (ask) + `permission.replied` (resolution) | Reply via the new permission reply endpoint, not by POSTing to a permissions path. |

## Migration mapping for OpenCodeClient.java

Current v1 call sites (line numbers as of 2026-09-23, file is 715 lines):

| Line(s) | v1 usage | v2 replacement |
| ------- | -------- | -------------- |
| 218 | `GET {server}/event` (SSE) | `GET {server}/api/event`; parse `data:`-only frames, skip `: heartbeat` comments. |
| 346 | waits for `session.idle` event | wait for `session.execution.succeeded` / `.failed` / `.interrupted` matching `data.sessionID`. |
| 375 | `GET /global/health` | `GET /api/info` (200 → `{version,pid,urls,paths}`, **not** wrapped in `data`). |
| 408 | `POST /session` | `POST /api/session` (same-ish body; response is `{"data":{session}}` — unwrap). |
| 416 | `GET /session` | `GET /api/session` (response `{"data":[...]}` — unwrap). |
| 444 | `GET /session/{id}` | `GET /api/session/{id}` (unwrap `data`). |
| 463 | `POST /session/{id}/message` (blocking) | `POST /api/session/{id}/prompt` body `{text, ...}` — **non-blocking**; then wait via SSE or `POST /api/experimental/session/{id}/wait`. |
| 472 | `POST /session/{id}/prompt_async` | same `POST /api/session/{id}/prompt`. |
| 480 | `POST /session/{id}/abort` (body `{}`) | `POST /api/session/{id}/interrupt?resume=...` (query param, no body; 200 with data). |
| 488 | `GET /session/{id}/message` | `GET /api/session/{id}/message` (unwrap `data`; newest-first list, first item is the `idle` marker). |
| 648 | handles `server.heartbeat` event | remove; heartbeats are `: heartbeat` comment lines. |
| ~679 | `session.error` handling (`error.message`) | `session.execution.failed`, read `data.error.message`. |
| 684 | `session.idle` handling | the execution trio above. |
| 702 | `POST /session/{id}/permissions/{permissionID}` | `POST /api/session/{id}/permission/{requestID}/reply` body `{decision:"once"|"always"|"reject", message?}` → 204 empty. |

Also: add the Basic auth header (`opencode:<password>`) to every request — the existing
no-arg auth that reads `serverapikey` from the `aiserver` table can supply the password; it just
needs to be sent as `Authorization: Basic base64("opencode:" + password)` rather than whatever
v1 used.

## Live probe recipe

```bash
PORT=49374   # bin/opencoded.sh default; verify with `opencode service get port`
PW='<fixed password — printed by bin/opencoded.sh / stored in the aiserver table>'
AUTH=$(printf 'opencode:%s' "$PW" | base64 -w0)
B="http://127.0.0.1:$PORT"

# health / version
curl -s -H "Authorization: Basic $AUTH" $B/api/info

# list sessions (wrapped in data)
curl -s -H "Authorization: Basic $AUTH" "$B/api/session?limit=5" | python3 -m json.tool

# create a session
SID=$(curl -s -X POST -H "Authorization: Basic $AUTH" -H 'Content-Type: application/json' \
  -d '{"title":"probe","agent":"build","location":{"directory":"/tmp"}}' \
  $B/api/session | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])')

# fire-and-forget prompt (times out fast = async)
time curl -s -X POST -H "Authorization: Basic $AUTH" -H 'Content-Type: application/json' \
  -d '{"text":"Reply PONG"}' $B/api/session/$SID/prompt

# SSE sample (watch for ": heartbeat" comments and data-only frames)
timeout 5 curl -s -N -H "Authorization: Basic $AUTH" $B/api/event

# capture a full turn's events
(timeout 60 curl -s -N -H "Authorization: Basic $AUTH" $B/api/event > /tmp/probe-events.txt 2>&1) &
sleep 1
curl -s -X POST -H "Authorization: Basic $AUTH" -H 'Content-Type: application/json' \
  -d '{"text":"Count from 1 to 5"}' $B/api/session/$SID/prompt > /dev/null
wait

# messages (newest-first; first item = idle marker)
curl -s -H "Authorization: Basic $AUTH" $B/api/session/$SID/message | python3 -m json.tool

# interrupt / wait
curl -s -X POST -H "Authorization: Basic $AUTH" "$B/api/session/$SID/interrupt"
curl -s -X POST -H "Authorization: Basic $AUTH" "$B/api/experimental/session/$SID/wait"

# permission reply (need a real permission.asked event to get requestID)
curl -s -i -X POST -H "Authorization: Basic $AUTH" -H 'Content-Type: application/json' \
  -d '{"decision":"once"}' $B/api/session/$SID/permission/<requestID>/reply
```

## Gotchas

- **Non-`/api` paths return SPA HTML with 200.** A "successful" GET of `/session` is actually the
  web app's index page. Always check that the body parses as JSON / starts with `{"data"` or
  `{"version"`.
- **`prompt` never blocks.** Code that relied on v1 `message` returning the assistant response
  must switch to event-driven completion (SSE) or the experimental `wait` endpoint.
- **The SSE stream is global.** Filter every session-scoped event by `data.sessionID`; events from
  other sessions (including this very OpenCode session, which can make streams very chatty) will
  otherwise corrupt status tracking.
- **`error` in `session.execution.failed` is an object** (`{type,message}`), not a string.
- **Permission request id lives in `permission.asked` → `data.id`**, and the reply endpoint is
  nested under `/permission/{requestID}/reply` (v1 used `/permissions/{id}`).
- **The pairing password normally rotates** on every `opencode pair` (401s after a re-pair are
  expected) — but in this deployment `bin/opencoded.sh` pins it to a fixed value, so 401s here
  mean the service was re-paired *without* the script; just re-run `bin/opencoded.sh`.
- `GET /api/info` is the one API response that is **not** wrapped in `{"data":...}`.
- If a `permission.asked` never arrives for a tool call, the session's permission config auto-
  approved it (observed: build agent ran bash without asking).

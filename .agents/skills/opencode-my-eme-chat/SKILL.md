---
name: opencode-my-eme-chat
description: Use when changing or debugging My EME chat (the app's chat with yourself, scenario my_eme_chat), where MyEmeChatSkill forwards every message to a per-chat OpenCode session in the user's workspace folder and streams OpenCode's events back as ONE chat message built up by messageappend / messagereplace patches. Covers the channel's opencodesessionid, the workspace folder and permission rules (MyEmeWorkspace), the per-session event queue in OpenCodeClient, MyEmeTurnMessage (events to ops and patches), MarkdownToQuill, AgentJobManager.broadcastMessagePatch, the patch wire format the Flutter app merges, routing of typed text, what is deliberately deferred, and how to test it.
---

# Skill: opencode-my-eme-chat

My EME chat sends each chat message to OpenCode and shows OpenCode's work as a single chat message
that grows while it happens. This is **not** the agent-job runner/status-card path; for that see
`opencode-running`. For the OpenCode HTTP API and event stream see `opencode-v2-api`. For the Flutter
side (merging patches, status widgets) see the app's `eme-app-chat-ui` and `eme-sdk-chat-messages`.

## Shape

| Piece | File (`plugins/finder/code/org/entermediadb/...`) | Job |
|---|---|---|
| Skill | `ai/skills/MyEmeChatSkill.java` | Step `my_eme_chat_confirm` of scenario `my_eme_chat`. Finds or creates the session, sends the prompt, runs the event loop, sends patches, saves and completes |
| Workspace | `ai/skills/MyEmeWorkspace.java` | The user's folder and the session permission rules |
| Message builder | `ai/skills/MyEmeTurnMessage.java` | Collects events into one message; decides each patch |
| Markdown | `markdown/MarkdownToQuill.java` | Reply markdown to Quill ops (vendored CommonMark) |
| Client | `mcp/client/OpenCodeClient.java` | `createSession(title, folder, permissions)`, `sessionExists`, `sendPrompt`, per-session event queue |
| Broadcast | `ai/agentjobs/AgentJobManager.broadcastMessagePatch` | Sends a patch; saves nothing |
| Push skip | `websocket/chat/ChatServer.java` | No push notification for `messageappend` / `messagereplace` |

Data: `channel.opencodesessionid` (`plugins/catalog/html/data/fields/channel.xml`). Skill and step docs:
`aiskill/my_eme.xml`, `automationstep/my_eme.xml`. Bean: `myEmeChatSkill` in `plugins/finder/html/src/plugin.xml`.

## One session per chat channel

- A My EME channel is `searchtype=emeprofile`, `user=<login>`. Its OpenCode session id is saved on the
  channel row in `opencodesessionid`.
- First message (or the id is blank, or `sessionExists` finds OpenCode lost it): create a session in the
  workspace with the permission rules, save the id on the channel.
- "New session" in the app already makes a new channel (`connectMyEmeChat(createNew: true)`), so it gets a
  new OpenCode session with no extra work.

## Workspace and permissions (`MyEmeWorkspace`)

- Folder: `WEB-INF/data/<catalogId>/workspaces/<userid>/` (e.g. `webapp/WEB-INF/data/site/workspaces/admin/`),
  resolved through the page repository, created on first use. **A plain folder, not a git repo**: OpenCode
  gives a plain folder its own project id; `git init` without commits falls into the shared `global`
  project, and a repo with a commit takes the root-commit hash (collides if folders are ever copied).
  Call it a "workspace" in code; EME already uses "project" for library collections.
- The user id comes from the channel row, never a request, and must match `^[A-Za-z0-9][A-Za-z0-9._@-]*$`
  with no `..` (`requireSafeUserId`).
- Rules (later rules override earlier): `*` allow, then deny `shell`, `external_directory`, `webfetch`,
  `websearch`, `browser`, `question`. The action for the shell is **`shell`**, not `bash` (a `bash` rule
  does nothing). Nobody is prompted. A denied action shows up as `session.tool.failed`, not
  `permission.asked`; a `permission.asked` is auto-rejected with a note as a safety net.
- `question` is denied **until the chat can show OpenCode's question forms** (step 5, not built).
- This is OpenCode's own permission check, not an OS sandbox. A sandbox is deferred.

## The turn

`process()` -> `streamTurn()`:

1. A blank prompt (arriving from the menu) just answers "What would you like me to do?".
2. Get the client from the `agentJobManager` bean, `connectToServer()` (listen **before** sending).
3. `openEventQueue(sessionId)`, then `sendPrompt` (fire-and-forget `POST /api/session/{id}/prompt`; a
   rejection ends the message with the reason).
4. Loop: block on `queue.poll` (no polling, wakes only on an OpenCode event, at most once a minute to check
   the 20 min `TURN_TIMEOUT_MS`), gather events for `BATCH_MS` (200 ms), `turn.nextPatch()`, broadcast.
   Save progress every `SAVE_EVERY_MS` (3 s) so a reload mid-turn shows roughly what was on screen.
5. `reply()` sets the final ops, `messagestatus` in the context, `nextAutomationStep` = this step (so the
   next message comes back here), and calls `processCompleted` (saves the row, broadcasts the whole message).

Steering: a second message while a turn runs opens the queue again; the older queue gets a
`client.superseded` event (`OpenCodeClient.SUPERSEDED`) and its message ends with "Continued in the next
reply." Known limit: the two replies share one cached `ChatMessageContext`, so the older one's final save can
land on the newer row.

## Events to one message (`MyEmeTurnMessage`)

Segments in order: reply text (markdown -> ops), tool lines (`read notes.txt…`, file names only, never server
paths), notes, errors. Muted lines are italic `#777777`; errors bold `#c62828`.

- Turn starts on `inbox.enqueued` / `execution.started` / `step.started`. An `execution.*` end event is
  ignored until then, so the end of an *earlier* turn cannot finish this one.
- `session.text.delta` appends; `session.text.ended` carries the whole part and overwrites (the resync).
  With a local model the whole answer may arrive as one delta; do not assume token streaming.
- `tool.input.started` (name), `tool.called` (target from `input`), `tool.success`, `tool.failed`.
- `execution.succeeded` -> `done`; `failed` / `interrupted` / timeout / refused prompt -> `error` with the
  reason as an error line.
- Status: `working` (busy, e.g. a tool), `streaming` (reply text open), `done`, `error`.

`nextPatch()` diffs the new full ops against the last sent ops: grew at the end -> `messageappend` with only
the new ops, otherwise `messagereplace` with all. Returns null when nothing changed, and while both are empty
(the "Working..." message is left alone). A status-only change is an append of no ops. Most text patches are
replaces, because a growing paragraph changes the end of the message; they are correct, just not minimal.

## Patch wire format (what the app merges)

Broadcast on the normal chat shape; `messageid` is the context's `agentmessage` id (one chatterbox row per
user message):

| Field | Value |
|---|---|
| `command` | `messageappend` (ops to add) or `messagereplace` (all ops) |
| `message` | string `{"ops":[...]}` |
| `seq` | 1, 2, 3... per message id |
| `agentcontextvalues` | string `{"messagestatus":"working|streaming|done|error"}` |

The finished message is sent last as an ordinary message (no `command`) with the same id, which also puts
right a client that missed a patch. The app reloads history when an append skips a `seq`.

## Routing typed text

The step that runs is the user message's `functionname`. The web chat sends the last agent message's
`nextfunctionname`; the Flutter app sends nothing. `AssistantManager.respondToChannel` therefore falls back
to `findNextFunctionName(channel)` (newest of the last 10 agent rows with a `nextfunctionname`) when
`functionname` is blank. It only applies when `functionname` is blank, but it is a core change.

## Deferred

MCP (per-call user key), OS sandbox, `asset` messages, user attachments, OpenCode questions as forms
(`MyEmeFormAnswerSkill`, step 5), reconnect reconciliation after an SSE drop (`GET /api/session/{id}/message`).

## After changing things

- Java: the EME server runs from the VS Code debugger; `bin/restart.sh` needs the Remote Control extension.
  `bin/compile.sh` accepts `BUILD_DIR` for a scratch compile. OpenCode itself: `bin/opencoded.sh status`
  (plain `start` also opens a browser pairing link).
- `channel.xml` or `aiskill/my_eme.xml`: reload the field/list data, see `field-definitions-maintenance` and
  `reload-list-data`.
- Quick checks without the server: compile the plugin Java into a scratch dir and replay captured OpenCode
  events through `MyEmeTurnMessage` and markdown samples through `MarkdownToQuill`.

## Testing live

Restart the server, open My EME chat in the app, switch to "My EME Chat", send "Create an html file called
helloworld.html". Expect one agent bubble that shows a spinner, then tool lines and text, then settles; the file
must exist in `webapp/WEB-INF/data/<catalog>/workspaces/<user>/`. Try a prompt that reads `/etc/hostname`
(should fail: `external_directory` denied) and one that asks for a shell command (denied).

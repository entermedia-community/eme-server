---
name: opencode-running
description: Use when changing or debugging how a chat runs OpenCode as an agent job step — OpenCodeRunnerSkill, OpenCodeStatusSkill, OpenCodeAnswerSkill, OpenCodeApproveSkill, OpenCodeUserRequestSkill, the eme_server_custom_skills / entity_chat_custom_skills scenarios, or the agent_job_showstatus.html card (progress, question form, security prompt, final reply). Covers which skill owns which part of the run, the step fields that hold a pending question, how replies are routed back to the right scenario step, and how to reload and test changes.
---

# Skill: opencode-running

How a chat request becomes an OpenCode session, how its progress is shown, and how a person's
answer gets back to OpenCode. For the OpenCode HTTP API and event stream itself, see
`opencode-v2-api`.

## The chain

| Step (in `eme_server_custom_skills`) | Skill | Job |
|---|---|---|
| `eme_server_custom_skills_user_request` | `OpenCodeUserRequestSkill` | Turns the chat message into `userrequest`, puts it on the root context |
| `eme_server_custom_skills_run` | `OpenCodeRunnerSkill` | Starts or resumes the OpenCode session, puts `sessionstatus` in the context, runs its children. **Does not wait.** |
| `eme_server_custom_skills_status` (`runafter` the run step) | `OpenCodeStatusSkill` | Polls the session, renders `agent_job_showstatus`, saves questions, shows the final reply |
| `eme_server_custom_skills_answer` | `OpenCodeAnswerSkill` | Sends the question form's answers, then re-runs the status step |
| `eme_server_custom_skills_approve` | `OpenCodeApproveSkill` | Sends once / always / reject for a security prompt, then re-runs the status step |

Steps: `plugins/catalog/html/data/lists/automationstep/eme_server_custom_skills.xml`.
`entity_chat_custom_skills.xml` uses the same skills under the step ids `openCodeRunner`,
`openCodeStatus`, `openCodeAnswer` and `openCodeApprove`. There, `openCodeStatus` runs **before** the
runner, so the status skill finds no session and falls back to following the agent job's status.

Code: `plugins/finder/code/org/entermediadb/ai/skills/OpenCode*Skill.java`. Session state:
`org.entermediadb.mcp.client.SessionStatus`, held by `OpenCodeClient` keyed by the **runner step's
id** (`client.loadStatus(stepid)`).

## OpenCodeStatusSkill loop

1. `findSessionStatus()` looks for `sessionstatus` in this context and its parents. Child contexts
   only read the root and themselves through `getContextValue`, so it walks `getParentContext()`
   itself. When resumed after a reply, it falls back to `client.loadStatus(currentagentjobstepid)`,
   then to `agentjobstep`.
2. Every 2 s (`POLL_INTERVAL_MS`): `client.advanceSession(logger, runnerstepid, 0)` applies new
   events without blocking, then `saveMarkdown()` copies the latest assistant text to the runner step,
   then `renderStatus()` + `processUpdate()` broadcasts the card. `waitBeforeResponding()` does the
   wait, so a newer chat message cancels it.
3. Question or permission pending (`getConfirmationPendingId() != null`): `savePendingQuestion()`
   writes it to the runner step, the card is rendered once, and the loop stops.
4. Completed: throws if `getError()` is set, otherwise puts `commandoutput` and returns to
   `startup_scenario`. A session running past `MAX_WAIT_MS` (20 min) throws.

## Pending question fields (on the runner's agentjobstep)

| Field | Value |
|---|---|
| `status` | `question` (form) or `securityprompt` (permission); see `jobstatus` list |
| `pendingquestion`, `markdowncontent` | The prompt text |
| `confirmationpendingid` | OpenCode's request id |
| `pendingform` | The form JSON (`id`, `title`, `fields[]`) for `question` |

Answer/Approve check `status`, send the reply, set `status` back to `running` and clear these fields.

## The status card (`plugins/mediadb/html/views/agentresponses/agent_job_showstatus.html`)

The question and progress live on the **runner's** step (`$sessionstatus.getAgentJobStep()`), not
`$currentagentjobstep`, which is the status step. Exactly one card shows: question form
(`$pendingform`), security approval, failed, completed (`$commandoutput`), or running.

Replies must stay in the chat job's scenario. `AgentModule` starts a new job whenever the posted
`currentscenario` differs from the current job's scenario, so never hardcode it. `renderStatus()` puts:

- `$currentscenario`: `agentjob.getScenarioId()`
- `$answerfunction` / `$approvefunction`: the enabled id of the step in this job that runs
  `openCodeAnswerSkill` / `openCodeApproveSkill`, from `OpenCodeStatusSkill.findFunctionForSkill()`

The form posts those along with `context_currentagentjobstepid` = the runner step id. Answer fields are
named `context_<formid>_<fieldkey>`; multiselect options post `context_<formid>_<fieldkey>__<value>=true`.
Answer and Approve set `setExecAutomationStep(<status step>)` so the card keeps updating after a reply.

## After changing things

- Java (`OpenCode*Skill.java`, `OpenCodeClient.java`): `bin/restart.sh`, then poll
  `http://localhost:8080/site/` until it responds.
- `agent_job_showstatus.html`: no restart; if you touched an `.xconf`, clear the page manager cache.
- Step or skill docs (`automationstep/*.xml`, `aiskill/opencode.xml`, `aiskill/chat_monitor.xml`):
  reset those tables, see `reload-list-data`.
- Keep the skill docs in `aiskill/opencode.xml` (Runner, Answer, Approve, User Request) and
  `aiskill/chat_monitor.xml` (Status) in step with the code.

## Testing

Open an EME chat, pick "Run Custom Skill", and send a request that makes OpenCode ask a question and
then run a tool that needs approval. Check that:

1. The running card updates while OpenCode works.
2. The question form appears; submitting it keeps the same job (the scenario does not reset) and the
   running card comes back.
3. The approval buttons work the same way.
4. The completed card shows the final reply and the time taken.

Repeat in an entity chat for `entity_chat_custom_skills`.

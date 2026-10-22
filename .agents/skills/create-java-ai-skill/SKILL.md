---
name: create-java-ai-skill
description: Use this skill whenever the user wants to add a new Java-based automation step to EME's AI/agent pipeline — requests like "create a new AI skill that does X", "add an automation step for Y", "write a Skill class for Z", or "make this run automatically after asset upload". This covers the full loop: writing the Skill class, wiring it as a Spring bean, and registering it so it is selectable and runnable from an automation scenario. Always consult this skill before hand-writing a Skill class or editing plugins/catalog/html/data/lists/aiskill*/*.xml, since the bean-id / data-id linkage is easy to get wrong.
---

# Create a Java AI Skill

Adds a new automation step ("Skill") to EME's AI pipeline, following the plugin/bean/data
conventions used across this project.

## Inputs

- `SkillName` — PascalCase Java class name, e.g. `AgentJobStatusSkill`.
- `AutomationScenario` — the `automationscenario` id the step runs under, e.g. `agentorchestrator`.
  Must match an existing id under `plugins/catalog/html/data/lists/automationscenario/*.xml`; it
  selects which `plugins/catalog/html/data/lists/automationstep/<AutomationScenario>.xml` file
  gets the new step appended.

Derive `beanId` = `SkillName` with the first letter lowercased (e.g. `agentJobStatusSkill`). Every
existing skill uses the *same string* for the Spring bean id, the `aiskill` table's `data id`, and
the `bean=` attribute on that row — e.g. `openCodeJobCreatorSkill` is the bean id in `plugin.xml`, the
`<data id="openCodeJobCreatorSkill" bean="openCodeJobCreatorSkill" ...>` row in `aiskill/chat_monitor.xml`,
and the `aiskill="openCodeJobCreatorSkill"` reference in `automationstep/agentorchestrator.xml`. Use
`beanId` everywhere below, and `SkillName` only for the Java class itself.

## Step 1: Place the plugin package

- Custom code goes in `Website/plugins/<yourplugin>/code/org/...` (or `plugins/<yourplugin>/code/org/...`
  in this repo for built-in plugins) so it can override or extend eme-lib behavior.
- Bean wiring for that plugin goes in `Website/plugins/<yourplugin>/html/src/plugin.xml` (or
  `plugins/<yourplugin>/html/src/plugin.xml`).
- Remember the fallback order: `Website/plugins/*` is used before `EME-LIB/plugins/*` when names match.

## Step 2: Write the Skill class

The core contract is `plugins/finder/code/org/entermediadb/ai/Skill.java`:

```java
public interface Skill
{
	void processStarting(AgentContext inContext);
	void process(AgentContext inContext);
	void processUpdate(AgentContext inContext);    // broadcast the last response, keep working
	void processCompleted(AgentContext inContext); // broadcast the last response, then run its exec step
}
```

- `AgentContext` is `org.entermediadb.ai.AgentContext` (not `org.entermediadb.ai.llm.AgentContext`
  — that's a different, unrelated class).
- The running step is an `org.entermediadb.ai.agentjobs.AgentJobStep`
  (`inContext.getCurrentAgentJobStep()`), and the job it belongs to is an
  `org.entermediadb.ai.agentjobs.AgentJob` (`inContext.getCurrentAgentJob()`, e.g.
  `getCurrentAgentJob().getScenarioId()`). The old `org.entermediadb.ai.llm.AutomationStep` and
  `RunningScenario` classes no longer exist.
- Extend `BaseSkill` (`plugins/finder/code/org/entermediadb/ai/BaseSkill.java`) and override only
  `process(...)` unless you specifically need to change start/complete behavior. `BaseSkill`'s
  `processStarting` fires the "starting" status (skipped if the context holds
  `cancelstartup<enabledId>` = true). `processUpdate` saves and broadcasts `inContext.getLastResponse()`
  to the chat without ending the step. `processCompleted` does the same broadcast, then runs the
  response's `getExecAutomationStep()` if one is set. Its default
  `process` calls `processCompleted` and then runs the step's children
  (`getCurrentAgentJobStep().getChildren()`) through `getAgentJobManager().createAgentContext(...)`
  and `getAgentJobManager().runProcess(...)` — call `super.process(inContext)` at the end of your
  override to keep that chaining, or omit it if this step must not auto-advance.
- To jump to a specific next step instead, call
  `setNextAutomationStep("<scenarioId>.<stepId>")` on the `LlmResponse` (see
  `OpenCodeJobCreatorSkill`).

```java
package org.entermediadb.ai.skills; // or a custom plugin's own skills package

import org.entermediadb.ai.AgentContext;
import org.entermediadb.ai.BaseSkill;

public class SkillName extends BaseSkill
{
	@Override
	public void process(AgentContext inContext)
	{
		// your logic here
		super.process(inContext); // runs the step's children — see BaseSkill.process above
	}
}
```

See `plugins/finder/code/org/entermediadb/ai/skills/OpenCodeJobCreatorSkill.java` for a real,
non-trivial example (only overrides `process`, casts `AgentContext` to a more specific subtype like
`ChatMessageContext` when it needs chat history).

### Waiting before responding

A skill that should pause (let a chat conversation settle, poll a running job) sleeps on its own
thread inside `process` using the `BaseSkill` helpers. There is no `setWaitTime` on `AgentContext`
anymore, and do not fire a complete status just to come back later, since that broadcasts an empty
message.

- `waitBeforeResponding(inContext, millis)` blocks the current thread. It returns `true` when the
  full wait passed and `false` when it was cancelled. Each chat message runs on its own executor
  thread, so this only blocks that one message.
- `cancelPendingWait(inContext)` wakes a wait by the **same skill class on the same channel**. Call
  it at the top of `process` when a newer message should replace the older one. Starting a new wait
  also cancels the previous one for that skill and channel. Contexts without a channel can wait but
  cannot be cancelled.
- `noResponse(inContext)` ends the step without saving or broadcasting anything (it sets a
  `cancel` response). Call it on every path that returns without a real answer, including a
  cancelled wait. Otherwise `runStep` reads whatever response was left on the shared context.
- `processUpdate(inContext)` broadcasts progress while the skill keeps going. Set the response with
  `setLastResponse` first.
- After a wait, re-check whatever the decision depends on (the latest chat message, the job
  status), since the world may have changed while sleeping.
- While a skill waits, its step and agent job stay `running`.

Debounce a chat message (see `LibraryCollectionChatMonitorSkill`):

```java
public void process(AgentContext inContext)
{
	cancelPendingWait(inContext); //A newer message replaces whatever we were waiting on
	// ... call the LLM, decide what to say ...
	if (!waitBeforeResponding(inContext, minutes * 60L * 1000L))
	{
		noResponse(inContext); //a newer message is being handled instead
		return;
	}
	// still the latest message? then render and processCompleted(inContext)
}
```

Poll until something finishes (see `AgentJobStatusSkill`):

```java
for (int loops = 0; ; loops++)
{
	LlmResponse response = renderStatus(inContext, job);
	inContext.setLastResponse(response);
	if (finished) { break; }
	processUpdate(inContext);
	if (!waitBeforeResponding(inContext, 5000L)) { noResponse(inContext); return; }
}
super.process(inContext); // processCompleted + children
```

## Step 3: Register the Skill bean

Add a bean entry to the owning plugin's `plugin.xml` (e.g. `plugins/finder/html/src/plugin.xml`,
alongside the other `*Skill` beans):

```xml
<bean id="beanId" class="org.entermediadb.ai.skills.SkillName" scope="prototype">
	<property name="moduleManager">
		<ref bean="moduleManager" />
	</property>
</bean>
```

Editing `plugin.xml` means the server must be restarted — see Step 6.

## Step 4: Add the aiskill row

Add a `<data>` record to `plugins/catalog/html/data/lists/aiskill/*.xml` — pick whichever existing
file is topically closest (these files are grouped by domain, e.g. `chat_monitor.xml`,
`automationagentbase.xml`, `goals.xml` — *not* one-file-per-automationscenario), or add a new
same-pattern file there if none fits. This is a data row on an already-existing list field, so it
does **not** need the `catalog-table-creator` skill (that's only for new tables/fields).

The parameters will be what the Java code needs to operate. Each parameter will have its id, description, and optionally an "optional" flag indicating whether it is required.

```xml
<data id="beanId" bean="beanId" enabled="true" agenttype="eventagent" ordering="10">
	<name>
		<language id="en"><![CDATA[Human-readable name]]></language>
	</name>
	<markdowncontent><![CDATA[One-sentence description of what this skill does — this text is
what embedding-based retrieval (see OpenCodeJobCreatorSkill) matches against, so make it descriptive.]]></markdowncontent>
<parameters><![CDATA[ 
[{
"id":"goal",
"description":"The goal to be achieved by this skill"
},
{
"id":"urgency",
"description":"The urgency level of achieving the goal",
"optional": true
}]
   ]]></parameters>,
   <defaultoutput><![CDATA[ [
  {
    "id": "jobstatus"
  }
] ]]></defaultoutput>
</data>
```

`id` and `bean` are always the same string (`beanId`, matching Step 3). Pick `agenttype` by copying
whichever existing row is most similar to what this skill does (`eventagent` for a plain
synchronous step, `remoteagent` for one tied to a chat/remote context, `logicagent` for
branching/conditional steps) — grep `agenttype=` in this folder for examples before guessing.

## Step 5: Append the automationstep row

Open `plugins/catalog/html/data/lists/automationstep/<AutomationScenario>.xml` (it must already
exist — creating a brand-new automationscenario is a separate, bigger change not covered here) and
add a `<data>` record just before `</records>`:

```xml
<data id="stepId" aiskill="beanId" automationscenario="AutomationScenario" enabled="true"
	agenttype="eventagent" processingmessage="Short user-facing status text" runafter="previousStepId">
	<name>
		<![CDATA[Human-readable step name]]>
	</name>
</data>
```

- `aiskill` must equal `beanId` from Step 4, not the Java class name.
- `runafter` should reference the `id` of whichever existing step in that file this one runs after
  (read the file first to find the current last step); omit it only if this step runs first.
- `agenttype` here can differ from the aiskill row's `agenttype` in existing examples — match it to
  the other steps in this specific automationstep file instead.

## Step 6: Restart, reload data

A running server reads `plugin.xml` and loads classes only at startup. **Whenever any
`plugins/*/html/src/plugin.xml` is edited, restart the server** — otherwise the new bean does not exist
and the step fails when it runs. New or changed Java classes need the same restart.

```bash
bin/restart.sh
```

- On localhost it restarts the VS Code debug session (**Launch eMedia**) through the Remote Control
  extension on port 3710; the Java extension recompiles before relaunching, so there is no separate
  compile step. If no debug session is running, it runs `bin/start.sh` to start one. On other
  instances it restarts the docker container.
- It returns as soon as the restart is sent, so poll `http://localhost:8080/site/` until it answers
  before testing. Note Tomcat listening on 8080 does **not** mean the app is ready — after a restart
  the Java extension first compiles (several minutes for this codebase), and even once Tomcat is up,
  `/site/` can keep returning 404 while the Spring context initializes. Poll `/site/` specifically;
  a bare `/` answering HTML does not prove the app is up.
- If it exits 1 ("VS Code is not reachable on port 3710"), don't work around it blindly: the
  WebSocket port is hardcoded in the script, and a local edit to `bin/restart.sh` (or
  `.vscode/settings.json`'s `remoteControl.port`) can make it point at the wrong port. Compare the
  port in the script against what is actually listening (`ss -tlnp | grep code`) and send the same
  node one-liner from the script against the correct port (ask the user first if you deviate from
  the script). Leave the user's local edits to the script untouched.
- If the VS Code window was closed or reloaded, the debug session is gone with it: `bin/restart.sh`
  will fall through to `bin/start.sh` (`workbench.action.debug.start`). After a window reload the
  Remote Control extension may take a few seconds to bind its port again — poll the port briefly
  before concluding VS Code is unreachable.

Then load the new `aiskill` and `automationstep` rows with the `reload-list-data` skill
(`restoredata` for both tables; a restart does not do this).

If `bin/restart.sh` exits 1, the server is still running the old code. Report that, and check the
edited files for compile errors through VS Code diagnostics (`getDiagnostics` on each `.java` file)
so the code is known to compile before the user restarts.

## Step 7: Validate

1. Confirm `beanId` appears in the automation agent list in the admin UI.
2. Trigger the target event/module and verify the skill executed in the logs.

## Skills that process assets (informatics)

Steps with `contextbean="informaticsContext"` receive a page of assets from the Informatics
Processor. Follow the same contract as `ClassifyManager`, `EmbeddingManager` and `TranscriberManager`:

- Wrap the context with `new InformaticsContext(inContext)` and read `getAssetsToProcess()`.
- When one asset fails, set `asset.setValue("llmerror", true)` and move on to the next one. Do not save
  the asset: `InformaticsProcessorSkill` saves the whole page at the end and marks the rest
  `taggedbyllm`.
- After the work, remove `llmerror` assets from the list and call `setAssetsToProcess(...)` so child
  steps skip them, then `super.process(...)`.
- Assets with `llmerror=true` are left out of later informatics queries until the flag is cleared, so
  a failure is not retried automatically.
- If the skill tracks its own progress in another table (as `videotrack.transcribestatus` does), make
  sure an `inprogress` state cannot get stuck forever after a restart. Treat it as stale after a time
  limit.

## Calling a non-LLM AI server (`aiserver` rows)

Remote services such as transcribe, translate, embedding or face detection are rows in
`plugins/catalog/html/data/lists/aiserver/*.xml`, usually with `connectionbean="httpConnection"`.
`plugins/finder/.agents/skills/transcriber/SKILL.md` walks through a complete example.

- Look the server up by type: `getMediaArchive().getLlmConnection("<aiservertype>")`. It picks the
  row with that `aiservertype` and the lowest `ordering`, so never hard-code a row id or URL. If no row
  matches, it falls back to the `localhost` row, which has no `serverroot`.
- `serverroot` (+ `serverpathprefix`) is the base URL, `serverapikey` is the bearer token
  (`connection.getApiKey()`).
- File upload: `connection.callJson("/path", headers, params)` with a `Map` of params sends a
  multipart POST. A `java.io.File` value becomes a file part (`application/octet-stream`), a `String`
  a text field. Headers passed here stay on the shared connection.
- A JSON body: `callJson("/path", JSONObject)`.
- Status other than 200 throws `OpenEditException("Could not call /path")` and logs the body.
  Read the result with `getRawResponse()` (object) or `getRawCollection()` (array).
- Responses are parsed with json-simple, which returns `Long` for whole numbers and `Double` for
  decimals. Read numbers as `((Number) json.get("x")).doubleValue()`, never `(double)` or `(Long)`.
- Connections are cached per server type in cache `llmconnection`. After changing an `aiserver` row,
  reset the table with `reload-list-data` and then clear caches, or the old URL and key stay in use.

## Splitting or renaming an existing skill

When one skill class is replaced by several (e.g. splitting a dispatcher-style `process()` into one
class per branch), do all of this or the split breaks at runtime:

1. **Find every reference first.** Grep the whole repo (Java, XML, templates, groovy) for both the
   class name and the old bean id. Callers usually hold the skill through a getter like
   `getSearchingSkill()` on a module/manager class — those getters are compile blockers if you
   delete the class before repointing or removing them. Dead getters that nothing calls can simply
   be deleted; live call sites must be repointed to the specific new bean.
2. **Keep the id convention.** Each new class gets `beanId` = lowerCamel of its class name, used for
   the Spring bean id, the `aiskill` data id, and the `aiskill=` reference in every `automationstep`
   row that should run it. Do not copy legacy anomalies where the aiskill data id differs from the
   bean (e.g. the old `searchingChatSkill` row pointed at bean `searchingSkill`).
3. **Replace the aiskill rows, don't just add.** `restoredata` matches by id, so the stale row with
   the old id survives the reload and its step fails when it runs (the orchestrator can't resolve the
   bean). Delete each old row through the mediadb API first, then restoredata — see the
   `reload-list-data` skill, section 3.
4. **Point each automationstep at the right new aiskill.** A dispatcher split means different step
   ids now map to different skills; update every row in every scenario file that referenced the old
   one (including steps whose branch the class still throws "not supported" for — keep them pointed
   at whichever new skill preserves the old behavior exactly, and tell the user).
5. **Verify fidelity against the working tree, not just git HEAD.** If the original file had
   uncommitted local edits, diffing the split classes against `git show HEAD:...` produces spurious
   "missing" lines for every local change. Diff against the actual pre-split working-tree copy
   instead, and account for intentional per-class differences (e.g. locals only one branch used).

## Calling an LLM: `html/ai/<provider>/calls/*.json` templates

A Skill that needs to call an LLM (`LlmConnection.callStructure`, `callToolsFunction`,
`callClassifyFunction`, ...) does so through a Velocity-templated JSON file at
`plugins/mediadb/html/ai/<provider>/calls/<function>.json` (default provider: `default`). This is
rendered by the **real** Velocity engine (`org.openedit.generators.VelocityGenerator`, see
`plugins/mediadb/html/ai/_site.xconf` mapping `.json` -> `velocityGenerator`) against the
`AgentContext`, and the rendered text is then parsed as JSON (`BaseLlmConnection.loadInputFromTemplate`
+ `JSONParser`). So the output must be strictly valid JSON, and the template source must be
strictly valid VTL — not just "close enough for an LLM to read".

### `#jesc` / `#jrender`

Defined once, in `plugins/system/html/display/velocitymacros.vm:65-66`:
```
#macro(jesc $object)"$!jsonUtil.escape($object)"#end
#macro(jrender $object)$!jsonUtil.escape($object)#end
```
- `#jesc($x)` JSON-escapes `$x` (via `JsonUtil.escape`, `plugins/finder/.../asset/util/JsonUtil.java:44`)
  **and wraps it in the surrounding double quotes for you** — use it for any message `"content"`
  value. Never add your own quotes around a `#jesc(...)` call.
- `#jrender($x)` does the same escaping without the wrapping quotes — use only where you're
  building the quotes yourself.
- `$x` can be a plain context variable (`#jesc($confirmationprompt)`) or an inline double-quoted
  VTL string with `$var`/`#foreach` interpolation (see `smartcreator_confirmoutline.json:10-11`).

### Hard rule: never put a literal `"` inside a `#jesc("...")` argument

The argument to `#jesc(...)` is parsed by Velocity as a double-quoted **string literal** — the
parser looks for the *next* unescaped `"` in the template source to end that literal, and it does
this at parse time, before any rendering happens. It does not know the text is "meant to be" JSON.
Confirmed against the actual shipped engine (`velocity-engine-core-2.3.jar`): an embedded `"`
breaks the parse with `ParseErrorException`, and a backslash-escaped `\"` breaks identically — there
is no working escape sequence for this. So:

- Every hand-typed instruction string inside `#jesc("...")` must contain **zero** literal `"`
  characters. If you need to show the model quoted/JSON-shaped example text inline, use single
  quotes (`'like this'`) instead — see `agentJobCreator.json` for a worked example of a JSON-shaped
  reference block written with single quotes for exactly this reason.
- If you need to embed real, dynamically-built JSON (an object/array coming from Java), do **not**
  type it into the template at all. Put it on the `AgentContext` as a variable and reference it —
  `$var` inside a double-quoted literal is just one token to the parser (interpolation happens
  after parsing), so whatever quote characters live in `$var`'s runtime value are irrelevant to
  parsing:
  ```
  "content": #jesc("[[Here is the list of available skills:]]

  $!availableskillsjson")
  ```
  with `inAgentContext.put("availableskillsjson", ...)` set from Java (e.g.
  `jsonUtil.toJson(selectedSkills)` or `JSONArray.toString()`).

### Prefer retrieval over a hardcoded catalog

Don't hand-type the full `aiskill` catalog (or any other large reference table) into a call
template just so the model can "see everything" — it duplicates the real source of truth (the
`aiskill` table, already embedded/indexed for semantic search) and bloats every call's token cost.
`plugins/finder/code/org/entermediadb/ai/skills/OpenCodeJobCreatorSkill.java` already does this the
right way for skill selection: `EmbeddingManager.callFindDocIds(...)` retrieves only the handful of
skills relevant to the current request; only that small, dynamic subset should be serialized (as
above) into the prompt.

### `response_format` / `json_schema`

For structured output, every call template follows the same `response_format.json_schema.schema`
shape: `type: object`, explicit `properties`, `required` listing **every** property (OpenAI strict
mode requires all properties be "required" even when a value can be `null` — model that as
`"type": ["string", "null"]`), and `additionalProperties: false` at every object level. See
`agentparamsfromskill.json` or `agentJobCreator.json` for worked examples, including an
array-of-objects-with-nested-array shape.

## Related

- `plugins/finder/.agents/skills/transcriber/SKILL.md`: worked example of an informatics skill that
  calls a non-LLM `aiserver` (transcribe) with multipart uploads.
- Data tables that back the AI pipeline (aiskill, automationstep, aiserver, aistyle, ...) live under
  `plugins/catalog/html/data/fields` and `plugins/catalog/html/data/lists` — use the
  `catalog-table-creator` skill (in the `catalog` plugin) if you also need a new backing table or field.

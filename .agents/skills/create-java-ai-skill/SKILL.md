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
the `bean=` attribute on that row — e.g. `agentJobCreatorSkill` is the bean id in `plugin.xml`, the
`<data id="agentJobCreatorSkill" bean="agentJobCreatorSkill" ...>` row in `aiskill/chat_monitor.xml`,
and the `aiskill="agentJobCreatorSkill"` reference in `automationstep/agentorchestrator.xml`. Use
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
	void startupScenario(AgentContext inContext);
	void endScenario(AgentContext inContext);
	void process(AgentContext inContext);
}
```

- `AgentContext` is `org.entermediadb.ai.AgentContext` (not `org.entermediadb.ai.llm.AgentContext`
  — that's a different, unrelated class).
- Extend `BaseSkill` (`plugins/finder/code/org/entermediadb/ai/BaseSkill.java`) and override only
  `process(...)` unless you specifically need to change startup/end behavior. `BaseSkill`'s
  `startupScenario` fires the "starting" status; its default `process` fires the "complete" status
  and then runs the step's children (`getCurrentAutomationStep().getChildren()`) — call
  `super.process(inContext)` at the end of your override to keep that chaining, or omit it if this
  step must not auto-advance.

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

See `plugins/finder/code/org/entermediadb/ai/skills/AgentJobCreatorSkill.java` for a real,
non-trivial example (only overrides `process`, casts `AgentContext` to a more specific subtype like
`ChatMessageContext` when it needs chat history).

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

## Step 4: Add the aiskill row

Add a `<data>` record to `plugins/catalog/html/data/lists/aiskill/*.xml` — pick whichever existing
file is topically closest (these files are grouped by domain, e.g. `chat_monitor.xml`,
`automationagentbase.xml`, `goals.xml` — *not* one-file-per-automationscenario), or add a new
same-pattern file there if none fits. This is a data row on an already-existing list field, so it
does **not** need the `catalog-table-creator` skill (that's only for new tables/fields).

```xml
<data id="beanId" bean="beanId" enabled="true" agenttype="eventagent" ordering="10">
	<name>
		<language id="en"><![CDATA[Human-readable name]]></language>
	</name>
	<markdowncontent><![CDATA[One-sentence description of what this skill does — this text is
what embedding-based retrieval (see AgentJobCreatorSkill) matches against, so make it descriptive.]]></markdowncontent>
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

## Step 6: Validate

1. Rebuild/reload so the Java class and the Spring bean definition are picked up.
2. Confirm `beanId` appears in the automation agent list in the admin UI.
3. Trigger the target event/module and verify the skill executed in the logs.

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
`plugins/finder/code/org/entermediadb/ai/skills/AgentJobCreatorSkill.java` already does this the
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

- Data tables that back the AI pipeline (aiskill, automationstep, aiserver, aistyle, ...) live under
  `plugins/catalog/html/data/fields` and `plugins/catalog/html/data/lists` — use the
  `catalog-table-creator` skill (in the `catalog` plugin) if you also need a new backing table or field.

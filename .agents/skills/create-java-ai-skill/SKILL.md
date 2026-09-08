---
name: create-java-ai-skill
description: Use this skill whenever the user wants to add a new Java-based automation step to EME's AI/agent pipeline — requests like "create a new AI skill that does X", "add an automation step for Y", "write a Skill class for Z", or "make this run automatically after asset upload". This covers the full loop: writing the Skill class, wiring it as a Spring bean, and registering it so it is selectable and runnable from an automation scenario. Always consult this skill before hand-writing a Skill class or editing plugins/catalog/html/data/lists/aiskill*/*.xml, since the bean-id / data-id linkage is easy to get wrong.
---

# Create a Java AI Skill

Adds a new automation step ("Skill") to EME's AI pipeline, following the plugin/bean/data
conventions used across this project.

## Step 1: Place the plugin package

- Custom code goes in `Website/plugins/<yourplugin>/code/org/...` (or `plugins/<yourplugin>/code/org/...`
  in this repo for built-in plugins) so it can override or extend eme-lib behavior.
- Bean wiring for that plugin goes in `Website/plugins/<yourplugin>/html/src/plugin.xml` (or
  `plugins/<yourplugin>/html/src/plugin.xml`).
- Remember the fallback order: `Website/plugins/*` is used before `EME-LIB/plugins/*` when names match.

## Step 2: Write the Skill class

The core contract is `plugins/finder/code/org/entermediadb/ai/Skill.java`:

- Required methods: `processstart(AgentContext)`, `process(AgentContext)`, `processend(AgentContext)`.
- Extend `BaseSkill` (`plugins/finder/code/org/entermediadb/ai/BaseSkill.java`) instead of implementing
  `Skill` directly unless you have a specific reason not to.

```java
package org.entermediadb.ai.custom.agents;

import org.entermediadb.ai.BaseSkill;
import org.entermediadb.ai.llm.AgentContext;

public class MyCustomSkill extends BaseSkill
{
	@Override
	public void process(AgentContext inContext)
	{
		// your logic here
		super.process(inContext); // optional: run child agents
	}
}
```

## Step 3: Register the Skill bean

Add a bean entry to the owning plugin's `plugin.xml` (e.g. `plugins/myplugin/html/src/plugin.xml`):

```xml
<bean id="myCustomSkill" class="org.entermediadb.ai.custom.agents.MyCustomSkill" scope="prototype">
	<property name="moduleManager">
		<ref bean="moduleManager" />
	</property>
</bean>
```

## Step 4: Make it selectable and runnable

- Add a skill definition in `plugins/catalog/html/data/lists/aiskill/*.xml` with a unique `data id`
  and `bean="myCustomSkill"`.
- Enable and order it in `plugins/catalog/html/data/lists/automationstep/*.xml` using
  `aiskill="<data id from aiskill>"`.
- Use `runafter` and `automationscenario` to control sequence and where it runs (e.g. after asset
  upload, on a scheduled job).

## Step 5: Validate

1. Rebuild/reload so the Java class and the Spring bean definition are picked up.
2. Confirm the new `data id` appears in the automation agent list in the admin UI.
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

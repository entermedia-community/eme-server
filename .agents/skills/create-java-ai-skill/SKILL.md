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

## Related

- Data tables that back the AI pipeline (aiskill, automationstep, aiserver, aistyle, ...) live under
  `plugins/catalog/html/data/fields` and `plugins/catalog/html/data/lists` — use the
  `catalog-table-creator` skill (in the `catalog` plugin) if you also need a new backing table or field.

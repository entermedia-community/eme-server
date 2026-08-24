# AGENTS.md

## How to customize EME

### Overview

EME-LIB is a custom Java base framework that is deployable using a script called resources/bin/eme.sh. The embded Tomcat library runs on http://localhost:8080 by default. It is expected NGINX will proxy the port 8080 in a production environmernt.

The two most important folders are:

1. EME-LIB: typically this is checked out into the users git folder from github: https://github.com/entermedia-community/eme-lib

2. Website can be stored in any folder name. By running: eme-lib/resources/bin/eme.sh init /usr/share/eme/MyServerName

### Prerequisites

- Install java and dependencies. See .agents/SKILL.md

### Codebase map

- `plugins` Contains extention points that are mounted in the web server using symbolic links
- `plugins/eme` This is the main entry point for the web site and theme of the site. It can be reached by going to http://localhost:8080/eme
- `plugins/system/lib` The main jar libraries that the system uses to process web pages etc
- `plugins/system/code` This handles html web page requests and user security
- `plugins/finder/html/find` This is the app contains all the components needed to organize and media library and for data entry
- `plugins/community/html/default` This is a community template to help render projects and goal tracking
- `plugins/finder/code` Contains the bulk of the java code that controls the system
- . If a user has a folder in WEbsite/plugins/_ that will be used first before EME-LIB/plugins/_ of the same name

### Web app conventions

VSS has a launcher that will build the java source code automatically into MyServerName/bin folder that tomcat uses to launch.

- FallBackDirectory Is the idea that when a file it needed and is not found that it will look in the parent folder. This effectively gives EME and html object inheritance and the idea of parent child html folders. The main fallback in the app is /eme -> /finder/find -> /community/default. All HTML files will be found within this fallback tree
- \_site.xconf This is an XML text file located in a folder. The properties are applied to any file path in that folder and sub-folders.
- .xconf Each page can have its own xconf file that will be merged with the \_site.xconf in it's folder on any parent folders
- catalog/data All the default tables, fields, views that are needed for data entry and searching the embeded elastic database
- layouts A layout file is an html file that can be layered on top of one another like an onion. Each level of layout renders some html. Finally the original page that was requested is rendered in the middle of the final layout. Layouts provide decorations such as side menus or headers/footers. Layouts are specified by including an <inner-layout /> tag within the folder or files .xconf file

### Package and feature boundaries

- There are no external dependecies outside of eme-lib
- plugins/openedit Is used to enable a small toolbar on the site. This toolbar will allow users to view and edit the layout and include structure
- plugins/manager folder that is used when users want to have more than one database. It is not needed for normal operation
- plugin/components - This folder is used by web pages to find common javascript libraries. The eme app users a /eme/components -> /components fallback to add additional javascript it needs.
- plugin/mediadb Contains the JSON REST APi services. All available API calls can be found in eme-lib/plugins/catalog/html/data/lists/endpoint/\*.xml files
- plugins/catalog/html/data/lists/aiskillenabled/_.xml Contains various automation steps that are performed by Java classes fined in plugins/catalog/html/data/lists/aiskill/_.xml
- plugins/finder/html/src/plugin.xml Contains Spring style bean definitions for finder Java classes
- Each eme-lib can spawn multiple Websites using the eme.sh command. A website is where the user will customize his site. The website will contain his database
- Website/data This is where any database and file uploads will be saved
- Website/webapp This is where the plugins will be linked and used by tomcat to render the html of the website to the user directly

Placement decision tree:

1. If the change is a existing html file then the browser will see the change on reload
2. If the change is editing an xconf file then the page cache of the server needs to be cleared
3. If an html of xconf file is added or removed the page cache must be cleared or server restarted
4. If a user has a folder in WEbsite/plugins/_ that will be used first before EME-LIB/plugins/_ of the same name

### Java plugin and Skill structure

Use this flow when creating a new plugin or custom AI automation skill.

#### 1) Define a new plugin package

- Place custom code in Website/plugins/<yourplugin>/code/org/... so it can override or extend eme-lib behavior.
- Place plugin bean wiring in Website/plugins/<yourplugin>/html/src/plugin.xml.
- Keep in mind fallback order: Website/plugins/_ is used before EME-LIB/plugins/_ when names match.

#### 2) Create your own Skill class

The core Skill contract is in plugins/finder/code/org/entermediadb/ai/Skill.java.

- Required methods: processstart(AgentContext), process(AgentContext), processend(AgentContext).
- Most implementations should extend BaseSkill (plugins/finder/code/org/entermediadb/ai/BaseSkill.java) instead of implementing Skill directly.

Example:

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

#### 3) Register the Skill bean

Add a bean entry in plugin.xml (example pattern from plugins/myplugin/html/src/plugin.xml):

```xml
<bean id="myCustomSkill" class="org.entermediadb.ai.custom.agents.MyCustomSkill" scope="prototype">
	<property name="moduleManager">
		<ref bean="moduleManager" />
	</property>
</bean>
```

#### 4) Make it selectable and runnable

- Add a skill definition in plugins/catalog/html/data/lists/aiskill/\*.xml with a unique data id and bean="myCustomSkill".
- Enable and order it in plugins/catalog/html/data/lists/aiskillenabled/\*.xml using aiskill="<data id from aiskill>".
- Use runafter and automationscenario to control sequence and where it runs.

#### 5) Validate changes

1. Rebuild/reload so Java classes and Spring bean definitions are loaded.
2. Confirm your new id appears in automation agent lists.
3. Trigger the target event/module and verify execution in logs.

### Database

Elasticsearch 2.4.6 is embedded into the server. It is auto configured based on the Website/webapp/WEB-INF/node.xml and plugins/catalog/html/configuration/\*.xml files. The elastic index is stored in Website/WEB-INF/elastic and is auto created on bootup

### Environment

- `JAVA_HOME` Used by eme.sh
- `EME_LIB` Optionally used by eme.sh start. If not set will use in the nearest eme-lib sibling folder of the website
- AI Server Security keys are stored in /usr/share/eme-lib/plugins/catalog/html/data/lists/aiserver/\*.xml

### Common commands

| Task | Command                   |
| ---- | ------------------------- |
| eme  | init /usr/share/myserver  |
| eme  | start /usr/share/myserver |

### Gotchas

- When running within VSS we recommend using the Java debugger to start and stop your WebSite

### Known issues

If there is syntax error, make sure user does not have georgewfraser.vscode-javac installed. This plugin is not compatible with the EME codebase and will cause syntax errors in the editor. Uninstalling this plugin should resolve the issue. The default java extension package provided by Microsoft is compatible and should be used instead.

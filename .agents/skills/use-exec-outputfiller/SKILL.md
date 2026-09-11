---
name: use-exec-outputfiller
description: Use this skill whenever EME Java code needs to run an external command-line tool (ffmpeg, exiftool, imagemagick convert, a shell one-liner, docker, etc.) or needs to copy/stream file and stream contents in a buffered way. Covers the two shared system-plugin utilities for that — org.openedit.util.Exec (run external processes, capture stdout/stderr, stream binary output) and org.openedit.util.OutputFiller (buffered fill/copy/read of streams and files) — including how to get them as Spring beans, which runExec/runExecStream overload to pick, how command keys resolve against /WEB-INF/bin/commandmap.xml, and the low-level FinalizedProcessBuilder path for custom process control.
---

# Skill: use-exec-outputfiller

How to run external processes and move stream/file data in EME Java code using the two shared
utilities in `plugins/system/code/org/openedit/util/`:

- `Exec` (`Exec.java`) — runs external commands, returns an `ExecResult` (stdout, stderr, exit
  code), can stream binary output to an `OutputStream`, and owns an `ExecutorManager` plus an
  `OutputFiller`.
- `OutputFiller` (`OutputFiller.java`) — buffered reader/writer for streams and files: `fill`,
  `readAll`, `readAllText`, safe `close` helpers, optional max-size cap.

Both live in the `system` plugin (framework-level, shared by every plugin). Do not roll your own
`ProcessBuilder` or manual byte-copy loops when either of these fits the job.

## Getting the beans

The singleton `Exec` bean is defined in `plugins/system/html/src/plugin.xml:89`:

```xml
<bean id="exec" class="org.openedit.util.Exec">
	<property name="xmlArchive"><ref bean="xmlArchive"/></property>
	<property name="xmlCommandsFilename"><value>/WEB-INF/bin/commandmap.xml</value></property>
	<property name="root"><ref bean="root"/></property>
	<property name="executorManager"><ref bean="executorManager"/></property>
</bean>
```

Two ways to use it from your class:

1. **Inject it as a property** (preferred for Spring-managed beans). Declare
   `protected Exec fieldExec;` with `getExec()`/`setExec(Exec)` and wire it in the owning
   plugin's `plugin.xml`:

   ```xml
   <bean id="myBean" class="org.example.MyBean" scope="prototype">
   	<property name="exec"><ref bean="exec" /></property>
   </bean>
   ```

   Real examples of this exact pattern: `ExiftoolMetadataExtractor.java:778-785`,
   `S3CmdAssetSource.java:78-85`, `WaterMarkTranscoder.java:27-34`,
   `OpenCodeRunnerSkill.java` (finder plugin).

2. **Look it up ad hoc** from the media archive (used by non-Spring or rarely-called code):

   ```java
   Exec exec = (Exec) getMediaArchive().getBean("exec");
   ```

   See `BaseAiManager.java:165` and `:211`.

There is no standalone Spring bean for `OutputFiller` — it's a plain utility class. Get one via
`exec.getFiller()` (lazily created, `Exec.java:76-83`) or just `new OutputFiller()`. Both are fine;
it holds only a buffer size and optional max-size.

## Running commands with Exec

### Pick the right overload

| Method | Use when |
|---|---|
| `runExec(String commandKey, Collection<String> args)` | Normal case. Resolves the command through `commandmap.xml`, uses the default 1h time limit. |
| `runExec(String commandKey, Collection<String> args, boolean saveOutput)` | Same, but capture stdout into `result.getStandardOut()`. **Pass `true` whenever you need the output.** |
| `runExec(String commandKey, Collection<String> args, boolean saveOutput, long timeoutMs)` | When you need a custom timeout (e.g. `60000` for exiftool in `ExiftoolMetadataExtractor.java:729`). |
| `runExec(String commandKey, Collection<String> args, File rootFolder)` / `(…, boolean, File, long)` | When the command must run from a specific working directory. |
| `runExec(List<String> com, File inRunFrom, boolean inSaveOutput, long inTimeout)` | Deprecated direct-list form — use a command key instead. |
| `runExecStream(String commandKey, List<String> args, OutputStream out, long timeoutMs)` | When the command writes **binary** output you want streamed straight to a file/`OutputStream` (e.g. exiftool writing a thumbnail, ffmpeg piping video). See `ExiftoolMetadataExtractor.java:110`, `BaseAiManager.java:213`. |
| `getProcess(String name)` | Long-running named process managed across calls via `RunningProcess`; only for daemons you start once and query repeatedly. |

### Command keys and commandmap.xml

The first argument to `runExec`/`runExecStream` is a **command key**, not necessarily a literal
binary path. Resolution happens in `Exec.lookUpCommand` (`Exec.java:332-405`) against
`webapp/WEB-INF/bin/commandmap.xml`:

```xml
<commandmaps>
	<commandmap os="LINUX">
		<commandbase>./WEB-INF/bin/linux</commandbase>
		<avconv>ffmpeg</avconv>
		<exiftoolthumb>./exiftoolthumb.sh</exiftoolthumb>
	</commandmap>
	<commandmap os="WINDOWS">…</commandmap>
	<commandmap os="MAC OS X">…</commandmap>
</commandmaps>
```

Rules (read `lookUpCommand` carefully before assuming):

- The `<commandmap os="...">` block whose `os` attribute is a substring of the upper-cased
  `os.name` system property wins.
- A key that maps to a value starting with `./` or `../` is resolved relative to that OS block's
  `<commandbase>` (itself resolved against the `root` bean = webapp root). The command's parent
  directory becomes the process working directory.
- A key that maps to a plain name (e.g. `avconv` -> `ffmpeg`) runs from `commandbase` with that
  name looked up on `PATH`.
- **A key that is not in commandmap.xml at all still works**: it falls through and is executed as-is
  from the system `PATH` (`Exec.java:396-404`). So `runExec("ls", args)` is legal; you only need a
  commandmap entry for OS-specific paths or bundled binaries.

Prefer adding a named key to `commandmap.xml` over hardcoding absolute paths in Java — that's how
every existing caller (ffmpeg/avconv, exiftool, convert, gs, aws, restartdocker, localopencommand)
is done.

### Reading the result

```java
ExecResult result = getExec().runExec("exiftool", args, true); // true = capture stdout
if (!result.isRunOk())
{
	// result.getReturnValue() is the exit code (0 == success; 1 is also set on exceptions)
	String error = result.getStandardError(); // falls back to stdout if stderr is empty and run failed
}
else
{
	String out = result.getStandardOut(); // only populated when saveOutput was true
}
```

Notes from `Exec.runExec(List, File, boolean, long)` (`Exec.java:176-230`):

- stdout capture (`getStandardOutputs()`) only happens when `inSaveOutput` is `true`.
- On any exception the method does **not** throw — it returns a result with `isRunOk()==false`,
  `returnValue==1`, and the exception text appended to stderr. Always check `isRunOk()`.
- The default time limit is 1 hour (`Exec.java:29`); pass an explicit timeout for anything that
  should fail fast.

### Worked example (from this codebase)

`plugins/finder/code/org/entermediadb/asset/scanner/ExiftoolMetadataExtractor.java:729`:

```java
ExecResult result = getExec().runExec("exiftool", command, true, 60000);
if (!result.isRunOk()) { … }
String output = result.getStandardOut();
```

## Streaming binary output with runExecStream

For commands that emit binary data (images, video), stream straight to an `OutputStream` instead
of capturing into memory:

```java
ByteArrayOutputStream output = new ByteArrayOutputStream();
ExecResult result = getExec().runExecStream("convert", args, output, 5000);
if (!result.isRunOk())
{
	throw new OpenEditException("Error converting image: " + result.getReturnValue());
}
byte[] bytes = output.toByteArray();
```

This is exactly `BaseAiManager.java:213` (convert an asset to base64 JPEG for an LLM). Under the
hood it uses `BinaryStreamingProcessBuilder` (`org/openedit/util/exec/BinaryStreamingProcessBuilder.java`),
which consumes stdout/stderr on a 2-thread pool before waiting, so large outputs cannot deadlock
the caller. You can also target a file: pass a `FileOutputStream` as the `OutputStream`.

## Low-level process control: FinalizedProcessBuilder

When the `runExec` overloads aren't enough (e.g. you must append output to a temp file yourself,
or need both stdout and stderr separately), drop down to the same building blocks `Exec.runExec`
uses — this is what `OpenCodeRunnerSkill.java` does:

```java
List<String> args = new ArrayList<String>();
args.add("/bin/sh");
args.add("-c");
args.add(commandLine);

FinalizedProcessBuilder builder = new FinalizedProcessBuilder(args);
builder.keepProcess(false);
builder.logInputtStream(true);   // gobble stdout (and merge stderr into it)
builder.directory(new File(".")); // working directory; null is NOT safe — Exec.runExec always sets one

FinalizedProcess process = builder.start(getExec().getExecutorManager());
try
{
	int exitcode = process.waitFor(getExec().getTimeLimit());
	String stdout = process.getStandardOutputs();
	String stderr = process.getErrorOutputs();
}
finally
{
	process.close(); // required — drains/closes the stream gobblers
}
```

Key facts (verified against `FinalizedProcessBuilder.java` / `FinalizedProcess.java`):

- Default is `gobbleInput=true`, `gobbleError=false`; with that combination `start()` calls
  `processBuilder.redirectErrorStream(true)`, so stderr lands in the stdout gobblers and
  `getStandardOutputs()` contains both. If you set `logErrorStream(true)` explicitly, read stderr
  separately via `getErrorOutputs()`.
- Always call `process.close()` in a `finally` block — it's what `Exec.runExec` does
  (`Exec.java:210-214`) and skipping it leaks the gobbler threads.
- Reuse `getExec().getExecutorManager()` and `getExec().getTimeLimit()` rather than creating your
  own executor, so processes share the managed pool and default timeout.

## Using OutputFiller

`OutputFiller` is a buffered copy/read helper (default buffer 2048 bytes). Get one via
`exec.getFiller()` or `new OutputFiller()`.

| Method | What it does |
|---|---|
| `fill(Reader in, Writer out)` / `fill(InputStream in, OutputStream out)` | Buffered copy between streams; flushes at the end. |
| `fill(InputStream in, OutputStream out, long inToSend)` | Copy exactly `inToSend` bytes (used to truncate/limit). |
| `fill(InputStream in, File out)` | Stream into a file, creating parent dirs. Note: uses **non-append** `FileOutputStream`. |
| `fill(File source, File dest)` | File-to-file copy, creating parent dirs. |
| `readAll(InputStream in)` | Read entire stream to `byte[]`. |
| `readAllText(InputStream in)` | Read entire stream as a UTF-8 `String` (returns `null` on IOException — null-check it). |
| `close(InputStream/OutputStream/Reader/Writer)` | Null-safe, swallow-IOException close. Use these instead of bare `.close()` in finally blocks. |
| `setMaxSize(long)` | Cap for the byte-based `fill` loops (`-1` = unlimited, the default). See `BaseElasticSearcher.java:4537`. |

### Worked example: reading a file's text content

From `OpenCodeRunnerSkill.java:144-168` (read a command-output temp file back into a string):

```java
protected String readFileContents(File inFile)
{
	FileInputStream in = null;
	try
	{
		in = new FileInputStream(inFile);
		String contents = getExec().getFiller().readAllText(in);
		return contents == null ? "" : contents;
	}
	catch (IOException e)
	{
		log.error("Failed reading " + inFile.getAbsolutePath(), e);
		return "";
	}
	finally
	{
		getExec().getFiller().close(in);
	}
}
```

### Worked example: streaming a response body to a file

`GeoCoder.java:167` pattern — fill an `InputStream` (e.g. from HTTP) into a writer/file:

```java
new OutputFiller().fill(new InputStreamReader(in), out);
```

## Decision guide

1. Need to run a command and read its text output? → `exec.runExec(key, args, true[, timeout])`,
   check `isRunOk()`, use `getStandardOut()`/`getStandardError()`.
2. Command produces binary you want in a file or byte array? → `exec.runExecStream(key, args, outputStream, timeout)`.
3. Need custom process handling (append to file, separate streams, unusual working dir)? →
   `FinalizedProcessBuilder` + `FinalizedProcess`, started with `exec.getExecutorManager()`.
4. Just copying/reading streams or files in Java? → `OutputFiller` (`fill`, `readAll`,
   `readAllText`, `close`).
5. Command path differs per OS or ships with the server? → add a key to
   `webapp/WEB-INF/bin/commandmap.xml` under the right `<commandmap os=…>` block; do not hardcode
   paths in Java.

## Validation checklist

1. Compile: `bin/compile.sh` (or the ant `compile` target in `plugins/finder/build.xml`) — new
   usage must compile against the system plugin classes.
2. For a new commandmap key: confirm the binary exists at the resolved path on the target OS
   (`<commandbase>` is relative to the webapp root), and that the `os` attribute matches
   (`LINUX`, `WINDOWS`, `MAC OS X` — matched as a substring of upper-cased `os.name`).
3. Always assert `ExecResult.isRunOk()` before trusting output; on failure log
   `getStandardError()` and `getReturnValue()`.
4. If you used `FinalizedProcessBuilder` directly, confirm `process.close()` is in a `finally`.
5. Smoke-test with a real command (e.g. `runExec("localopencommand", …)` or an exiftool call) and
   check the server log line `Running: …` that `Exec.runExec` emits (`Exec.java:182`).

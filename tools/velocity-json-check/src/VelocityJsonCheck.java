import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.InternalContextAdapter;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.runtime.directive.Directive;
import org.apache.velocity.runtime.parser.node.ASTEQNode;
import org.apache.velocity.runtime.parser.node.ASTNENode;
import org.apache.velocity.runtime.parser.node.Node;
import org.apache.velocity.runtime.parser.node.SimpleNode;
import org.apache.velocity.util.introspection.Info;
import org.apache.velocity.util.introspection.UberspectImpl;
import org.apache.velocity.util.introspection.VelMethod;
import org.apache.velocity.util.introspection.VelPropertyGet;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Checks that Velocity-templated .json files always render to valid JSON.
 *
 * Every template is rendered with the server's own Velocity engine, macro library and #ifnull/#ifnotnull
 * semantics, but with "stub" values in place of real data. Each place in the template that produces a value
 * (a root reference like $user, or a call like $users.getPageOfHits()) is a "site". A run decides how every
 * site behaves (true/non-empty with 2 items, false/empty, 1 item, null, or a string that needs escaping). The
 * checker renders a baseline, then flips one site at a time, parses every output as strict JSON, and blames
 * the flipped site for any output that does not parse.
 *
 * Usage:
 *   VelocityJsonCheck --root <eme-server> [--json] file...     check files, print diagnostics
 *   VelocityJsonCheck --root <eme-server> --server             read one path per stdin line, answer one JSON line each
 */
public class VelocityJsonCheck
{
	// ------------------------------------------------------------------ run state (single-threaded)

	enum Mode
	{
		NORMAL, // truthy, 2 items when iterated, renders as 0
		FALSY, // false / empty / treated as absent by #ifnull, renders as 0
		ONE, // truthy, 1 item when iterated
		NULL, // the producer returns null
		HOSTILE, // renders as a string that breaks JSON unless escaped
		WORD // renders as a bare word (detects "this value is not inside quotes")
	}

	enum Event
	{
		TESTED, ITERATED, RENDERED
	}

	static final class Site
	{
		final String key;
		final String label;
		final int line;
		final int column;
		final Set<Event> events = EnumSet.noneOf(Event.class);
		/** Reads data (property, get*, is*, ...). Only reads are tested as null; actions like $pages.include() are not. */
		boolean read = true;
		String suffix = "";

		String describe()
		{
			return "`" + label + "`" + suffix;
		}

		Site(String inKey, String inLabel, int inLine, int inColumn)
		{
			key = inKey;
			label = inLabel;
			line = inLine;
			column = inColumn;
		}
	}

	static final class Run
	{
		static String templateName;
		static String[] sourceLines;
		static Mode base = Mode.NORMAL;
		static String flippedKey;
		static Mode flippedMode;
		static final Map<String, Site> sites = new LinkedHashMap<>();
		static VelocityJsonCheck checker;
		static VelocityContext context;
		/** Directory of the template being rendered, for relative $pages.include("./x.json") */
		static final java.util.ArrayDeque<File> dirs = new java.util.ArrayDeque<>();
		static File pluginHtml;
		static boolean fragmentAllowed;
		static final List<Diagnostic> notes = new ArrayList<>();

		static void reset(String inTemplate, String[] inLines)
		{
			templateName = inTemplate;
			sourceLines = inLines;
			sites.clear();
			notes.clear();
			base = Mode.NORMAL;
			flippedKey = null;
		}

		/** Mode for a site. Sites outside the template under test (macro library) always behave normally. */
		static Mode modeOf(String inKey)
		{
			if (inKey == null)
			{
				return Mode.NORMAL;
			}
			if (inKey.equals(flippedKey))
			{
				return flippedMode;
			}
			return base;
		}

		static void record(String inKey, Event inEvent)
		{
			Site site = inKey == null ? null : sites.get(inKey);
			if (site != null)
			{
				site.events.add(inEvent);
			}
		}

		/**
		 * Registers a site produced by a method or property in the template under test; null for other templates.
		 * Sites are keyed by their expression text ($server.membercount, $types.contains("people")) so every use of
		 * the same expression behaves the same way within a run, like real data would.
		 */
		static String siteFor(Info inInfo, String inName, boolean inMethod)
		{
			if (inInfo == null || !templateName.equals(inInfo.getTemplateName()))
			{
				return null;
			}
			int column = charColumn(inInfo.getLine(), inInfo.getColumn());
			String text = expressionAt(inInfo.getLine(), column, inName, inMethod);
			String key = text != null ? text : inInfo.getLine() + ":" + column + ":" + inName;
			if (!sites.containsKey(key))
			{
				Site site = new Site(key, text != null ? text : "." + inName + (inMethod ? "()" : ""), inInfo.getLine(), column);
				site.read = !inMethod || READ_METHOD.matcher(inName).find();
				sites.put(key, site);
			}
			return key;
		}

		static final Pattern READ_METHOD = Pattern.compile("^(get|is|has|find|search|lookup)");

		/** Source text of the reference chain ending at this method/property, e.g. $types.contains("people"). */
		static String expressionAt(int inLine, int inColumn, String inName, boolean inMethod)
		{
			if (inLine < 1 || inLine > sourceLines.length)
			{
				return null;
			}
			String text = sourceLines[inLine - 1];
			int at = text.indexOf(inName, Math.max(inColumn - 1 - inName.length() - 1, 0));
			if (at < 0)
			{
				return null;
			}
			int dollar = -1;
			// The chain starts at the nearest '$' before the name that is not inside the chain's own arguments
			int depth = 0;
			for (int i = at - 1; i >= 0; i--)
			{
				char c = text.charAt(i);
				if (c == ')')
				{
					depth++;
				}
				else if (c == '(')
				{
					if (depth == 0)
					{
						break;
					}
					depth--;
				}
				else if (c == '$' && depth == 0)
				{
					dollar = i;
					break;
				}
				else if (depth == 0 && !(Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '!' || c == '{' || c == ']' || c == '['))
				{
					break;
				}
			}
			if (dollar < 0)
			{
				return null;
			}
			int end = at + inName.length();
			if (inMethod)
			{
				int open = text.indexOf('(', end);
				if (open < 0)
				{
					return null;
				}
				int d = 0;
				char quote = 0;
				int i = open;
				for (; i < text.length(); i++)
				{
					char c = text.charAt(i);
					if (quote != 0)
					{
						if (c == quote)
						{
							quote = 0;
						}
					}
					else if (c == '"' || c == '\'')
					{
						quote = c;
					}
					else if (c == '(')
					{
						d++;
					}
					else if (c == ')' && --d == 0)
					{
						break;
					}
				}
				end = Math.min(i + 1, text.length());
			}
			String expr = text.substring(dollar, end).replace("$!", "$").replace("${", "$");
			return expr.length() > 80 ? expr.substring(0, 77) + "…" : expr;
		}

		/** Velocity (JavaCC) columns expand tabs to 8; convert to a 1-based character column. */
		static int charColumn(int inLine, int inColumn)
		{
			if (inLine < 1 || inLine > sourceLines.length)
			{
				return inColumn;
			}
			String text = sourceLines[inLine - 1];
			int visual = 1;
			for (int i = 0; i < text.length(); i++)
			{
				if (visual >= inColumn)
				{
					return i + 1;
				}
				visual = text.charAt(i) == '\t' ? ((visual - 1) / 8 + 1) * 8 + 1 : visual + 1;
			}
			return text.length() + 1;
		}

		static String siteForRoot(String inName)
		{
			String key = "$" + inName;
			if (!sites.containsKey(key))
			{
				int[] pos = findRoot(inName);
				Site site = new Site(key, "$" + inName, Math.max(pos[0], 1), pos[1]);
				site.suffix = pos[0] > 0 ? "" : " (from an included template)";
				sites.put(key, site);
			}
			return key;
		}

		static int[] findRoot(String inName)
		{
			Pattern p = Pattern.compile("\\$!?\\{?" + Pattern.quote(inName) + "\\b");
			for (int i = 0; i < sourceLines.length; i++)
			{
				Matcher m = p.matcher(sourceLines[i]);
				if (m.find())
				{
					return new int[] { i + 1, m.start() + 1 };
				}
			}
			return new int[] { 0, 1 };
		}
	}

	// ------------------------------------------------------------------ stub values

	public static final class Stub implements Iterable<Object>
	{
		/** Per-render counter: each stub prints a distinct number, so dynamic keys ("${detail.id}":) never collide. */
		static int nextId;
		final String site;
		final Mode mode;
		final int id = ++nextId;

		Stub(String inSite, Mode inMode)
		{
			site = inSite;
			mode = inMode;
		}

		static Object produce(String inSite)
		{
			Mode mode = Run.modeOf(inSite);
			return mode == Mode.NULL ? null : new Stub(inSite, mode);
		}

		boolean absent()
		{
			return mode == Mode.FALSY;
		}

		/** Items when iterated; also what size()/length() answer, so `#if($foreach.count < $x.size())` stays consistent. */
		int count()
		{
			return absent() ? 0 : mode == Mode.ONE ? 1 : 2;
		}

		// Velocity's DuckType calls these for #if, comparisons and arithmetic
		public boolean getAsBoolean()
		{
			Run.record(site, Event.TESTED);
			return !absent();
		}

		public Number getAsNumber()
		{
			Run.record(site, Event.TESTED);
			return count();
		}

		public boolean isEmpty()
		{
			Run.record(site, Event.TESTED);
			return absent();
		}

		@Override
		public Iterator<Object> iterator()
		{
			Run.record(site, Event.ITERATED);
			int count = count();
			List<Object> items = new ArrayList<>();
			for (int i = 0; i < count; i++)
			{
				items.add(new Stub(null, Mode.NORMAL));
			}
			return items.iterator();
		}

		@Override
		public String toString()
		{
			Run.record(site, Event.RENDERED);
			switch (mode)
			{
				case HOSTILE:
					return "a\"b\\c\nd";
				case WORD:
					return "abc";
				default:
					return String.valueOf(id);
			}
		}
	}

	/** Method names that are void-like in practice ($context.putPageValue(...)); they render as nothing. */
	static final Pattern VOIDISH = Pattern.compile("^(set|put|add|remove|clear|append|log|debug|info|warn)([A-Z].*)?$");

	public static final class StubUberspect extends UberspectImpl
	{
		@Override
		public Iterator getIterator(Object inObj, Info inInfo)
		{
			if (inObj instanceof Stub)
			{
				return ((Stub) inObj).iterator();
			}
			return super.getIterator(inObj, inInfo);
		}

		@Override
		public VelMethod getMethod(Object inObj, String inName, Object[] inArgs, Info inInfo)
		{
			if (inObj instanceof Stub)
			{
				String site = VOIDISH.matcher(inName).matches() ? null : Run.siteFor(inInfo, inName, true);
				boolean voidish = site == null && VOIDISH.matcher(inName).matches();
				StubMethod m = new StubMethod(inName, site, voidish);
				m.info = inInfo;
				return m;
			}
			// A real object (e.g. $jsonUtil) called with stub arguments: resolve and call it with their string form
			Object[] converted = convertArgs(inArgs);
			VelMethod real = super.getMethod(inObj, inName, converted, inInfo);
			return real == null ? null : new ConvertingMethod(real);
		}

		@Override
		public VelPropertyGet getPropertyGet(Object inObj, String inName, Info inInfo)
		{
			if (inObj instanceof Stub)
			{
				if (SIZE_METHODS.contains(inName) || inName.equals("empty"))
				{
					String method = inName.equals("empty") ? "isEmpty" : inName;
					return new VelPropertyGet()
					{
						public Object invoke(Object o)
						{
							return new StubMethod(method, null, false).invoke(o, new Object[0]);
						}

						public boolean isCacheable()
						{
							return false;
						}

						public String getMethodName()
						{
							return method;
						}
					};
				}
				return new StubGet(inName, Run.siteFor(inInfo, inName, false));
			}
			return super.getPropertyGet(inObj, inName, inInfo);
		}
	}

	static Object[] convertArgs(Object[] inArgs)
	{
		if (inArgs == null)
		{
			return null;
		}
		Object[] out = inArgs.clone();
		for (int i = 0; i < out.length; i++)
		{
			if (out[i] instanceof Stub)
			{
				out[i] = out[i].toString();
			}
		}
		return out;
	}

	static final Set<String> SIZE_METHODS = Set.of("size", "length", "getSize", "getLength", "count", "getCount", "getTotal", "isEmpty");

	static final class StubMethod implements VelMethod
	{
		final String name;
		final String site;
		final boolean voidish;
		Info info;

		StubMethod(String inName, String inSite, boolean inVoidish)
		{
			name = inName;
			site = inSite;
			voidish = inVoidish;
		}

		@Override
		public Object invoke(Object inObj, Object[] inParams)
		{
			Stub receiver = (Stub) inObj;
			if (SIZE_METHODS.contains(name) && (inParams == null || inParams.length == 0))
			{
				Run.record(receiver.site, Event.TESTED);
				return name.equals("isEmpty") ? (Object) (receiver.count() == 0) : (Object) receiver.count();
			}
			if (name.equals("include") && inParams != null && inParams.length > 0 && inParams[0] != null)
			{
				return Run.checker.include(String.valueOf(inParams[0]), info);
			}
			if (name.equals("putPageValue") && inParams != null && inParams.length == 2 && inParams[0] != null)
			{
				// keep the value so an included template sees it, like the real WebPageRequest
				Run.context.put(String.valueOf(inParams[0]), inParams[1]);
				return "";
			}
			if (voidish)
			{
				return "";
			}
			return Stub.produce(site);
		}

		@Override
		public boolean isCacheable()
		{
			return false;
		}

		@Override
		public String getMethodName()
		{
			return name;
		}

		@Override
		public Method getMethod()
		{
			return null;
		}

		@Override
		public Class<?> getReturnType()
		{
			return Object.class;
		}
	}

	static final class ConvertingMethod implements VelMethod
	{
		final VelMethod real;

		ConvertingMethod(VelMethod inReal)
		{
			real = inReal;
		}

		@Override
		public Object invoke(Object inObj, Object[] inParams) throws IllegalAccessException, java.lang.reflect.InvocationTargetException
		{
			return real.invoke(inObj, convertArgs(inParams));
		}

		@Override
		public boolean isCacheable()
		{
			return false;
		}

		@Override
		public String getMethodName()
		{
			return real.getMethodName();
		}

		@Override
		public Method getMethod()
		{
			return real.getMethod();
		}

		@Override
		public Class<?> getReturnType()
		{
			return real.getReturnType();
		}
	}

	static final class StubGet implements VelPropertyGet
	{
		final String name;
		final String site;

		StubGet(String inName, String inSite)
		{
			name = inName;
			site = inSite;
		}

		@Override
		public Object invoke(Object inObj)
		{
			return Stub.produce(site);
		}

		@Override
		public boolean isCacheable()
		{
			return false;
		}

		@Override
		public String getMethodName()
		{
			return name;
		}
	}

	/** Every reference the template never #set resolves to a stub for that root name. */
	static final class StubContext extends VelocityContext
	{
		static final Set<String> BUILTIN = Set.of("foreach", "velocityCount", "velocityHasNext", "template", "macro", "evaluate", "define", "parse", "include",
				"bodyContent");

		StubContext(Map<String, Object> inReal)
		{
			super(new HashMap<>(inReal));
		}

		@Override
		public Object internalGet(String inKey)
		{
			if (super.internalContainsKey(inKey))
			{
				return super.internalGet(inKey);
			}
			if (BUILTIN.contains(inKey))
			{
				return null;
			}
			return Stub.produce(Run.siteForRoot(inKey));
		}

		@Override
		public boolean internalContainsKey(String inKey)
		{
			return true;
		}

		/**
		 * Velocity caches method lookups per context by method name and argument types, not per call site, so
		 * `$a.contains("x")` and `$b.contains("y")` would share the first one's site. Stubs need per-site lookups.
		 */
		@Override
		public org.apache.velocity.util.introspection.IntrospectionCacheData icacheGet(Object inKey)
		{
			return null;
		}

		@Override
		public void icachePut(Object inKey, org.apache.velocity.util.introspection.IntrospectionCacheData inData)
		{
			// disabled; see icacheGet
		}
	}

	// ------------------------------------------------------------------ #ifnull / #ifnotnull
	// Same semantics as org.openedit.generators.IfNull / IfNotNull, but a FALSY stub counts as absent so the
	// "missing" branch is exercised too.

	static boolean isAbsent(Object inValue)
	{
		if (inValue instanceof Stub)
		{
			Stub s = (Stub) inValue;
			Run.record(s.site, Event.TESTED);
			return s.absent();
		}
		return inValue == null;
	}

	public static final class CheckIfNull extends Directive
	{
		@Override
		public String getName()
		{
			return "ifnull";
		}

		@Override
		public int getType()
		{
			return BLOCK;
		}

		@Override
		public boolean render(InternalContextAdapter inContext, Writer inWriter, Node inNode) throws IOException
		{
			if (isAbsent(inNode.jjtGetChild(0).value(inContext)))
			{
				inNode.jjtGetChild(1).render(inContext, inWriter);
			}
			return true;
		}
	}

	public static final class CheckIfNotNull extends Directive
	{
		@Override
		public String getName()
		{
			return "ifnotnull";
		}

		@Override
		public int getType()
		{
			return BLOCK;
		}

		@Override
		public boolean render(InternalContextAdapter inContext, Writer inWriter, Node inNode) throws IOException
		{
			if (!isAbsent(inNode.jjtGetChild(0).value(inContext)))
			{
				inNode.jjtGetChild(1).render(inContext, inWriter);
			}
			return true;
		}
	}

	// ------------------------------------------------------------------ == / != against stubs
	// Velocity compares a stub with "nouser" by string form, which is never equal, so #elseif chains on
	// `$x == "literal"` would never be exercised. After parsing, each == / != node in the template under test is
	// wrapped: when one side is a stub, `<site> == <literal>` becomes its own site (equal unless flipped).

	static final class StubEq extends SimpleNode
	{
		final Node original;
		final boolean negate;

		StubEq(Node inOriginal, boolean inNegate)
		{
			super(0);
			original = inOriginal;
			negate = inNegate;
		}

		@Override
		public boolean evaluate(InternalContextAdapter inContext)
		{
			Object left = original.jjtGetChild(0).value(inContext);
			Object right = original.jjtGetChild(1).value(inContext);
			Stub stub = left instanceof Stub ? (Stub) left : right instanceof Stub ? (Stub) right : null;
			Object other = stub == left ? right : left;
			if (stub == null || stub.site == null || other == null || other instanceof Stub)
			{
				return original.evaluate(inContext);
			}
			String key = stub.site + " == \"" + other + "\"";
			if (!Run.sites.containsKey(key))
			{
				Site owner = Run.sites.get(stub.site);
				String label = (owner != null ? owner.label : stub.site) + " == \"" + other + "\"";
				Run.sites.put(key, new Site(key, label, original.getLine(), Run.charColumn(original.getLine(), original.getColumn())));
			}
			Run.record(key, Event.TESTED);
			Mode mode = Run.modeOf(key);
			boolean equal = mode != Mode.FALSY && mode != Mode.NULL;
			return negate ? !equal : equal;
		}

		@Override
		public Object value(InternalContextAdapter inContext)
		{
			return evaluate(inContext);
		}

		@Override
		public int getLine()
		{
			return original.getLine();
		}

		@Override
		public int getColumn()
		{
			return original.getColumn();
		}
	}

	static void wrapComparisons(Node inNode)
	{
		for (int i = 0; i < inNode.jjtGetNumChildren(); i++)
		{
			Node child = inNode.jjtGetChild(i);
			wrapComparisons(child);
			if (child instanceof ASTEQNode || child instanceof ASTNENode)
			{
				StubEq wrapper = new StubEq(child, child instanceof ASTNENode);
				wrapper.jjtSetParent(inNode);
				inNode.jjtAddChild(wrapper, i);
			}
		}
	}

	// ------------------------------------------------------------------ diagnostics

	static final class Diagnostic
	{
		final String severity;
		final int line;
		final int column;
		final String message;

		Diagnostic(String inSeverity, int inLine, int inColumn, String inMessage)
		{
			severity = inSeverity;
			line = Math.max(1, inLine);
			column = Math.max(1, inColumn);
			message = inMessage;
		}

		String key()
		{
			return severity + line + ":" + column + message;
		}
	}

	/** Result of rendering one variant. error == null means the output is valid JSON. */
	static final class Outcome
	{
		String output;
		String error;
		int outputLine;
		int outputColumn;
		boolean duplicateKey;
		boolean renderFailed;
		boolean reported;
		int templateLine;
		int templateColumn;
	}

	// ------------------------------------------------------------------ engine

	final File root;
	final File macroLibrary;
	long macroStamp;
	VelocityEngine engine;
	Object jsonUtil;
	boolean dump;
	final JsonFactory jsonFactory = new JsonFactory();

	VelocityJsonCheck(File inRoot)
	{
		root = inRoot;
		macroLibrary = new File(root, "plugins/system/html/display/velocitymacros.vm");
		jsonFactory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
	}

	void ensureEngine() throws Exception
	{
		if (engine != null && macroLibrary.lastModified() == macroStamp)
		{
			return;
		}
		macroStamp = macroLibrary.lastModified();
		// Mirrors org.openedit.generators.VelocityGenerator#init
		Properties p = new Properties();
		p.put("resource.loader", "file");
		p.put("file.resource.loader.path", new File(root, "plugins/system/html").getAbsolutePath() + "," + root.getAbsolutePath());
		p.put("file.resource.loader.cache", "false");
		p.put("runtime.log.invalid.references", "false");
		p.put("velocimacro.permissions.allow.inline", "true");
		p.put("velocimacro.permissions.allow.inline.to.replace.global", "true");
		p.put("velocimacro.context.localscope", "true");
		p.put("velocimacro.library", "display/velocitymacros.vm");
		p.put("userdirective", CheckIfNull.class.getName() + "," + CheckIfNotNull.class.getName());
		p.put("runtime.introspector.uberspect", StubUberspect.class.getName());
		VelocityEngine e = new VelocityEngine();
		e.init(p);
		engine = e;
		if (jsonUtil == null)
		{
			jsonUtil = Class.forName("org.entermediadb.asset.util.JsonUtil").getDeclaredConstructor().newInstance();
		}
	}

	static final int MAX_INCLUDE_DEPTH = 6;

	/** Renders $pages.include(path) with the current context, the way the server would. */
	String include(String inPath, Info inInfo)
	{
		File target = resolveInclude(inPath);
		if (target == null || Run.dirs.size() > MAX_INCLUDE_DEPTH)
		{
			if (inInfo != null && Run.templateName.equals(inInfo.getTemplateName()) && Run.flippedKey == null && Run.base == Mode.NORMAL)
			{
				Run.notes.add(new Diagnostic("warning", inInfo.getLine(), Run.charColumn(inInfo.getLine(), inInfo.getColumn()),
						"Could not resolve $pages.include(\"" + inPath + "\"); checked as if it renders a JSON object ({})."));
			}
			return "{}";
		}
		String rel = root.toPath().relativize(target.toPath()).toString();
		StringWriter out = new StringWriter();
		Run.dirs.push(target.getParentFile());
		try
		{
			engine.getTemplate(rel, "UTF-8").merge(Run.context, out);
		}
		finally
		{
			Run.dirs.pop();
		}
		return out.toString();
	}

	File resolveInclude(String inPath)
	{
		String path = inPath.trim();
		if (path.isEmpty())
		{
			return null;
		}
		List<File> candidates = new ArrayList<>();
		if (!path.startsWith("/"))
		{
			candidates.add(new File(Run.dirs.peek(), path));
		}
		else
		{
			if (Run.pluginHtml != null)
			{
				candidates.add(new File(Run.pluginHtml, path));
			}
			File[] plugins = new File(root, "plugins").listFiles();
			if (plugins != null)
			{
				for (File plugin : plugins)
				{
					candidates.add(new File(plugin, "html" + path));
				}
			}
		}
		for (File f : candidates)
		{
			File found = existing(f, 0);
			if (found != null)
			{
				return found;
			}
		}
		return null;
	}

	/**
	 * The file itself; else the same path under a `fallbackdirectory` declared in an ancestor _site.xconf (the
	 * server's page fallback, e.g. ai/gemini -> ai/default); else, when a path segment came from a stub value (digits),
	 * any directory in that position.
	 */
	File existing(File inFile, int inDepth)
	{
		File f;
		try
		{
			f = inFile.getCanonicalFile();
		}
		catch (IOException ex)
		{
			return null;
		}
		if (!f.toPath().startsWith(root.toPath()))
		{
			return null;
		}
		if (f.isFile())
		{
			return f;
		}
		File plugins = new File(root, "plugins").getAbsoluteFile();
		if (inDepth > 4 || !f.toPath().startsWith(plugins.toPath()))
		{
			return null;
		}
		// plugins/<name>/html/<rest>
		java.nio.file.Path rel = plugins.toPath().relativize(f.toPath());
		if (rel.getNameCount() < 3 || !rel.getName(1).toString().equals("html"))
		{
			return null;
		}
		File html = plugins.toPath().resolve(rel.subpath(0, 2)).toFile();
		for (File dir = f.getParentFile(); dir != null && dir.toPath().startsWith(html.toPath()); dir = dir.getParentFile())
		{
			String fallback = fallbackDirectory(new File(dir, "_site.xconf"));
			if (fallback != null)
			{
				String rest = dir.toPath().relativize(f.toPath()).toString();
				File alt = existing(new File(html, fallback + "/" + rest), inDepth + 1);
				if (alt != null)
				{
					return alt;
				}
			}
		}
		// an all-digits segment came from a stub value: try every directory there
		java.nio.file.Path inner = html.toPath().relativize(f.toPath());
		for (int i = 0; i < inner.getNameCount() - 1; i++)
		{
			if (inner.getName(i).toString().matches("\\d+"))
			{
				File parent = i == 0 ? html : html.toPath().resolve(inner.subpath(0, i)).toFile();
				String tail = inner.subpath(i + 1, inner.getNameCount()).toString();
				File[] dirs = parent.listFiles(File::isDirectory);
				if (dirs != null)
				{
					java.util.Arrays.sort(dirs);
					for (File d : dirs)
					{
						File alt = existing(new File(d, tail), inDepth + 1);
						if (alt != null)
						{
							return alt;
						}
					}
				}
				break;
			}
		}
		return null;
	}

	static final Pattern FALLBACK = Pattern.compile("name=\"fallbackdirectory\"\\s*>\\s*([^<]+)<");

	static String fallbackDirectory(File inXconf)
	{
		if (!inXconf.isFile())
		{
			return null;
		}
		try
		{
			Matcher m = FALLBACK.matcher(Files.readString(inXconf.toPath(), StandardCharsets.UTF_8));
			if (m.find())
			{
				// "/${applicationid}/ai/default" -> "/ai/default" (relative to the plugin's html folder)
				return m.group(1).trim().replaceFirst("^/?\\$\\{applicationid\\}", "");
			}
		}
		catch (IOException ex)
		{
			// unreadable; no fallback
		}
		return null;
	}

	/** Basenames of templates other templates include; their output may be a JSON fragment, not a document. */
	Set<String> includedNames;

	Set<String> includedNames()
	{
		if (includedNames != null)
		{
			return includedNames;
		}
		includedNames = new java.util.HashSet<>();
		Pattern inc = Pattern.compile("include\\(\\s*\"([^\"]+)\"");
		for (String top : new String[] { "plugins", "tools" })
		{
			indexIncludes(new File(root, top), inc);
		}
		return includedNames;
	}

	void indexIncludes(File inDir, Pattern inc)
	{
		if (!inDir.isDirectory())
		{
			return;
		}
		try (java.util.stream.Stream<java.nio.file.Path> all = Files.walk(inDir.toPath()))
		{
			all.filter(f -> {
				String n = f.getFileName().toString();
				return n.endsWith(".json") || n.endsWith(".html") || n.endsWith(".vm");
			}).forEach(f -> {
				try
				{
					Matcher m = inc.matcher(Files.readString(f, StandardCharsets.UTF_8));
					while (m.find())
					{
						String p = m.group(1);
						includedNames.add(p.substring(p.lastIndexOf('/') + 1));
					}
				}
				catch (Exception ex)
				{
					// unreadable or binary; skip
				}
			});
		}
		catch (IOException ex)
		{
			// no index; every file is treated as a full document
		}
	}

	Outcome render(Template inTemplate)
	{
		Outcome o = new Outcome();
		Map<String, Object> real = new HashMap<>();
		real.put("jsonUtil", jsonUtil);
		// "$apphome/services/..." then resolves to plugins/<plugin>/html/services/... in include()
		real.put("apphome", "");
		real.put("applicationid", "");
		StringWriter out = new StringWriter();
		try
		{
			Stub.nextId = 0;
			Run.context = new StubContext(real);
			Run.dirs.clear();
			Run.dirs.push(new File(root, inTemplate.getName()).getParentFile());
			inTemplate.merge(Run.context, out);
		}
		catch (Throwable ex)
		{
			o.renderFailed = true;
			o.error = "Velocity render failed: " + rootMessage(ex);
			o.output = out.toString();
			return o;
		}
		o.output = out.toString();
		validate(o);
		if (o.error != null && !o.duplicateKey && Run.fragmentAllowed && validFragment(o.output))
		{
			o.error = null;
		}
		return o;
	}

	/** An included partial may render `{..},{..},` or `"a":1,"b":2`: valid once wrapped in [ ] or { }. */
	boolean validFragment(String inOutput)
	{
		String body = inOutput.trim().replaceAll("^,+", "").replaceAll(",+$", "").trim();
		if (body.isEmpty())
		{
			return true;
		}
		for (String wrapped : new String[] { "[" + body + "]", "{" + body + "}" })
		{
			Outcome probe = new Outcome();
			probe.output = wrapped;
			validate(probe);
			if (probe.error == null)
			{
				return true;
			}
		}
		return false;
	}

	void validate(Outcome o)
	{
		if (o.output.trim().isEmpty())
		{
			o.error = "output is empty";
			return;
		}
		try (JsonParser parser = jsonFactory.createParser(o.output))
		{
			int depth = 0;
			boolean done = false;
			JsonToken t;
			while ((t = parser.nextToken()) != null)
			{
				if (done)
				{
					JsonLocation loc = parser.getTokenLocation();
					fail(o, "unexpected content after the end of the JSON value", loc);
					return;
				}
				if (t == JsonToken.START_OBJECT || t == JsonToken.START_ARRAY)
				{
					depth++;
				}
				else if (t == JsonToken.END_OBJECT || t == JsonToken.END_ARRAY)
				{
					depth--;
				}
				if (depth == 0)
				{
					done = true;
				}
			}
		}
		catch (JsonParseException ex)
		{
			String msg = ex.getOriginalMessage();
			o.duplicateKey = msg != null && msg.startsWith("Duplicate field");
			fail(o, cleanJacksonMessage(msg), ex.getLocation());
		}
		catch (IOException ex)
		{
			o.error = ex.getMessage();
		}
	}

	static void fail(Outcome o, String inMessage, JsonLocation inLoc)
	{
		o.error = inMessage;
		if (inLoc != null)
		{
			o.outputLine = inLoc.getLineNr();
			o.outputColumn = inLoc.getColumnNr();
		}
	}

	static String cleanJacksonMessage(String inMsg)
	{
		if (inMsg == null)
		{
			return "invalid JSON";
		}
		int cut = inMsg.indexOf('\n');
		String msg = cut > 0 ? inMsg.substring(0, cut) : inMsg;
		return msg.replaceAll(" \\(code \\d+\\)", "");
	}

	static String rootMessage(Throwable ex)
	{
		Throwable t = ex;
		while (t.getCause() != null && t.getCause() != t)
		{
			t = t.getCause();
		}
		String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
		return msg.replace('\n', ' ');
	}

	/** Output line around the failure, e.g. `"goingcount": $event.goingcount,`. */
	static String snippet(Outcome o)
	{
		if (o.output == null || o.outputLine < 1)
		{
			return "";
		}
		String[] lines = o.output.split("\n", -1);
		if (o.outputLine > lines.length)
		{
			return "";
		}
		String line = lines[o.outputLine - 1].trim();
		if (line.length() > 90)
		{
			int col = Math.max(0, Math.min(o.outputColumn - 1 - (lines[o.outputLine - 1].length() - lines[o.outputLine - 1].stripLeading().length()), line.length()));
			int from = Math.max(0, col - 45);
			line = (from > 0 ? "…" : "") + line.substring(from, Math.min(line.length(), from + 90)) + "…";
		}
		return line;
	}

	/** Best-effort map from an output line back to the template line that produced it. */
	static int[] mapToTemplate(Outcome o, String[] inSource)
	{
		if (o.output == null || o.outputLine < 1)
		{
			return new int[] { 1, 1 };
		}
		String[] out = o.output.split("\n", -1);
		Pattern anchorPattern = Pattern.compile("(\"[A-Za-z_][\\w-]{0,40}\"\\s*:)");
		// The failing line, then the lines before it: the first static key found anchors the position
		for (int back = 0; back <= 6 && o.outputLine - 1 - back >= 0; back++)
		{
			String line = out[Math.min(o.outputLine - 1 - back, out.length - 1)];
			Matcher m = anchorPattern.matcher(line);
			String anchor = null;
			while (m.find())
			{
				anchor = m.group(1); // last key on the line is closest to the failure
			}
			if (anchor == null)
			{
				continue;
			}
			for (int i = 0; i < inSource.length; i++)
			{
				int at = inSource[i].indexOf(anchor);
				if (at >= 0)
				{
					// the failure is `back` output lines after the anchor; point at the anchor's template line
					return new int[] { i + 1, at + 1 };
				}
			}
		}
		return new int[] { 1, 1 };
	}

	// ------------------------------------------------------------------ the check

	static final int MAX_RENDERS = 3000;

	List<Diagnostic> check(File inFile) throws Exception
	{
		ensureEngine();
		String rel = root.toPath().toAbsolutePath().normalize().relativize(inFile.toPath().toAbsolutePath().normalize()).toString();
		String source = Files.readString(inFile.toPath(), StandardCharsets.UTF_8);
		String[] lines = source.split("\n", -1);
		Run.reset(rel, lines);
		blamed.clear();
		Run.checker = this;
		Matcher plugin = Pattern.compile("^plugins/[^/]+/html").matcher(rel.replace(File.separatorChar, '/'));
		Run.pluginHtml = plugin.find() ? new File(root, plugin.group()) : null;
		Run.fragmentAllowed = includedNames().contains(inFile.getName());
		Map<String, Diagnostic> found = new LinkedHashMap<>();

		Template template;
		try
		{
			template = engine.getTemplate(rel, "UTF-8");
			wrapComparisons((Node) template.getData());
		}
		catch (ParseErrorException ex)
		{
			add(found, new Diagnostic("error", ex.getLineNumber(), Run.charColumn(ex.getLineNumber(), ex.getColumnNumber()), "Velocity parse error: " + firstLine(ex.getMessage())));
			return new ArrayList<>(found.values());
		}

		int renders = 0;
		// Baselines: everything present (loops of 2), and everything empty/false/absent
		Outcome truthy = variant(template, Mode.NORMAL, null, null);
		if (dump)
		{
			System.err.println("----- " + rel + " (every value present, lists of 2)\n" + truthy.output + "\n-----");
		}
		Outcome falsy = variant(template, Mode.FALSY, null, null);
		renders += 2;
		if (falsy.error != null && falsy.error.equals(truthy.error) && snippet(falsy).equals(snippet(truthy)))
		{
			// Fails the same way whatever the data: the problem is in the static text
			reportBaseline(found, truthy, lines, "for any data");
			truthy.reported = true;
			falsy.reported = true;
		}
		renders += explainBaseline(found, template, truthy, Mode.NORMAL, Mode.FALSY, lines, "is true/present", "every value is present and every list has 2 items");
		renders += explainBaseline(found, template, falsy, Mode.FALSY, Mode.NORMAL, lines, "is false/empty/missing", "every value is empty or false");

		List<Site> sites = new ArrayList<>(Run.sites.values());
		for (Site site : sites)
		{
			if (renders > MAX_RENDERS)
			{
				break;
			}
			boolean branches = site.events.contains(Event.TESTED) || site.events.contains(Event.ITERATED);
			if (branches)
			{
				Outcome o = variant(template, Mode.NORMAL, site.key, Mode.FALSY);
				renders++;
				reportFlip(found, o, truthy, site, "error", "is false/empty/missing");
			}
			if (branches)
			{
				Outcome o = variant(template, Mode.FALSY, site.key, Mode.NORMAL);
				renders++;
				reportFlip(found, o, falsy, site, "error", "is true while everything else is false/empty");
			}
			if (site.events.contains(Event.ITERATED))
			{
				Outcome o = variant(template, Mode.NORMAL, site.key, Mode.ONE);
				renders++;
				reportFlip(found, o, truthy, site, "error", "has exactly one item");
			}
			if (site.events.contains(Event.RENDERED) && truthy.error == null)
			{
				if (site.read)
				{
					Outcome nul = variant(template, Mode.NORMAL, site.key, Mode.NULL);
					renders++;
					reportFlip(found, nul, truthy, site, "warning", "is null (it prints as the literal reference, or as nothing with $!; give it a default)");
				}
				Outcome word = variant(template, Mode.NORMAL, site.key, Mode.WORD);
				renders++;
				if (word.error == null)
				{
					// The value sits inside a JSON string. Does it survive quotes, backslashes and newlines?
					Outcome hostile = variant(template, Mode.NORMAL, site.key, Mode.HOSTILE);
					renders++;
					if (hostile.error != null && !hostile.renderFailed)
					{
						add(found, new Diagnostic("info", site.line, site.column,
								site.describe() + " is inserted into a JSON string without escaping: a value containing \" \\ or a newline produces invalid JSON. Use #jesc(...)."));
					}
				}
			}
		}
		for (Diagnostic d : Run.notes)
		{
			add(found, d);
		}
		return new ArrayList<>(found.values());
	}

	/**
	 * A baseline failed: find the sites whose flip makes the output valid, and blame them. Falls back to mapping
	 * the failing output line back to the template when no single flip helps.
	 */
	int explainBaseline(Map<String, Diagnostic> inFound, Template inTemplate, Outcome inBaseline, Mode inBase, Mode inFlip, String[] inSource,
			String inWhen, String inBaselineDescription)
	{
		if (inBaseline.error == null || inBaseline.reported)
		{
			return 0;
		}
		int renders = 0;
		List<Site> culprits = new ArrayList<>();
		for (Site site : new ArrayList<>(Run.sites.values()))
		{
			if (!site.events.contains(Event.TESTED) && !site.events.contains(Event.ITERATED))
			{
				continue;
			}
			Outcome o = variant(inTemplate, inBase, site.key, inFlip);
			renders++;
			if (o.error == null)
			{
				culprits.add(site);
			}
		}
		String severity = inBaseline.duplicateKey || "output is empty".equals(inBaseline.error) ? "warning" : "error";
		if (inBaseline.renderFailed)
		{
			add(inFound, new Diagnostic("error", 1, 1, inBaseline.error));
		}
		else if (!culprits.isEmpty() && culprits.size() <= 3)
		{
			// Emptying a loop often "fixes" output too; when a condition is among the culprits, blame the condition
			List<Site> conditions = new ArrayList<>();
			for (Site c : culprits)
			{
				if (!c.events.contains(Event.ITERATED))
				{
					conditions.add(c);
				}
			}
			if (!conditions.isEmpty())
			{
				culprits = conditions;
			}
			for (Site c : culprits)
			{
				blamed.add(c.key);
				add(inFound, new Diagnostic(severity, c.line, c.column, "Invalid JSON when " + c.describe() + " " + inWhen + ": " + inBaseline.error + at(inBaseline)));
			}
		}
		else
		{
			reportBaseline(inFound, inBaseline, inSource, "when " + inBaselineDescription);
		}
		inBaseline.reported = true;
		return renders;
	}

	Outcome variant(Template inTemplate, Mode inBase, String inFlipKey, Mode inFlipMode)
	{
		Run.base = inBase;
		Run.flippedKey = inFlipKey;
		Run.flippedMode = inFlipMode;
		return render(inTemplate);
	}

	void reportBaseline(Map<String, Diagnostic> inFound, Outcome o, String[] inSource, String inWhen)
	{
		if (o.error == null)
		{
			return;
		}
		int[] pos = mapToTemplate(o, inSource);
		String severity = o.duplicateKey || "output is empty".equals(o.error) ? "warning" : "error";
		String what = o.renderFailed ? o.error : "Invalid JSON " + inWhen + ": " + o.error + at(o);
		add(inFound, new Diagnostic(severity, pos[0], pos[1], what));
	}

	/** Sites already blamed for an error; a site gets one error, not one per scenario. */
	final Set<String> blamed = new java.util.HashSet<>();

	void reportFlip(Map<String, Diagnostic> inFound, Outcome o, Outcome inBaseline, Site inSite, String inSeverity, String inWhen)
	{
		if (o.error == null || sameFailure(o, inBaseline))
		{
			return;
		}
		if (inSeverity.equals("error") && !blamed.add(inSite.key))
		{
			return;
		}
		String severity = o.duplicateKey || "output is empty".equals(o.error) ? "warning" : inSeverity;
		String what = o.renderFailed ? o.error : "Invalid JSON when " + inSite.describe() + " " + inWhen + ": " + o.error + at(o);
		add(inFound, new Diagnostic(severity, inSite.line, inSite.column, what));
	}

	/** A flip that fails exactly like its (already reported) baseline adds nothing. */
	static boolean sameFailure(Outcome a, Outcome b)
	{
		return b.error != null && a.error.equals(b.error) && snippet(a).equals(snippet(b));
	}

	static String at(Outcome o)
	{
		String s = snippet(o);
		return s.isEmpty() ? "" : " — output: " + s;
	}

	static void add(Map<String, Diagnostic> inFound, Diagnostic d)
	{
		inFound.putIfAbsent(d.key(), d);
	}

	static String firstLine(String inMsg)
	{
		if (inMsg == null)
		{
			return "";
		}
		int cut = inMsg.indexOf('\n');
		return (cut > 0 ? inMsg.substring(0, cut) : inMsg).trim();
	}

	// ------------------------------------------------------------------ output

	static String json(String inFile, List<Diagnostic> inDiags, long inMillis, String inFatal)
	{
		StringBuilder b = new StringBuilder();
		b.append("{\"file\":").append(q(inFile)).append(",\"ms\":").append(inMillis);
		if (inFatal != null)
		{
			b.append(",\"fatal\":").append(q(inFatal));
		}
		b.append(",\"diagnostics\":[");
		for (int i = 0; i < inDiags.size(); i++)
		{
			Diagnostic d = inDiags.get(i);
			if (i > 0)
			{
				b.append(',');
			}
			b.append("{\"severity\":").append(q(d.severity)).append(",\"line\":").append(d.line).append(",\"column\":").append(d.column).append(",\"message\":")
					.append(q(d.message)).append('}');
		}
		return b.append("]}").toString();
	}

	static String q(String s)
	{
		StringBuilder b = new StringBuilder("\"");
		for (char c : s.toCharArray())
		{
			switch (c)
			{
				case '"':
					b.append("\\\"");
					break;
				case '\\':
					b.append("\\\\");
					break;
				case '\n':
					b.append("\\n");
					break;
				case '\r':
					b.append("\\r");
					break;
				case '\t':
					b.append("\\t");
					break;
				default:
					if (c < 0x20)
					{
						b.append(String.format("\\u%04x", (int) c));
					}
					else
					{
						b.append(c);
					}
			}
		}
		return b.append('"').toString();
	}

	public static void main(String[] args) throws Exception
	{
		java.util.logging.LogManager.getLogManager().reset();
		java.util.logging.Logger.getLogger("").setLevel(Level.OFF);

		File root = null;
		boolean asJson = false;
		boolean server = false;
		boolean dump = false;
		List<String> files = new ArrayList<>();
		for (int i = 0; i < args.length; i++)
		{
			switch (args[i])
			{
				case "--root":
					root = new File(args[++i]);
					break;
				case "--json":
					asJson = true;
					break;
				case "--server":
					server = true;
					break;
				case "--dump":
					dump = true;
					break;
				default:
					files.add(args[i]);
			}
		}
		if (root == null || !new File(root, "plugins/system/lib").isDirectory())
		{
			System.err.println("--root must point at the eme-server checkout");
			System.exit(2);
		}
		// Velocity writes to stdout in a few corner cases; keep stdout for our protocol only
		PrintStream out = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8);
		System.setOut(new PrintStream(java.io.OutputStream.nullOutputStream()));

		VelocityJsonCheck checker = new VelocityJsonCheck(root.getAbsoluteFile());
		checker.dump = dump;
		if (server)
		{
			out.println("{\"ready\":true}");
			BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
			String line;
			while ((line = in.readLine()) != null)
			{
				line = line.trim();
				if (!line.isEmpty())
				{
					out.println(checkOne(checker, line));
				}
			}
			return;
		}

		int errors = 0;
		int warnings = 0;
		int infos = 0;
		for (String f : files)
		{
			long start = System.currentTimeMillis();
			List<Diagnostic> diags;
			try
			{
				diags = checker.check(new File(f));
			}
			catch (Throwable ex)
			{
				diags = Collections.singletonList(new Diagnostic("error", 1, 1, "checker failed: " + rootMessage(ex)));
			}
			if (asJson)
			{
				out.println(json(f, diags, System.currentTimeMillis() - start, null));
			}
			for (Diagnostic d : diags)
			{
				if (d.severity.equals("error"))
				{
					errors++;
				}
				else if (d.severity.equals("warning"))
				{
					warnings++;
				}
				else
				{
					infos++;
				}
				if (!asJson)
				{
					out.println(display(root, f) + ":" + d.line + ":" + d.column + ": " + d.severity + ": " + d.message);
				}
			}
		}
		if (!asJson)
		{
			out.println("checked " + files.size() + " file(s): " + errors + " error(s), " + warnings + " warning(s), " + infos + " unescaped-value hint(s)");
		}
		System.exit(errors > 0 ? 1 : 0);
	}

	static String display(File inRoot, String inPath)
	{
		java.nio.file.Path r = inRoot.toPath().toAbsolutePath().normalize();
		java.nio.file.Path f = new File(inPath).toPath().toAbsolutePath().normalize();
		return f.startsWith(r) ? r.relativize(f).toString() : inPath;
	}

	static String checkOne(VelocityJsonCheck inChecker, String inPath)
	{
		long start = System.currentTimeMillis();
		try
		{
			List<Diagnostic> diags = inChecker.check(new File(inPath));
			return json(inPath, diags, System.currentTimeMillis() - start, null);
		}
		catch (Throwable ex)
		{
			return json(inPath, Collections.emptyList(), System.currentTimeMillis() - start, rootMessage(ex));
		}
	}
}

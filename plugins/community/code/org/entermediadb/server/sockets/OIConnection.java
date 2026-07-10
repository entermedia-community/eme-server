package org.entermediadb.server.sockets;

import java.io.StringReader;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.entermediadb.asset.MediaArchive;
import org.entermediadb.events.PathEventManager;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.openedit.ModuleManager;
import org.openedit.WebPageRequest;
import org.openedit.data.SearcherManager;
import org.openedit.data.ValuesMap;
import org.openedit.event.WebEvent;
import org.openedit.page.manage.PageManager;
import org.openedit.servlet.OpenEditEngine;
import org.openedit.users.User;
import org.openedit.users.UserManager;

public class OIConnection extends Endpoint implements MessageHandler.Partial<String>
{
	private static final Log log = LogFactory.getLog(OIConnection.class);
	private RemoteEndpoint.Basic remoteEndpointBasic;
	protected JSONParser fieldJSONParser;
	protected ModuleManager fieldModuleManager;
	protected SearcherManager fieldSearcherManager;
	protected String fieldCurrentConnectionId;
	protected OIServer fieldOiServer;
	protected String fieldSessionID;
	protected String fieldUserId;
	protected String fieldCatalogId;

	public User getUser()
	{
		return fieldUser;
	}

	public void setUser(User inUser)
	{
		fieldUser = inUser;
	}

	protected Date fieldConnectionTime;
	protected User fieldUser;
	protected UserManager fieldUserManager;
	protected PageManager fieldPageManager;
	protected Date fieldLastConnection;
	protected ValuesMap fieldValues;

	public Map getProperties()
	{
		if (fieldValues == null)
		{
			fieldValues = new ValuesMap();
		}
		return fieldValues;
	}

	public void setProperties(ValuesMap inProperties)
	{
		fieldValues = inProperties;
	}

	public Date getLastConnection()
	{
		return fieldLastConnection;
	}

	public void setLastConnection(Date inLastConnection)
	{
		fieldLastConnection = inLastConnection;
	}

	protected OpenEditEngine fieldEngine;

	public OpenEditEngine getEngine()
	{
		if (fieldEngine == null)
		{
			fieldEngine = (OpenEditEngine) getModuleManager().getBean("OpenEditEngine");

		}

		return fieldEngine;
	}

	public PageManager getPageManager()
	{
		if (fieldPageManager == null)
		{
			fieldPageManager = (PageManager) getModuleManager().getBean("pageManager");

		}

		return fieldPageManager;
	}

	public void setPageManager(PageManager inPageManager)
	{
		fieldPageManager = inPageManager;
	}

	public UserManager getUserManager(String inCatalogId)
	{
		if (fieldUserManager == null)
		{
			fieldUserManager = (UserManager) getModuleManager().getBean(inCatalogId, "userManager");

		}

		return fieldUserManager;

	}

	public void setUserManager(UserManager inUserManager)
	{
		fieldUserManager = inUserManager;
	}

	public String getUserId()
	{
		return fieldUserId;
	}

	public void setUserId(String inUserId)
	{
		fieldUserId = inUserId;
	}

	public OIServer getOiServer()
	{
		if (fieldOiServer == null)
		{
			fieldOiServer = (OIServer) getModuleManager().getBean("system", "oiServer");
		}

		return fieldOiServer;
	}

	public void setChatServer(OIServer fieldChatServer)
	{
		this.fieldOiServer = fieldChatServer;
	}

	public SearcherManager getSearcherManager()
	{
		if (fieldSearcherManager == null)
		{
			fieldSearcherManager = (SearcherManager) getModuleManager().getBean("searcherManager");
		}
		return fieldSearcherManager;
	}

	public void setSearcherManager(SearcherManager inSearcherManager)
	{
		fieldSearcherManager = inSearcherManager;
	}

	public ModuleManager getModuleManager()
	{
		return fieldModuleManager;
	}

	public void setModuleManager(ModuleManager inModuleManager)
	{
		fieldModuleManager = inModuleManager;
	}

	protected StringBuffer fieldBufferedMessage;

	@Override
	public void onError(Session session, Throwable throwable)
	{
		// TODO Auto-generated method stub
		super.onError(session, throwable);
	}

	@Override
	public void onClose(Session session, CloseReason closeReason)
	{

		getOiServer().removeConnection(this);
		if (fieldCatalogId != null)
		{
			MediaArchive archive = (MediaArchive) getModuleManager().getBean(fieldCatalogId, "mediaArchive");

			User user = archive.getUser(fieldUserId);
			archive.fireGeneralEvent(user, "oiconnection", "disconnected", new HashMap());

		}
		log.info("User " + getUserId() + " Disconnected Session" + getSessionId());
		super.onClose(session, closeReason);

	}

	@Override
	public void onOpen(Session session, EndpointConfig endpointConfig)
	{
		javax.servlet.http.HttpSession http = (javax.servlet.http.HttpSession) session.getUserProperties().get("javax.servlet.http.HttpSession");

		fieldConnectionTime = new Date();

		String query = session.getQueryString();
		Map params = getQueryMap(query);

		fieldSessionID = (String) params.get("sessionid");
		fieldCatalogId = (String) params.get("catalogid");
		ModuleManager modulemanager = (ModuleManager) session.getUserProperties().get("moduleManager");
		if (modulemanager == null)
		{
			throw new RuntimeException("modulemanager did not get set, Web site must be accessed with a session");
		}

		setModuleManager(modulemanager);

		log.info(session.getId());
		Map props = endpointConfig.getUserProperties();
		User user = getUser();

		if (user == null)
		{
			String entermediakey = (String) params.get("entermedia.key");
			log.info("Entermedia key was " + entermediakey);
			String catalogtotry = fieldCatalogId;
			if (catalogtotry == null)
			{
				catalogtotry = "system";
			}
			if (entermediakey != null)
			{
				String account = entermediakey.substring(0, entermediakey.indexOf("md5"));
				log.info("Got here - testing more");
				MediaArchive archive = (MediaArchive) getModuleManager().getBean(catalogtotry, "mediaArchive");
				User target = archive.getUser(account);
				if (target != null)
				{
					log.info("Got here -found a user");

					String emkey = archive.getUserManager().getEnterMediaKey(target);
					if (entermediakey.equals(emkey))
					{
						log.info("Got here -found using " + target);

						user = target;
					}
				}
			}
		}

		if (user != null)
		{
			setUserId(user.getId());
			setUser(user);
		}

		remoteEndpointBasic = session.getBasicRemote();

		session.addMessageHandler(this);
		getOiServer().addConnection(this);

		// session.addMessageHandler(new EchoMessageHandlerBinary(remoteEndpointBasic));

	}

	public String getSessionId()
	{
		return fieldSessionID;
	}

	public void setSessionId(String inSessionID)
	{
		fieldSessionID = inSessionID;
	}

	public JSONParser getJSONParser()
	{
		if (fieldJSONParser == null)
		{
			fieldJSONParser = new JSONParser();
		}
		return fieldJSONParser;
	}

	protected StringBuffer getBufferedMessage()
	{
		if (fieldBufferedMessage == null)
		{
			fieldBufferedMessage = new StringBuffer();
		}

		return fieldBufferedMessage;
	}

	@Override
	public synchronized void onMessage(String inData, boolean completed)
	{

		getBufferedMessage().append(inData);
		if (!completed)
		{
			return;
		}

		String message = getBufferedMessage().toString();
		if ("keepalive".equals(message) || message.length() == 0 || message.contains("keepalive"))
		{
			setLastConnection(new Date());
			JSONObject ok = new JSONObject();
			ok.put("status", "ok");
			sendMessage(ok);
			fieldBufferedMessage = null;

			return;
		}
		fieldBufferedMessage = null;

		try
		{
			if (inData.length() == 0)
			{
				return;
			}
			JSONObject map = (JSONObject) getJSONParser().parse(new StringReader(message));
			String catalogId = (String) map.get("catalogid");
			PathEventManager manager = (PathEventManager) getModuleManager().getBean(catalogId, "pathEventManager");

			String runpath = (String) map.get("runpath");
			if (runpath != null)
			{

				String username = getUserId();
				User user = (User) getUserManager(catalogId).getUser(username);

				WebPageRequest request = manager.getRequestUtils().createPageRequest(runpath, user);
				for (Iterator iterator = map.keySet().iterator(); iterator.hasNext();)
				{
					String key = (String) iterator.next();
					Object value = map.get(key);
					if (value instanceof String)
					{
						request.setRequestParameter(key, (String) value);
					}
					else
						if (value instanceof Long)
						{
							request.setRequestParameter(key, Long.toString((Long) value));
						}
						else
							if (value instanceof Boolean)
							{
								request.setRequestParameter(key, Boolean.toString((Boolean) value));
							}
							else
								if (value instanceof Integer)
								{
									request.setRequestParameter(key, Integer.toString((Integer) value));
								}
					request.putPageValue(key, value);
				}
				request.putPageValue("websocket", this);
				WebEvent event = new WebEvent();
				// dummy event;
				request.putPageValue("webevent", event);
				request.putPageValue("json", map);
				getEngine().executePageActions(request);
				getEngine().executePathActions(request);
			}

			String catalogid = (String) map.get("catalogid");
			if (catalogid != null)
			{
				for (Iterator iterator = map.keySet().iterator(); iterator.hasNext();)
				{
					String key = (String) iterator.next();
					Object value = map.get(key);
					getProperties().put(key, value);
				}

			}

		}
		catch (Exception e)
		{
			log.error("Could not parse: ", e);
		}
		fieldBufferedMessage = null;

	}

	public void sendMessage(JSONObject json)
	{
		try
		{
			remoteEndpointBasic.sendText(json.toJSONString());
		}
		catch (Exception e)
		{
			log.error(e);
			// throw new OpenEditException(e);
		}
	}

	public RemoteEndpoint.Basic getRemoteEndpointBasic()
	{
		return remoteEndpointBasic;

	}

	private Map<String, String> getQueryMap(String query)
	{
		if (query != null)
		{
			String[] params = query.split("&");
			Map<String, String> map = new HashMap<String, String>();
			for (String param : params)
			{
				String name = param.split("=")[0];
				String value = param.split("=")[1];
				map.put(name, value);
				return map;
			}

		}

		return new <String, String>HashMap();
	}

	// public
	// String data = "Ping";
	// ByteBuffer payload = ByteBuffer.wrap(data.getBytes());
	// newUserConnection.getUserSession().getBasicRemote().sendPing(payload);

}

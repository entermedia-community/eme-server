/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.entermediadb.server.sockets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.openedit.ModuleManager;
import org.openedit.cache.CacheManager;
import org.openedit.data.SearcherManager;
import org.openedit.util.ExecutorManager;

public class OIServer
{

	private static final Log log = LogFactory.getLog(OIServer.class);

	private Set<OIConnection> connections = new CopyOnWriteArraySet<OIConnection>();
	private HashMap<String, ArrayList> fieldConnectionMap;

	public HashMap<String, ArrayList> getConnectionMap()
	{
		if (fieldConnectionMap == null)
		{
			fieldConnectionMap = new HashMap();

		}

		return fieldConnectionMap;
	}

	public void setConnectionMap(HashMap inConnectionMap)
	{
		fieldConnectionMap = inConnectionMap;
	}

	private static final String CACHENAME = "OIServer";

	protected CacheManager fieldCacheManager;
	protected ModuleManager fieldModuleManager;
	protected SearcherManager fieldSearcherManager;
	protected JSONParser fieldJSONParser;

	public JSONParser getJSONParser()
	{
		if (fieldJSONParser == null)
		{
			fieldJSONParser = new JSONParser();
		}
		return fieldJSONParser;
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

	public CacheManager getCacheManager()
	{
		if (fieldCacheManager == null)
		{
			fieldCacheManager = (CacheManager) getModuleManager().getBean("cacheManager");// new CacheManager();
		}

		return fieldCacheManager;
	}

	public void setCacheManager(CacheManager inCacheManager)
	{
		fieldCacheManager = inCacheManager;
	}

	public void removeConnection(OIConnection inBenchConnection)
	{
		for (Iterator iterator = connections.iterator(); iterator.hasNext();)
		{
			OIConnection benchConnection = (OIConnection) iterator.next();

			if (inBenchConnection == benchConnection)
			{
				connections.remove(benchConnection);
				if (inBenchConnection.getUserId() != null)
				{
					ArrayList connections = getConnectionMap().get(inBenchConnection.getUserId());
					connections.remove(benchConnection);
					if (connections.size() == 0)
					{
						getConnectionMap().remove(inBenchConnection.getUserId());// This might not be necessary
					}
				}

				break;
			}
		}
	}

	public void addConnection(OIConnection inConnection)
	{
		log.info("starting to add connnection");
		String sessionid = inConnection.getSessionId();
		ArrayList toremove = new ArrayList();
		for (Iterator iterator = connections.iterator(); iterator.hasNext();)
		{
			OIConnection benchConnection = (OIConnection) iterator.next();
			String oldid = benchConnection.getSessionId();
			if (oldid != null && oldid.equals(sessionid))
			{
				toremove.add(benchConnection);
				log.info("removing" + benchConnection.getSessionId() + " USer was " + benchConnection.getUserId());
			}

		}
		if (inConnection.getUserId() != null)
		{
			// log.info("added to online user list");
			ArrayList connections = getConnectionMap().get(inConnection.getUserId());
			if (connections == null)
			{
				connections = new ArrayList();
				getConnectionMap().put(inConnection.getUserId(), connections);

			}
			connections.add(inConnection);

		}

		for (Iterator iterator = toremove.iterator(); iterator.hasNext();)
		{
			OIConnection connection = (OIConnection) iterator.next();
			removeConnection(connection);
		}

		log.info("Total User Connections: " + getConnectedUsers().size());
		log.info("Total User Connections: " + connections.size());

		// TODO Auto-generated method stub
		log.info("adding " + inConnection.getUserId() + ":" + inConnection.getSessionId());
		connections.add(inConnection);

	}

	public int getTotalSessionsByUser()
	{

		return getConnectedUsers().size();
	}

	public List getConnectedUsers()
	{
		ArrayList users = new ArrayList();

		for (Iterator iterator = connections.iterator(); iterator.hasNext();)
		{
			OIConnection connection = (OIConnection) iterator.next();
			if (connection.getUserId() != null && !users.contains(connection.getUserId()))
			{
				users.add(connection.getUserId());
			}
		}
		return users;
	}

	public boolean isUserConnected(String inId)
	{
		ArrayList connections = getConnectionMap().get(inId);
		if (connections != null && connections.size() > 0)
		{
			return true;
		}
		return false;

	}

	public void broadcastMessage(JSONObject inMap)
	{
		log.info("Sending " + inMap.toJSONString() + " to " + connections.size() + "Clients");
		for (Iterator iterator = connections.iterator(); iterator.hasNext();)
		{
			OIConnection benchConnection = (OIConnection) iterator.next();
			benchConnection.sendMessage(inMap);

		}

	}

	public ExecutorManager getExecutorManager(String inCatalogId)
	{
		ExecutorManager queue = (ExecutorManager) getModuleManager().getBean(inCatalogId, "executorManager");
		return queue;
	}

	public void broadcastMessage(List<String> inUsers, JSONObject inCommand)
	{

		for (Iterator iterator = connections.iterator(); iterator.hasNext();)
		{
			OIConnection benchConnection = (OIConnection) iterator.next();
			if (inUsers.contains(benchConnection.getUserId()))
			{
				benchConnection.sendMessage(inCommand);
			}
		}
	}

	public void broadcastToConnections(List<OIConnection> inUsers, JSONObject inCommand)
	{

		for (Iterator iterator = connections.iterator(); iterator.hasNext();)
		{
			OIConnection benchConnection = (OIConnection) iterator.next();
			benchConnection.sendMessage(inCommand);

		}
	}

	public void sendMessage(String inUserId, JSONObject inCommand)
	{
		sendMessage(inUserId, inCommand, true);
	}

	public void sendMessage(String inUserId, JSONObject inCommand, boolean allowduplicates)
	{
		ArrayList userconnections = (ArrayList) getConnectionMap().get(inUserId);
		if (userconnections != null)
		{
			for (Iterator iterator = userconnections.iterator(); iterator.hasNext();)
			{
				OIConnection connection = (OIConnection) iterator.next();
				if (connection != null)
				{
					connection.sendMessage(inCommand);
				}
			}
		}
	}

}

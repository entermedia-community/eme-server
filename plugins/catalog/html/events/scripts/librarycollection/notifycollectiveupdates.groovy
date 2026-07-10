package librarycollection;

import org.apache.commons.collections.IteratorUtils
import org.entermediadb.asset.MediaArchive
import org.entermediadb.email.WebEmail
import org.openedit.Data
import org.openedit.MultiValued
import org.openedit.data.Searcher
import org.openedit.hittracker.HitTracker
import org.openedit.users.User
import org.openedit.util.DateStorageUtil
import org.openedit.util.URLUtilities

import groovy.util.logging.Log


//Chat Notifications

public void init()
{
	MediaArchive mediaArchive = (MediaArchive)context.getPageValue("mediaarchive");
	String	appid =  mediaArchive.getCatalogSettingValue("events_notify_collective_app");
	
	Data notificationsent = mediaArchive.getCatalogSetting("collectives_notification_lastsent");
	if( notificationsent == null)
	{
		notificationsent = mediaArchive.getSearcher("catalogsettings").createNewData();
		notificationsent.setId("collectives_notification_lastsent");
		notificationsent.setName("Projects Notification Last Sent");
		notificationsent.setValue("value", DateStorageUtil.getStorageUtil().addDaysToDate(new Date(), -2));
	}
	
	String datestrinng = notificationsent.get("value");
	
	//datestrinng= "2022-07-03T10:38:13.099Z";
	
	Date since = DateStorageUtil.getStorageUtil().parseFromStorage(datestrinng);
	Date started = new Date();
	
	//--Things to notify
	
	Map<String, Map<String, List>> collectionsupdated = new HashMap();
	
	//--Tickets
	HitTracker alltickets = mediaArchive.query("projectgoal").after("emrecordstatus.recordmodificationdate", since).sort("projectstatus").sort("creationdateDown").search();
	getUpdatedRows(collectionsupdated, alltickets, "tickets");

	//-- Goals
	HitTracker alltasks = mediaArchive.query("goaltask").after("emrecordstatus.recordmodificationdate", since).search();
	getUpdatedRows(collectionsupdated, alltasks, "tasks");

	//Expenses
	HitTracker allexpenses = mediaArchive.query("collectiveexpense").after("emrecordstatus.recordmodificationdate", since).search();
	getUpdatedRows(collectionsupdated, allexpenses, "expenses");
	
	//Posts
	HitTracker allposts = mediaArchive.query("userupload").after("emrecordstatus.recordmodificationdate", since).search();
	getUpdatedRows(collectionsupdated, allposts, "posts");
	
	//log.info(collectionsupdated);
	
	
	
	//Notify users on collections
	//ontheteam == true
	//prfile.sendchatnotifications == true
	Map<String, List> usernotifications = new HashMap();
	for (String collectionid in collectionsupdated.keySet()) {
		
		Collection users = mediaArchive.query("librarycollectionusers").exact("collectionid", collectionid).exact("ontheteam", "true").search();
		
		if(!users.isEmpty()) {
			for(Data auser in users)
			{
				String userid = auser.get("followeruser");
				//if(!userwhochecked.contains(userid + "_" + chattopicid))
				//{
					Data profile = mediaArchive.getCachedData("userprofile", userid);
					
					//make it not false?
					if(profile != null && profile.getBoolean("sendchatnotifications") == true)
					{
						log.info("Chat Notification disabled " + userid);
						continue;
					}
					
					//Users to Notify Only per Collection
					List  notifications = usernotifications.get(userid);
					if( notifications == null)
					{
						notifications = new ArrayList();
					}
					notifications.add(collectionid)
					usernotifications.put(userid, notifications);
				//}
			}
			
		}
		
	}	
		

	
	
	/*SEND EMAIL*/
	
	String template = "/" + appid + "/theme/emails/collective-updates.html";
	//Loop over the remaining topics
	try
	{
		for (String notifyuserid in usernotifications.keySet())
		{
			List collections = usernotifications.get(notifyuserid);
			
			User followeruser = mediaArchive.getUser(notifyuserid);
			if (followeruser == null || followeruser.getEmail() == null) 
			{
				log.error("Invalid User or no email address " + notifyuserid);
				continue;
			}
			
			Map<String, Map<String, List>> usercollectionsupdated = new HashMap();
				
			WebEmail templatemail = mediaArchive.createSystemEmail(followeruser, template);
			String notificationsprefix = mediaArchive.getCatalogSettingValue("notificationsprefix");
			if (notificationsprefix == null)
			{
				notificationsprefix = "[OI]";
			}
			if( collections.size() > 1)
			{
				templatemail.setSubject(notificationsprefix + " " + collections.size() + " User Notifications"); //TODO: Translate
				for(String usercollectionid in collections) {
					usercollectionsupdated.put(usercollectionid, collectionsupdated.get(usercollectionid));
				}
			}
			else
			{
				String oneitem = collections.iterator().next();
				Data collection = mediaArchive.getCachedData("librarycollection", oneitem);
				templatemail.setSubject(notificationsprefix + " " + collection.getName() + " Notification"); //TODO: Translate
				
				usercollectionsupdated.put(oneitem, collectionsupdated.get(oneitem));
			}
			
			Map objects = new HashMap();
			objects.put("usernotifications",usernotifications);
			objects.put("collectionsupdated",usercollectionsupdated);
			objects.put("followeruser",followeruser);
			objects.put("applink","/" + appid);
			objects.put("mediaarchive",mediaArchive);
			objects.put("messagessince",since);
			objects.put("siteroot", getSiteRoot());
			String dates = DateStorageUtil.getStorageUtil().formatDateObj(since, "dd-MM-YYYY");
			objects.put("since", dates);
			
			templatemail.send(objects);
			
			log.info("Chat Notified " + followeruser.getEmail() + " " + templatemail.getSubject() + " Site: " + getSiteRoot());
		}
	} 
	catch (Exception ex)
	{
		log.error("Could not process " ,ex);
	}	
	
	notificationsent.setValue("value", started);
	mediaArchive.saveData("catalogsettings",notificationsent);

}


private void getUpdatedRows(Map<String, Map<String, List>> collectionsupdated, HitTracker events, String table) {
	MediaArchive mediaArchive = (MediaArchive)context.getPageValue("mediaarchive");
	String	appid =  mediaArchive.getCatalogSettingValue("events_notify_collective_app");
	
	for (Data row in events)
		{
			String collectionid = row.get("collectionid");
			
			if(collectionid == null) {
				collectionid = row.get("librarycollection");
			}	
			
			if(collectionid == null) {
				continue;
			}
			//Save Collections to Notify
			Map<String, Map<String, List>>  notifications = collectionsupdated.get(collectionid);
			if( notifications == null)
			{
				notifications = new HashMap();
				//New collection save collection info too
				Data collection = mediaArchive.getCachedData("librarycollection", collectionid);
				if(collection) {
					String collection_url = getSiteRoot() + "/" + appid + "/collective/channel/"+ collection.getId() +"/"+ URLUtilities.dash(collection.getName()) + ".html?collectionid="+collection.getId();
					collection.setValue("finalurl", collection_url);
					
					notifications.put("collection", collection);
				}
			}
			
			//
			Boolean included = false;
			if(table.equals("tasks")) {
				//check if the ticket already listed
				String ticketid = row.getValue("projectgoal");
				Map<String, Map<String, List>> tickets = notifications.get("tickets");
				if( tickets != null && ticketid in tickets.keySet())
				{			
						Map<String, List> theticket = tickets.get(ticketid);
						Map<String, List> thetasks = theticket.get("tasks");
						if(thetasks == null) {
							thetasks = new HashMap();
						}
						thetasks.put(row.getId(), row);
						theticket.put("tasks", thetasks);
						tickets.put(ticketid, theticket);
						notifications.put("tickets", tickets);
						collectionsupdated.put(collectionid, notifications);
						included = true;
				} 
				else {
						//add ticket to task??
						Data ticket = mediaArchive.getCachedData("projectgoal", ticketid);
						if (ticket) {
							Map<String, Map<String, List>> collectionitems = notifications.get(table);
							if (collectionitems == null) {
								collectionitems = new HashMap();
							}
							Map<String, List> rowitem = new HashMap();
							rowitem.put("data", row);
							rowitem.put("ticket", ticket);
							collectionitems.put(row.getId(), rowitem);
							notifications.put(table, collectionitems);
							collectionsupdated.put(collectionid, notifications);
							included = true;
						}
				}
			}
			if(!included) {
				Map<String, Map<String, List>> collectionitems = notifications.get(table);
				if (collectionitems == null) {
					collectionitems = new HashMap();
				}
				Map<String, List> rowitem = new HashMap();
				rowitem.put("data", row);
				collectionitems.put(row.getId(), rowitem);
				notifications.put(table, collectionitems);
				collectionsupdated.put(collectionid, notifications);
			}
		}
	return;
}


private String getSiteRoot() {
	MediaArchive mediaArchive = context.getPageValue("mediaarchive");
	String site = mediaArchive.getCatalogSettingValue("siteroot");
	if (!site) {
		site = mediaArchive.getCatalogSettingValue("cdn_prefix");
	}
	return site;
}



init();


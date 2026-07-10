package librarycollection;

import org.entermediadb.asset.MediaArchive
import org.entermediadb.email.WebEmail
import org.openedit.Data
import org.openedit.hittracker.HitTracker
import org.openedit.users.User
import org.openedit.util.DateStorageUtil


public void init()
{
	MediaArchive mediaArchive = (MediaArchive)context.getPageValue("mediaarchive");
	Data notificationsent = mediaArchive.getCatalogSetting("collection_chat_notification_lastsent");
	if( notificationsent == null)
	{
		notificationsent = mediaArchive.getSearcher("catalogsettings").createNewData();
		notificationsent.setId("collection_chat_notification_lastsent");
		notificationsent.setName("Chat Notification Last Sent");
		notificationsent.setValue("value", DateStorageUtil.getStorageUtil().addDaysToDate(new Date(), -2));
	}
	String datestrinng = notificationsent.get("value");
	Date since = DateStorageUtil.getStorageUtil().parseFromStorage(datestrinng);
	Date started = new Date();
	
	//default
	String	appid =  mediaArchive.getCatalogSettingValue("events_notify_collective_app");
	String template = "/" + appid + "/theme/emails/collective-update-event.html";
	
	
	//Get Communities
	HitTracker communities = mediaArchive.query("communitytag").search();
	for (Data community in communities) 
	{
		//get Projects in this communiy
		HitTracker collections = mediaArchive.query("librarycollection").exact("communitytag", community.getId()).search();
		for (Data collection in collections) 
		{
			//GET community app from this
			String siteid = context.findValue("siteid");
			String communitypath = community.get("templatepath");
			appid = communitypath;
			template = siteid + appid + "/theme/emails/community-chats-notifications.html";

			//First get all the topics on each community
			HitTracker alltopicsmodified = mediaArchive.query("chattopiclastmodified").exact("collectionid", collection.getId()).after("datemodified", since).search();
			Collection topicsmod = alltopicsmodified.collectValues("chattopicid");
		
			//get the last checked
			Collection alltopicschecked = mediaArchive.query("chattopiclastchecked").exact("collectionid", collection.getId()).orgroup("chattopicid", topicsmod).after("datechecked", since).search();
		
			Set userwhochecked = new HashSet();
			for (Data topiccheck in alltopicschecked) 
			{
				String userid = topiccheck.get("userid");
				String chattopicid = topiccheck.get("chattopicid");
				userwhochecked.add(userid + "_" + chattopicid);
			}
			
			Map<String,List> usertopics = new HashMap();
			
			if(alltopicsmodified.size() > 0) {
				
				
				for (Data topicmod in alltopicsmodified)
				{
					String collectionid = topicmod.get("collectionid");
					String chattopicid = topicmod.get("chattopicid");
					
					Data project = mediaArchive.getCachedData("librarycollection", collectionid);
					
					log.info("New chats found at: " + project);
					
					Collection users = mediaArchive.query("librarycollectionusers").exact("collectionid", collectionid).exact("ontheteam", "true").search();
					for(Data auser in users)
					{
						String userid = auser.get("followeruser");
						if(!userwhochecked.contains(userid + "_" + chattopicid))
						{
							Data profile = mediaArchive.getData("userprofile", userid);
							
							//make it not false?
							if(profile != null && profile.getBoolean("sendchatnotifications") == true)
							{
								log.info("Chat Notification disabled " + userid);
								continue;
							}
							//Notify them of what they missed only
							List topics = usertopics.get(userid);
							if( topics == null)
							{
								topics = new ArrayList();
							}
							topics.add(topicmod);
							usertopics.put(userid, topics);
						}
					}
				}
			}
			
			
			//Loop over the remaining topics
			try
			{
				for (String useerid in usertopics.keySet())
				{
					List topicmods = usertopics.get(useerid);
					User followeruser = mediaArchive.getUser(useerid);
					if (followeruser == null || followeruser.getEmail() == null) 
					{
						log.error("Invalid User or no email address " + useerid);
						continue;
					}
						
					WebEmail templatemail = mediaArchive.createSystemEmail(followeruser, template);
					String subject = community.getName() + " Project Notifications";
					String now_string = DateStorageUtil.getStorageUtil().getTodayForDisplay();
					
					if( topicmods.size() > 1)
					{
						subject = "[" + community.getName() + "] " +now_string+" Multiple Project Notifications (" + topicmods.size() + ")"; //TODO: Translate
					}
					else
					{
						Data oneitem = topicmods.iterator().next();
						Data topic = mediaArchive.getCachedData("collectiveproject", oneitem.get("chattopicid") );
						
						
						subject = "[" + collection.getName() + "] " + now_string + " Chat Notifications"  ;
						
						templatemail.setFromName(collection.getName());
					}
					
					templatemail.setSubject(subject); //TODO: Translate
					Map objects = new HashMap();
					objects.put("topicmods",topicmods);
					objects.put("followeruser",followeruser);
					objects.put("mediaarchive",mediaArchive);
					objects.put("messagessince",since);
					objects.put("community",community);
			
					objects.put("siteid", siteid);
					objects.put("applink","/" + appid);
					//objects.put("siteroot",getSiteRoot());
					
					templatemail.send(objects);
					//log.info("Email to: " + followeruser.getEmail() + " Subject: " + subject + " Template: "+template);
				
					log.info("Chat notifications sent to: " + followeruser.getEmail());
				}
			} 
			catch (Exception ex)
			{
				log.error("Could not process " ,ex);
			}	
			
			
		}
	}
	//Save notification last sent date
	notificationsent.setValue("value", started);
	mediaArchive.saveData("catalogsettings",notificationsent);
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


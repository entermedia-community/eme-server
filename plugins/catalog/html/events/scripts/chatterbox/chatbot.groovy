package chatterbox;

import org.apache.commons.collections.IteratorUtils
import org.entermediadb.asset.MediaArchive
import org.entermediadb.email.WebEmail
import org.openedit.Data
import org.openedit.MultiValued
import org.openedit.data.Searcher
import org.openedit.hittracker.HitTracker
import org.openedit.users.User
import org.openedit.util.DateStorageUtil


//Chat Notifications

public void init()
{
	MediaArchive mediaArchive = (MediaArchive)context.getPageValue("mediaarchive");
	
	Integer minutes = 10;
	
	Date since = DateStorageUtil.getStorageUtil().subtractFromNow(minutes*60000);

	//First get all the topics
	HitTracker alltopicsmodified = mediaArchive.query("chattopiclastmodified").sort("datemodified").search();
	for (Data topicmod in alltopicsmodified)
	{
		String userid = topicmod.get("userid");
		String collectionid = topicmod.get("collectionid");
		//user is Agent?
		//Data clientid = mediaArchive.query("librarycollectionusers").exact("userid", userid).exact("collectionid", collectionid).exact("ontheteam", "true").searchOne();
		Data isagent = mediaArchive.query("agentlist").exact("userid", userid).searchOne();
		if (isagent != null) {
			//do nothing for now
			continue;
		}
		else {
			//it is a client typing, now search if topic is alert-enabled
			String topicid = topicmod.get("chattopicid");
			Data topic = mediaArchive.query("collectiveproject").exact("id", topicid).exact("autoresponse", "true").searchOne();
			if(topic!=null) {
				//Need to be alerted
				log.info("Old chat without response from "+userid+" at "+topicid);
				//Create a ChatBot User?
				//Send message with this user  -> emsupport
				//Hello, we will review your message ASAP. Agents got alerted about your request.
				
			}
			
		}
	}
	
	
	//Collection topicsmod = alltopicsmodified.collectValues("chattopicid"); //more than one?
	
	//what to check first, user or topic?
	
	
	//HitTracker topics = mediaArchive.query("collectiveproject").orgroup("chattopicid", topicsmod).search();
	
	
	
}

init();
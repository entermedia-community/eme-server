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


public void init()
{
	MediaArchive mediaArchive = (MediaArchive)context.getPageValue("mediaarchive");
	
	String collectionid = context.getRequestParameter("collectionid");
	
	HitTracker followers = mediaArchive.query("librarycollectionlikes").exact("collectionid", collectionid).search();
	if (followers == null)
	{
		log.info("No Followers in this Project. " + collectionid)
		return;  //No followers
	}
	
	HitTracker users = mediaArchive.query("user").all().search();
	
	Collection userids = followers.collectValues("followeruser");
	
	Collection tosave = new ArrayList();
	for (userdata in users) {
		
		if (!userids.contains(userdata.getId())) 
		{
			MultiValued subscription = mediaArchive.getSearcher("librarycollectionlikes").createNewData();
			subscription.setValue("collectionid", collectionid);
			subscription.setValue("followeruser", userdata.getId());
			subscription.setValue("addeddate", new Date());
			subscription.setValue("enabled", true);
			tosave.add(subscription);
		}
	}
	
	mediaArchive.saveData("librarycollectionlikes",tosave);
}





init();


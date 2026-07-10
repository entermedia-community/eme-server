package blog;

import org.apache.commons.collections.map.HashedMap
import org.entermediadb.asset.Asset
import org.entermediadb.asset.MediaArchive
import org.entermediadb.email.WebEmail
import org.openedit.*
import org.openedit.hittracker.HitTracker
import org.openedit.users.User

//Chat Notifications

public void init()
{
	MediaArchive archive = (MediaArchive)context.getPageValue("mediaarchive");
	
	//Search Post to publish
	
	HitTracker posttopublish = archive.query("userpost").exact("poststatus", "readytopublish").search();
	
	for (Iterator iterator = posttopublish.iterator(); iterator.hasNext();) {
		MultiValued userpost =  (MultiValued) iterator.next();
		try 
		{
			userpost.setValue("poststatus", "published");
			archive.getSearcher("userpost").saveData(userpost);
			log.info("Published: " + userpost);
			
			notifyfollowers(userpost.getId(), userpost.get("librarycollection"));
			
			
		}
		catch (Throwable e)
		{
			log.error("Error sending notifications: " + userpost.get("name"), e);
			userpost.setValue("poststatus", "error");
			archive.getSearcher("userpost").saveData(userpost);
		}
		
	}
	
}




public void notifyfollowers(String userpostid, String collectionid)
{
	MediaArchive archive = context.getPageValue("mediaarchive");
	
	HitTracker teammembers = archive.query("librarycollectionusers").exact("ontheteam", "true").exact("collectionid", collectionid).search();
	HitTracker followers = archive.query("librarycollectionlikes").exact("collectionid", collectionid).exact("enabled", true).search();
	if (followers == null)
	{
		log.info("No Followers in this Project. " + collectionid)
		return;  //No followers
	}
	
	HashMap<String, User> notifyusers = new HashedMap();
	Set userids = new HashSet();
	
	//loop Team Members
	for (Iterator followerIterator = teammembers.iterator(); followerIterator.hasNext();) {
		MultiValued follower = (MultiValued)followerIterator.next();
		User user = archive.getUser(follower.get("followeruser"));
		if(user != null)
		{
			notifyusers.put(follower.get("id"), user);
			userids.add(user.getId());
		}
	}

	
	//loop likes
	for (Iterator followerIterator2 = followers.iterator(); followerIterator2.hasNext();) {
		MultiValued follower = (MultiValued)followerIterator2.next();
		if (userids.contains(follower.get("followeruser")))
		{
			continue;
		}
		User user = archive.getUser(follower.get("followeruser"));
		if(user != null)
		{
			notifyusers.put(follower.get("id"), user);
		}
	} 
	
	Data collection = archive.getCachedData("librarycollection", collectionid);
	Data community = archive.getCachedData("communitytag", collection.get("communitytag"));
	
	Data blogpost = archive.getCachedData("userpost", userpostid);
	
	
	if (collection == null || community == null)
	{
		log.error("Skiping post: " + userpostid + " No Collection: " + collection + " Community:" + community);
		return;
	}
	
	String emailSubject = "["+ collection.getName() +"] " + blogpost.getName();
	
	if (blogpost.get("name") != null)
	{
		emailSubject = blogpost.getName();
	}
	
	String siteid = context.findValue("siteid");
	String template = siteid + community.get("templatepath") + "/theme/emails/newpostnotifyfollowers.html";
	
	log.info("Sending notifications for: " +blogpost + " To:" + notifyusers.size() + " users");
	
	Iterator<String> keys = notifyusers.keySet().iterator();
	while(keys.hasNext())
	{
		String key = keys.next();
		User user = notifyusers.get(key);
		
		if (user.get("email") == null)
		{
			log.error("User with no email address: " + user);
			continue;
		}
		
		User postuser = archive.getUser(blogpost.get("owner"));
		
		String emailfromname = null;
		
		if (postuser.getFirstName() != null && postuser.getLastName() != null)
		{
			emailfromname = postuser.getFirstName() + " " + postuser.getLastName();
		}
		else {
			emailfromname = postuser.getShortDescription();
		}
	
		WebEmail templatemail = archive.createSystemEmail(user, template);
		templatemail.setFromName(emailfromname);
		
		templatemail.setSubject(emailSubject); //TODO: Translate
		Map objects = new HashMap();
		String entermediakey = archive.userManager.getEnterMediaKey(user);
		objects.put("entermediakey",entermediakey);
		objects.put("teamuser",user);
		objects.put("librarycol", collection);
		objects.put("apphome", context.getPageValue("apphome"));
		objects.put("applink", context.getPageValue("applink"));
		objects.put("siteroot", getSiteRoot());
		objects.put("communityhome", "/" + siteid + community.get("templatepath"));
		objects.put("community",community);
		objects.put("followerid",key);
		objects.put("followeremail", user.getEmail());
		objects.put("blogpost",blogpost);
		
		
		objects.put("postuser",postuser);
		
		
		Asset postasset = archive.getAsset(blogpost.primarymedia);
		String postimage = community.get("externaldomain") + archive.asLinkToGenerated(postasset, "image1200x628.jpg");
		objects.put("postimage", postimage);
			
		templatemail.send(objects);
		
		log.info("User Notified: " + user.get("email"));
	}
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
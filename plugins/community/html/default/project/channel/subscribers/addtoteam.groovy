package subscribers;

import org.entermediadb.asset.Category
import org.entermediadb.asset.MediaArchive
import org.entermediadb.email.WebEmail
import org.openedit.Data
import org.openedit.data.Searcher
import org.openedit.users.User
import org.openedit.users.authenticate.PasswordGenerator

public void init()
{
	MediaArchive archive = context.getPageValue("mediaarchive");
	
	String collectionid = context.getRequestParameter("collectionid");
	String firstName = context.getRequestParameter("firstName.value");
	String lastName = context.getRequestParameter("lastName.value");
	String email = context.getRequestParameter("email.value");
	if(email) {
		email = email.trim().toLowerCase();
	}
	String teamuserid = context.getRequestParameter("dataid");
	String addtoteam = context.getRequestParameter("addtoteam");
	
	//log.info("Adding used to team " + teamuserid + " and " + email);
	
	User teamuser = null;
/* Check for duplicate email. */
	if (teamuserid != null) 
	{
		teamuser = archive.getUserManager().getUser(teamuserid);			
	}
	else if (email != null) {
		teamuser = archive.getUserManager().getUserByEmail(email);
		if (teamuser != null) {
			teamuserid = teamuser.getId();
		}
	}
	//not a user, create
	if( teamuser == null)
	{
		String	password = new PasswordGenerator().generate();
			
		teamuser = archive.getUserManager().createUser(null, password);
		teamuser.setFirstName(firstName);
		teamuser.setLastName(lastName);
		teamuser.setEmail(email);
		teamuser.setEnabled(true);
		archive.getUserManager().saveUser(teamuser);
		teamuserid = teamuser.getId();
	}
	
	if (teamuserid == null) {
		log.info("Error adding user: " + email + " to team " + collectionid);
		return;
	}
	
	
	
	
	Data subscription = archive.query("librarycollectionusers").exact("followeruser", teamuserid).exact("collectionid", collectionid).searchOne();
	if (subscription != null)
	{
		//exists, but is ontheteam?
		if (subscription.getValue("ontheteam") != "true" && addtoteam == "true") {
			subscription.setValue("ontheteam",true);
			archive.getSearcher("librarycollectionusers").saveData(subscription);
		} 
	}
	else
	{
		subscription = archive.getSearcher("librarycollectionusers").createNewData();
		subscription.setValue("collectionid",collectionid);
		subscription.setValue("followeruser",teamuserid);
		if (addtoteam == "true") {
			subscription.setValue("ontheteam",true);
		}
		subscription.setValue("addeddate",new Date());
		archive.getSearcher("librarycollectionusers").saveData(subscription);
	}
	context.putPageValue("subscription",subscription);
	
//	Set template path with applicationid instead of just 'apphome'
	String apphome = context.getPageValue("apphome");
	String template = apphome + "/theme/emails/collection-add-teammember.html";
	
	Data librarycol = archive.getCachedData("librarycollection", collectionid);
	Data community = archive.getCachedData("communitytag", librarycol.get("communitytag"));
	
	if (librarycol == null)
		{
			log.error("No Collection: " + librarycol);
			return;
		}
	

	WebEmail templatemail = archive.createSystemEmail(teamuser, template);

	if (community != null) {
		templatemail.setSubject(community.getName() + ": Added to Team"); //TODO: Translate
	}
	else 
	{
		templatemail.setSubject("Added to Team " + librarycol.getName()); //TODO: Translate
	}

	Map objects = new HashMap();
	String entermediakey = archive.userManager.getEnterMediaKey(teamuser);
	objects.put("entermediakey",entermediakey);
	objects.put("user", context.getUser());
	objects.put("teamuser",teamuser);
	
	objects.put("librarycol", librarycol);
	objects.put("project", librarycol);
	objects.put("apphome", context.getPageValue("apphome"));
	objects.put("applink", context.getPageValue("applink"));
	objects.put("siteroot", getSiteRoot());
	objects.put("apphome", apphome);
	objects.put("community",community);
		
	log.info("Sending add to Project email  " + teamuserid);
	
	templatemail.send(objects);
			
	log.info("User: " + email + " Added to team: " + teamuserid);
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



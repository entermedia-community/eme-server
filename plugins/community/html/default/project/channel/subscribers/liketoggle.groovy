package subscribers;

import org.entermediadb.asset.MediaArchive
import org.openedit.MultiValued
import org.openedit.users.User

public void init()
{

	String collectionid = context.getRequestParameter("collectionid");
	String email = context.getRequestParameter("email");
	MediaArchive archive = context.getPageValue("mediaarchive");
	
	if(collectionid == null)
	{
		return;
	}
	
	User user = archive.getUserManager().getUserByEmail(email);
	
	if(user == null)
	{
		
		return;
	}
	
	MultiValued subscription =  archive.query("librarycollectionlikes").exact("collectionid",collectionid).exact("followeruser",user.getId()).searchOne();
	
	if (subscription != null)
	{
		
		if( subscription.getBoolean("enabled")  )
		{
			subscription.setValue("enabled", false);
			
		}
		else
		{
			subscription.setValue("enabled", true);
			
		}
		archive.saveData("librarycollectionlikes",subscription);
		
	}
	else
	{
		subscription = archive.getSearcher("librarycollectionlikes").createNewData();
		subscription.setValue("collectionid", collectionid);
		subscription.setValue("followeruser", user.getId());
		subscription.setValue("addeddate", new Date());
		subscription.setValue("enabled", true);
		archive.saveData("librarycollectionlikes",subscription);
		log.info("Subscription added");
		
	}
	
	context.putPageValue("collectionid", collectionid);
	
	
		
}

init();



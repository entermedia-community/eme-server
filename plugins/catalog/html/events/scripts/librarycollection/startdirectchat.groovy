import org.entermediadb.asset.MediaArchive
import org.entermediadb.projects.*
import org.openedit.Data
import org.openedit.data.BaseSearcher
import org.openedit.data.Searcher
import org.openedit.hittracker.HitTracker

public void addUser(MediaArchive mediaArchive, Data collection, String inUserId)
{
	Data userrecord = mediaArchive.query("librarycollectionusers").exact("collectionid",collection.getId()).exact("followeruser", inUserId).searchOne();
	log.info("Found user " + userrecord)
	if( userrecord == null)
	{
		userrecord = mediaArchive.getSearcher("librarycollectionusers").createNewData();
		userrecord.setValue("collectionid",collection.getId());
		userrecord.setValue("followeruser",inUserId);
		userrecord.setValue("ontheteam","true");
		mediaArchive.saveData("librarycollectionusers",userrecord);
	}

}

public void init()
{
	MediaArchive mediaArchive = context.getPageValue("mediaarchive");//Search for all files looking for videos
	BaseSearcher collectionsearcher = mediaArchive.getSearcher("librarycollection");

	String[] users = context.getRequestParameters("users");
	
	String oneuser = users[0];
	//
	HitTracker oneworkspaces = mediaArchive.query("librarycollectionusers").exact("ontheteam", "true").hitsPerPage(1000).exact("followeruser", oneuser).search(context);
	log.info("oneworkspaces " + oneworkspaces);
	
	//Collect these up
	String otheruser = users[1];
	Collection validids = oneworkspaces.collectValues("collectionid");
	HitTracker otherworkspaces = mediaArchive.query("librarycollectionusers").orgroup("collectionid",validids).exact("ontheteam", "true").hitsPerPage(1000).exact("followeruser", otheruser).search(context);
	log.info("otherworkspaces " + otherworkspaces);
	
	Collection othervalidids = otherworkspaces.collectValues("collectionid");
	
	HitTracker dmcollections = collectionsearcher.query().exact("collectiontype","3").ids(othervalidids).hitsPerPage(1000).search();
	
	log.info(dmcollections.getQuery() + " found " + dmcollections);
	
	LibraryCollection collection = null;
	if( !dmcollections.isEmpty() )
	{
		collection = collectionsearcher.loadData(dmcollections.first());
	}
	if(collection == null )
	{
		//create it
		collection = (LibraryCollection)collectionsearcher.createNewData();
		Searcher librarysearcher = mediaArchive.getSearcher("library");
		if( collection.getLibrary() == null)
		{
			Data library = librarysearcher.searchById("collectives");
			if( library == null)
			{
				library = librarysearcher.createNewData();
				library.setId("collectives");
				library.setValue("owner", "admin");
				library.setName("Collectives");
				librarysearcher.saveData(library);
			}
			collection.setValue("library",library.getId());
		}	
		if( collection.get("owner") == null )
		{
			collection.setValue("owner",user.getId());
		}	
		collection.setValue("collectiontype","3");
		collection.setName("Messages "+ users);
		collectionsearcher.saveData(collection);
		log.info("Saved " + collection.getId() + " with " + collection.get("collectiontype" ));
	}		
	//Get extra users 
	String[] otherusers = context.getRequestParameters("users");
	for (userid in otherusers) 
	{
		addUser(mediaArchive,collection,userid);
	}
	
	mediaArchive.getProjectManager().getRootCategory(mediaArchive,collection);
		
	BaseSearcher colectivesearcher = mediaArchive.getSearcher("collectiveproject");
	Data newproject = colectivesearcher.query().exact("parentcollectionid",collection.getId()).match("name","General").searchOne();
	if( newproject == null)
	{
		newproject = colectivesearcher.createNewData();
		newproject.setName("General");
		newproject.setValue("parentcollectionid",collection.getId());
		colectivesearcher.saveData( newproject );
	}
	context.putPageValue("searcher",collectionsearcher);
	context.putPageValue("data",collection);
	context.putPageValue("librarycol",collection);
	context.putPageValue("library",collection.getLibrary());
	
}

init();
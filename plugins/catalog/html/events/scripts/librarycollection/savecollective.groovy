import org.entermediadb.asset.MediaArchive
import org.entermediadb.location.Position
import org.entermediadb.projects.*
import org.openedit.Data

import org.openedit.data.BaseSearcher
import org.openedit.data.Searcher

public void addUser(MediaArchive mediaArchive, Data collection, String inUserId)
{
	Data userrecord = mediaArchive.query("librarycollectionusers").exact("collectionid",collection.getId()).exact("followeruser", inUserId).searchOne();
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
	String  id = data.getId();
	LibraryCollection collection = (LibraryCollection)collectionsearcher.searchById(id);
	Searcher librarysearcher = mediaArchive.getSearcher("library");
	log.info("User is: " + user.getId() );

	
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

	addUser(mediaArchive,collection,context.getUserName());
	
	String collectiontype = context.getRequestParameter("collectiontype");
	if( collectiontype == "3")
	{
		//Get extra users 
		String[] otherusers = context.getRequestParameters("otherusers");
		for (userid in otherusers) {
			addUser(mediaArchive,collection,user);
		}
	}
	
	//#set( $team = $mediaarchive.query("librarycollectionusers").exact("collectionid",$collectionid).exact("ontheteam",true).search($context) )
	
	collectionsearcher.saveData(collection);
	
	//mediaArchive.getProjectManager().getRootCategory(mediaArchive,collection);
		
	BaseSearcher colectivesearcher = mediaArchive.getSearcher("collectiveproject");
	Data newproject = colectivesearcher.query().exact("parentcollectionid",collection.getId()).orgroup("name","Public|General").searchOne();
	if( newproject == null)
	{
		newproject = colectivesearcher.createNewData();
		newproject.setName("Public");
		newproject.setValue("parentcollectionid",collection.getId());
		colectivesearcher.saveData( newproject );
	}
	context.putPageValue("librarycol",collection);
	context.putPageValue("library",collection.getLibrary());
	
	//Redirect
	String url = collection.get("urlname");
	if( url != null)
	{
		context.redirect("/" + url);
	}
	
}

init();
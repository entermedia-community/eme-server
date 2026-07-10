import org.entermediadb.asset.MediaArchive
import org.entermediadb.projects.*
import org.openedit.*
import org.openedit.hittracker.*
import org.openedit.data.*

public void init()
{
	MediaArchive mediaArchive = context.getPageValue("mediaarchive");
	Searcher searcher = mediaArchive.getSearcher("librarycollection");
	HitTracker all = mediaArchive.query("librarycollection").all().search();
	List tosave = new ArrayList();
	for(Data data in all)
	{
		LibraryCollection collection = searcher.loadData(data);
		collection.setValue("name", collection.getName());
		tosave.add(collection);
	}	
	searcher.saveAllData(tosave,null);
	log.info("saved ${tosave.size()}");
}

init();
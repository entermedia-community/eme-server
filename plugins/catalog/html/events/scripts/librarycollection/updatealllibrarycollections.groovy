package librarycollection

import org.entermediadb.asset.Category
import org.entermediadb.asset.MediaArchive
import org.openedit.Data
import org.openedit.data.Searcher
import org.openedit.hittracker.HitTracker


public void init() {
	MediaArchive mediaArchive = (MediaArchive)context.getPageValue("mediaarchive");
	
	HitTracker all = mediaArchive.query("librarycollection").exact("collectiontype", "1").search();
	all.each {
		Data librarycol = it;
		if(librarycol.get("communitytag") == null) {
		
		librarycol.setValue("communitytag", "oi" );
		librarycol.setValue("communitytag", "oicommunity" );
		mediaArchive.getSearcher("librarycollection").saveData(librarycol, null);
	    log.info("saving library $librarycol");
			}
		
    }
}

init();


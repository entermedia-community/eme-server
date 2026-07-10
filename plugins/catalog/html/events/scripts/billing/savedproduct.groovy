package billing;

import org.entermediadb.asset.MediaArchive
import org.openedit.*
import org.openedit.data.Searcher
import org.openedit.util.PathUtilities

public void init() {
	MediaArchive mediaArchive = context.getPageValue("mediaarchive");
	Searcher productSearcher = mediaArchive.getSearcher("collectiveproduct");
	Data product = context.getPageValue("data");
	
	if( product.getValue("urlname") == null)
	{
		String dash = PathUtilities.dash(product.getName());
		product.setValue("urlname",dash)
	}
	
	//always set product active?
	log.info(product.get("billingstatus") + "  "+ product.get("nextbillon"));
	if (product.get("billingstatus") == null && product.get("nextbillon") != null)
	{
		product.setValue("billingstatus", "active");
		log.info("Set to active");
	}
	
	
	productSearcher.saveData(product);
} 

init();



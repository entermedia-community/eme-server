package importing;

import org.entermediadb.asset.MediaArchive
import org.openedit.Data
import org.openedit.OpenEditException

public void init()
{
	MediaArchive archive = context.getPageValue("mediaarchive");

	Data upload = context.getPageValue("entity");

	if( upload == null )
	{
		throw new OpenEditException("No entity");	
	}
	//TODO: Check for duplicate urllinks
	
	String projectlink = context.getPageValue("projectlink");
	
	context.redirect("${projectlink}/blog/${upload.urlname}");
		
}


init();



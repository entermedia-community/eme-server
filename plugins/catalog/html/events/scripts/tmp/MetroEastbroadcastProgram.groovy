package importing

import org.bouncycastle.crypto.tls.HashAlgorithm
import org.entermediadb.asset.Asset
import org.entermediadb.asset.MediaArchive
import org.entermediadb.asset.orders.Order
import org.openedit.Data
import org.openedit.data.DataWithSearcher
import org.openedit.data.Searcher
import org.openedit.hittracker.HitTracker

public void init()
{
		String publishdestinationid = "AZW00oqtIgvTx4L_NwPM";
		String publishpressetid = "AZW01zhEIgvTx4L_NwPd";
	
		MediaArchive archive = context.getPageValue("mediaarchive");//Search for all files looking for videos
		
		Searcher searcher = archive.getSearcher("dam_program");
		HitTracker programs = searcher.query().exact("dam_publishstatus","ready").search();
		programs.enableBulkOperations();
		programs.setHitsPerPage(100);
		List tosave = new ArrayList();
		for (Data entity in programs)
		{

		   Asset asset = archive.getAsset(entity.get("primarymedia"));
		   if (asset != null)
		   	{
				Order order = archive.getOrderManager().createNewOrder(archive.getCatalogId(), context, true);
				order.setValue("status", "pending");
				archive.getOrderManager().saveOrder(archive, order);
				HashMap itemproperties = new HashMap();
				
				String mask = "${data.program_number}_${data.episode_number}.ts";
				Map vals = new HashMap();
				
				vals.put("data", entity);  
				
				String exportname = archive.replaceFromMask(mask,"dam_program", entity,  vals, context.getLocale())
				
				itemproperties("itemexportname", exportname);
				itemproperties.put("publishdestination", publishdestinationid);
				itemproperties.put("pressetid", publishpressetid);
				itemproperties.put("publishstartdate", new Date());
			    archive.getOrderManager().addItemToOrder(archive.getCatalogId(), order, asset, itemproperties);
			   
			   entity.setValue("dam_publishstatus", "complete");
			   tosave.add(entity);
			   
			   order.setValue("status", "orderplaced");
			   archive.getOrderManager().saveOrder(archive, order);
			   //Todo: save order, fire share event check order status
			   
			   
		   }
		}
		searcher.saveAllData(tosave, null);
		
		archive.fireSharedMediaEvent("ordering", "checkorderstatus");
	
		
}

init();
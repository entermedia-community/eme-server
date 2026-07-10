package billing;

import org.openedit.util.DateStorageUtil
import org.entermediadb.asset.MediaArchive
import org.openedit.*
import org.openedit.data.Searcher
import org.openedit.users.User
import org.openedit.hittracker.HitTracker


public void init() {
	MediaArchive mediaArchive = context.getPageValue("mediaarchive");

	Searcher transactionSearcher = mediaArchive.getSearcher("transaction");
	ArrayList tosave = new ArrayList();
	HitTracker pendingNotification = transactionSearcher.query()
			.missing("receiptstatus").search();
			
	pendingNotification.enableBulkOperations();
	
	for (Data receipt in pendingNotification) {
		
		receipt.setValue("receiptstatus", "new");
		//transactionSearcher.saveData(receipt);
		
		tosave.add(receipt);
		if(tosave.size() == 100)
			{
				transactionSearcher.saveAllData( tosave, null );
				log.info(pendingNotification.size() + " receipts updated");
				tosave.clear();
			}

	}
	transactionSearcher.saveAllData( tosave, null );
	log.info(pendingNotification.size() + " receipts updated");
}



init();

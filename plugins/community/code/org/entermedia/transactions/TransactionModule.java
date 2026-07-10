package org.entermedia.transactions;

import org.entermediadb.asset.modules.BaseMediaModule;
import org.openedit.WebPageRequest;

public class TransactionModule extends BaseMediaModule
{
	public void loadTransactionManager(WebPageRequest inReq)
	{
		String catalogid = inReq.findPathValue("catalogid");
		TransactionManager manager = (TransactionManager) getModuleManager().getBean(catalogid, "transactionManager");

		inReq.putPageValue("transactionManager", manager);
	}
}

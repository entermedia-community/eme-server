package org.openinstitute.finance;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.entermedia.transactions.TransactionManager;
import org.entermediadb.asset.MediaArchive;
import org.openedit.CatalogEnabled;
import org.openedit.Data;
import org.openedit.ModuleManager;
import org.openedit.users.User;

public class BankAccountManager implements CatalogEnabled
{
	private static final Log log = LogFactory.getLog(BankAccountManager.class);
	protected ModuleManager fieldModuleManager;
	protected MediaArchive fieldMediaArchive;
	protected String fieldCatalogId;
	protected FinanceManager fieldFinanceManager;

	public FinanceManager getFinanceManager()
	{
		if (fieldFinanceManager == null)
		{
			fieldFinanceManager = (FinanceManager) getModuleManager().getBean(getCatalogId(), "financeManager");
		}

		return fieldFinanceManager;
	}

	public void setFinanceManager(FinanceManager inFinanceManager)
	{
		fieldFinanceManager = inFinanceManager;
	}

	public String getCatalogId()
	{
		return fieldCatalogId;
	}

	public void setCatalogId(String inCatalogId)
	{
		fieldCatalogId = inCatalogId;
	}

	public ModuleManager getModuleManager()
	{
		return fieldModuleManager;
	}

	public void setModuleManager(ModuleManager inModuleManager)
	{
		fieldModuleManager = inModuleManager;
	}

	public MediaArchive getMediaArchive()
	{
		if (fieldMediaArchive == null)
		{
			fieldMediaArchive = (MediaArchive) getModuleManager().getBean(getCatalogId(), "mediaArchive");
		}
		return fieldMediaArchive;
	}

	public void setMediaArchive(MediaArchive inMediaArchive)
	{
		fieldMediaArchive = inMediaArchive;
	}

	public Data getBankAccountByLink(String inLinkid)
	{
		return null;
	}

	public void sendMoney(Data inBankaccount)
	{}

	public Data loadBankAccountLink(String inLinkid)
	{
		Data lookup = getMediaArchive().getCachedData("bankaccountlookup", inLinkid);

		return lookup;
	}

	public User loadBankAccountUser(String inLinkid)
	{
		Data lookup = loadBankAccountLink(inLinkid);
		String bankaccountid = lookup.get("bankaccount");
		String userid = lookup.get("user");
		User user = getMediaArchive().getUser(userid);
		// Data bankaccount =
		// getMediaArchive().getCachedData("bankaccount",bankaccountid);
		return user;
	}

}

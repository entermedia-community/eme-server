package org.openinstitute.finance;

import java.util.List;
import java.util.Map;

import org.entermediadb.asset.modules.BaseMediaModule;
import org.openedit.Data;
import org.openedit.WebPageRequest;
import org.openedit.users.User;

public class BankAccountModule extends BaseMediaModule
{

	public FinanceManager getFinanceManager(WebPageRequest inReq)
	{
		String catalogid = inReq.findValue("catalogid");
		return (FinanceManager) getModuleManager().getBean(catalogid, "financeManager");
	}

	public BankAccountManager getBankAccountManager(WebPageRequest inReq)
	{
		String catalogid = inReq.findValue("catalogid");
		return (BankAccountManager) getModuleManager().getBean(catalogid, "bankAccountManager");
	}

	public void loadBankAccount(WebPageRequest inReq)
	{
		BankAccountManager bm = getBankAccountManager(inReq);
		String linkid = inReq.getRequestParameter("l");
		User user = bm.loadBankAccountUser(linkid);
		// int balance = bm.loadBankAccountBalance(linkid);

		// Grab the balance and cache it?
		inReq.putPageValue("accountuser", user);

		Data lookup = bm.loadBankAccountLink(linkid);
		String bankaccountid = lookup.get("bankaccount");

		Data bank = bm.getMediaArchive().getCachedData("bankaccount", bankaccountid);

		List<BankTransaction> transactions = bm.getFinanceManager().getAllTransactionByBank(bankaccountid, null);

		// transactions

		Map<String, Double> totals = bm.getFinanceManager().getTotalsByCurrencyType(transactions);

		inReq.putPageValue("financeManager", bm.getFinanceManager());
		inReq.putPageValue("totals", totals);
		inReq.putPageValue("linkid", linkid);

	}

	public void send(WebPageRequest inReq)
	{
		BankAccountManager bm = getBankAccountManager(inReq);
		String linkid = inReq.getRequestParameter("l");

		Data bankaccount = bm.getBankAccountByLink(linkid);
		bm.sendMoney(bankaccount);
	}

	public void receive(WebPageRequest inReq)
	{

	}

	public void deposit(WebPageRequest inReq)
	{

	}

	public void withdraw(WebPageRequest inReq)
	{

	}

	public void resetpin(WebPageRequest inReq)
	{

	}

	public void loadsummary(WebPageRequest inReq)
	{

	}

	public void requestpayment(WebPageRequest inReq)
	{

	}

	public void loadScreenName(WebPageRequest inReq)
	{
		BankAccountManager bm = getBankAccountManager(inReq);
		String linkid = inReq.getRequestParameter("l");
		User user = bm.loadBankAccountUser(linkid);
		inReq.putPageValue("sendtouser", user);

	}
}

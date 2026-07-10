package org.openinstitute.finance;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.entermedia.transactions.TransactionManager;
import org.entermediadb.asset.MediaArchive;
import org.openedit.CatalogEnabled;
import org.openedit.Data;
import org.openedit.ModuleManager;
import org.openedit.MultiValued;
import org.openedit.data.BaseData;
import org.openedit.data.QueryBuilder;
import org.openedit.data.Searcher;
import org.openedit.hittracker.HitTracker;
import org.openedit.hittracker.ListHitTracker;
import org.openedit.hittracker.SearchQuery;
import org.openedit.util.MathUtils;

public class FinanceManager implements CatalogEnabled
{
	private static final Log log = LogFactory.getLog(TransactionManager.class);
	protected ModuleManager fieldModuleManager;
	protected MediaArchive fieldMediaArchive;
	protected String fieldCatalogId;
	protected DecimalFormat df = new DecimalFormat("#.00");

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

	// get expenses from expenses db

	public Map getTotalIncomesByDateRange(String inCollectionId, DateRange inDateRange, String topicid)
	{
		HashMap<String, Object> bycurrency = new HashMap<String, Object>();

		// Regular Income
		Searcher incomesSearcher = getMediaArchive().getSearcher("collectiveincome");
		QueryBuilder query = incomesSearcher.query();
		query.exact("collectionid", inCollectionId);
		Collection incometypes = getMediaArchive().query("incometype").exact("iscapitalloan", false).search();
		query.orgroup("incometype", incometypes);

		if (topicid != null)
		{
			query.exact("collectiveproject", topicid);
		}

		addDateRange(query, "date", inDateRange);
		HitTracker hits = incomesSearcher.search(query.getQuery());
		hits.setHitsPerPage(1000);

		for (Iterator iterator = hits.iterator(); iterator.hasNext();)
		{
			MultiValued data = (MultiValued) iterator.next();
			String currency = (String) data.getValue("currencytype");

			Double currencytotal = (Double) bycurrency.get(currency);
			if (currencytotal == null || currencytotal == 0.0)
			{
				currencytotal = 0.0;
				bycurrency.put(currency, currencytotal);

			}
			Double total = (Double) data.getValue("total");
			if (total == null)
			{
				log.error("Make sure totals are set: collectiveincome " + data.getId());
				continue;
			}
			currencytotal = currencytotal + total;
			bycurrency.replace(currency, currencytotal);
		}

		// OI Donations (transaction table)
		incomesSearcher = getMediaArchive().getSearcher("transaction");
		query = incomesSearcher.query();
		query.exact("collectionid", inCollectionId);
		if (topicid != null)
		{
			query.exact("collectiveproject", topicid);
		}
		addDateRange(query, "paymentdate", inDateRange);
		hits = incomesSearcher.search(query.getQuery());
		hits.setHitsPerPage(1000);
		for (Iterator iterator = hits.iterator(); iterator.hasNext();)
		{
			MultiValued data = (MultiValued) iterator.next();
			String currency = (String) data.getValue("currencytype");

			Double currencytotal = (Double) bycurrency.get(currency);
			if (currencytotal == null || currencytotal == 0.0)
			{
				currencytotal = 0.0;
				bycurrency.put(currency, currencytotal);

			}
			currencytotal = currencytotal + (Double) data.getValue("totalprice");
			bycurrency.replace(currency, currencytotal);
		}

		// Transfers
		// Pull out only income transactions
		Map<String, List<Data>> summarytransfers = getTransfersByCurrencyForEntity(inCollectionId, inDateRange, true);
		for (Map.Entry<String, List<Data>> set : summarytransfers.entrySet())
		{
			List<Data> transfers = (List<Data>) set.getValue();
			String currency = set.getKey();
			for (Iterator iterator = transfers.iterator(); iterator.hasNext();)
			{
				Data transfer = (Data) iterator.next();

				Double currencytotal = (Double) bycurrency.get(currency);
				if (currencytotal == null || currencytotal == 0.0)
				{
					currencytotal = 0.0;
					bycurrency.put(currency, currencytotal);

				}
				currencytotal = currencytotal + (Double) transfer.getValue("total");
				bycurrency.replace(currency, currencytotal);
			}
		}

		// Paid Invoices
		incomesSearcher = getMediaArchive().getSearcher("collectiveinvoice");
		query = incomesSearcher.query();
		query.exact("collectionid", inCollectionId);
		if (topicid != null)
		{
			// TODO: Add topics to invoices query.exact("collectiveproject",topicid);
		}
		query.exact("paymentstatus", "paid");
		addDateRange(query, "invoicepaidon", inDateRange);
		hits = incomesSearcher.search(query.getQuery());
		hits.setHitsPerPage(1000);

		for (Iterator iterator = hits.iterator(); iterator.hasNext();)
		{
			MultiValued data = (MultiValued) iterator.next();
			String currency = (String) data.getValue("currencytype");
			if (currency == null)
			{
				currency = "1";
			}
			Double currencytotal = (Double) bycurrency.get(currency);
			if (currencytotal == null || currencytotal == 0.0)
			{
				currencytotal = 0.0;
				bycurrency.put(currency, currencytotal);

			}
			currencytotal = currencytotal + (Double) data.getValue("totalprice");
			bycurrency.replace(currency, currencytotal);
		}

		return bycurrency;

	}

	public Map getIncomeTypesByDateRange(String inCollectionId, DateRange inDateRange, String topicid)
	{
		Map income = getIncomeTypesByDateRange(inCollectionId, false, inDateRange, topicid);
		return income;
	}

	public Map getCapitalIncomeByDateRange(String inCollectionId, DateRange inDateRange, String topicid)
	{
		Map income = getIncomeTypesByDateRange(inCollectionId, true, inDateRange, topicid);
		return income;
	}

	public Map getIncomeTypesByDateRange(String inCollectionId, boolean incapital, DateRange inDateRange, String topicid)
	{
		HashMap<String, Collection> bycurrency = new HashMap<String, Collection>();

		// Regular Income
		Searcher incomesSearcher = getMediaArchive().getSearcher("collectiveincome");
		QueryBuilder query = incomesSearcher.query();
		query.exact("collectionid", inCollectionId);
		Collection incometypes = getMediaArchive().query("incometype").exact("iscapitalloan", incapital).search();
		query.orgroup("incometype", incometypes);
		if (topicid != null)
		{
			query.exact("collectiveproject", topicid);
		}

		addDateRange(query, "date", inDateRange);

		HitTracker hits = incomesSearcher.search(query.getQuery());
		hits.enableBulkOperations();

		for (Iterator iterator = hits.iterator(); iterator.hasNext();)
		{
			MultiValued data = (MultiValued) iterator.next();
			String currency = (String) data.getValue("currencytype");

			Collection values = (Collection) bycurrency.get(currency);
			if (values == null)
			{
				values = new ListHitTracker();
				// values.add(data);
				bycurrency.put(currency, values);
			}
			values.add(data);

		}

		// OI Donations (transaction table)
		// incomesSearcher = getMediaArchive().getSearcher("transaction");
		// query = incomesSearcher.query();
		// query.exact("collectionid", inCollectionId);
		// if( topicid != null)
		// {
		// query.exact("collectiveproject",topicid);
		// }
		//
		// addDateRange(query,"paymentdate",inDateRange);
		//
		// hits = incomesSearcher.search(query.getQuery());
		// hits.setHitsPerPage(1000);
		//
		// for (Iterator iterator = hits.iterator(); iterator.hasNext();) {
		// MultiValued data = (MultiValued) iterator.next();
		// String currency = (String) data.getValue("currencytype");
		//
		// Collection values = (Collection) bycurrency.get(currency);
		// if( values == null)
		// {
		// values = new ListHitTracker();
		// //values.add(data);
		// bycurrency.put(currency, values);
		// }
		// values.add(data);
		// }

		// Paid Invoices
		// incomesSearcher = getMediaArchive().getSearcher("collectiveinvoice");
		// query = incomesSearcher.query();
		// query.exact("collectionid", inCollectionId);
		// if( topicid != null)
		// {
		// //TODO: Add topics to invoices query.exact("collectiveproject",topicid);
		// }
		// query.exact("paymentstatus","paid");
		// addDateRange(query,"invoicepaidon",inDateRange);
		// hits = incomesSearcher.search(query.getQuery());
		// hits.setHitsPerPage(1000);
		//
		// for (Iterator iterator = hits.iterator(); iterator.hasNext();) {
		// MultiValued data = (MultiValued) iterator.next();
		// String currency = "1";//(String) data.getValue("currencytype"); //TODO:
		// Support currencies
		//
		// Collection values = (Collection) bycurrency.get(currency);
		// if( values == null)
		// {
		// values = new ListHitTracker();
		// //values.add(data);
		// bycurrency.put(currency, values);
		// }
		// values.add(data);
		//
		// }

		// //Pull out only income transactions
		// Map<String,List<Data>> summarytransfers =
		// getTransfersByCurrencyForEntity(inCollectionId,inDateRange,true);
		// for (Map.Entry<String, List<Data>> set :summarytransfers.entrySet()) {
		// List<Data> values = (List<Data>) set.getValue();
		// String currency = set.getKey();
		//
		// Collection allvalues = (Collection) bycurrency.get(currency);
		// if( allvalues == null)
		// {
		// allvalues = new ListHitTracker();
		// //values.add(data);
		// bycurrency.put(currency, values);
		// }
		// allvalues.addAll(values);
		// }
		// //summarize by Currency
		// String currentcurrency = "";
		// for (Map.Entry<String, Object> set :bycurrency.entrySet()) {
		// Collection data = (Collection) set.getValue();
		// HashMap<String, Object> currenttype = new HashMap<String, Object>();
		// String currenttypeid = "";
		// for (Iterator iterator = data.iterator(); iterator.hasNext();) {
		// MultiValued row = (MultiValued) iterator.next();
		// String incometype = (String) row.getValue("incometype");
		// if( incometype == null)
		// {
		// if( row.getValue("paymententitydest") != null ||
		// row.getValue("paymententitysource") != null )
		// {
		// incometype = "7"; //Transfer
		// }
		// else
		// {
		// incometype = "6"; //Invoices
		// }
		// }
		// BaseData values = (BaseData)currenttype.get(incometype);
		// if (values == null) {
		// values = new BaseData();
		// values.setValue("total", 0.0);
		// currenttype.put(incometype, values); //Consolidating into one object
		// }
		// Double currenttotal = (Double) row.getValue("totalprice");
		// if( currenttotal == null )
		// {
		// currenttotal = (Double) row.getValue("total");
		// }
		// Double subtotal = (Double)values.getValue("total");
		// if( subtotal == null)
		// {
		// subtotal = 0D;
		// }
		// if( currenttotal == null)
		// {
		// currenttotal = 0D;
		// }
		// values.setValue("total", subtotal + currenttotal);
		// currenttypeid = (String) row.getValue("currencytype");
		// if( currenttypeid == null )
		// {
		// currenttypeid = "1"; //USD
		// }
		// values.setValue("currencytype", currenttypeid);
		// values.setValue("incometype", incometype);
		//
		// }
		// bycurrency.replace(set.getKey(), currenttype);
		//
		// }

		return bycurrency;
	}

	public Map<String, List> getCapitalExpenseTypesByDateRange(String inCollectionId, DateRange inDateRange, String topicid)
	{
		Map<String, List> bycurrency = getExpenseTypesByDateRange(inCollectionId, inDateRange, true, topicid);
		return bycurrency;
	}

	public Map<String, List> getNormalExpenseTypesByDateRange(String inCollectionId, DateRange inDateRange, String topicid)
	{
		Map<String, List> bycurrency = getExpenseTypesByDateRange(inCollectionId, inDateRange, false, topicid);
		return bycurrency;
	}

	public Map<String, List> getExpenseTypesByDateRange(String inCollectionId, DateRange inDateRange, boolean capital, String topicid)
	{
		Searcher expensesSearcher = getMediaArchive().getSearcher("collectiveexpense");
		QueryBuilder query = expensesSearcher.query();

		Collection expensetypes = getMediaArchive().query("expensetype").exact("iscapitalloan", capital).search();
		query.orgroup("expensetype", expensetypes);

		query.exact("collectionid", inCollectionId);
		if (topicid != null)
		{
			query.exact("collectiveproject", topicid);
		}
		addDateRange(query, "date", inDateRange);

		HitTracker hits = expensesSearcher.search(query.getQuery());
		hits.enableBulkOperations();
		log.info("expenses: " + hits);
		Map<String, List> bycurrency = new HashMap<String, List>();

		for (Iterator iterator = hits.iterator(); iterator.hasNext();)
		{
			MultiValued data = (MultiValued) iterator.next();
			String currency = (String) data.getValue("currencytype");
			if (currency == null)
			{
				currency = "1";
			}
			List values = (List) bycurrency.get(currency);
			if (values == null)
			{
				values = new ArrayList();
				// values.add(data);
				bycurrency.put(currency, values);
			}
			values.add(data);
		}

		// Pull out only income
		/*
		 * Map<String,List<Data>> summarytransfers =
		 * getTransfersByCurrencyForEntity(inCollectionId,inDateRange,false);
		 * 
		 * for (Map.Entry<String, List<Data>> set :summarytransfers.entrySet()) { List<Data> values =
		 * (List<Data>) set.getValue(); String currency = set.getKey();
		 * 
		 * Collection allvalues = (Collection) bycurrency.get(currency); if( allvalues == null) { allvalues
		 * = new ListHitTracker(); } allvalues.addAll(values); bycurrency.put(currency, values); }
		 */

		// summarize by ExpenseType
		// Collection bycurrencydata = sumarizeByExpenseType(bycurrency);

		return bycurrency;
	}

	public Collection<Data> sumarizeByType(Collection inExpensesOfOneCurrency, final String valuetype)
	{
		HashMap<String, Data> byexpensetype = new HashMap<String, Data>();
		for (Iterator iterator = inExpensesOfOneCurrency.iterator(); iterator.hasNext();)
		{
			Data expense = (Data) iterator.next();
			String currenttypeid = (String) expense.getValue(valuetype);
			Data totalbyexpense = (MultiValued) byexpensetype.get(currenttypeid);
			if (totalbyexpense == null)
			{
				totalbyexpense = new BaseData();
				totalbyexpense.setValue("total", 0.0);
				Data type = getMediaArchive().getCachedData(valuetype, currenttypeid);
				totalbyexpense.setValue(valuetype, type);
				byexpensetype.put(currenttypeid, totalbyexpense);
			}
			// Add up all the values
			totalbyexpense.setValue("total", addUp((Double) totalbyexpense.getValue("total"), (Double) expense.getValue("total")));
			totalbyexpense.setValue("currencytype", (String) expense.getValue("currencytype"));

		}
		List<Data> values = new ArrayList(byexpensetype.values());
		// TODO: sort em
		Collections.sort(values, new Comparator<Data>() {
			@Override
			public int compare(Data inArg0, Data inArg1)
			{
				Data one = (Data) inArg0.getValue(valuetype);
				Data two = (Data) inArg1.getValue(valuetype);
				if (one.getName() != null && two.getName() != null)
				{
					int i = one.getName().compareToIgnoreCase(two.getName());
					return i;
				}

				return 0;
			}
		});
		return values;
	}

	protected <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map)
	{
		List<Entry<K, V>> list = new ArrayList<>(map.entrySet());
		list.sort(Entry.comparingByValue());

		Map<K, V> result = new LinkedHashMap<>();
		for (Entry<K, V> entry : list)
		{
			result.put(entry.getKey(), entry.getValue());
		}

		return result;
	}

	private Double addUp(Double inValue, Double inValue2)
	{
		if (inValue == null)
		{
			inValue = 0D;
		}
		if (inValue2 == null)
		{
			inValue2 = 0D;
		}

		return inValue + inValue2;
	}

	public Map getNetExpenseByCurrency(String inCollectionId, DateRange inDateRange, String topicid)
	{
		Searcher expensesSearcher = getMediaArchive().getSearcher("collectiveexpense");
		QueryBuilder query = expensesSearcher.query();
		query.exact("collectionid", inCollectionId);
		if (topicid != null)
		{
			query.exact("collectiveproject", topicid);
		}
		Collection expensetypes = getMediaArchive().query("expensetype").exact("iscapitalloan", false).search();
		query.orgroup("expensetype", expensetypes);

		// query.not("reimbursedstatus","1"); //Do not include in totals. Has not
		// happened yet
		addDateRange(query, "date", inDateRange);

		HitTracker hits = expensesSearcher.search(query.getQuery());
		hits.enableBulkOperations();

		HashMap<String, Object> bycurrency = new HashMap<String, Object>();

		for (Iterator iterator = hits.iterator(); iterator.hasNext();)
		{
			MultiValued data = (MultiValued) iterator.next();
			String currency = (String) data.getValue("currencytype");
			if (currency == null)
			{
				currency = "1";
			}
			Double currencytotal = (Double) bycurrency.get(currency);
			if (currencytotal == null)
			{
				currencytotal = 0.0;
				bycurrency.put(currency, currencytotal);

			}
			Double total = (Double) data.getValue("total");
			currencytotal = addUp(currencytotal, total);
			bycurrency.put(currency, currencytotal);
		}

		Map<String, List<Data>> summarytransfers = getTransfersByCurrencyForEntity(inCollectionId, inDateRange, false);
		for (Map.Entry<String, List<Data>> set : summarytransfers.entrySet())
		{
			List<Data> transfers = (List<Data>) set.getValue();
			String currency = set.getKey();
			for (Iterator iterator = transfers.iterator(); iterator.hasNext();)
			{
				Data transfer = (Data) iterator.next();

				Double currencytotal = (Double) bycurrency.get(currency);
				if (currencytotal == null || currencytotal == 0.0)
				{
					currencytotal = 0.0;
					bycurrency.put(currency, currencytotal);

				}
				currencytotal = currencytotal + (Double) transfer.getValue("total");
				bycurrency.replace(currency, currencytotal);
			}
		}

		return bycurrency;

	}

	public QueryBuilder addDateRange(QueryBuilder inQuery, String inField, DateRange inDateRange)
	{
		if (inDateRange == null || inDateRange.isAllTime())
		{
			return inQuery;
		}
		inQuery.between(inField, inDateRange.getStartDate(), inDateRange.getEndDate());
		return inQuery;
	}

	public Map getNetIncomeByCurrency(String inCollectionId, DateRange inDateRange, String topicid)
	{
		Map incomebycurrency = getTotalIncomesByDateRange(inCollectionId, inDateRange, topicid);
		Map expensesbycurrency = getNetExpenseByCurrency(inCollectionId, inDateRange, topicid);

		HashMap<String, Object> netIncome = new HashMap<String, Object>();

		Iterator<Map.Entry<String, Object>> it = incomebycurrency.entrySet().iterator();
		while (it.hasNext())
		{
			Map.Entry<String, Object> pair = it.next();
			netIncome.putIfAbsent(pair.getKey(), pair.getValue());
		}

		Iterator<Map.Entry<String, Object>> it2 = expensesbycurrency.entrySet().iterator();
		while (it2.hasNext())
		{
			Map.Entry<String, Object> pair = it2.next();
			Double current = (Double) netIncome.get(pair.getKey());
			if (current == null)
			{
				current = 0.0;
				netIncome.putIfAbsent(pair.getKey(), current - (Double) pair.getValue());
			}
			else
			{
				netIncome.replace(pair.getKey(), current - (Double) pair.getValue());
			}
		}

		return netIncome;
	}

	public HitTracker createTracker(List inCollection)
	{
		ListHitTracker tracker = new ListHitTracker(inCollection);
		tracker.setSessionId("financemanager");
		return tracker;
	}

	public List<BankTransaction> getAllInvestmentsByBank(String inBankId, DateRange inDateRange)
	{
		List<BankTransaction> results = getInvestmentsByAccount(null, inBankId, inDateRange);
		return results;
	}

	public List<BankTransaction> getInvestmentsByAccount(String inCollectionId, String inBankId, DateRange inDateRange)
	{
		List<BankTransaction> transactions = new ArrayList();

		HitTracker tracker = null;
		Searcher incomesSearcher = null;
		QueryBuilder query = null;

		incomesSearcher = getMediaArchive().getSearcher("collectiveincome");
		query = addDateRange(incomesSearcher.query(), "date", inDateRange);
		Collection incometypes = getMediaArchive().query("incometype").exact("iscapitalloan", true).search();
		query.orgroup("incometype", incometypes);
		if (inCollectionId != null)
		{
			query.exact("collectionid", inCollectionId);
		}
		tracker = query.exact("paidfromaccount", inBankId).search();
		addAll(incomesSearcher.getSearchType(), tracker, transactions);

		Collection expensetypes = getMediaArchive().query("expensetype").exact("iscapitalloan", true).search();
		incomesSearcher = getMediaArchive().getSearcher("collectiveexpense");
		query = addDateRange(incomesSearcher.query(), "date", inDateRange);
		query.orgroup("expensetype", expensetypes);
		if (inCollectionId != null)
		{
			query.exact("collectionid", inCollectionId);
		}
		tracker = query.exact("ispaid", "true").exact("paidfromaccount", inBankId).search();
		addAll(incomesSearcher.getSearchType(), tracker, transactions);
		for (Iterator iterator = transactions.iterator(); iterator.hasNext();)
		{
			BankTransaction bankTransaction = (BankTransaction) iterator.next();
			if (!"AX-oS0XP5OXsVhEn3agJ".equals(bankTransaction.getValue("expensetype"))) // Disburstment
			{
				bankTransaction.setBePositive(true);
			}
		}
		Collections.sort(transactions, new Comparator<BankTransaction>() {
			public int compare(BankTransaction arg0, BankTransaction arg1)
			{
				int i = arg1.getDate().compareTo(arg0.getDate());
				return i;
			};
		});

		return transactions;
	}

	public List<BankTransaction> getAllTransactionByBank(String inBankId, DateRange inDateRange)
	{
		List<BankTransaction> results = getTransactionsByAccount(null, inBankId, inDateRange);
		return results;
	}

	public List<BankTransaction> getTransactionsByAccount(String inCollectionId, String inBankId, DateRange inDateRange)
	{
		List<BankTransaction> transactions = new ArrayList();

		HitTracker tracker = null;
		Searcher incomesSearcher = null;
		if ("1".equals(inBankId)) // TODO: Add accounts to invoices
		{
			// incomesSearcher = getMediaArchive().getSearcher("transaction");
			// tracker =
			// addDateRange(incomesSearcher.query(),"paymentdate",inDateRange).search();
			// addAll(incomesSearcher.getSearchType(),tracker,transactions);

			// incomesSearcher = getMediaArchive().getSearcher("collectiveinvoice");
			// QueryBuilder query =
			// addDateRange(incomesSearcher.query(),"invoicepaidon",inDateRange);
			// tracker = query.exact("paymentstatus","paid").search();
			// addAll(incomesSearcher.getSearchType(),tracker,transactions);

		}

		incomesSearcher = getMediaArchive().getSearcher("collectiveinvoice");
		QueryBuilder query = addDateRange(incomesSearcher.query(), "invoicepaidon", inDateRange);
		if (inCollectionId != null)
		{
			query.exact("collectionid", inCollectionId);
		}
		tracker = query.exact("paymentstatus", "paid").exact("paidfromaccount", inBankId).search();
		addAll(incomesSearcher.getSearchType(), tracker, transactions);

		incomesSearcher = getMediaArchive().getSearcher("collectiveincome");
		query = addDateRange(incomesSearcher.query(), "date", inDateRange);
		if (inCollectionId != null)
		{
			query.exact("collectionid", inCollectionId);
		}
		tracker = query.exact("paidfromaccount", inBankId).search();
		addAll(incomesSearcher.getSearchType(), tracker, transactions);

		// incomesSearcher = getMediaArchive().getSearcher("collectiveinvestment");
		// query = addDateRange(incomesSearcher.query(),"date",inDateRange);
		// if( inCollectionId != null)
		// {
		// query.exact("collectionid",inCollectionId);
		// }
		// tracker = query.exact("bankaccount",inBankId).search();
		// addAll(incomesSearcher.getSearchType(),tracker,transactions);

		incomesSearcher = getMediaArchive().getSearcher("collectiveexpense");
		query = addDateRange(incomesSearcher.query(), "date", inDateRange);
		if (inCollectionId != null)
		{
			query.exact("collectionid", inCollectionId);
		}
		tracker = query.exact("ispaid", "true").exact("paidfromaccount", inBankId).search();
		addAll(incomesSearcher.getSearchType(), tracker, transactions);

		// incomesSearcher = getMediaArchive().getSearcher("collectivereimbursement");
		// query = addDateRange(incomesSearcher.query(),"paymentdate",inDateRange);
		// if( inCollectionId != null)
		// {
		// query.exact("collectionid",inCollectionId);
		// }
		// tracker =
		// query.exact("ispaid","true").exact("paidfromaccount",inBankId).search();
		// addAll(incomesSearcher.getSearchType(),tracker,transactions);

		// sort
		Collections.sort(transactions, new Comparator<BankTransaction>() {
			public int compare(BankTransaction arg0, BankTransaction arg1)
			{
				int i = arg1.getDate().compareTo(arg0.getDate());
				return i;
			};
		});

		/*
		 * 
		 * #foreach($userid in $pendingexpensesreimburseuser_total.keySet()) <tr> <td></td> <td></td>
		 * <td></td> #set($subtotal = $pendingexpensesreimburseuser_total.get($userid)) <td
		 * class="text-left" style="width:150px">$!mediaarchive.getUser( $userid )</td> <td
		 * class="text-right"> $!context.doubleToMoney($subtotal, 2) </td> <td></td> <td></td> <td></td>
		 * </tr> #end
		 * 
		 * 
		 * AggregationBuilder b = AggregationBuilders.terms("currencytype_total").field("currencytype");
		 * SumBuilder sum = new SumBuilder("total_sum"); sum.field("total"); b.subAggregation(sum);
		 * query.setAggregation(b); query.setHitsPerPage(50); HitTracker hits =
		 * searcher.cachedSearch(context,query);
		 * 
		 * 
		 * //hits.enableBulkOperations(); //Breaks aggregations, when logging all searches
		 * //hits.getActiveFilterValues(); Map currencymap = hits.getAggregationMap("currencytype_total");
		 * // log.info("query" + query + " hits " + hits.size() + " agr:" + hits.getActiveFilterValues() +
		 * " map: " + currencymap); context.putPageValue("currencytype_total",currencymap);
		 * context.putPageValue("searcher", searcher); context.putPageValue("expenses", hits);
		 */

		return transactions;
	}

	public Map<String, Double> getTotalsByCurrencyType(Collection<BankTransaction> transactions)
	{
		Map<String, Double> bytype = new HashMap();

		for (Iterator iterator = transactions.iterator(); iterator.hasNext();)
		{
			BankTransaction transaction = (BankTransaction) iterator.next();
			Double total = bytype.get(transaction.getCurrencyType());
			if (total == null)
			{
				total = 0.0;
			}
			total = total + transaction.getAmount();
			bytype.put(transaction.getCurrencyType(), total);
		}
		return bytype;
	}

	protected void addAll(String inSearchType, HitTracker inTracker, List<BankTransaction> inTransactions)
	{
		for (Iterator iterator = inTracker.iterator(); iterator.hasNext();)
		{
			MultiValued hit = (MultiValued) iterator.next();
			BankTransaction transaction = new BankTransaction(inSearchType, hit);
			inTransactions.add(transaction);

		}

	}

	public HashMap<String, Double> getNetTransafersForUser(String inEntityId, DateRange inDateRange)
	{
		HitTracker hits = getAllTransfersForEntity(inEntityId, inDateRange);

		HashMap<String, Double> bycurrency = new HashMap();

		for (Iterator iterator = hits.iterator(); iterator.hasNext();)
		{
			MultiValued data = (MultiValued) iterator.next();
			String currency = (String) data.getValue("currencytype");
			Double currencytotal = (Double) bycurrency.get(currency);
			if (currencytotal == null || currencytotal == 0.0)
			{
				currencytotal = 0.0;
				bycurrency.put(currency, currencytotal);
			}
			String source = data.get("paymententitysource");

			if (source.equals(inEntityId))
			{
				if (currency.equals("2")) // Work points
				{
					if ("1".equals(data.get("currencytransferstatus")))// pending
					{
						currencytotal = currencytotal + (Double) data.getValue("total");
					}
				}
				else
				{
					currencytotal = currencytotal - (Double) data.getValue("total");
				}
			}
			else
			{
				currencytotal = currencytotal + (Double) data.getValue("total");
			}
			bycurrency.replace(currency, currencytotal);
		}

		return bycurrency;
	}

	public HashMap<String, List<Data>> getTransfersByCurrencyForEntity(String inEntityId, DateRange inDateRange, boolean ifincome)
	{
		HitTracker hits = getAllTransfersForEntity(inEntityId, inDateRange);

		String currenttypeid = "";
		HashMap<String, List<Data>> expensesummarybycurrency = new HashMap();

		for (Iterator iterator = hits.iterator(); iterator.hasNext();)
		{
			MultiValued data = (MultiValued) iterator.next();
			boolean addit = false;
			if (inEntityId.equals(data.get("paymententitydest")))
			{
				if (ifincome)
				{
					addit = true;
				}
			}
			else
				if (!ifincome)
				{
					addit = true;
				}
			if (addit)
			{
				String currencytype = (String) data.getValue("currencytype");
				List<Data> extypes = expensesummarybycurrency.get(currencytype);
				if (extypes == null)
				{
					extypes = new ArrayList();
					expensesummarybycurrency.put(currencytype, extypes);
				}
				extypes.add(data);
			}
		}

		return expensesummarybycurrency;
	}

	public HitTracker getPendingTransfersForEntity(String inEntityId, DateRange inDateRange)
	{
		HitTracker tracker = getTransfersForEntity(inEntityId, null, "1", inDateRange);
		return tracker;

	}

	public HitTracker getAllTransfersForEntity(String inEntityId, DateRange inDateRange)
	{
		HitTracker tracker = getTransfersForEntity(inEntityId, null, null, inDateRange);
		return tracker;
	}

	public HitTracker getTransfersForEntity(String inEntityId, String currencytype, String currencytransferstatus, DateRange inDateRange)
	{
		Searcher incomesSearcher = getMediaArchive().getSearcher("currencytransfer");
		QueryBuilder query = incomesSearcher.query();
		addDateRange(query, "date", inDateRange);

		SearchQuery finalq = query.getQuery();

		QueryBuilder query2 = incomesSearcher.query();
		query2.exact("paymententitysource", inEntityId);
		query2.exact("paymententitydest", inEntityId);
		query2.or();

		finalq.addChildQuery(query2.getQuery());

		if (currencytype != null && !currencytype.equals("*"))
		{
			finalq.addExact("currencytype", currencytype);
		}

		if (currencytransferstatus != null && !currencytransferstatus.equals("*"))
		{
			finalq.addExact("currencytransferstatus", currencytransferstatus);
		}

		finalq.addSortBy("dateDown");
		HitTracker hits = incomesSearcher.search(finalq);
		hits.setHitsPerPage(1000);
		return hits;
	}

	public HashMap<String, Object> getPointsForEntity(String inEntityId, DateRange inDateRange)
	{
		HitTracker hits = getAllTransfersForEntity(inEntityId, inDateRange);

		HashMap<String, Object> bycurrency = new HashMap<String, Object>();

		for (Iterator iterator = hits.iterator(); iterator.hasNext();)
		{
			MultiValued data = (MultiValued) iterator.next();
			String currency = (String) data.getValue("currencytype");

			Double currencytotal = (Double) bycurrency.get(currency);
			if (currencytotal == null || currencytotal == 0.0)
			{
				currencytotal = 0.0;
				bycurrency.put(currency, currencytotal);
			}

			if (currency.equals("2") && "2".equals(data.get("currencytransferstatus"))) // Points are paid so dont add
																						// em
			{
				// Dont add to the total
			}
			else
			{
				String source = data.get("paymententitysource");
				if (!currency.equals("2") && source.equals(inEntityId))
				{
					currencytotal = currencytotal - (Double) data.getValue("total");
				}
				else
				{
					currencytotal = currencytotal + (Double) data.getValue("total");
				}
			}
			bycurrency.replace(currency, currencytotal);
		}

		return bycurrency;
	}

	public Double inDollars(String inCurrency, Double number)
	{

		if (inCurrency.equals("1"))
		{
			return number;
		}
		Data currencytype = getMediaArchive().getCachedData("currencytype", inCurrency);
		if (currencytype == null || currencytype.get("exchangetousd") == null)
		{
			log.info("null currencytype exchange " + inCurrency);
			return -1D;
		}
		double exchange = Double.parseDouble(currencytype.get("exchangetousd"));
		double dollars = MathUtils.divide(number, exchange);
		return dollars;

	}

	public Collection<MultiCurrency> getTotalsReimbursedByUser(String inCollectionId, DateRange inDateRange)
	{
		return getTotalsReimbursedByUser(inCollectionId, inDateRange, null);
	}

	public Collection<MultiCurrency> getTotalsReimbursedByUser(String inCollectionId, DateRange inDateRange, String topicid)
	{
		Searcher expensesSearcher = getMediaArchive().getSearcher("collectiveexpense");
		QueryBuilder query = expensesSearcher.query();
		query.exact("collectionid", inCollectionId);
		query.exact("reimbursedstatus", "2"); // paid
		if (topicid != null)
		{
			query.exact("collectiveproject", topicid);
		}
		addDateRange(query, "date", inDateRange);

		HitTracker hits = expensesSearcher.search(query.getQuery());
		hits.setHitsPerPage(1000);

		Map<String, MultiCurrency> byUsers = new HashMap(); // By currency? Or just dollars?

		for (Iterator iterator = hits.iterator(); iterator.hasNext();)
		{
			MultiValued hit = (MultiValued) iterator.next();
			String user = hit.get("reimburseuser");
			MultiCurrency bycurrency = byUsers.get(user);
			if (bycurrency == null)
			{
				bycurrency = new MultiCurrency();
				bycurrency.setKeyedOn(user);
				byUsers.put(user, bycurrency);
			}
			String currency = hit.get("currencytype");
			bycurrency.addTo(currency, hit.getDouble("total"));
		}
		return byUsers.values();

	}

	public Double getDollarForPointForUser(String inUserId, String inCollectionId)
	{
		Data oneuser = getMediaArchive().query("librarycollectionusers").exact("collectionid", inCollectionId).exact("followeruser", inUserId).searchOne();

		Object value = null;
		if (oneuser != null)
		{
			value = oneuser.getValue("dollarsperpoints");
		}
		if (value == null)
		{
			Data collection = getMediaArchive().getCachedData("librarycollection", inCollectionId);
			value = collection.getValue("dollarsperpoints");
		}
		if (value == null || value.toString().equals("0.0"))
		{
			return 1D;
		}
		return Double.parseDouble(value.toString());
	}
}

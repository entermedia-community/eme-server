package org.entermedia.transactions;

import java.text.DecimalFormat;
import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.entermediadb.asset.MediaArchive;
import org.openedit.CatalogEnabled;
import org.openedit.Data;
import org.openedit.ModuleManager;
import org.openedit.MultiValued;
import org.openedit.data.Searcher;

public class TransactionManager implements CatalogEnabled
{
	private static final Log log = LogFactory.getLog(TransactionManager.class);
	protected String fieldCatalogId;
	protected MediaArchive fieldMediaArchive;
	protected ModuleManager fieldModuleManager;
	protected DecimalFormat df = new DecimalFormat("#.00");

	public String getCombinedTotalIncome(String inCollectionId, String currency)
	{
		if (inCollectionId == null)
		{
			return "0";
		}
		double total = getTransactionTotal(inCollectionId);
		double other = getOtherDonationTotal(inCollectionId);
		double combined = total + other;
		return formatCurrency(combined, currency);
	}

	private String formatCurrency(double inCombined, String inCurrency)
	{
		double combined = inCombined;
		// Convert to local currency
		if (inCurrency == null || inCurrency.equals("1"))
		{

		}
		else
		{
			MultiValued currencyType = (MultiValued) getMediaArchive().getCachedData("currencytype", inCurrency);
			double extchange = currencyType.getFloat("exchangetousd");

			combined = combined * extchange;

		}
		return df.format(combined); // TODO Handle Qs
	}

	private double getOtherDonationTotal(String inCollectionId)
	{
		Searcher invoiceSearcher = getMediaArchive().getSearcher("collectiveincome");
		Collection<Data> clientinvoices = invoiceSearcher.query().exact("collectionid", inCollectionId).search();
		double amount = 0;

		for (Data it : clientinvoices)
		{
			MultiValued real = (MultiValued) invoiceSearcher.loadData(it);
			String type = real.get("currencytype");
			if (type == null || type.equals("1"))
			{
				amount = amount + real.getFloat("total");
			}
			else
			{
				MultiValued othercurrencyType = (MultiValued) getMediaArchive().getCachedData("currencytype", type);
				double extchange = othercurrencyType.getFloat("exchangetousd");
				// Convert to dollars first
				double inusd = real.getFloat("total") / extchange;
				amount = amount + inusd;
			}

		}
		return amount;
	}

	public double getTransactionTotal(String inCollectionId)
	{
		if (inCollectionId == null)
		{
			return 0D;
		}
		Searcher invoiceSearcher = getMediaArchive().getSearcher("transaction");
		Collection<Data> clientinvoices = invoiceSearcher.query().exact("collectionid", inCollectionId).search();
		double amount = 0;

		for (Data it : clientinvoices)
		{
			MultiValued real = (MultiValued) invoiceSearcher.loadData(it);

			try
			{
				String type = real.get("currencytype");
				if (type == null || type.equals("1"))
				{
					amount = amount + real.getFloat("totalprice");
				}
				else
				{
					MultiValued othercurrencyType = (MultiValued) getMediaArchive().getCachedData("currencytype", type);
					double extchange = othercurrencyType.getFloat("exchangetousd");
					// Convert to dollars first
					double inusd = real.getFloat("totalprice") / extchange;
					amount = amount + inusd;
				}

			}
			catch (Exception e)
			{
				log.error("Can't retrieve/parse invoice " + real.get("name") + " amount", e);
			}
		}

		return amount;

	}

	public String getCatalogId()
	{
		return fieldCatalogId;
	}

	public void setCatalogId(String inCatalogId)
	{
		fieldCatalogId = inCatalogId;
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

	public ModuleManager getModuleManager()
	{
		return fieldModuleManager;
	}

	public void setModuleManager(ModuleManager inModuleManager)
	{
		fieldModuleManager = inModuleManager;
	}

}

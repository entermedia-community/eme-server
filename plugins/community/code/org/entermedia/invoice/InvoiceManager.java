package org.entermedia.invoice;

import java.text.DecimalFormat;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.entermediadb.asset.MediaArchive;
import org.openedit.CatalogEnabled;
import org.openedit.Data;
import org.openedit.ModuleManager;
import org.openedit.MultiValued;
import org.openedit.data.Searcher;
import org.openedit.hittracker.HitTracker;
import org.openedit.users.User;

public class InvoiceManager implements CatalogEnabled
{
	private static final Log log = LogFactory.getLog(InvoiceManager.class);
	protected String fieldCatalogId;
	protected MediaArchive fieldMediaArchive;
	protected ModuleManager fieldModuleManager;
	protected DecimalFormat df = new DecimalFormat("#.00");

	public int getUnpaidInvoiceNumber(String inCollectionId)
	{
		int res = 0;
		Collection<Data> clientinvoices = getMediaArchive().query("clientinvoice").match("clientinvoicestatus", InvoiceStatus.UNPAID).exact("librarycollection", inCollectionId).search();

		if (clientinvoices != null)
		{
			res = clientinvoices.size();
		}
		return res;
	}

	public int getPaidInvoiceNumber(String inCollectionId)
	{
		int res = 0;
		Collection<Data> clientinvoices = getMediaArchive().query("clientinvoice").match("status", InvoiceStatus.PAID).exact("librarycollection", inCollectionId).search();

		if (clientinvoices != null)
		{
			res = clientinvoices.size();
		}
		return res;
	}

	public int getCancelledInvoiceNumber(String inCollectionId)
	{
		int res = 0;
		Collection<Data> clientinvoices = getMediaArchive().query("clientinvoice").match("status", InvoiceStatus.CANCELED).exact("librarycollection", inCollectionId).search();

		if (clientinvoices != null)
		{
			res = clientinvoices.size();
		}
		return res;
	}

	public int getRefundedInvoiceNumber(String inCollectionId)
	{
		int res = 0;
		Collection<Data> clientinvoices = getMediaArchive().query("clientinvoice").match("status", InvoiceStatus.REFUNDED).exact("librarycollection", inCollectionId).search();

		if (clientinvoices != null)
		{
			res = clientinvoices.size();
		}
		return res;
	}

	public String getAccountBalance(String inCollectionId)
	{
		Searcher invoiceSearcher = getMediaArchive().getSearcher("clientinvoice");
		Collection<Data> clientinvoices = invoiceSearcher.query().match("clientinvoicestatus", InvoiceStatus.UNPAID).exact("librarycollection", inCollectionId).search();
		double amount = 0;

		for (Data it : clientinvoices)
		{
			MultiValued real = (MultiValued) invoiceSearcher.loadData(it);

			try
			{
				amount += (double) real.getValue("amount");
			}
			catch (Exception e)
			{
				log.error("Can't retrieve/parse invoice " + real.get("name") + " amount", e);
			}
		}
		return df.format(amount);
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

	protected void saveNewInvoiceForStore(MediaArchive mediaArchive, MultiValued invoice, MultiValued product)
	{
		int days = calculateDays(invoice);

		double price = product.getDouble("productprice");

		// Daily?
		double totalcost = price * days;

		Map map = new HashMap();
		map.put("productid", product.getId());
		map.put("productquantity", days);
		map.put("productprice", totalcost);

		List products = new ArrayList(1);
		products.add(map);
		invoice.setValue("productlist", products);
		invoice.setValue("totalprice", totalcost);
		if (invoice.getValue("currencytype") != null)
		{
			invoice.setValue("currencytype", product.get("currencytype"));
		}
		// Grab email from user who ordered the product
		User sentto = mediaArchive.getUser(invoice.get("forcustomer"));
		User sentto2 = mediaArchive.getUser(invoice.get("owner"));
		StringBuffer to = new StringBuffer();
		if (sentto != null && sentto.getEmail() != null)
		{
			to.append(sentto.getEmail());
		}
		if (sentto != sentto2 && sentto2 != null && sentto2.getEmail() != null)
		{
			if (sentto != null && sentto.getEmail() != null)
			{
				to.append(",");
			}
			to.append(sentto2.getEmail());
		}
		if (to.length() > 0)
		{
			invoice.setValue("sentto", to.toString()); // Collective admin plus the users
		}
		invoice.setValue("paymentstatus", "sendinvoice");

		mediaArchive.saveData("collectiveinvoice", invoice);
		mediaArchive.fireSharedMediaEvent("billing/sendinvoices");

	}

	public int calculateDays(MultiValued invoice)
	{
		Date startdate = invoice.getDate("duedate");
		if (startdate == null)
		{
			startdate = invoice.getDate("startdate");
		}
		Date enddate = invoice.getDate("enddate");
		long noOfDaysBetween = ChronoUnit.DAYS.between(startdate.toInstant(), enddate.toInstant());

		return (int) Math.ceil(noOfDaysBetween);
	}

	// TODO: Move this to InvoiceManager

	public HitTracker getInvoiceFromMonth(String status, int year, int month)
	{
		Calendar start = month == 0 ? new GregorianCalendar(year, 0, 1) : new GregorianCalendar(year, month - 1, 1);

		// Calendar end = month == 0 ? new GregorianCalendar(year, 11, 31) : new
		// GregorianCalendar(year, month, 1);
		// end.add(Calendar.DAY_OF_YEAR, -1);

		Calendar end = month == 0 ? new GregorianCalendar(year, 11, 31) : new GregorianCalendar(year, month, 1);
		// fix 31th
		int days = end.getActualMaximum(Calendar.DAY_OF_MONTH);
		end.set(Calendar.DAY_OF_MONTH, days);
		end.set(Calendar.MINUTE, 59);
		end.set(Calendar.HOUR_OF_DAY, 23);
		end.set(Calendar.SECOND, 59);

		HitTracker invoice = getMediaArchive().query("collectiveinvoice").exact("paymentstatus", status).between("duedate", start.getTime(), end.getTime()).sort("duedateDown").search();
		return invoice;
	}
	// TODO: Move this to InvoiceManager

	public Data getInvoiceById(String invoiceId)
	{
		Data invoice = getMediaArchive().getSearcherManager().getCachedData(getCatalogId(), "collectiveinvoice", invoiceId);
		return invoice;
	}

	// TODO: Move this to InvoiceManager

	public ArrayList getInvoiceProductList(String invoiceId)
	{
		Data invoice = getMediaArchive().getSearcherManager().getCachedData(getCatalogId(), "collectiveinvoice", invoiceId);
		if (invoice == null)
		{
			return null;
		}
		ArrayList products = (ArrayList) invoice.getValue("productlist");
		return products;
	}
	// TODO: Move this to InvoiceManager

	/*
	 * public String getProductName (String productId) { Data product =
	 * getMediaArchive().getSearcherManager().getData(getCatalogId(), "collectiveproduct", productId);
	 * if (product == null) { return null; } String name = (String) product.getValue("name"); return
	 * name; }
	 */

	public MultiValued getProductById(String productId)
	{
		MultiValued product = (MultiValued) getMediaArchive().getSearcherManager().getCachedData(getCatalogId(), "collectiveproduct", productId);
		if (product == null)
		{
			return null;
		}
		return product;
	}

	public Data getWorkspaceById(String workspaceId)
	{
		Data workspace = getMediaArchive().getSearcherManager().getCachedData(getCatalogId(), "librarycollection", workspaceId);
		if (workspace == null)
		{
			return null;
		}
		return workspace;
	}

	public Date getEndDate(MultiValued product)
	{
		Calendar endbilldate = Calendar.getInstance();
		Date nextBillOn = product.getDate("nextbillon");
		endbilldate.setTime(nextBillOn);
		if (product.getInt("recurringperiod") == 0)
		{
			// Daily endbilldate is nextbillon
		}
		if (product.getInt("recurringperiod") == 1)
		{
			endbilldate.add(Calendar.DAY_OF_YEAR, -1);
		}

		return endbilldate.getTime();
	}

}

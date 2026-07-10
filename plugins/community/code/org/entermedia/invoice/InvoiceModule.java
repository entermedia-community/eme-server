package org.entermedia.invoice;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.entermediadb.asset.MediaArchive;
import org.entermediadb.asset.modules.BaseMediaModule;
import org.openedit.Data;
import org.openedit.MultiValued;
import org.openedit.WebPageRequest;
import org.openedit.hittracker.HitTracker;
import org.openedit.util.DateStorageUtil;

public class InvoiceModule extends BaseMediaModule
{
	public void loadInvoiceManager(WebPageRequest inReq)
	{
		String catalogid = inReq.findPathValue("catalogid");
		InvoiceManager manager = (InvoiceManager) getModuleManager().getBean(catalogid, "invoiceManager");

		inReq.putPageValue("invoiceManager", manager);
	}

	public void saveNewInvoiceForStore(WebPageRequest inReq)
	{
		MediaArchive mediaArchive = getMediaArchive(inReq);
		InvoiceManager manager = (InvoiceManager) mediaArchive.getBean("invoiceManager");
		// Grab data?
		MultiValued invoice = (MultiValued) inReq.getPageValue("data");

		String range = inReq.getRequestParameter("datarange");
		if (range != null)
		{
			String[] parts = range.split(" ");
			Date start = DateStorageUtil.getStorageUtil().parseFromStorage(parts[0]);
			invoice.setValue("duedate", start);
			Date enddate = DateStorageUtil.getStorageUtil().parseFromStorage(parts[2]);
			Calendar endcal = new GregorianCalendar();
			endcal.setTime(enddate);
			int year = endcal.get(Calendar.YEAR); // 2012
			if (year < 1000)
			{
				endcal.set(Calendar.YEAR, year + 2000);
				enddate = endcal.getTime();
			}

			invoice.setValue("enddate", enddate);
		}
		else
		{
			String sduedate = inReq.getRequestParameter("duedate");
			Date duedate = DateStorageUtil.getStorageUtil().parseFromStorage(sduedate);
			invoice.setValue("duedate", duedate);

			String senddate = inReq.getRequestParameter("enddate");
			Date enddate = DateStorageUtil.getStorageUtil().parseFromStorage(senddate);
			invoice.setValue("enddate", enddate);
		}

		String productid = inReq.getRequestParameter("productlist.add");
		MultiValued product = null;
		if (productid != null)
		{
			product = (MultiValued) mediaArchive.getData("collectiveproduct", productid);
			manager.saveNewInvoiceForStore(mediaArchive, invoice, product);
			inReq.setRequestParameter("invoiceid", invoice.getId());
		}
		else
		{
			// Just editing an existing invoice
			productid = inReq.getRequestParameter("productid");
			product = (MultiValued) mediaArchive.getData("collectiveproduct", productid);

		}
		inReq.putPageValue("data", product); // Render on details page
		inReq.putPageValue("collectiveproduct", product); // Render on details page
		// TODO email someone
		// Depending on the producttype??
		inReq.putPageValue("status", "saved");

	}

	public void getRentalsByDate(WebPageRequest inReq)
	{
		MediaArchive mediaArchive = getMediaArchive(inReq);

		String collectionid = inReq.getRequestParameter("collectionid");

		String year_s = inReq.getRequestParameter("year");
		String month_s = inReq.getRequestParameter("month");

		int year = 0;
		if (year_s == null)
		{
			year = mediaArchive.getCurrentYear();
		}
		else
		{
			year = Integer.parseInt(year_s);
		}
		int month = 0;
		if (month_s != null)
		{
			month = Integer.parseInt(month_s);
		}

		Calendar start = month == 0 ? new GregorianCalendar(year, 0, 1) : new GregorianCalendar(year, month - 1, 1);
		Calendar end = month == 0 ? new GregorianCalendar(year, 11, 31) : new GregorianCalendar(year, month - 1, 1);
		// fix 31th
		int days = end.getActualMaximum(Calendar.DAY_OF_MONTH);
		end.set(Calendar.DAY_OF_MONTH, days);
		end.set(Calendar.MINUTE, 59);
		end.set(Calendar.HOUR_OF_DAY, 23);
		end.set(Calendar.SECOND, 59);

		HitTracker rentals = mediaArchive.query("collectiveinvoice").exact("collectionid", collectionid).between("duedate", start.getTime(), end.getTime()).sort("enddateUp").search();
		inReq.putPageValue("rentals", rentals);
	}

	public void getCommunityRentalsByDate(WebPageRequest inReq)
	{
		MediaArchive mediaArchive = getMediaArchive(inReq);

		Data communitytagcategory = (Data) inReq.getPageValue("communitytag");

		HitTracker collections = mediaArchive.query("librarycollection").exact("communityid", communitytagcategory.getId()).search();

		String year_s = inReq.getRequestParameter("year");
		String month_s = inReq.getRequestParameter("month");

		int year = 0;
		if (year_s == null)
		{
			year = mediaArchive.getCurrentYear();
		}
		else
		{
			year = Integer.parseInt(year_s);
		}
		int month = 0;
		if (month_s != null)
		{
			month = Integer.parseInt(month_s);
		}

		Calendar start = month == 0 ? new GregorianCalendar(year, 0, 1) : new GregorianCalendar(year, month - 1, 1);
		Calendar end = month == 0 ? new GregorianCalendar(year, 11, 31) : new GregorianCalendar(year, month - 1, 1);
		// fix 31th
		int days = end.getActualMaximum(Calendar.DAY_OF_MONTH);
		end.set(Calendar.DAY_OF_MONTH, days);
		end.set(Calendar.MINUTE, 59);
		end.set(Calendar.HOUR_OF_DAY, 23);
		end.set(Calendar.SECOND, 59);

		HitTracker rentals =
			mediaArchive.query("collectiveinvoice").orgroup("collectionid", collections.collectValues("id")).between("duedate", start.getTime(), end.getTime()).sort("enddateUp").search();
		inReq.putPageValue("rentals", rentals);
	}

}

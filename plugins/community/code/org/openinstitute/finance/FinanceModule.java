package org.openinstitute.finance;

import org.entermediadb.asset.modules.BaseMediaModule;
import org.openedit.WebPageRequest;

public class FinanceModule extends BaseMediaModule
{

	public void loadDateRange(WebPageRequest inReq)
	{
		DateRange range = null;
		String year = null;
		String clearfilters = inReq.getRequestParameter("clearfilters");
		if (!Boolean.parseBoolean(clearfilters))
		{
			range = (DateRange) inReq.getSessionValue("daterange");
			year = inReq.getRequestParameter("year");
		}
		if (year != null)
		{
			range = new DateRange();
			String month = inReq.getRequestParameter("month");

			int intyear = Integer.parseInt(year);
			if (month != null && !month.equals("0"))
			{
				if (intyear == -1)
				{
					intyear = 0; // Only
				}
				range.setYearAndMonth(intyear, Integer.parseInt(month));
			}
			else
				if (intyear == -1)
				{
					range.setAllTime(true);
					range.setYearPicked(-1);
				}
				else
				{
					range.setYearToDate(intyear);
				}
		}
		if (range == null)
		{
			range = new DateRange();
			range.setAllTime(false);
			range.setYearToDate(0); // YTD
		}
		inReq.putSessionValue("daterange", range);

	}

	public FinanceManager loadFinanceManager(WebPageRequest inReq)
	{
		String catalogid = inReq.findPathValue("catalogid");
		FinanceManager manager = (FinanceManager) getModuleManager().getBean(catalogid, "financeManager");
		inReq.putPageValue("financeManager", manager);
		return manager;
	}

}

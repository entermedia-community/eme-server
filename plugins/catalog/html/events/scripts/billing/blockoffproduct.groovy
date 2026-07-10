package billing;

import org.openedit.Data
import org.openinstitute.finance.BookingDate
import org.openinstitute.finance.DateRange
 

public void runit()
{
	//Search for dates
	Collection products = mediaarchive.query("collectiveproduct").exact("producttype","5").search();

	log.info("Products " + products.size());
	Collection tosave = new ArrayList();
	for(Data product in products)
	{
        Date now = new Date();
        Date from = mediaarchive.getBean("dateStorageUtil").addDaysToDate(now,-200); //Anything recently booked?
		Collection invoices = mediaarchive.query("collectiveinvoice").after("duedate",from).exact("productlist.productid",product.getId()).sort("duedate").search();
		//context.putPageValue("invoices",invoices);
		DateRange range = new DateRange();
		
		for(Data invoice in invoices)
		{
			Date startDate = invoice.getDate("duedate");
			Date endDate = invoice.getDate("enddate");
			range.addBlockedDateRange(startDate, endDate);
		}
		log.info("Invoices " + invoices.size() + " " + invoices.getSearchQuery() + " show dates "+ range.getBlockedDates());
		//context.putPageValue("blockeddates",range);
		
		Collection toblock = new ArrayList();
		for(BookingDate blocked in range.getBlockedDates())
		{
			toblock.add( blocked.toDateString() );
		}
		if( !toblock.isEmpty())
		{
			product.setValue("blockeddates", toblock);
			log.info(product.getName() + " Blocked " + toblock);
			tosave.add(product);
		}
	}
	log.info("Saved " + tosave);
	mediaarchive.getSearcher("collectiveproduct").saveAllData(tosave, null);
}

runit();

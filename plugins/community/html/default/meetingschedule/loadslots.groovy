import org.entermediadb.asset.MediaArchive
import org.openedit.Data
import org.openedit.util.DateStorageUtil



public void init()
{
MediaArchive archive = context.getPageValue("mediaarchive");
	//Search for slots
	DateStorageUtil util = DateStorageUtil.getStorageUtil();
	
	TimeZone TZ = TimeZone.getTimeZone("America/New_York")
	
	Calendar now = Calendar.getInstance(TZ);
	Calendar startofday = util.createCalendar(TZ);
	List nextthree = new ArrayList();
	
	int day = startofday.get(Calendar.DAY_OF_YEAR);
	if(now.get(Calendar.HOUR_OF_DAY) > 12) {
		day++;
	}
	while(nextthree.size() < 12)
	{
		//log.info("before..." + today1.getTime());
		startofday.set(Calendar.DAY_OF_YEAR, day);
		//log.info("after..." + today1.getTime());
		if( startofday.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY )
		{
			day++;
			startofday.set(Calendar.DAY_OF_YEAR, day);
		}
		if( startofday.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY )
		{
			day += 2;
			startofday.set(Calendar.DAY_OF_YEAR, day);
		}
		int hour = 9;
		while( nextthree.size() < 12 && hour <= 16) //Search 3 times
		{
			startofday.set(Calendar.HOUR_OF_DAY, hour);
			Data found = archive.query("clientmeeting").exact("time",startofday.getTime()).searchOne();
			if( found == null)
			{
				Calendar good = startofday.clone();
				nextthree.add(good.getTime());
			}
			hour++;
		}
		
		day++;
	}
	log.info("request processing..." + nextthree);
	context.putPageValue("founddays", nextthree);
}

init();






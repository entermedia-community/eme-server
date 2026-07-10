package users;

import org.entermediadb.asset.MediaArchive
import org.entermediadb.asset.Asset
import org.entermediadb.asset.Category
import org.openedit.Data
import org.openedit.data.QueryBuilder
import org.openedit.data.Searcher
import org.openedit.hittracker.HitTracker
import org.openedit.util.DateStorageUtil

//loop all year


public void init() {

	String year = context.getRequestParameter("year");
	String month = context.getRequestParameter("month");
	if (year== null || month == null) {
		//Generate All
		GregorianCalendar cal = new GregorianCalendar();
		Integer thisyear = cal.get(Calendar.YEAR);
		Integer thismonth = cal.get(Calendar.MONTH);
		thismonth = thismonth + 1;
		for(Integer c = 1; c <= thismonth; c++) {
			bymonth(thisyear, c);
		}
		
	}
	else {
		Integer yearInt = Integer.parseInt(year);
		Integer monthInt = Integer.parseInt(month)
		bymonth(yearInt, monthInt);
	}
}


public void bymonth(Integer yearInt, Integer monthInt) {
	MediaArchive archive = context.getPageValue("mediaarchive");

		monthInt = monthInt -1;//Zero base
	
	GregorianCalendar cal = new GregorianCalendar();
	cal.setTime(new Date());
	cal.set(Calendar.YEAR, yearInt);
	cal.set(Calendar.MONTH, monthInt);
	cal.set(Calendar.DAY_OF_MONTH, cal.getActualMinimum(Calendar.DAY_OF_MONTH));
	cal.set(Calendar.HOUR_OF_DAY, 0);
	cal.set(Calendar.MINUTE, 0);
	cal.set(Calendar.SECOND, 0);
	cal.set(Calendar.MILLISECOND, 0);
	Date start = cal.getTime();
	//log.info("to "+ start);

	context.putPageValue("since", start);


	int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
	cal.set(Calendar.DAY_OF_MONTH, days);
	cal.set(Calendar.MINUTE, 59);
	cal.set(Calendar.HOUR_OF_DAY, 23);
	cal.set(Calendar.SECOND, 59);

	Date onemonth = cal.getTime();
	//log.info("to "+ onemonth);

	Searcher tasksearcher = archive.getSearcher("goaltask");
	QueryBuilder q = tasksearcher.query();
	HitTracker alltasks = q.between("completedon", start, onemonth).sort("completedby").search();
	//log.info("Query: " + alltasks.getSearchQuery());

	//log.info("Found ${alltasks.size()} for for ${monthInt}" );
		
	Map<String, Map<String, List>> usersupdated = new HashMap();

	for (Iterator iterator = alltasks.iterator(); iterator.hasNext();)
	{
		Data  task = (Data) iterator.next();
		String userid = task.getValue("completedby");
		String collectionid = task.getValue("collectionid");
		String usercollectionid = "${userid}|${collectionid}";
		
		if (task.getValue("projectdepartment") == null)
		{
			log.info("No projectdepartment on " + task.getName());
			continue;
		}
		Category cat = archive.getCategory(task.getValue("projectdepartment"));
		if( cat == null)
		{
			tasktotal = 10;
		}
		else 
		{
			Double tasktotal = cat.getValue("goalpoints");
			if (tasktotal == null || tasktotal == 0) {
				tasktotal = 10;
			}
		}		
		Map<String, List> currentuser = usersupdated.get(usercollectionid);
		if(currentuser == null) {
			currentuser = new HashMap();
		}
		Double usertotal = (Double) currentuser.get("total");
		if (usertotal == null) {
			usertotal = 0.0;
		}
		
		usertotal = usertotal + tasktotal;
		//log.info("Adding $usertotal = $usertotal + $tasktotal  for ${monthInt}");

		if(task.getValue("completedurgent")) {
			usertotal = usertotal + 10;
		}
		currentuser.put("userid",userid);
		currentuser.put("collectionid",collectionid);
		currentuser.put("total",usertotal);
		currentuser.put("month",monthInt+1);
		currentuser.put("year",yearInt);
		usersupdated.put(usercollectionid, currentuser);
	}
	//log.info("usersupdated size ${usersupdated.size()} for ${monthInt}" );
	
	//Update by user
	Searcher statementsearcher = archive.getSearcher("currencytransfer");
	Calendar today = Calendar.getInstance();
	int savecount = 0;
	if (usersupdated.size()>0) {
		for (String usercollectionid in usersupdated.keySet())
		{
			Map<String, List> currentuser = usersupdated.get(usercollectionid);
			
			String userid = usercollectionid.split("\\|")[0];
			String collectionid = usercollectionid.split("\\|")[1];
			//search user+month+year
			
			
			Data rowexists = (Data) statementsearcher.query().exact("paymententitydest", collectionid)
					.exact("paymententitysource", userid)			
					.exact("generatedbyscript", "monthlystatements")
					.between("date", start, onemonth)
					.searchOne(); 
			
			if (rowexists != null) 
			{
				double d1 = currentuser.get("total");
				double d2 = rowexists.getValue("total");
				if( d1 != d2)
				{
					rowexists.setValue("total", currentuser.get("total"));
					statementsearcher.saveData(rowexists);
					savecount++;
				}	
			}
			else {
				Data row = statementsearcher.createNewData();
				row.setValue("total", currentuser.get("total"));
				row.setValue("generatedbyscript", "monthlystatements");
				row.setValue("paymententitysourcetype", "user");
				row.setValue("paymententitysource", userid);
				row.setValue("paymententitydest", collectionid);
				row.setValue("paymententitydesttype", "librarycollection");
				row.setValue("currencytransferstatus", "1"); //Pending to be paid
				row.setValue("currencytype", "2");
				row.setValue("expensetype", "2");
				//String dates = DateStorageUtil.getStorageUtil().formatForStorage(start);
				//String endate = DateStorageUtil.getStorageUtil().formatForStorage(onemonth);
				Data collection = archive.getCachedData("librarycollection",collectionid);
				String name = null;
				if( collection != null )
				{
						name = collection.getName()
				}
				else
				{
					name = collectionid;
				}
				int month = monthInt + 1;
				String dates = "${name} ( ${month}/${yearInt} )";
				row.setValue("name",  dates );
				row.setValue("date", onemonth);
				statementsearcher.saveData(row);
				savecount++;
			}
		}
	}
	int month = monthInt + 1;
	if( savecount > 0)
	{
		log.info("${month}/${yearInt} Updated $savecount transactions");
	}
}


init();
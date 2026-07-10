package users;

import org.entermediadb.asset.MediaArchive
import org.openedit.Data
import org.openedit.data.Searcher
import org.openedit.hittracker.HitTracker
import org.openedit.users.User
import org.openedit.users.UserSearcher



public void init() {
	//String useridA = context.getRequestParameter("copytoid"); //will be keept//
	//String useridB = context.getRequestParameter("copyid"); //move from//
	
	MediaArchive archive = context.getPageValue("mediaarchive");
	UserSearcher usersearch = archive.getSearcherManager().getSearcher("system","user");
	
	HitTracker allusers = usersearch.query().exact("enabled", true).exists("email").sort("email").sort("creationdateUp").search();
	allusers.enableBulkOperations()
	log.info(allusers)
	
	String currentuserid  = ""
	String currentuseremail  = ""
	for (user in allusers) {
		if (currentuseremail == user.email)
		{
			merge(currentuserid, user.id)
		}	
		else {
			currentuserid = user.id
			currentuseremail = user.email
		}
	}
	 
}

 
public void merge(String useridA, String useridB)
{
	
	
	//useridA = "userA"; //will be keept//
	//useridB = "userB";
	
	if(useridA == null || useridB == null) {
		log.info("No users defined");
		return;
	}
	
	
	MediaArchive archive = context.getPageValue("mediaarchive");
	UserSearcher usersearch = archive.getSearcherManager().getSearcher("system","user");
	
	User userA = null;
	userA = usersearch.getUser(useridA);
	
	User userB = null;
	userB = usersearch.getUser(useridB);
	
	if (userA == null || userB == null) {
		log.info("Users not found "+userA +" - "+userB)
		return;
	}
	
	
	HashMap dependants = new HashMap();
	
	dependants.put("asset", "owner");
	dependants.put("chatterbox", "user");
	dependants.put("goaltask", "owner");
	dependants.put("goaltask", "startedby");
	dependants.put("goaltask", "completedby");
	dependants.put("librarycollection", "owner");
	dependants.put("librarycollectionusers", "followeruser");
	dependants.put("transaction", "userid");
	dependants.put("transactionLog", "user");
	dependants.put("projectgoal", "owner");
	dependants.put("userupload", "owner");
	dependants.put("collectiveexpense", "user");
	dependants.put("collectiveexpense", "reimburseuser");
	dependants.put("collectivereimbursement", "user");
	dependants.put("collectiveincome", "user");
	dependants.put("collectiveinvoice", "owner");
	dependants.put("collectiveproduct", "owner");
	dependants.put("collectivinvestment", "user");
	dependants.put("collectivinvestment", "investmentuser");
	
	dependants.keySet().each {
		String table = it;
		String field = dependants.get(it);
		update(table, field, useridB, useridA);
		
	}
	//Disable the user
	userB.setEnabled(false);
	usersearch.saveData(userB)
	
}

public void update(String inTable, String inField,String oldvalue,  String newValue) {
	
	
	MediaArchive archive = context.getPageValue("mediaarchive");
	Searcher searcher = archive.getSearcher(inTable);
	
	Collection toupdate = searcher.fieldSearch(inField, oldvalue);
	toupdate = new ArrayList(toupdate);
	Collection tosave = new ArrayList()
	toupdate.each {
		
		Data hit = searcher.loadData(it);
		hit.setValue(inField, newValue);
		tosave.add(hit)
	}
	log.info("${inTable} moved ${oldvalue}  to ${newValue}  ${tosave.size()} records")
	searcher.saveAllData(tosave, null)
}

init();





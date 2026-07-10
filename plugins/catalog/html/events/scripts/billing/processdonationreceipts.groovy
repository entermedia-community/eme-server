package billing;

import org.entermediadb.asset.MediaArchive
import org.entermediadb.email.WebEmail
import org.openedit.*
import org.openedit.data.Searcher
import org.openedit.hittracker.HitTracker
import org.openedit.users.User
import org.openedit.util.DateStorageUtil
import org.openedit.util.URLUtilities

public void init() {
	MediaArchive mediaArchive = context.getPageValue("mediaarchive");
	Searcher transactionSearcher = mediaArchive.getSearcher("transaction");
	//Searcher receiptSearcher = mediaArchive .getSearcher("donationreceipt");
	
	sendDonationReceipt(mediaArchive, transactionSearcher);
}

private void sendDonationReceipt(MediaArchive mediaArchive, Searcher transactionSearcher) {
	HitTracker pendingNotification = transactionSearcher.query().exact("receiptstatus","new").search();
	pendingNotification.setHitsPerPage(20);
	pendingNotification.enableBulkOperations();
	if(pendingNotification.size()>0) 
	{
		log.info("Sending Notification for " + pendingNotification.size() + " Donations");
		sendReceipt(mediaArchive, transactionSearcher, pendingNotification.getPageOfHits(), "notificationsent");
	}
}

private void sendReceipt(MediaArchive mediaArchive, Searcher transactionSearcher, Collection receipts, String iteratorType) {
	
	
	Integer count = 0; 
	for (Iterator receiptIterator = receipts.iterator(); receiptIterator.hasNext();) {
	
		Data receipt = transactionSearcher.loadData(receiptIterator.next());
		User user = mediaArchive.getUser(receipt.getValue("userid"));
		
		String receiptemail = receipt.getValue("paymentemail");
		
		if (receiptemail == null && user != null) {
			//get it from user account
			receiptemail = user.getEmail();
		}
		
		if (receiptemail != null) {
			String emailbody = "";
			String subject = "";
			String collectionid = receipt.getValue("collectionid");
			
			Data collection = mediaArchive.getData("librarycollection", collectionid);
			if(collection == null) {
				log.info("Can't send receipt, project does not exists: " + collectionid)
				continue;
			}		
			Map objects = new HashMap();
			
			objects.put("mediaarchive", mediaArchive);
			
			objects.put("receipt", receipt);
			
			String dates = DateStorageUtil.getStorageUtil().formatDateObj(receipt.getValue("paymentdate"), "dd-MM-YYYY");
			objects.put("donationdate", dates);
			
			
			if(user != null)
			{
				objects.put("donor", (String) user.getScreenName());
			}
			else 
			{
				objects.put("donor", "Sponsor");
			}

			objects.put("amount", "\$" + context.doubleToMoney(receipt.getValue("totalprice"), 2) );
			
			Data currencytype = mediaArchive.getCachedData("currencytype", receipt.getValue("currencytype"));
			if (currencytype != null)
			{
				objects.put("currency", currencytype.getText("name", context));
			}
			else 
			{
				objects.put("currency", "USD");
			}
			
			String collection_url = '';
			String collection_url_donation = '';
			String projectname = collection.getName();
			
			String appid = context.findValue("sitelink");
			
			appid = 'app';  //Sitelink not working
			
			if (collection != null) {
				objects.put("project", collection);
				
				collection_url = getSiteRoot() + "/" + appid + "/collective/channel/"+collection.getId() +"/"+ URLUtilities.dash(collection.getName()) + ".html";
				objects.put("project_url", collection_url);
				
				collection_url_donation = getSiteRoot() + "/" + appid + "/collective/donate/"+collection.getId()+"/donate.html"
				objects.put("project_url_donation", collection_url_donation);

				//log.info(collection_url_donation);
			}
			
			
			//get emailbody from collection
			if(collection != null) {
				if (collection.getValue("donationemailtemplate")) {
					emailbody = (String) collection.getValue("donationemailtemplate");
				}
				if (collection.getValue("donationemailsubject")) {
					subject = (String) collection.getValue("donationemailsubject");
				}
			}
			//default emailbody+subject
			if (emailbody.equals("")) {
				emailbody = (String) mediaArchive.getCatalogSettingValue("donation_email_body");
			}
			if (emailbody.equals("")) {
				emailbody = "Thank you for your Donation.";
			}
			if (subject.equals("")) {
				subject =  projectname +" Donation Receipt";
			}
			
			String body = mediaArchive.getReplacer().replace(emailbody, objects);
			subject = mediaArchive.getReplacer().replace(subject, objects);
		
			WebEmail templateEmail = mediaArchive.createSystemEmailBody(receiptemail);
			templateEmail.setSubject(subject);
			templateEmail.loadSettings(context);
			templateEmail.send(body);
			
			receipt.setValue("receiptstatus", "sent");
			transactionSearcher.saveData(receipt);
			
			log.info("Email from ["+projectname+"] Sent to: " + receiptemail);
			count = count+1;
			
			sleep(5000);
			
			
		}
		else
		{
			log.info("Missing email address for receipt: "+receipt.getId())
		}
	}
}


private String getSiteRoot() {
	MediaArchive mediaArchive = context.getPageValue("mediaarchive");
	String site = mediaArchive.getCatalogSettingValue("siteroot");
	if (!site) {
		site = mediaArchive.getCatalogSettingValue("cdn_prefix");
	}
	return site;
}

init();

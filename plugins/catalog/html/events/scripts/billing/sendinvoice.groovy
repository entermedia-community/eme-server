package billing;

import org.entermediadb.asset.MediaArchive
import org.entermediadb.email.WebEmail
import org.openedit.*
import org.openedit.data.Searcher
import org.openedit.util.DateStorageUtil
import org.openedit.util.URLUtilities

public void init() {
	MediaArchive mediaArchive = context.getPageValue("mediaarchive");
	
	Searcher invoiceSearcher = mediaArchive .getSearcher("collectiveinvoice");
	String invoiceid = context.getRequestParameter("invoiceid");
	if(invoiceid!=null) {
		log.info("Individual Invoice: ${invoiceid}");
		Data invoice = mediaArchive.getBean("invoiceManager").getInvoiceById(invoiceid);
		if(invoice != null) {

			if(invoice.get("paymentstatus").equals("error")) {
				//notifiy about error?
				
			}
			else if (!invoice.get("paymentstatus").equals("paid")) {
				invoiceContactIterate(mediaArchive, invoiceSearcher, invoice, "notificationsent");
				invoice.setValue("paymentstatus", "invoiced");
				invoice.setValue("notificationsent", "true")
				invoiceSearcher.saveData(invoice);
		
			}
			else if(invoice.get("paymentstatus").equals("paid") && !Boolean.valueOf(invoice.get("notificationpaidsent"))) {
				log.info("Sending Paid Notification for ${invoiceid}");
				invoiceContactIterate(mediaArchive, invoiceSearcher, invoice, "notificationpaidsent");
				invoice.setValue("notificationpaidsent", "true")
				invoiceSearcher.saveData(invoice);
			}
		}
	}
	else {
		log.info("Sending Pending & Recurring Invoices...");
		// Notifications
		sendInvoiceNotifications(mediaArchive, invoiceSearcher);
		//sendInvoiceOverdueNotifications(mediaArchive, invoiceSearcher);  //Need to adjust Due Calculation
	}
	
}

private void sendInvoiceNotifications(MediaArchive mediaArchive, Searcher invoiceSearcher) {
	Collection pendingNotificationInvoices = invoiceSearcher.query()
			.exact("notificationsent","false")
			.exact("paymentstatus","sendinvoice").search();

	
	if (pendingNotificationInvoices.size()>0)
	{
		log.info("Sending Notification for " + pendingNotificationInvoices.size() + " invoices");
		for (Data invoice: pendingNotificationInvoices)
		{
			invoiceContactIterate(mediaArchive, invoiceSearcher, invoice, "notificationsent");

			//Notify project Admins about invoice
			invoiceNotifyProject(mediaArchive, invoiceSearcher, invoice, "");
		}
		
		
	}
}

private void sendInvoiceOverdueNotifications(MediaArchive mediaArchive, Searcher invoiceSearcher) {
	Calendar today = Calendar.getInstance();
	Collection pendingNotificationInvoices = invoiceSearcher.query()
			.before("startdate", today.getTime())
			.exact("notificationoverduesent", "false")
			.exact("paymentstatus","invoiced").search();

	
	if (pendingNotificationInvoices.size()>0)
	{
		log.info("Found " + pendingNotificationInvoices.size() + " overdue invoices");
		for (Data invoice: pendingNotificationInvoices)
		{
			invoiceContactIterate(mediaArchive, invoiceSearcher, invoice, "notificationoverduesent");
		}
	}
}

private void sendInvoicePaidNotifications(MediaArchive mediaArchive, Searcher invoiceSearcher) {
	Calendar today = Calendar.getInstance();
	Collection pendingNotificationInvoices = invoiceSearcher.query()
			.exact("notificationpaidsent", "false")
			.exact("paymentstatus","paid").search();

	
	if (pendingNotificationInvoices.size()>0)
	{
		log.info("Found " + pendingNotificationInvoices.size() + " paid invoices");
		for (Data invoice: pendingNotificationInvoices)
		{
			invoiceContactIterate(mediaArchive, invoiceSearcher, invoice, "notificationpaidsent");
		}
	
	}
}

private void invoiceContactIterate(MediaArchive mediaArchive, Searcher invoiceSearcher, Data invoice, String iteratorType) 
{

	String appid = mediaArchive.getCatalogSettingValue("events_billing_notify_invoice_appid");
		
	//Data invoice = invoiceSearcher.loadData(invoiceIterator.next());
	String collectionid = invoice.getValue("collectionid");
	Data librarycol = mediaArchive.getCachedData("librarycollection", collectionid);
	if( librarycol == null)
	{
		invoice.setValue(iteratorType, "true");
		invoiceSearcher.saveData(invoice);
		
	}
	
	invoice.setValue("paymentstatusreason", null);
	
	String emails = invoice.getValue("sentto");
	if( emails == null)
	{
		log.info("Error no email address to send invoice. Invoice: ${invoice.getId()} ${invoice.getName()}" );
		invoice.setValue("paymentstatus", "error");
		invoice.setValue("paymentstatusreason", "Empty Send To");
		invoiceSearcher.saveData(invoice);
		return;
	}
	List<String> emaillist = Arrays.asList(emails.split(","));

	Boolean sent = false;
	if (emaillist.size()>0) 
	{
		//log.info("Sending email to  "+emaillist.size()+" : "+librarycol+ " ("+collectionid+")");
		for (String email : emaillist)
		 {
			if (email != null) {
				if (email) {
					sendinvoiceEmail(mediaArchive, email, invoice, librarycol, iteratorType);
					sent = true; //needs better error handling
				}
			}
		}
		if (sent) {
			//send Copy to bookkeeper@entermediadb.org
			String ccinvoices = mediaArchive.getCatalogSettingValue("invoice_cc_email");
			if (ccinvoices != null) {
				sendinvoiceEmail(mediaArchive, ccinvoices, invoice, librarycol, iteratorType);
			}
			
			Calendar today = Calendar.getInstance();
			invoice.setValue("sentto", emails);
			invoice.setValue("sentdate", today.getTime());
			invoice.setValue(iteratorType, "true");
			
			if (!invoice.get("paymentstatus").equals("paid")) { //TODO: Seems weird. Should this be if invoice.get("paymentstatus") == null 
				//mark as invoiced
				invoice.setValue("paymentstatus", "invoiced");
			}
			
		}
		else {
			invoice.setValue("paymentstatus", "error");
			invoice.setValue("paymentstatusreason", "Send email error");
			log.info("Error sending invoice to addresses: ${emails}");
		}
		invoiceSearcher.saveData(invoice);
		
		}
		else 
		{
			log.info("No email addresses to send Invoice for: "+librarycol+ " ("+collectionid+")");
		}
}




private void invoiceNotifyProject(MediaArchive mediaArchive, Searcher invoiceSearcher, Data invoice, String iteratorType)
{

	String appid = mediaArchive.getCatalogSettingValue("events_billing_notify_invoice_appid");
		
	//Data invoice = invoiceSearcher.loadData(invoiceIterator.next());
	String collectionid = invoice.getValue("collectionid");
	Data librarycol = mediaArchive.getCachedData("librarycollection", collectionid);
	
	String emails = librarycol.getValue("contactemail");
	
	if( emails == null)
	{
		log.info("Error sending invoice to email to Project Team, missing contactemail. Invoice: ${invoice.getId()} Client: ${invoice.getName()} invoiceNotifyProject" );
		invoice.setValue("paymentstatus", "error");
		invoice.setValue("paymentstatusreason", "No contact email set on project");
		invoiceSearcher.saveData(invoice);
		return;
	}
	
	List<String> emaillist = Arrays.asList(emails.split(","));

	StringBuffer errorEmails = new StringBuffer();
	if (emaillist.size()>0)
	{
		log.info("Sending email to  "+emaillist.size()+" members of: "+librarycol+ " ("+collectionid+")");
		for (String email : emaillist)
		 {
			if (email != null) {
				if (email) {
					try
					{
						sendinvoiceEmail(mediaArchive, email, invoice, librarycol, "notifyprojectadmins");
						
					}
					catch(Throwable ex)
					{
						errorEmails.append(email + " " + ex.getMessage());
					}
					
				}
			}
		}
		if (!errorEmails.isEmpty()) {
			invoice.setValue("paymentstatus", "error");
			invoice.setValue("paymentstatusreason", "Error sending email to: " +errorEmails);
			log.info("Error sending invoice to addresses: ${errorEmails}");
		}
		invoiceSearcher.saveData(invoice);
		
	}
	else
	{
		log.info("No email addresses to send Notification for: "+librarycol+ " ("+collectionid+")");
	}
}

/*

private void sendEmail(MediaArchive mediaArchive, String contact, Data invoice, Data librarycol, String subject, String htmlTemplate) {
	sendEmail(mediaArchive, contact, invoice, librarycol, subject, htmlTemplate, null);
}
*/
private void sendinvoiceEmail(MediaArchive mediaArchive, String contact, Data invoice, Data librarycol, String messagetype) {
	String siteid = context.findValue("siteid");
	
	
	//String appid = mediaArchive.getCatalogSettingValue("events_billing_notify_invoice_appid");

	Data community = mediaArchive.getData("communitytag", librarycol.get("communitytag"));
	if (community == null) {
		log.error("Missing Community Tag for Project: " + librarycol);
		return;
	}
	
	String communitypath = community.get("templatepath");
	
	String template = siteid + communitypath + "/theme/emails/";
	
	String actionUrl = community.get("externaldomain") + "/" + librarycol.get("urlname");
	
	//String key = mediaArchive.getUserManager().getEnterMediaKey(contact);
	//actionUrl = actionUrl + "&entermedia.key=" + key;
	
	Map objects = new HashMap();
	
	String subject;
	String invoiceemailheader;
	String invoiceemailfooter;
	
	String collectionid = librarycol.getId();
	
	String invoicenumber = invoice.getValue("invoicenumber");
	
	String project = librarycol.getName();
	
	String month = "";
	
	switch(messagetype) {
		case "notificationsent":
			actionUrl = actionUrl + "/services/paynow.html?invoiceid=" + invoice.getValue("id") + "&collectionid=" + collectionid;
			actionUrl = actionUrl + "&contactemail="+contact;
			

			subject = "[${project}] Invoice #${invoicenumber}";
			
			if(invoice.getValue("isrecurring")) {
				if(invoice.getName()!= null) {
					subject = "[${project}] " + invoice.getName();
				}
				else {
					subject = "[${project}] ${invoicemonth} Invoice";
				}
			}


//			else {
			Date duedate = invoice.getDate("duedate");
			if( duedate != null)
			{
				month = context.getLocaleManager().getMonthName(duedate, context.getLocale());
			}
			//}
			
			if (invoice.getInt("recurringperiod") == 1) 
			{
				template = template + "invoice-to-client-monthly.html";
			}
			else 
			{
				template = template + "invoice-to-client.html";
			}
			
			//Invoice Template from collection
			invoiceemailheader = librarycol.get("invoiceemailheader");
			if(invoiceemailheader == null || invoiceemailheader.equals("")) {
				invoiceemailheader =  mediaArchive.getCatalogSettingValue("invoice_email_header");
			}
			
			
			invoiceemailfooter = librarycol.get("invoiceemailfooter");
			if(invoiceemailfooter == null || invoiceemailheader.equals("")) {
				invoiceemailfooter =  mediaArchive.getCatalogSettingValue("invoice_email_footer");
			}
		
		break;
	case "notificationoverduesent":
		subject = "Overdue Invoice for " + librarycol.getName();
		template = template + "send-overdue-invoice-event.html";
		//Invoice Template from collection
		invoiceemailheader = librarycol.get("invoiceemailheader");
		if(invoiceemailheader == null || invoiceemailheader.equals("")) {
			invoiceemailheader =  mediaArchive.getCatalogSettingValue("invoice_email_header");
		}
		
		
		invoiceemailfooter = librarycol.get("invoiceemailfooter");
		if(invoiceemailfooter == null || invoiceemailheader.equals("")) {
			invoiceemailfooter =  mediaArchive.getCatalogSettingValue("invoice_email_footer");
		}

		break;
	case "notificationpaidsent":
		subject = "Payment Received " + librarycol.getName();
		template = template + "send-paid-invoice-event.html";
		
		invoiceemailheader = librarycol.get("invoicepaidemail");
		if(invoiceemailheader == null || invoiceemailheader.equals("")) {
			invoiceemailheader =  mediaArchive.getCatalogSettingValue("invoice_paid_email");
		}
		break;
	case "notifyprojectadmins":
		subject = "Invoice generated at " + librarycol.getName();
		template = template + "invoice-to-notify-team.html";
		
		invoiceemailheader = librarycol.get("invoicepaidemail");
		/*if(invoiceemailheader == null || invoiceemailheader.equals("")) {
			invoiceemailheader =  mediaArchive.getCatalogSettingValue("invoice_notification");
		}*/
		break;
	}
	

	actionUrl = URLUtilities.urlEscape(actionUrl);
	
	String supportUrl = actionUrl;
	supportUrl = URLUtilities.urlEscape(supportUrl);
	
	//Other Pay options
	String invoicepayoptions = null;
	String parentprojectinvoicing = librarycol.get("parentprojectinvoicing");
	if (parentprojectinvoicing != null)
	{
		Data parentcollectioninvoice = mediaArchive.getCachedData("librarycollection", parentprojectinvoicing);
		if (parentcollectioninvoice != null)
		{
			invoicepayoptions = parentcollectioninvoice.get("invoicepayoptions");
		}
	}
	if (invoicepayoptions == null)
	{
		invoicepayoptions = librarycol.get("invoicepayoptions");
	}
	if(invoicepayoptions == null) {
		invoicepayoptions =  mediaArchive.getCatalogSettingValue("invoice_pay_options");
	}
	
	WebEmail templateEmail = mediaArchive.createSystemEmail(contact, template);
	
	//Variables
	objects.put("followeruser", contact);
	objects.put("invoiceto", contact); //change to name
	
	objects.put("mediaarchive", mediaArchive);
	objects.put("invoice", invoice);
	objects.put("invoicenumber", invoice.getValue("invoicenumber"));
	objects.put("project", librarycol);
	objects.put("supporturl", supportUrl);
	objects.put("actionurl", actionUrl);
	
	objects.put("siteroot", getSiteRoot());
	objects.put("librarycol",librarycol);
	
	String emailname = librarycol.get("contactname"); 
	
	if(emailname == null)
	{
		emailname = librarycol.getName();
	}
	
	templateEmail.setFromName(emailname);

	
	objects.put("community" , community);
	objects.put("communitylink" , community.get("externaldomain"));
	objects.put("communityhome","/" + siteid + community.get("templatepath"));

	 
	
	//recurring
	objects.put("invoicemonth", month);
	if(invoice.getDate("duedate") != null) {
		String dates = DateStorageUtil.getStorageUtil().formatDateObj(invoice.getDate("duedate"), "dd/MM/YY");
		
		objects.put("startdate",  dates); //legacy
		objects.put("duedate",  dates);
	}
	if(invoice.getDate("enddate") != null) {
		String datee = DateStorageUtil.getStorageUtil().formatDateObj(invoice.getDate("enddate"), "dd/MM/YY"); 
		objects.put("enddate", datee);
	}
	
	//period
	String recurringperiod = mediaArchive.getData("productrecurringperiod", invoice.get("recurringperiod"));
	objects.put("period", recurringperiod);
	
	String invoicedescription = mediaArchive.getReplacer().replace(invoice.get("invoicedescription"), objects);
	objects.put("invoicedescription", invoicedescription);
	
	String invoicename = mediaArchive.getReplacer().replace(invoice.get("name"), objects);
	objects.put("invoicename", invoicename);
	
	String invoicesubject = mediaArchive.getReplacer().replace(subject, objects);
	templateEmail.setSubject(invoicesubject);
	
	
	if (!invoiceemailheader.equals("")) {
		invoiceemailheader = mediaArchive.getReplacer().replace(invoiceemailheader, objects);
	}
	if (!invoiceemailfooter.equals("")) {
		invoiceemailfooter= mediaArchive.getReplacer().replace(invoiceemailfooter, objects);
	}
	
	objects.put("invoiceheader", invoiceemailheader);
	objects.put("invoicefooter", invoiceemailfooter);
	
	objects.put("invoicepayoptions", invoicepayoptions);
	
	//printurl
	String printurl = actionUrl+ "/services/printpreview.html?invoiceid="+invoice.getId()+"&collectionid=" + invoice.getValue("collectionid");
	objects.put("printurl", printurl);
	
	templateEmail.send(objects);
	log.info(librarycol.getName() + " - Invoice #"+ invoice.getValue("invoicenumber")+" - Sent to: "+contact + " Template: " + template);
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






package billing;

import org.entermediadb.asset.MediaArchive
import org.openedit.*
import org.openedit.data.Searcher
import org.openedit.users.User
import org.openedit.util.DateStorageUtil

public void init() {
	MediaArchive mediaArchive = context.getPageValue("mediaarchive");
	Searcher productSearcher = mediaArchive .getSearcher("collectiveproduct");
	Searcher invoiceSearcher = mediaArchive .getSearcher("collectiveinvoice");

	generateInvoice(mediaArchive, productSearcher);  //status=active
	
	

}

private void generateInvoice(MediaArchive mediaArchive, Searcher productSearcher) {
	int daysToExpire = 7; // invoice will expire in (days)
	Calendar today = Calendar.getInstance();
	Calendar due = Calendar.getInstance();
	due.add(Calendar.DAY_OF_YEAR, -5); // make invoice 5 days before next bill date

	String productid = context.getRequestParameter("id");
	String invoiced = context.getRequestParameter("invoiced");
	
	if (productid == null) {
		productid = context.getPageValue("id");
	}
	MultiValued product = productSearcher.searchById(productid);
	//if(product != null && product.getValue("billingstatus") == "active" && invoiced != "true")
	if(product != null)
	{
		Date lastbilldate = product.getValue("lastgeneratedinvoicedate");
//		if (lastbilldate < nextBillOn) { // otherwise assume it's already created
			Searcher invoiceSearcher = mediaArchive.getSearcher("collectiveinvoice");
			Data invoice = invoiceSearcher.createNewData();

//			Calendar invoiceDue = Calendar.getInstance();
//			invoiceDue.add(Calendar.DAY_OF_YEAR, daysToExpire);

			//Move this to aux-table?
			HashMap<String,Object> productItem = new HashMap<String,Object>();
				productItem.put("productid", product.getValue("id"));
				productItem.put("productname", product.getName());
				String productname = product.getValue("name");
				//productItem.put("productdescription", product.getValue("productdescription"));
				productItem.put("productquantity", 1 );
				productItem.put("productprice", product.getValue("productprice"));
			Collection items = new ArrayList();
			items.add(productItem);
			//--
			
			invoice.setValue("productlist", items);
			invoice.setValue("paymentstatus", "pending");
			invoice.setValue("isautopaid", product.getValue("isautopaid"));
			invoice.setValue("collectionid", product.getValue("collectionid"));
			invoice.setValue("owner", product.getValue("owner"));
			invoice.setValue("totalprice", product.getValue("productprice"));
			//invoice.setValue("duedate", invoiceDue.getTime()); //defined later
			invoice.setValue("invoicedescription", product.getValue("productdescription"));
			invoice.setValue("notificationsent", "false");
			invoice.setValue("createdon", today.getTime());
			
			//fix currencyprice if not defined deault to USD
			String productcurrency = product.getValue("currencytype");
			if (productcurrency == null) {
				productcurrency = '1';
			}
			invoice.setValue("currencytype",  productcurrency);
			
			//name -subject
			String collectionid = product.getValue("collectionid");
			Data collection = mediaArchive.getCachedData("librarycollection", collectionid);
			
			if(productname != null) {
				invoice.setValue("name", productname);
			}
			else if(collection != null) {
				String name = "\${project} - \${invoicemonth} Invoice";
				invoice.setValue("name", name);
			}
			
			String contactsstring = "";
			
			if(product.getValue("sentto") != null) {
				contactsstring = product.getValue("sentto");
			}
			else {
				Collection contacts = mediaArchive.query("librarycollectionusers")
									.exact("collectionid",product.getValue("collectionid"))
									.exact("ontheteam",true)
									.exact("isbillingcontact","true")
									.search();
				
				if (contacts!= null) {
					for(Data c:contacts)
					{
						User user = mediaArchive.getUser(c.getValue("followeruser"));
						
						if(user!= null && user.getEmail() != null) {
							if (contactsstring != "") {
								contactsstring = contactsstring + ", " + user.getEmail(); 
							}	
							else {
								contactsstring =  user.getEmail();
							}
						}
					}
				}
			}
			invoice.setValue("sentto", contactsstring);
			
			//Start Billing Date
			Date nextbillon = product.getValue("nextbillon");
			if (nextbillon == null) {
				nextbillon = today.getTime();
			}
	
			invoice.setValue("duedate", nextbillon);
			
			invoiceSearcher.saveData(invoice);
			log.info("New Invoice generated for " + collection.getName());
			
			
			context.putPageValue("invoiceid", invoice.getId())
			
			
		}
		
//	}
	
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

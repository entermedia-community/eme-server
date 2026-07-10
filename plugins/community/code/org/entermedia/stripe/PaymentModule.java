package org.entermedia.stripe;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.entermedia.invoice.InvoiceManager;
import org.entermediadb.asset.MediaArchive;
import org.entermediadb.asset.modules.BaseMediaModule;
import org.openedit.Data;
import org.openedit.WebPageRequest;
import org.openedit.cache.CacheManager;
import org.openedit.data.Searcher;
import org.openedit.data.SearcherManager;
import org.openedit.hittracker.HitTracker;
import org.openedit.money.Money;
import org.openedit.users.User;
import org.openedit.util.DateStorageUtil;

public class PaymentModule extends BaseMediaModule {

	private static final Log log = LogFactory.getLog(PaymentModule.class);

	protected SearcherManager fieldSearcherManager;
	protected HttpClient fieldHttpClient;
	protected StripePaymentProcessor fieldOrderProcessor;

	protected CacheManager fieldCacheManager;

	public CacheManager getCacheManager() {
		return fieldCacheManager;
	}

	public void setCacheManager(CacheManager inCacheManager) {
		fieldCacheManager = inCacheManager;
	}

	protected InvoiceManager getInvoiceManager(WebPageRequest inReq) {
		InvoiceManager manager = (InvoiceManager) getMediaArchive(inReq).getBean("invoiceManager");
		return manager;
	}

	public StripePaymentProcessor getPaymentProcessor(WebPageRequest inReq) {
		MediaArchive archive = getMediaArchive(inReq);
		String collectionId = inReq.getRequestParameter("collectionid");
		StripePaymentProcessor processor = getPaymentProcessor(archive.getCatalogId(), collectionId);
		inReq.putPageValue("paymentprocessor", processor);
		return processor;
	}

	public StripePaymentProcessor getPaymentProcessor(String inCatalogId, String inCollectionId) {
		StripePaymentProcessor processor = (StripePaymentProcessor) getCacheManager().get("paymentprocessor",
				inCollectionId);

		if (processor == null) {
			processor = new StripePaymentProcessor();
			getCacheManager().put("paymentprocessor", inCollectionId, processor);
			processor.setCollectionId(inCollectionId);
			MediaArchive mediaArchive = (MediaArchive) getModuleManager().getBean(inCatalogId, "mediaArchive");
			processor.setMediaArchive(mediaArchive);
		}

		return processor;
	}

	public HttpClient getHttpClient() {
		RequestConfig globalConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.DEFAULT).build();
		HttpClient client = HttpClients.custom().setDefaultRequestConfig(globalConfig).build();
		return client;

	}

	public void setHttpClient(HttpClient inHttpClient) {
		fieldHttpClient = inHttpClient;
	}

	public SearcherManager getSearcherManager() {
		return fieldSearcherManager;
	}

	public void setSearcherManager(SearcherManager inSearcherManager) {
		fieldSearcherManager = inSearcherManager;
	}

	public User createCheckoutUser(WebPageRequest inReq) {
		User user = inReq.getUser();
		if (user == null) {
			String email = inReq.getRequestParameter("contactemail");
			if (email != null) {
				user = getUserManager(inReq).createTempUserFromEmail(email);
				inReq.putSessionValue("checkoutuser", user);
			}
			user = (User) inReq.getPageValue("checkoutuser");
		}

		Map<String, Object> stripeUser = getStripeUser(inReq);

		return user;
	}

	// *Process Regular Payments (EM Portal) *//

	public void processPayment(WebPageRequest inReq) throws IOException, InterruptedException, URISyntaxException {
		Boolean stripeCust = inReq.getRequestParameter("customerselected") == "true";

		MediaArchive archive = getMediaArchive(inReq);
		Searcher payments = archive.getSearcher("transaction");
		Data payment = payments.createNewData();
		payments.updateData(inReq, inReq.getRequestParameters("field"), payment);

		payment.setValue("paymenttype", "stripe");
		Calendar today = Calendar.getInstance();

		String paymentintent = (String) inReq.getSessionValue("payinginvoice");
		if (paymentintent == null) {
			log.info("Payment resubmition prevented");
			return;
		}
		inReq.putSessionValue("payinginvoice", null);

		String invoiceId = inReq.getRequestParameter("invoiceid");
		Searcher invoiceSearcher = archive.getSearcher("collectiveinvoice");
		Data invoice = getInvoiceManager(inReq).getInvoiceById(invoiceId);

		if (invoice == null) {
			log.info("Payment fail, Invoice " + invoiceId + " not found");
			return;
		}

		if (invoice.get("paymentstatus").equals("paid")) {
			log.info("Invoice " + invoiceId + " already Paid");
			return;
		}

		Searcher workspaceSearcher = archive.getSearcher("librarycollection");
		Data workspace = getInvoiceManager(inReq).getWorkspaceById((String) invoice.getValue("collectionid"));
		if (Boolean.parseBoolean(inReq.getRequestParameter("savestripecreditcard"))) {
			workspace.setValue("savestripecreditcard", "true");
			workspaceSearcher.saveData(workspace);
		}

		StripePaymentProcessor processor = getPaymentProcessor(archive.getCatalogId(), workspace.getId());

		payment.setValue("totalprice", invoice.getValue("totalprice")); // safer to get value from database
		// log.info(payment);

		Boolean isRecurring = Boolean.valueOf(inReq.getRequestParameter("recurring"));
		Boolean isSuccess = false;

		String userid = payment.get("userid");
		User user = archive.getUser(userid);
		inReq.putSessionValue("checkoutuser", user);

		String source = inReq.getRequestParameter("selectedsource");
		if (source == null) {
			source = inReq.getRequestParameter("stripenewsource");
		}

		String customerId = (String) inReq.getRequestParameter("stripecustomer");
		String tokenid = inReq.getRequestParameter("stripetokenid");

		if (customerId == null) {

			customerId = processor.createCustomer2((String) invoice.getValue("collectionid"), user, tokenid);
		} else {
			if (tokenid != null) {
				if (!processor.updateCustomersSource(customerId, tokenid)) {
					log.info("Error updating user payment method on Stripe. " + customerId);
					return;
				}
			}
		}

		if (customerId == null || customerId.isEmpty()) {
			invoice.setValue("paymentstatus", "error");
			invoice.setValue("paymentstatusreason", "Stripe error, please contact your admin");
			invoiceSearcher.saveData(invoice);
			inReq.putPageValue("invoice", invoice);
			log.info("Error creating Stripe Customer for invoice: " + invoice.getValue("invoicenumber"));
			return;
		}

		log.info("Creating Charge - Invoice: " + invoice.getValue("invoicenumber") + " Customer: " + customerId
				+ " Source: " + source);

		String useremail = "";
		if (user != null) {
			useremail = user.getEmail();
		}

		isSuccess = processor.createCharge(payment, customerId, source, invoice, useremail);

		if (isSuccess) {
			log.info("Paid Stripe invoice: " + invoice.getValue("invoicenumber"));
			invoice.setValue("paymentstatus", "paid");
			invoice.setValue("invoicepaidon", today.getTime());

			payment.setValue("paymentstatus", "success");
		} else {
			invoice.setValue("paymentstatus", "error");
			invoice.setValue("paymentstatusreason", "Credit Card failed");
			payment.setValue("paymentstatus", "error");
			log.error("Error creating Charge for Stripe invoice: " + invoice.getValue("invoicenumber"));
		}

		payments.saveData(payment);
		inReq.putPageValue("payment", payment);

		// For Tracking payments
		invoice.setValue("transaction", payment.getId());

		invoiceSearcher.saveData(invoice);
		inReq.putPageValue("invoice", invoice);
	}

	public void processPaymentDonation(WebPageRequest inReq)
			throws IOException, InterruptedException, URISyntaxException {

		MediaArchive archive = getMediaArchive(inReq);
		String username = inReq.getUserName();
		User user = inReq.getUser();
		String collectionid = inReq.findValue("collectionid");
		inReq.putPageValue("collectionid", collectionid);
		Data workspace = archive.getCachedData("librarycollection", collectionid);

		if (workspace == null) {
			// can't continue without Collection
			log.info("Collection not found: " + collectionid);
			return;
		}
		StripePaymentProcessor processor = getPaymentProcessor(archive.getCatalogId(), workspace.getId());

		String source = inReq.getRequestParameter("selectedsource");
		if (source == null) {
			source = inReq.getRequestParameter("stripenewsource");
		}

		String customerId = (String) inReq.getRequestParameter("stripecustomer");
		String tokenid = inReq.getRequestParameter("stripetokenid");

		if (customerId == null) {

			customerId = processor.createCustomer2(collectionid, user, tokenid);
		} else {
			if (tokenid != null) {
				if (!processor.updateCustomersSource(customerId, tokenid)) {
					log.info("Error updating user payment method on Stripe. " + customerId);
					return;
				}
			}
		}

		if (customerId == null || customerId.isEmpty()) {
			log.info("Error creating Stripe Customer for Donation on " + workspace.getName());
			return;
		}

		// Update Transactions Table
		Searcher payments = archive.getSearcher("transaction");
		Data payment = payments.createNewData();
		payments.updateData(inReq, inReq.getRequestParameters("field"), payment);

		payment.setValue("userid", username);
		payment.setValue("collectionid", collectionid);
		payment.setValue("paymentemail", user.getEmail()); // match pp
		payment.setValue("name", user.getName()); // match pp
		payment.setValue("paymenttype", "stripe");
		payment.setValue("isdonation", true);

		try {
			// boolean success = getPaymentProcessor(archive.getCatalogId(),
			// collectionid).process(user, payment, token);
			boolean success = processor.createChargeDonation(payment, customerId, source, workspace, user.getEmail());
			if (success) {
				payment.setValue("paymentstatus", "success");

				String frequency = inReq.findValue("frequency");
				if (frequency != null && frequency != "") {
					Searcher plans = archive.getSearcher("paymentplan");
					Data plan = plans.createNewData();
					plan.setValue("userid", username);
					plan.setValue("frequency", frequency);
					plan.setValue("amount", payment.getValue("totalprice"));
					plan.setValue("lastprocessed", new Date());
					plan.setValue("planstatus", "active");
					plans.saveData(plan);

					payment.setValue("paymentplan", plan.getId());
				}

				payment.setValue("receiptstatus", "new");

				// TODO: in case different receipt required.
				// Donation Receipt
				Searcher donationreceipt = archive.getSearcher("donationreceipt");
				Data receipt = donationreceipt.createNewData();
				receipt.setValue("paymentid", payment.getId());
				receipt.setValue("amount", payment.getValue("totalprice"));
				receipt.setValue("donor", user.getName());
				receipt.setValue("donoremail", user.getEmail());
				receipt.setValue("collectionid", collectionid);
				receipt.setValue("donationdate", new Date());
				receipt.setValue("receiptstatus", "new");
				donationreceipt.saveData(receipt);
				inReq.putPageValue("receipt", receipt);

			} else {
				payment.setValue("paymentstatus", "error");
				log.debug("Payment: failed.");
				inReq.putPageValue("paymenterror", "Payment Error");
			}

			payment.setValue("paymentdate", new Date());

			payments.saveData(payment);
			inReq.putPageValue("payment", payment);

		} catch (Throwable e) {
			// TODO: handle exception
			log.error(e.getMessage(), e);
			inReq.putPageValue("paymenterror", e.getMessage());
		}

	}

	public Boolean cancelService(WebPageRequest inReq) {
		MediaArchive archive = getMediaArchive(inReq);
		Searcher payments = archive.getSearcher("collectiveproduct");
		String productId = (String) inReq.getRequestParameter("productid");
		Data product = getInvoiceManager(inReq).getProductById(productId);
		product.setValue("billingstatus", "canceled");
		payments.saveData(product);
		inReq.putPageValue("collectionid", product.getValue("collectionid"));
		return true;
	}

	public ArrayList<Map<String, Object>> getSubscriptions(WebPageRequest inReq) {
		MediaArchive archive = getMediaArchive(inReq);
		String email = inReq.getUser().getEmail();
		try {
			String collectionId = inReq.getRequestParameter("collectionid");
			if (collectionId == null) {
				throw new IllegalArgumentException("Collectionid is required");
			}

			StripePaymentProcessor processor = getPaymentProcessor(archive.getCatalogId(), collectionId);
			String customer = processor.getCustomerId(email, null);
			ArrayList<Map<String, Object>> subs = processor.getSubscriptions(customer);
			inReq.putPageValue("subs", subs);
			return subs;
		} catch (URISyntaxException | IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	public Map<String, Object> getStripeUser(WebPageRequest inReq) {
		MediaArchive archive = getMediaArchive(inReq);
		String collectionId = inReq.getRequestParameter("collectionid");
		User user = inReq.getUser();
		String email = null;
		if (user != null && user.getEmail() != null) {
			email = user.getEmail();
		} else {
			email = "billing+" + collectionId + "@entermediadb.com";
		}

		try {
			StripePaymentProcessor processor = getPaymentProcessor(archive.getCatalogId(), collectionId);
			ArrayList<Map<String, Object>> customers = processor.getCustomers(email);
			if (customers != null && customers.size() > 0) {
				inReq.putPageValue("customer", customers.get(0));
				return (Map<String, Object>) customers.get(0);
			}
		} catch (URISyntaxException | IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		return null;
	}

	public void getIds(WebPageRequest inReq) {
		inReq.putPageValue("collectionid", inReq.getRequestParameter("collectionid"));
		inReq.putPageValue("productid", inReq.getRequestParameter("productid"));
	}

	public Boolean cancelSubscription(WebPageRequest inReq) {
		MediaArchive archive = getMediaArchive(inReq);
		String subscription = (String) inReq.getRequestParameter("subid");
		try {
			String collectionId = inReq.getRequestParameter("collectionid");
			StripePaymentProcessor processor = getPaymentProcessor(archive.getCatalogId(), collectionId);
			Boolean resp = processor.cancelSubscriptions(subscription);
			inReq.putPageValue("status", resp);
			return resp;
		} catch (URISyntaxException | IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	public ArrayList<Map<String, Object>> getInvoices(WebPageRequest inReq) {
		MediaArchive archive = getMediaArchive(inReq);
		String customer = (String) inReq.getRequestParameter("customerid");
		String subscription = (String) inReq.getRequestParameter("subid");

		try {
			String collectionId = inReq.getRequestParameter("collectionid");
			StripePaymentProcessor processor = getPaymentProcessor(archive.getCatalogId(), collectionId);
			ArrayList<Map<String, Object>> invoices = processor.getInvoices(customer, subscription);
			inReq.putPageValue("invoices", invoices);
			return invoices;
		} catch (URISyntaxException | IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	public void createInvoice(WebPageRequest inReq) {
		MediaArchive archive = getMediaArchive(inReq);
		Data invoice = loadCurrentCart(inReq);
		invoice.setValue("paymentstatus", "invoiced");
		invoice.setValue("paymentdate", new Date());
		invoice.setValue("owner", inReq.getUserName());
		String collectionid = inReq.findValue("collectionid");
		invoice.setValue("collectionid", collectionid);
		archive.saveData("collectioninvoice", invoice);

		inReq.removeSessionValue("current-cart");
	}

	public Data loadCurrentCart(WebPageRequest inReq) {
		MediaArchive archive = getMediaArchive(inReq);
		Searcher invoicesearcher = archive.getSearcher("collectioninvoice");
		Data invoice = (Data) inReq.getSessionValue("current-cart");
		if (invoice == null) {
			invoice = invoicesearcher.createNewData();
			inReq.putSessionValue("current-cart", invoice);
		}
		Money invoicetotal = getInvoiceTotal(archive, invoice);
		inReq.putPageValue("invoicetotal", invoicetotal);

		return invoice;

	}

	public void cancelInvoice(WebPageRequest inReq) {
		MediaArchive archive = getMediaArchive(inReq);
		String invoiceid = inReq.getRequiredParameter("id");
		Data invoice = (Data) getInvoiceManager(inReq).getInvoiceById(invoiceid);
		if (invoice != null) {
			String status = (String) invoice.getValue("paymentstatus");
			if (status == null || status.equals("pending") || status.equals("invoiced")) {
				// only pending and invoiced can be canceled
				invoice.setValue("paymentstatus", "canceled");
				archive.saveData("collectiveinvoice", invoice);
				// Unlock products
				List products = (List) invoice.getValues("productlist");
				if (products == null) {
					return;
				}
				for (Iterator iterator = products.iterator(); iterator.hasNext();) {
					Map productinfo = (Map) iterator.next();
					String productid = (String) productinfo.get("productid");
					Data product = archive.getData("collectiveproduct", productid);
					product.setValue("locked", "false");
					archive.saveData("collectiveproduct", product);
				}
			}
		}

		inReq.removeSessionValue("current-cart");
	}

	public void addProductsToInvoice(WebPageRequest inReq) {
		MediaArchive archive = getMediaArchive(inReq);

		Data invoice = loadCurrentCart(inReq);

		String[] ids = inReq.getRequestParameters("productid");

		List products = (List) invoice.getValues("productlist");
		if (products == null) {
			products = new ArrayList();
			invoice.setValue("productlist", products);
		}
		for (String id : ids) {
			HashMap codemap = new HashMap();
			codemap.put("productid", id);
			String quantity = inReq.getRequestParameter(id + ".quantity");
			if (quantity == null) {
				quantity = "1";
			}
			codemap.put("quantity", quantity);
			products.add(codemap);
		}

		Money invoicetotal = getInvoiceTotal(archive, invoice);
		inReq.putPageValue("invoicetotal", invoicetotal);

	}

	public Money getInvoiceTotal(MediaArchive inArchive, Data invoice) {
		Money money = new Money();

		List products = (List) invoice.getValues("productlist");
		if (products == null) {
			return money;
		}
		for (Iterator iterator = products.iterator(); iterator.hasNext();) {
			Map productinfo = (Map) iterator.next();
			String productid = (String) productinfo.get("productid");
			Data product = inArchive.getData("collectiveproduct", productid);
			String pricestring = product.get("productprice");
			if (pricestring != null) {
				money = money.add(pricestring);
			}
			// Need to do quantity multiplication
		}

		return money;

	}

	public void processRecurringPayments(WebPageRequest inReq) {

		MediaArchive archive = getMediaArchive(inReq);

		Searcher payments = archive.getSearcher("transaction");
		Searcher plans = archive.getSearcher("paymentplan");

		Calendar now = Calendar.getInstance();
		now.add(now.DAY_OF_YEAR, -7);

		HitTracker toprocess = plans.query().before("lastprocessed", now.getTime()).exact("frequency", "weekly")
				.exact("planstatus", "active").search();

		processTransactions(inReq, toprocess);

		now = Calendar.getInstance();
		now.add(now.MONTH, -1);
		HitTracker monthly = plans.query().before("lastprocessed", now.getTime()).exact("frequency", "monthly")
				.exact("planstatus", "active").search();

		processTransactions(inReq, monthly);

		now = Calendar.getInstance();
		now.add(now.YEAR, -1);
		HitTracker yearly = plans.query().before("lastprocessed", now.getTime()).exact("frequency", "yearly")
				.exact("planstatus", "active").search();

		processTransactions(inReq, yearly);

		//
		// <option value="">Once</option>
		// <option value="weekly">Weekly</option>
		// <option value="monthly">Monthly</option>
		// <option value="yearly">Yearly</option>
		//

	}

	public void processTransactions(WebPageRequest inReq, HitTracker inYearly) {
		MediaArchive archive = getMediaArchive(inReq);
		Searcher payments = archive.getSearcher("transaction");

		Searcher paymentplans = archive.getSearcher("paymentplan");

		for (Iterator iterator = inYearly.iterator(); iterator.hasNext();) {
			Data paymentplan = (Data) iterator.next();
			String userid = paymentplan.get("userid");
			User user = archive.getUserManager().getUser(userid);
			Data payment = payments.createNewData();
			String amount = paymentplan.get("amount");
			if (amount == null) {
				continue;
			}
			payment.setValue("totalprice", amount);
			payment.setValue("paymentplan", paymentplan.getId());
			StripePaymentProcessor processor = getPaymentProcessor(archive.getCatalogId(), payment.get("collectionid"));
			processor.process(user, payment, null);
			payments.saveData(payment);
			paymentplan.setValue("lastprocessed", new Date());
			paymentplans.saveData(paymentplan);
		}

	}

	public void saveInvoice(WebPageRequest inReq) {
		MediaArchive mediaArchive = getMediaArchive(inReq);
		Data invoice = getInvoiceManager(inReq).getInvoiceById(inReq.getRequestParameter("id"));

		// Emails
		String sentto = inReq.getRequestParameter("sentto.value");
		invoice.setValue("sentto", sentto);

		String invoicedescription = inReq.getRequestParameter("invoicedescription.value");
		invoice.setValue("invoicedescription", invoicedescription);

		String invoicename = inReq.getRequestParameter("name.value");
		invoice.setValue("name", invoicename);

		String totalprice = inReq.getRequestParameter("totalprice.value");
		invoice.setValue("totalprice", totalprice);

		String duedate = inReq.getRequestParameter("duedate");
		if (duedate != null) {
			Date start = DateStorageUtil.getStorageUtil().parseFromStorage(duedate);
			invoice.setValue("duedate", start);
		}

		String enddate = inReq.getRequestParameter("enddate");
		if (enddate != null) {
			Date end = DateStorageUtil.getStorageUtil().parseFromStorage(enddate);
			invoice.setValue("enddate", end);
		}

		mediaArchive.saveData("collectiveinvoice", invoice);

		/*
		 * List products = (List) invoice.getValues("productlist"); if (products ==
		 * null) { return; } for
		 * (Iterator iterator = products.iterator(); iterator.hasNext();) { Map
		 * productinfo = (Map)
		 * iterator.next(); String productid = (String) productinfo.get("productid");
		 * Data product =
		 * mediaArchive.getData("collectiveproduct", productid);
		 * product.setValue("locked", "true");
		 * mediaArchive.saveData("collectiveproduct", product); }
		 */

	}

	public void createProductService(WebPageRequest inReq) {
		MediaArchive mediaArchive = getMediaArchive(inReq);
		Searcher collectiveproductsearcher = mediaArchive.getSearcher("collectiveproduct");
		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);

		Data saved = collectiveproductsearcher.createNewData();
		collectiveproductsearcher.updateData(inReq, inReq.getRequestParameters("field"), saved);
		saved.setValue("createdon", today.getTime());
		// saved.setValue("billingstatus", "active");
		// saved.setValue("nextbillon", today.getTime());
		collectiveproductsearcher.saveData(saved, null);
		inReq.putPageValue("id", saved.getId());
	}

	public void copyProductService(WebPageRequest inReq) {
		MediaArchive mediaArchive = getMediaArchive(inReq);
		Searcher collectiveproductsearcher = mediaArchive.getSearcher("collectiveproduct");
		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);

		String sourceId = inReq.getRequestParameter("id");

		Data source = (Data) collectiveproductsearcher.searchById(sourceId);

		Data saved = (Data) collectiveproductsearcher.cloneData(source);
		saved.setValue("createdon", today.getTime());
		saved.setValue("billingstatus", "new");
		saved.setValue("lastgeneratedinvoicedate", null);
		// saved.setValue("nextbillon", today.getTime());
		collectiveproductsearcher.saveData(saved, null);
		inReq.putPageValue("data", saved);

	}

	public void toggleAutoPay(WebPageRequest inReq) {
		MediaArchive archive = getMediaArchive(inReq);
		Searcher searcher = archive.getSearcher("collectiveproduct");
		String productId = inReq.getRequestParameter("id");
		Data product = getInvoiceManager(inReq).getProductById(productId);

		Boolean isPayment = (Boolean) product.getValue("isautopaid");
		if (isPayment == null) {
			isPayment = false;
		}
		product.setValue("isautopaid", !isPayment);
		searcher.saveData(product);
	}

	// public void connectClient(WebPageRequest inReq){
	// String scope = inReq.getRequestParameter("scope");
	// String code = inReq.getRequestParameter("code");
	// if (scope == null || !scope.equals("read_write") || code == null){
	// inReq.putPageValue("error","Invalid request parameters");
	// return;
	// }
	// MediaArchive store = (MediaArchive) inReq.getPageValue("store");
	// if (store == null){
	// inReq.putPageValue("error","Store not defined");
	// return;
	// }
	// if (!"stripe".equals(store.get("gateway"))){
	// inReq.putPageValue("error","Store not configured for stripe");
	// return;
	// }
	// String clientsecret = null;
	// if (store.isProductionMode()){
	// clientsecret = store.get("secretkey");
	// } else {
	// clientsecret = store.get("testsecretkey");
	// }
	// if (clientsecret == null){
	// inReq.putPageValue("error","Stripe configuration error");
	// return;
	// }
	// HttpRequestBuilder builder = new HttpRequestBuilder();
	//
	// HttpPost postMethod = null;
	// try
	// {
	// String fullpath = "https://connect.stripe.com/oauth/token";
	//
	// postMethod = new HttpPost(fullpath);
	// builder.addPart("code",code);
	// builder.addPart("client_secret",clientsecret);
	// builder.addPart("grant_type","authorization_code");
	// postMethod.setEntity(builder.build());
	// StatusLine line = getHttpClient().execute(postMethod).getStatusLine();
	// if (line.getStatusCode() == 200)
	// {
	// //need to save response
	// String response = IOUtils.toString(postMethod.getEntity().getContent());
	// Type stringStringMap = new TypeToken<Map<String, String>>(){}.getType();
	// Map<String,String> map = new Gson().fromJson(response, stringStringMap);
	// if (map.containsKey("error") || !map.containsKey("access_token")){
	// inReq.putPageValue("error",map.get("error_description"));
	// } else{
	// String accesstoken = map.get("access_token");
	// String userid = map.get("stripe_user_id");
	// String publishkey = map.get("stripe_publishable_key");//js part
	// String refresh_token = map.get("refresh_token");
	// //persist in catalogsettings
	// MediaArchive archive = (MediaArchive) inReq.getPageValue("mediaarchive");
	// Searcher searcher = archive.getSearcher("catalogsettings");
	// List<Data> list = new ArrayList<Data>();
	// Data data = (Data) searcher.searchById("stripe_access_token");
	// if (data == null){
	// data = searcher.createNewData();
	// data.setId("stripe_access_token");
	// data.setName("Stripe Connect Access Token");
	// }
	// data.setProperty("value", accesstoken);
	// list.add(data);
	// Data data2 = (Data) searcher.searchById("stripe_user_id");
	// if (data2 == null){
	// data2 = searcher.createNewData();
	// data2.setId("stripe_user_id");
	// data2.setName("Stripe Connect User ID");
	// }
	// data2.setProperty("value", userid);
	// list.add(data2);
	// Data data3 = (Data) searcher.searchById("stripe_publishable_key");
	// if (data3 == null){
	// data3 = searcher.createNewData();
	// data3.setId("stripe_publishable_key");
	// data3.setName("Stripe Connect Publish Key");
	// }
	// data3.setProperty("value", publishkey);
	// list.add(data3);
	// //save to catalogsettings
	//
	//
	// Data data4 = (Data) searcher.searchById("stripe_refresh_token");
	// if (data4 == null){
	// data4 = searcher.createNewData();
	// data4.setId("stripe_refresh_token");
	// data4.setName("Stripe Refresh Token");
	// }
	// data4.setProperty("value", refresh_token);
	// list.add(data4);
	//
	//
	//
	// searcher.saveAllData(list, inReq.getUser());
	//
	//
	// inReq.putPageValue("accesstoken",accesstoken);
	// inReq.putPageValue("userid",userid);
	// }
	// } else {
	// inReq.putPageValue("error","Post Error: status code "+line.getStatusCode() );
	// }
	// }
	// catch (Exception e)
	// {
	// throw new OpenEditException(e.getMessage(), e);
	// }
	//
	// finally
	// {
	// if (postMethod != null)
	// {
	// try
	// {
	// postMethod.releaseConnection();
	// }
	// catch (Exception e){}
	// }
	// }
	//
	// generateTestKey(inReq);
	// }
	//
	//
	//
	// public void generateTestKey(WebPageRequest inReq){
	//// curl -X POST https://connect.stripe.com/oauth/token \
	//// -d client_secret= \
	//// -d refresh_token=REFRESH_TOKEN \
	//// -d grant_type=refresh_token
	// Store store = (Store) inReq.getPageValue("store");
	//
	//
	// String clientsecret = store.get("testsecretkey");
	//
	// MediaArchive archive = (MediaArchive) inReq.getPageValue("mediaarchive");
	// Searcher searcher = archive.getSearcher("catalogsettings");
	// Data data4 = (Data) searcher.searchById("stripe_refresh_token");
	//
	// HttpPost postMethod = null;
	// HttpRequestBuilder builder = new HttpRequestBuilder();
	//
	// try
	// {
	// String fullpath = "https://connect.stripe.com/oauth/token";
	//
	// postMethod = new HttpPost(fullpath);
	// builder.addPart("refresh_token",data4.get("value"));
	// builder.addPart("client_secret",clientsecret);
	// builder.addPart("grant_type","refresh_token");
	//
	// int statusCode =
	// getHttpClient().execute(postMethod).getStatusLine().getStatusCode();
	// if (statusCode == 200)
	// {
	// //need to save response
	// String response = IOUtils.toString(postMethod.getEntity().getContent());
	// Type stringStringMap = new TypeToken<Map<String, String>>(){}.getType();
	// Map<String,String> map = new Gson().fromJson(response, stringStringMap);
	// if (map.containsKey("error") || !map.containsKey("access_token")){
	// inReq.putPageValue("error",map.get("error_description"));
	// } else{
	// String key = map.get("stripe_publishable_key");
	// Data data = (Data) searcher.searchById("stripe_test_publishable_key");
	// if (data == null){
	// data = searcher.createNewData();
	// data.setId("stripe_test_publishable_key");
	// data.setName("Stripe Test Access Token");
	// }
	// data.setProperty("value", key);
	// searcher.saveData(data, inReq.getUser());
	// }
	// } else {
	// inReq.putPageValue("error","Post Error: status code "+statusCode);
	// }
	// }
	// catch (Exception e)
	// {
	// throw new OpenEditException(e.getMessage(), e);
	// }
	//
	// finally
	// {
	// if (postMethod != null)
	// {
	// try
	// {
	// postMethod.releaseConnection();
	// }
	// catch (Exception e){}
	// }
	// }
	// }
	//
	// public void loadTransactions(WebPageRequest inReq){
	// Store store = (Store) inReq.getPageValue("store");
	// //set application key
	// Data setting = null;
	// if(store.isProductionMode()){
	// setting = getSearcherManager().getData(store.getCatalogId(),
	// "catalogsettings", "stripe_access_token");
	// log.info("loading catalogsettings from production mode");
	// } else{
	// setting = getSearcherManager().getData(store.getCatalogId(),
	// "catalogsettings", "stripe_test_access_token");
	// log.info("loading catalogsettins from test mode");
	// }
	// if (setting!=null && setting.get("value")!=null){
	// Stripe.apiKey = setting.get("value");
	// log.info("set apikey from catalogsettings entry");
	// } else {
	// if(store.isProductionMode()){
	// Stripe.apiKey = store.get("secretkey");
	// log.info("set apikey using store's secret key in production mode");
	// } else{
	// Stripe.apiKey = store.get("testsecretkey");
	// log.info("set apikey using store's secret key in test mode");
	// }
	// }
	// String limit = inReq.getRequestParameter("limit");
	// if (limit == null || limit.isEmpty()){
	// limit = "10";
	// }
	// String after = inReq.getRequestParameter("after");
	// boolean fix = Boolean.parseBoolean(inReq.getRequestParameter("fix"));
	// log.info("<h4>Searching all Stripe charges, showing "+limit+", starting
	// "+(after == null ? "at the beginning" : "after "+after)+"</h4>");
	// try{
	// List list = new ArrayList();
	// Map<String, Object> chargeParams = new HashMap<String, Object>();
	// chargeParams.put("limit", limit);//number of results to return
	// if (after!=null && !after.isEmpty()){
	// chargeParams.put("starting_after",after);
	// }
	// ChargeCollection col = Charge.all(chargeParams);
	// Iterator<Charge> itr = col.getData().iterator();
	// while(itr.hasNext()){
	// Charge charge = itr.next();
	// String orderid = charge.getDescription();
	// if (orderid == null || orderid.isEmpty()){
	// log.info("Unable to find an order id on charge "+charge.getId()+",
	// skipping");
	// continue;
	// }
	// Order order = (Order) store.getOrderSearcher().searchById(orderid);
	// if (order == null){
	// log.info("Unable to load order "+orderid);
	// continue;
	// }
	// if ("stripe".equals(order.get("gateway")) == false){
	// order.setProperty("gateway","stripe");//update gateway
	// }
	// String transactionid = charge.getBalanceTransaction();
	// if (transactionid == null){
	// log.info("Order "+order.getId()+" balancetransaction not set, is refunded?
	// "+charge.getRefunded());
	// continue;
	// }
	// updateValues(order,transactionid);
	// String stripefee = order.get("stripefee");
	// String profitshare = order.get("profitshare") == null ? "0" :
	// order.get("profitshare");
	// String net = order.get("net");
	// //what was charged the client
	// Money stripetotal = new Money(stripefee).add(new Money(profitshare)).add(new
	// Money(net));
	// //what is on the order
	// Money total = order.getTotalPrice();
	// //difference
	// Money delta = total.subtract(stripetotal);
	// if (!delta.isZero() && !delta.isNegative() && fix){
	// log.info("<span style='color:red'>Fixing "+order+", Correcting for
	// "+delta+"</span>");
	// fixOrder(store, order,delta);
	// log.info("<br/>");
	// }
	// log.info("Order "+order.getId()+", Stripe Fee: "+stripefee+", Profit share:
	// "+profitshare+", Net: "+net+", Stripe Total: "+stripetotal+", Order Total:
	// "+order.getTotalPrice());
	// list.add(order);
	// }
	// log.info("<hr><strong>Saving changes to orders</strong>");
	// store.getOrderSearcher().saveAllData(list, null);
	// ///print out last
	// int size = col.getData().size();
	// if (size == 0){
	// log.info("<hr><strong>Finished</strong>");
	// } else {
	// Charge last = col.getData().get(size - 1);
	// log.info("<hr>Last Charge ID: <strong>"+last.getId()+"</strong>");
	// }
	// } catch (Exception e){
	// log.error(e.getMessage(),e);
	// }
	// }
	//
	// protected void updateValues(Order inOrder, String inTransaction){
	// try{
	// BalanceTransaction balance = BalanceTransaction.retrieve(inTransaction);
	// List<Fee> details = balance.getFeeDetails();
	// for (Iterator<Fee> iterator = details.iterator(); iterator.hasNext();)
	// {
	// Fee fee = iterator.next();
	// //have to account for div by zero errors
	// //could we find a fee in this list that has a value of zero?
	// float moneyval = 0;
	// if (fee.getAmount().intValue() != 0){
	// moneyval = (float) fee.getAmount() / 100;
	// }
	// Money money = new Money(String.valueOf(moneyval));
	// if("stripe_fee".equals(fee.getType())){
	// inOrder.setProperty("stripefee", money.toShortString());
	// } else if("application_fee".equals(fee.getType())){
	// inOrder.setProperty("profitshare", money.toShortString());
	// }
	// }
	// float net = (float) balance.getNet() / 100;
	// Money money = new Money(String.valueOf(net));
	// inOrder.setProperty("net",money.toShortString());
	// }catch (Exception e){
	// log.error(e.getMessage(),e);
	// }
	// }
	//
	// protected void fixOrder(Store inStore, Order inOrder, Money inMoney){
	// //correct order by adding an adjustment
	// //associate it with first non-coupon item on the order
	// //account for changes to tax as well
	//
	// //check order first: no shipping, one item on cart
	// if (inOrder.getShippingMethod() != null || inOrder.getItems().size() > 1 ||
	// !inOrder.getAdjustments().isEmpty() || inOrder.getTaxes().size() > 1){
	// log.info("Unable to fix order "+inOrder.getId()+": has more than one item, or
	// has a shipping method, or has adjustments, or has more than one tax rate,
	// skipping");
	// return;
	// }
	// Money subtotal = inOrder.getSubTotal();
	// Money tax = inOrder.getTax();
	// Money total = inOrder.getTotalPrice();
	//
	// CartItem item = (CartItem) inOrder.getItems().get(0);
	// Customer customer = inStore.createCustomer();
	// customer.setTaxRates(new ArrayList<TaxRate>());
	// DiscountAdjustment adjustment = new DiscountAdjustment();
	// adjustment.setProductId(item.getProduct().getId());
	// adjustment.setInventoryItemId(item.getSku());
	// if (tax.isZero()){
	// adjustment.setDiscount(inMoney);//no tax on the order so just add corrected
	// amount
	// } else {
	// Map<TaxRate,Money> map = inOrder.getTaxes();
	// TaxRate rate = null;
	// Iterator<TaxRate> keys = map.keySet().iterator();
	// while (keys.hasNext()){
	// rate = keys.next();
	// customer.getTaxRates().add(rate);//should have only one TaxRate
	// }
	// //original price
	// Fraction fraction = rate.getFraction();
	// Fraction subfact = fraction.add(1);
	// Money discount = inMoney.divide(subfact);
	// adjustment.setDiscount(discount);
	// }
	// //create a new cart and place customer / adjustments on it
	// Cart cart = new Cart();
	// cart.setCustomer(customer);
	// cart.addAdjustment(adjustment);
	// cart.addItem(item);
	//
	// Money newsubtotal = cart.getSubTotal();
	// Money newtax = cart.getTotalTax();
	// Money newtotal = cart.getTotalPrice();
	//
	// Money delta = total.subtract(inMoney).subtract(newtotal);
	// if (delta.doubleValue() < 0.005){
	// //add adjustments and new tallies
	// inOrder.getAdjustments().add(adjustment);
	// inOrder.setSubTotal(newsubtotal);
	// inOrder.setTotalTax(newtax);
	// inOrder.setTotalPrice(newtotal);
	// log.info("<span style='color:blue;'>Original Total: "+total+" New Total:
	// "+inOrder.getTotalPrice()+", Difference: "+delta+"</span>");
	// } else {
	// log.info("<span style='color:red;'>Unable to update order
	// "+inOrder.getId()+"</span>");
	// }
	// }
}

package scripts;


import org.entermediadb.asset.Asset
import org.entermediadb.asset.Category
import org.entermediadb.asset.MediaArchive
import org.entermediadb.asset.upload.FileUpload
import org.entermediadb.asset.upload.UploadRequest
import org.openedit.util.DateStorageUtil

public void handleUpload() {
	MediaArchive openeditcatalog = (MediaArchive)context.getPageValue("mediaarchive");
	
	MediaArchive archive = null;
	
	String catalogid = context.getRequestParameter("catalogid");
	if(catalogid != null) {
		archive = openeditcatalog.getModuleManager().getBean(catalogid, "mediaarchive");
	}
	else {
		archive = openeditcatalog;
	}

	
	
	FileUpload command = archive.getSearcherManager().getModuleManager().getBean("fileUpload");
	UploadRequest properties = command.parseArguments(context);
	
	if (properties == null) {
		return;
	}
	if (properties.getFirstItem() == null) {
		return;
		
	}
	
	String sourcepath = "uploaded/cms/" + context.getUserName() + "/${properties.getFirstItem().getName()}";
	Asset current = archive.getAssetBySourcePath(sourcepath);
	if(current ==  null){
		current = archive.createAsset(sourcepath);
	}
	current.setProperty("assetaddeddate", DateStorageUtil.getStorageUtil().formatForStorage(new Date()));
	Category target = archive.getCategoryArchive().createCategoryTree("submissions/${context.getUserName()}/");
	current.addCategory(target);
	archive.getCategoryArchive().saveAll();
	String path = "/WEB-INF/data/" + archive.getCatalogId()	+ "/originals/" + sourcepath + "/${properties.getFirstItem().getName()}";
	String[] fields = context.getRequestParameters("field");
	if(fields != null){
		archive.getAssetSearcher().updateData(context, fields, current);
	}
	properties.saveFileAs(properties.getFirstItem(), path, context.getUser());
	current.setPrimaryFile(properties.getFirstItem().getName());
	current.setProperty("owner", context.getUserName());
	current.setProperty("userprofile", context.getUserProfile().getId());
	current.setProperty("submittedfile", "true");
	archive.saveAsset(current, null);
	context.putPageValue("newasset", current);
	List listids = new ArrayList();
	listids.add(current.getId());
	archive.fireMediaEvent("importing/assetsuploaded",context.getUser(),current);;
	
	//sendEmail(context,  "ian@ijsolutions.ca, rsherkin@gmail.com", "/medcenter/client/account/uploademail.html");
	
}



handleUpload();


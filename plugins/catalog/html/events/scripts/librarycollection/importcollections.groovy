package librarycollection
import java.util.*
import org.apache.http.StatusLine
import org.apache.http.client.methods.CloseableHttpResponse
import org.entermediadb.asset.Asset
import org.entermediadb.asset.MediaArchive
import org.entermediadb.asset.pull.OriginalPuller
import org.json.simple.JSONObject
import org.openedit.Data
import org.openedit.data.*
import org.openedit.util.HttpSharedConnection
import org.openedit.util.JSONParser

public void init()
{
	MediaArchive mediaArchive = context.getPageValue("mediaarchive");

	HttpSharedConnection connection = new HttpSharedConnection();
	String baseurl = "https://openinstitute.org/site/mediadb";
	String entermediakey = "1106md5420940dd427e0a60de560b00f00de530d2380b93208d";

	String exportsearchtype = "librarycollection";		
	
	def search = '''{
		"page": "1",
		"hitsperpage": "100",
		"query": {
		  "terms": [
			{
			  "field": "communitytag",
			  "operation": "exact",
			  "value": "3"
			}
		  ]
		}
	  }'''


/*
//Bring just one specific collection
def search = '''{
		"page": "1",
		"hitsperpage": "100",
		"query": {
		  "terms": [
			{
			  "field": "id",
			  "operation": "exact",
			  "value": "1106-messages"
			}
		  ]
		}
	  }'''
	  
*/
	
	  
  //local
	  /*
  baseurl = "http://localhost:8080/finder/mediadb"
  entermediakey = "adminmd5421c0af185908a6c0c40d50fd5e3f16760d5580bc"
  search = '''{
		"page": "1",
		"hitsperpage": "2",
		"query": {
		  "terms": [
			{
			  "field": "name",
			  "operation": "matches",
			  "value": "C*"
			}
		  ]
		}
	  }'''
  */

	JSONParser json_parser = new JSONParser();
	JSONObject searchparams =json_parser.parse(search); 
	
	connection.addSharedHeader("X-tokentype", "entermedia")
	connection.addSharedHeader("X-token", entermediakey)
	
	String url = baseurl + "/services/lists/export/" + exportsearchtype
	
	CloseableHttpResponse response = connection.sharedPostWithJson(url, searchparams)
	
	StatusLine filestatus = response.getStatusLine();
	if (filestatus.getStatusCode() != 200)
	{
		//Problem
		log.info( filestatus.getStatusCode() + " URL issue " + " " + url);
		return null;
	}
	
	Searcher searcher = mediaArchive.getSearcher(exportsearchtype);
	Map responsemap = connection.parseJson(response)
	Collection results = responsemap.get("results");
	for (map in results) {
		String field = "owner";
		String oldvalue = map.get(field)
		if (oldvalue != null)
		{
			map.put(field, "ib_"+oldvalue)
		}
	}

	//Save the Collections
	//searcher.saveJson(results)
	
	//def replacemap = ["AZqnEhttnrYY6bRGwPRw":"AZpgFZ_vQC-UlD66sXgS" ];
	
	//Loop auxiliar tables
	// uploads, assets, goals, tasks, chatterbox, userposts, librarycollectionusers
	
	for (map in results) {
		String collectionid = map.id;
		//String newid = replacemap.get(collectionid)
		
		/*
		downloadData(connection, "projectgoal", "collectionid", collectionid, collectionid)
		downloadData(connection, "goaltask", "collectionid", collectionid, collectionid)
		downloadData(connection, "chatterbox", "collectionid", collectionid, collectionid)
		downloadData(connection, "channel", "dataid", collectionid, collectionid)
		downloadData(connection, "collectiveproject", "parentcollectionid", collectionid, collectionid)
		downloadData(connection, "userpost", "librarycollection", collectionid, collectionid)
		downloadData(connection, "librarycollectionusers", "collectionid", collectionid, collectionid)
		
		downloadData(connection, "bankaccount", "collectionid", collectionid, collectionid)
		downloadData(connection, "collectiveinvoice", "collectionid", collectionid, collectionid)
		downloadData(connection, "collectiveproduct", "collectionid", collectionid, collectionid)
		downloadData(connection, "collectiveincome", "collectionid", collectionid, collectionid)
		downloadData(connection, "collectiveexpense", "collectionid", collectionid, collectionid)
		downloadData(connection, "collectiveinvestment", "collectionid", collectionid, collectionid)
		downloadData(connection, "collectivelinkreferralcode", "collectionid", collectionid, collectionid)
		downloadData(connection, "collectivelinktracker", "collectionid", collectionid, collectionid)
		downloadData(connection, "collectivereimbursement", "collectionid", collectionid, collectionid)
		downloadData(connection, "donationreceipt", "collectionid", collectionid, collectionid)
		downloadData(connection, "transaction", "collectionid", collectionid, collectionid)
		*/
	}
	
	/*
	//Download Assets
	for (map in results) {
		String collectionid = map.id;
		downloadAssets(connection, collectionid, map.rootcategory)
	}
	*/

	//Download Assets from a specific category
	downloadAssets(connection, null, "AZtcRpF9AtO-rTscxHxm")  //Blog images category
	
	
	
	/*for (map in results) {
		String collectionid = map.id;
		String newid =replacemap.get(collectionid)
		if (newid != null)
		{
			map.put("id", newid)
		}
	}*/
	
	
	
	log.info("saved ${results.size()} Collections");
}

void downloadData(HttpSharedConnection inConnection, String inSearchtype, String inForeignkey, String inForeignid, String inNewcollectionid)
{
	
	def search = '''{
		"page": "1",
		"hitsperpage": "5000",
		"query": {
		  "terms": [
			{
			  "field": "'''+ inForeignkey + '''",
			  "operation": "exact",
			  "value": "'''+ inForeignid + '''"
			}
		  ]
		}
	  }'''
	//log.info("searching ${search}");
	
	JSONParser json_parser = new JSONParser();
	JSONObject searchparams =json_parser.parse(search);
	
	String baseurl = "https://openinstitute.org/site/mediadb";
	String url = baseurl + "/services/lists/export/" + inSearchtype
	
	CloseableHttpResponse response = inConnection.sharedPostWithJson(url, searchparams)
	
	StatusLine filestatus = response.getStatusLine();
	if (filestatus.getStatusCode() != 200)
	{
		//Problem
		log.info( filestatus.getStatusCode() + " URL issue " + " " + url);
		return null;
	}
	
	MediaArchive mediaArchive = context.getPageValue("mediaarchive");
	Searcher searcher = mediaArchive.getSearcher(inSearchtype);
	Map responsemap = inConnection.parseJson(response)
	Collection results = responsemap.get("results");
	
	HashSet dependants = new HashSet();
	
	dependants.add("owner");
	dependants.add("user");
	dependants.add("startedby");
	dependants.add("completedby");
	dependants.add("followeruser");
	dependants.add("userid");
	dependants.add("reimburseuser");
	dependants.add("investmentuser");
	
	
	
	for (map in results) {
		dependants.each {
			String field = it;
			String oldvalue = map.get(field)
			if (oldvalue != null)
			{
				map.put(field, "ib_"+oldvalue)
			}
			
		}
	}
	
	
	
	searcher.saveJson(results)
	
	if (results.size() <= 0)
	{
		return
	}
	
	log.info("saved ${results.size()} ${inSearchtype} for key: ${inForeignid}");
	
	for (map in results) {
		String foreignkeyid = map.id;
		if ("goaltask".equals(inSearchtype))
		{
			//download subtypes: goaltaskuserrole
			downloadData(inConnection, "goaltaskuserrole", "goaltaskid", foreignkeyid, null)
		}
		else if ("chatterbox".equals(inSearchtype))
		{
			//download subtypes: attachments
			downloadData(inConnection, "chatterboxattachment", "messageid", foreignkeyid, null)
		}
	}
}


void downloadAssets(HttpSharedConnection inConnection, String inCollectionid, String inCategoryid)
{
	def search = '''{
		"page": "1",
		"hitsperpage": "1000",
		"query": {
		  "terms": [
			{
			  "field": "category",
			  "operation": "exact",
			  "value": "'''+ inCategoryid + '''"
			}
		  ]
		}
	  }'''
	//log.info("searching ${search}");
	
	JSONParser json_parser = new JSONParser();
	JSONObject searchparams =json_parser.parse(search);
	
	String inSearchtype = "asset"
	
	String baseurl = "https://openinstitute.org/site/mediadb";
	String url = baseurl + "/services/lists/export/" + inSearchtype
	
	CloseableHttpResponse response = inConnection.sharedPostWithJson(url, searchparams)
	
	StatusLine filestatus = response.getStatusLine();
	if (filestatus.getStatusCode() != 200)
	{
		//Problem
		log.info( filestatus.getStatusCode() + " URL issue " + " " + url);
		return null;
	}
	MediaArchive mediaArchive = context.getPageValue("mediaarchive");
	Searcher searcher = mediaArchive.getSearcher(inSearchtype);
	Map responsemap = inConnection.parseJson(response)
	Collection results = responsemap.get("results");
	for (data in results) {
		data.put("importstatus", "created")
	}
	searcher.saveJson(results)
	
	log.info("saved ${results.size()} assets for collection ${inCollectionid}");
	
	if (results.size() <= 0)
	{
		return
	}
	
	OriginalPuller originalpuller = (OriginalPuller)mediaArchive.getBean("originalPuller")
	Data remotenode = mediaArchive.getCachedData("editingcluster", "AZzjQMTWwrmEy5JXqA0x")
	for (data in results) {
		Asset asset = mediaArchive.getAsset(data.id)
		String assetpath = mediaArchive.getOriginalContent(asset).getAbsolutePath()
		File assetfile = new File(assetpath)
		if (!assetfile.exists())
		{
			log.info("Downloading file: ${asset.name} size: $asset.filesize" )
			originalpuller.downloadOriginalFromSource(mediaArchive, inConnection, remotenode, asset, assetfile, true)
			log.info("Downloading file finish: ${asset.name}" )
		}
	}
	
	log.info("downloaded ${results.size()} assets");
	
	
}


init();
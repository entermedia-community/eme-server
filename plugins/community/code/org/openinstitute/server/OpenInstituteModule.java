package org.openinstitute.server;

import java.util.Collection;
import java.util.Iterator;

import org.entermediadb.asset.MediaArchive;
import org.entermediadb.asset.modules.BaseMediaModule;
import org.entermediadb.asset.util.JsonUtil;
import org.entermediadb.projects.LibraryCollection;
import org.entermediadb.projects.ProjectManager;
import org.openedit.Data;
import org.openedit.WebPageRequest;
import org.openedit.data.QueryBuilder;
import org.openedit.hittracker.HitTracker;
import org.openedit.hittracker.SearchQuery;
import org.openedit.hittracker.Term;
import org.openedit.profile.UserProfile;
import org.openedit.servlet.SiteData;
import org.openedit.util.PathUtilities;

public class OpenInstituteModule extends BaseMediaModule
{

	public void loadCommunityCategory(WebPageRequest inReq)
	{
		LibraryCollection col = (LibraryCollection) inReq.getPageValue("librarycol");
		if (col != null)
		{
			Collection all = col.getValues("communitytag");
			if (all != null && !all.isEmpty())
			{
				String communitytagcategory = (String) all.iterator().next();
				Data data = getMediaArchive(inReq).getCachedData("communitytag", communitytagcategory);
				inReq.putPageValue("communitytag", data);
			}
		}
	}

	// public LibraryCollection loadCollectionFromCommunityTagFolder(WebPageRequest
	// inReq)
	// {
	// MediaArchive archive = getMediaArchive(inReq);
	// String tagid = PathUtilities.extractDirectoryName(inReq.getPath());
	// ProjectManager manager = getProjectManager(inReq);
	// Data tag = archive.getData("communitytag", tagid);
	// if(tag != null) {
	// LibraryCollection col = manager.getLibraryCollection(archive,
	// (String)tag.getValue("collectionid"));
	// if( col != null)
	// {
	// inReq.putPageValue("librarycol", col);
	// inReq.putPageValue("collectionid", col.getId());
	// }
	// return col;
	// }
	// return null;
	//
	// }

	public Data loadCommunityTagByDomain(WebPageRequest inReq)
	{
		MediaArchive archive = getMediaArchive(inReq);
		Collection tags = archive.getList("communitytag");
		String url = inReq.getSiteRoot();
		for (Iterator iterator = tags.iterator(); iterator.hasNext();)
		{
			Data tag = (Data) iterator.next();
			String domain = tag.get("externaldomain");
			if (domain != null && url.contains(domain))
			{
				inReq.putPageValue("communitytag", tag);
				return tag;
			}
		}
		return null;
	}

	public void loadCommunityTagFolder(WebPageRequest inReq)
	{
		MediaArchive archive = getMediaArchive(inReq);
		String tagid = inReq.findPathValue("communitytag");
		Data tag = null;
		if (tagid != null)
		{
			tag = archive.getCachedData("communitytag", tagid);
		}
		if (tag != null)
		{
			inReq.putPageValue("communitytag", tag);
			String siteid = inReq.findPathValue("siteid");

			String communityhome = "/" + siteid + tag.get("templatepath");
			inReq.putPageValue("communityhome", communityhome);

			String communitylink = communityhome;
			if (tag.get("externaldomain") != null)
			{
				SiteData sitedata = (SiteData) inReq.getPageValue("sitedata");
				if (sitedata != null) // We are on a domain
				{
					communitylink = ""; // tag.get("externaldomain"); //or ?
				}
			}
			inReq.putPageValue("communitylink", communitylink);

		}
	}

	public Data loadCommunityTagCategory(WebPageRequest inReq)
	{
		MediaArchive archive = getMediaArchive(inReq);
		String communitytagcategory = inReq.getRequestParameter("communitytag");
		Data tag = null;
		if (communitytagcategory != null)
		{
			tag = archive.getCachedData("communitytag", communitytagcategory);
		}
		if (tag == null)
		{
			SiteData data = (SiteData) inReq.getPageValue("sitedata");
			if (data != null)
			{
				String tagid = data.get("domaincommunityid");
				if (tagid != null)
				{
					tag = archive.getCachedData("communitytag", tagid);
				}
			}
		}
		if (tag != null)
		{
			inReq.putPageValue("communitytag", tag);
			inReq.putPageValue("communitytagcategoryid", tag.getId());
		}
		return tag;
	}

	public void loadCommunityBlog(WebPageRequest inReq)
	{
		Data tag = (Data) inReq.getPageValue("communitytag");
		if (tag == null)
		{
			tag = loadCommunityTagByDomain(inReq);
		}

		Collection collections = getMediaArchive(inReq).query("librarycollection").exact("communitytag", tag).search(inReq);

		QueryBuilder builder = getMediaArchive(inReq).query("userupload");

		HitTracker topuploads = null;
		builder.exact("exclusivecontent", false);
		if (collections.isEmpty())
		{
			builder.exact("librarycollection", "NONE");
		}
		else
		{
			builder.orgroup("librarycollection", collections);
		}
		builder.all();
		topuploads = builder.named("topuploads").sort("uploaddateDown").search(inReq);
		//
		// String page = inReq.getRequestParameter("page");
		// if( page != null)
		// {
		// topuploads.setPage(Integer.parseInt(page));
		// }
		inReq.putPageValue("topuploads", topuploads);

	}

}

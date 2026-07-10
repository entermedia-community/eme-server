package org.openinstitute.community;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.entermediadb.manager.BaseManager;
import org.openedit.CatalogEnabled;
import org.openedit.Data;
import org.openedit.OpenEditException;
import org.openedit.data.QueryBuilder;
import org.openedit.hittracker.HitTracker;
import org.openedit.page.Page;
import org.openedit.page.PageLoader;
import org.openedit.page.manage.PageManager;
import org.openedit.servlet.RightPage;
import org.openedit.servlet.Site;
import org.openedit.util.PathUtilities;
import org.openedit.util.URLUtilities;

public class ProfileLoader extends BaseManager implements PageLoader, CatalogEnabled
{
	protected PageManager fieldPageManager;
	private static final Log log = LogFactory.getLog(ProfileLoader.class);

	public PageManager getPageManager()
	{
		return fieldPageManager;
	}

	public void setPageManager(PageManager inPageManager)
	{
		fieldPageManager = inPageManager;
	}

	@Override
	public RightPage getRightPage(URLUtilities util, Site site, Page inPage)
	{
		if (site == null)
		{
			RightPage right = new RightPage();
			right.setRightPage(inPage);
			return right;

		}
		String requestedPath = util.getOriginalPath();
		String[] url = requestedPath.split("/");

		// Check that we are actually going to the page /site/community/...
		String appid = inPage.getProperty("applicationid");
		if (appid != null && url.length > 0 && appid.startsWith(url[1]))
		{
			return null;
		}

		if (url.length > 1 && (url[1].equals("mediadb")))
		{
			return null;
		}

		if (url.length > 1 && !url[1].equals("profiles"))
		{
			// Not in teams
			return null;
		}

		String profileurlname = null;
		String anythingelse = null;

		// communityurlname = subdomain[0];
		if (url.length > 1) // Might be a virtual project
		{
			profileurlname = url[2]; // Giessing
			if (url.length > 3)
			{
				anythingelse = requestedPath.substring(requestedPath.indexOf(profileurlname) + profileurlname.length());
			}
		}

		if (profileurlname == null)
		{
			String domain = util.domain(); // Not used
			RightPage page = goHome(inPage, domain);
			// if( page == null)
			// {
			// String applicationid = inPage.get("applicationid");
			//
			// Page apphome = getPageManager().getPage("/" + applicationid + "/app/index.html");
			// page = new RightPage();
			// page.setRightPage(apphome);
			// }
			return page;
		}

		// TODO: Keep a cached list of sub folders where we always load the page from
		// blogs projects etc and assume .../index.html
		if (getMediaArchive().getCatalogId().endsWith("notset"))
		{
			throw new OpenEditException("Invalid catalog for " + requestedPath + " " + getMediaArchive().getCatalogId());
		}

		String apphome = "/" + appid;
		String fixedpath = apphome + "/profiles/" + profileurlname;

		Page page = null;

		if (anythingelse == null)
		{
			page = getPageManager().getPage(fixedpath);
		}
		else
		{
			fixedpath = fixedpath + anythingelse;
			page = getPageManager().getPage(fixedpath);
		}

		RightPage right = new RightPage();
		right.putPageValue("apphome", apphome);
		if (page.exists()) // Must be a real page
		{
			if (page.isFolder())
			{
				page = getPageManager().getPage(fixedpath + "/index.html");
			}
			right.setRightPage(page);
			return right;
		}
		else
		{
			Page indexpage = getPageManager().getPage(fixedpath + "/index.html");
			if (indexpage.exists()) // Must be a real page
			{
				right.setRightPage(indexpage);
				return right;
			}
			if (Boolean.parseBoolean(page.get("virtual")))
			{
				right.setRightPage(page);
				return right;
			}
		}

		// Must be a project with something on the end?
		QueryBuilder query = getMediaArchive().query("emeprofile").exact("urlname", profileurlname).hitsPerPage(1);
		HitTracker hits = getMediaArchive().getCachedSearch(query);
		Data emeprofile = (Data) hits.first();
		if (emeprofile != null)
		{
			String template = null;
			if (anythingelse == null)
			{

				template = apphome + "/components/chat-dashboard/intro.html";
			}
			else
			{
				template = apphome + "/profile" + anythingelse;
			}
			String justname = PathUtilities.extractFileName(template);
			if (!justname.contains("."))
			{
				template = template + "/index.html";
			}
			Page otherpage = getPageManager().getPage(template);
			// if( !otherpage.exists())
			// {
			// //log.info("Cant find " + template);
			// }
			right.putParam("emeprofileid", emeprofile.getId());
			emeprofile = getMediaArchive().getSearcher("emeprofile").loadData(emeprofile);
			right.putPageValue("emeprofile", emeprofile);
			right.putPageValue("entity", emeprofile);
			Data module = getMediaArchive().getCachedData("module", "emeprofile");
			right.putPageValue("entitymodule", module);

			right.putPageValue("urlname", "/profiles/" + emeprofile.get("urlname"));
			right.setRightPage(otherpage);
			return right;
		}
		else
		{
			log.info("Couldn't find Collection: " + profileurlname);
		}
		if (log.isDebugEnabled())
			log.debug("Couldn't find any content: Orinal path: " + requestedPath + " Community Home:" + apphome);
		return null;
	}

	protected RightPage goHome(Page inPage, String domain)
	{
		String applicationid = inPage.get("applicationid");

		String apphome = "/" + applicationid;

		String template = apphome + "/index.html"; // ?communitytagcategoryid=" + first.getId()
													// communities/emedia/home.html
		Page page = getPageManager().getPage(template);
		RightPage right = new RightPage();
		right.putPageValue("apphome", apphome);

		right.setRightPage(page);
		return right;
	}

}

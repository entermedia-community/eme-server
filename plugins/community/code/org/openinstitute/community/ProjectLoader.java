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

public class ProjectLoader extends BaseManager implements PageLoader, CatalogEnabled
{
	protected PageManager fieldPageManager;
	private static final Log log = LogFactory.getLog(ProjectLoader.class);

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
		// if( sitedata != null )
		// {
		// String realpath = sitedata.get("domainpath");
		// //This just adds back the missing /site/..
		// if( !inPage.getPath().startsWith(realpath))
		// {
		// //sitedata.fixRealPath(realpath)
		// Page page = getPageManager().getPage(realpath + inPage.getPath());
		// RightPage right = new RightPage();
		// right.setRightPage(page);
		// return right;
		// }
		// }
		String requestedPath = util.getOriginalPath();
		// Only works with domains being set. Otherwise use normal page actions to load
		// project pages
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

		// Check domain?
		String domain = util.domain();
		// String[] subdomain = domain.split("\\.");
		// if(subdomain.length < 3)
		// {
		// subdomain = ("default." + domain).split("\\.");
		// }
		// String communityurlname = null;
		String secondpart = null;
		String anythingelse = null;

		// communityurlname = subdomain[0];
		if (url.length > 1) // Might be a virtual project
		{
			secondpart = url[1]; // might be wrong
			if (url.length > 2)
			{
				anythingelse = requestedPath.substring(requestedPath.indexOf(secondpart) + secondpart.length());
			}
		}

		if (secondpart == null)
		{
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
		String fixedpath = apphome + "/" + secondpart;

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
		if (secondpart.equals("project"))
		{
			secondpart = url[2];
			if (anythingelse != null)
			{
				anythingelse = requestedPath.substring(requestedPath.indexOf(secondpart) + secondpart.length());
				if (anythingelse.length() == 0)
				{
					anythingelse = null;
				}

			}
		}
		// Must be a project with something on the end?
		QueryBuilder query = getMediaArchive().query("librarycollection").exact("urlname", secondpart).hitsPerPage(1);
		HitTracker hits = getMediaArchive().getCachedSearch(query);
		Data librarycollection = (Data) hits.first();
		if (librarycollection != null)
		{
			String template = null;
			if (anythingelse == null)
			{
				template = apphome + "/project/chat/index.html";
			}
			else
				if (anythingelse.startsWith("/modules"))
				{
					template = apphome + "/views/modules/" + url[3] + "/editors/listentities/tabs/rendertypes/" + url[4] + ".html";
				}
				else
				{
					template = apphome + "/project" + anythingelse;
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
			right.putParam("collectionid", librarycollection.getId());
			librarycollection = getMediaArchive().getSearcher("librarycollection").loadData(librarycollection);
			right.putPageValue("librarycol", librarycollection);
			right.putPageValue("urlname", "/" + librarycollection.get("urlname"));
			right.setRightPage(otherpage);
			return right;
		}
		else
		{
			// log.info("Couldn't find Collection: " + secondpart);
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


import org.entermediadb.asset.MediaArchive
import org.openedit.OpenEditException
import org.openedit.profile.UserProfile
import org.openedit.users.User

public void init()
{
	MediaArchive archive = context.getPageValue("mediaarchive");

	String id = context.getRequestParameter("userid");
	if (id == null)
	{
		return;
	}
	UserProfile profile = context.getPageValue("userprofile");
	if( !profile.hasPermission("viewsettings") )
	{
		if( !id.equals(context.getUser().getId()) )
		{
			throw new OpenEditException("No permission to delete other users");
		}
	}
	
	User selecteduser = archive.getUser(id);			

	String email = selecteduser.getEmail();
	String first = selecteduser.getFirstName();
	String last = selecteduser.getLastName();
	selecteduser.setName(first + " " + last + " " + email);
	selecteduser.setEmail(null);
	selecteduser.setLastName("deleted");
	selecteduser.setFirstName("deleted");
	
	Collection uploads = archive.query("userupload").exact("owner",id).search();
	for (upload in uploads)
	{
		archive.getSearcher("userupload").delete(upload, selecteduser);
	}
	selecteduser.setValue("assetportrait",null);
	
	archive.saveData("user",selecteduser);
		
	//Clear profile pic?
	
	
	context.putPageValue("data",selecteduser);
	context.putPageValue("searcher",archive.getSearcher("user"));
	
}

init();



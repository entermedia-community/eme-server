package chatterbox;

import org.entermediadb.asset.MediaArchive
import org.openedit.Data
import org.openedit.MultiValued
import org.openedit.hittracker.HitTracker
import org.openedit.util.DateStorageUtil


//Chat Notifications

public void init()
{
	MediaArchive mediaArchive = (MediaArchive)context.getPageValue("mediaarchive");
	
	HitTracker alltopicsmodified = mediaArchive.query("goaltask").exact("taskstatus","3").search();
	for (MultiValued topicmod in alltopicsmodified)
	{
		Date day = topicmod.getDate("completedon");
		if( day == null)
		{
			day = topicmod.getDate("creationdate");
			if( day == null)
			{
				day = new Date();
			}
			topicmod.setValue("completedon",day);
			mediaArchive.saveData("goaltask",topicmod);
			log.info("Saved one " + day);
		}
	}
}

init();
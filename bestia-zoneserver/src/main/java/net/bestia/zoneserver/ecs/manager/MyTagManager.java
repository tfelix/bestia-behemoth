package net.bestia.zoneserver.ecs.manager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Entity;
import com.artemis.managers.TagManager;

public class MyTagManager extends TagManager {
	
	private Logger log = LogManager.getLogger(MyTagManager.class);
	
	@Override
	public void changed(Entity e) {
		log.info(e.toString());
	}

}

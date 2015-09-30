package net.bestia.zoneserver.script.temp;

import net.bestia.model.domain.Location;
import net.bestia.zoneserver.manager.BestiaManager;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

/**
 * Already implemented {@link MapTriggerScript} so no groovy script
 * implementation is needed. This script is used as the main portal script to
 * move a bestia/entity when entering a portal to a new location.
 * 
 * @author Thomas Felix
 *
 */
public class PortalMapTriggerScript extends MapTriggerScript {

	private final Location destination;

	public PortalMapTriggerScript(Location dest) {
		this.destination = new Location(dest);
	}

	@Override
	public void onEnter(BestiaManager entity) {

		// Currently we check if the entity is a player bestia. Maybe this is
		// removed in the future.
		if(!(entity instanceof PlayerBestiaManager)) {
			return;
		}
		
		entity.getLocation().setMapDbName(destination.getMapDbName());
		entity.getLocation().setX(destination.getX());
		entity.getLocation().setY(destination.getY());
	}

}

package net.bestia.zoneserver.script;

import net.bestia.model.domain.Location;
import net.bestia.zoneserver.manager.BestiaManager;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

/**
 * Already implemented {@link MapTriggerScript} so no groovy script
 * implementation is needed. This script is used as the main portal script to
 * move a bestia/entity when entering a portal to a new location.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PortalMapTriggerScript extends MapTriggerScript {

	private final Location destination;

	public PortalMapTriggerScript(Location dest) {
		this.destination = new Location(dest);
	}

	/**
	 * Std. Ctor. for conforming to the {@link Script} interface. Objects
	 * created via this ctor can only be used to generate script keys.
	 */
	public PortalMapTriggerScript() {
		this.destination = null;
	}

	@Override
	public void onEnter(BestiaManager entity) {

		// Currently we check if the entity is a player bestia. Maybe this is
		// removed in the future.
		if (!(entity instanceof PlayerBestiaManager)) {
			return;
		}

		entity.getLocation().setMapDbName(destination.getMapDbName());
		entity.getLocation().setX(destination.getX());
		entity.getLocation().setY(destination.getY());
	}
	
	@Override
	public void onExit(BestiaManager entity) {
		// no op.
	}
	
	@Override
	public void onInside(BestiaManager entity) {
		// no op.
	}

}

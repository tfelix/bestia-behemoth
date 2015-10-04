package net.bestia.zoneserver.script.temp;

import net.bestia.zoneserver.manager.BestiaManager;

/**
 * Script classes implementing this interface can be triggered if an entity
 * walks onto them. Most likly this will be the most used script class for maps.
 * The apropriate callbacks will be called by the ECS when an entity walks on
 * this script.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapTriggerScript {

	/**
	 * Called when an entity enters the script area.
	 */
	public void onEnter(BestiaManager entity) {
		// no op.		
	}

	/**
	 * Called when an entity leaves the script area.
	 * @param bm 
	 */
	public void onExit(BestiaManager bm) {
		// no op.
	}

	/**
	 * Called when an entity stands inside the script.
	 */
	public void onInside() {
		// no op.
	}
}

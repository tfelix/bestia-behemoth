package net.bestia.zoneserver.script;

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
public class MapTriggerScript extends MapScript {

	private final ScriptManager manager;

	/**
	 * {@link MapTriggerScript}s methods can not be called when it was
	 * constructed this way.
	 */
	public MapTriggerScript() {
		super();
		manager = null;
	}

	public MapTriggerScript(String mapDbName, String name, ScriptManager scriptManager) {
		super(mapDbName, name);
		if (scriptManager == null) {
			throw new IllegalArgumentException("ScriptManager can not be null.");
		}
		this.manager = scriptManager;
	}
	
	private void checkManager() {
		if(manager == null) {
			throw new IllegalStateException("onXXX methods can not be called when constructed via std. ctor.");
		}
	}

	/**
	 * Called when an entity enters the script area.
	 */
	public void onEnter(BestiaManager entity) {
		checkManager();
		addBinding("event", "onEnter");
		manager.execute(this);
	}

	/**
	 * Called when an entity leaves the script area.
	 * 
	 * @param bm
	 */
	public void onExit(BestiaManager entity) {
		checkManager();
		addBinding("event", "onExit");
		manager.execute(this);
	}

	/**
	 * Called when an entity stands inside the script.
	 */
	public void onInside(BestiaManager entity) {
		checkManager();
		addBinding("event", "onInside");
		manager.execute(this);
	}
}

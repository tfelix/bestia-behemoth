package net.bestia.zoneserver.script;

import net.bestia.zoneserver.proxy.BestiaEntityProxy;

/**
 * Simplyfies {@link MapScript} creation. It will cache the name of the map and
 * will use it in order to create scripts.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapScriptFactory {

	private final String mapDbName;
	private final MapScriptAPI api;

	public MapScriptFactory(String mapDbName, MapScriptAPI api) {
		this.mapDbName = mapDbName;
		this.api = api;
	}

	public MapScript getScript(String name, BestiaEntityProxy bestiaManager) {
		return new MapScript(mapDbName, name, api, bestiaManager);
	}

}

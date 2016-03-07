package net.bestia.zoneserver.script;

import net.bestia.zoneserver.manager.BestiaEntityProxy;

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

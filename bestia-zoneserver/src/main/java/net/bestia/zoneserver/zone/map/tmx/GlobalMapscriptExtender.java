package net.bestia.zoneserver.zone.map.tmx;

import net.bestia.zoneserver.zone.map.Map.MapBuilder;
import tiled.core.Map;

/**
 * Adds the global mapscripts parsed to the builder.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class GlobalMapscriptExtender implements TMXMapExtender {

	/**
	 * Reads the map scripts, parses them into the right format and fills the
	 * builder with it.
	 * 
	 * @param tiledMap
	 */
	@Override
	public void extendMap(Map tiledMap, MapBuilder builder) {
		final String scriptStr = tiledMap.getProperties().getProperty("globalScripts");

		builder.globalMapscript = scriptStr;
	}

}

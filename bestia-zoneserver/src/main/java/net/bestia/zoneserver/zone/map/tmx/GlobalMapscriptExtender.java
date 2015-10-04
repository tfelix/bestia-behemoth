package net.bestia.zoneserver.zone.map.tmx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.bestia.zoneserver.zone.map.Map.MapBuilder;
import tiled.core.Map;

/**
 * Adds the global mapscripts parsed to the builder.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
class GlobalMapscriptExtender implements TMXMapExtender {

	/**
	 * Reads the map scripts, parses them into the right format and fills the
	 * builder with it.
	 * 
	 * @param tiledMap
	 */
	@Override
	public void extendMap(Map tiledMap, MapBuilder builder) {
		final String scriptStr = tiledMap.getProperties().getProperty("globalScripts");

		if (scriptStr == null) {
			builder.mapscripts = new ArrayList<>();
			return;
		}

		final List<String> scripts = Arrays.stream(scriptStr.split(",")).map((String x) -> x.trim())
				.collect(Collectors.toList());

		builder.globalMapscripts = scripts;
	}

}

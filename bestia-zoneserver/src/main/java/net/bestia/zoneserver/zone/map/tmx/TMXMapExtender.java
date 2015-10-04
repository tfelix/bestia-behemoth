package net.bestia.zoneserver.zone.map.tmx;

import net.bestia.zoneserver.zone.map.Map.MapBuilder;
import tiled.core.Map;

/**
 * Classes implementing this interface will automagically be picked up by the
 * {@link TMXMaploader} and be used to extend the MapBuilder with certain
 * (parsable) features from the TMX mapfile. Classes implementing this interface
 * MUST have a standard constructor in order for the auto-pickup to work.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
interface TMXMapExtender {

	/**
	 * The method must extend the map builder with the given feature.
	 * 
	 * @param tiledMap
	 * @param builder
	 */
	public void extendMap(Map tiledMap, MapBuilder builder);
}

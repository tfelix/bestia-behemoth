package net.bestia.zoneserver.zone.map.tmx;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.zoneserver.zone.map.Map.MapBuilder;
import net.bestia.zoneserver.zone.shape.Rect;
import net.bestia.zoneserver.zone.spawn.SpawnLocation;
import net.bestia.zoneserver.zone.spawn.Spawner;
import tiled.core.Map;
import tiled.core.MapObject;
import tiled.core.ObjectGroup;

/**
 * Parses the spawn locations and spawn information and adds them to the map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class SpawnTMXMapExtender extends TMXMapHelper implements TMXMapExtender {

	private static final Logger LOG = LogManager.getLogger(SpawnTMXMapExtender.class);

	private java.util.Map<Integer, SpawnLocation> spawnLocations = new HashMap<>();
	private int tileSize = 1;

	@Override
	public void extendMap(Map tiledMap, MapBuilder builder) {

		final Properties mapProps = tiledMap.getProperties();
		
		if(tiledMap.getTileHeight() != tiledMap.getTileWidth()) {
			throw new IllegalStateException("Tiled map tile sizes must be qudratic!");
		}
		
		tileSize = tiledMap.getTileHeight();

		final String spawnStr = mapProps.getProperty("spawn");
		
		// Check if we have something to spawn.
		if(spawnStr == null || spawnStr.isEmpty()) {
			return;
		}
		
		final String spawns[] = spawnStr.split(";");

		// Create the locations.
		createSpawnLocations(tiledMap);

		if (spawns.length == 0) {
			// No spawn.
			return;
		}

		final List<Spawner> spawners = new ArrayList<>();

		for (String spStr : spawns) {
			final Spawner spawner = createSpawner(spStr, tiledMap);
			if (spawner == null) {
				// Could not create spawner.
				continue;
			}
			spawners.add(spawner);
		}

		builder.spawns = spawners;
	}

	private void createSpawnLocations(Map tiledMap) {
		final ObjectGroup layer = getObjectLayer(tiledMap, "spawn");

		final Iterator<MapObject> objIter = layer.getObjects();
		while (objIter.hasNext()) {
			final MapObject mapObj = objIter.next();

			final int id = createSpawnLocationId(mapObj.getName());

			if (id == -1) {
				LOG.warn("Invalid spawn area id: {}, map: {}", mapObj.getName(), getMapName(tiledMap));
				continue;
			}

			final Rectangle box = mapObj.getBounds();
			final Rect newArea = new Rect(box.x / tileSize, box.y / tileSize, box.width / tileSize, box.height / tileSize);

			if (!spawnLocations.containsKey(id)) {
				spawnLocations.put(id, new SpawnLocation(newArea));
			} else {
				spawnLocations.get(id).addArea(newArea);
			}
		}
	}

	/**
	 * Checks if the name of the spawn area/object is valid. Returns its ID or
	 * otherwise -1 which is an invalid ID.
	 * 
	 * @param name
	 * @return
	 */
	private int createSpawnLocationId(String name) {
		if (!name.startsWith("id:")) {
			return -1;
		}
		try {
			return Integer.parseInt(name.substring(3));
		} catch (NumberFormatException ex) {
			// Invalid ID.
			return -1;
		}
	}

	/**
	 * Creates a spawner who holds all information to manage bestias spawns.
	 * 
	 * @param str
	 * @param tiledMap
	 * @return
	 */
	private Spawner createSpawner(String str, Map tiledMap) {

		if (!str.matches("\\d+,\\w+,\\d+,(\\d+-\\d+|\\d+)")) {
			return null;
		}

		try {
			final String[] tokens = str.split(",");

			// ID
			final int spawnLocationId = Integer.parseInt(tokens[0]);

			// Mob name.
			final String mobName = tokens[1];

			// Number.
			final int count = Integer.parseInt(tokens[2]);

			final int minDelay, maxDelay;

			// Range.
			if (tokens[3].matches("\\d+-\\d+")) {

				final String[] split = tokens[3].split("-");
				minDelay = Integer.parseInt(split[0]);
				maxDelay = Integer.parseInt(split[1]);

			} else {
				minDelay = maxDelay = Integer.parseInt(tokens[3]);
			}

			final SpawnLocation location = spawnLocations.get(spawnLocationId);

			if (location == null) {
				LOG.warn("Not existing area id: {}. Map: {}", spawnLocationId, getMapName(tiledMap));
				return null;
			}

			return new Spawner(mobName, location, minDelay, maxDelay, count);

		} catch (NumberFormatException ex) {
			// something went wrong.
			return null;
		}

	}

}

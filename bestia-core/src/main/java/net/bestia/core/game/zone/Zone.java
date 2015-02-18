package net.bestia.core.game.zone;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The Zone holds the static mapdata as well is responsible for managing
 * entities, actors, scripts etc.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Zone {

	private final ScheduledExecutorService executor;
	private final Map map;

	public Zone(Properties config, Map map) {

		executor = Executors.newScheduledThreadPool(Integer.parseInt(config
				.getProperty("zoneThreads")));

		this.map = map;
	}

	/**
	 * Spawns a new entity on this map.
	 * 
	 * @param entity
	 */
	public void spawnEntity(Entity entity) {
		// Add the entity into the quad tree.

		// Notify all listener about the new spawn.

		// Call the script trigger of the new entity.
		
		// Notify every observer in range... (must be done here?)
	}

	/**
	 * Checks if the zone/map is walkable at the given coordinates.
	 * 
	 * @param cords
	 *            Coordinates to be checked.
	 * @return TRUE if the tile is walkable, FALSE otherwise.
	 */
	public boolean isWalkable(Point cords) {
		boolean baseWalk = map.isWalkable(cords);

		// TODO check with scripts and entities.

		return baseWalk;
	}

	/**
	 * Returns the given walkspeed for a given tile. The walkspeed is fixed
	 * point 1000 means 1.0, 500 means 0.5 and so on. If the tile is not
	 * walkable at all 0 will be returned.
	 * 
	 * @param cords
	 * @return
	 */
	public int getWalkspeed(Point cords) {
		if (!map.isWalkable(cords)) {
			return 0;
		}

		// Ask the map for the general walking speed, then look for effects of
		// placed entities who might afflict these speed.
		int baseSpeed = map.getWalkspeed(cords);

		// TODO modify the baseSpeed by entities.

		return baseSpeed;
	}

	/**
	 * Finds the shortest path between these two points. If no path could be
	 * found or if the maximum search depth was exhausted null is returned.
	 * 
	 * @param start
	 *            Start coordinates.
	 * @param end
	 *            End coordinates.
	 * @return List of coordinates to walk or null if no path could be found.
	 */
	public List<Point> findPath(Point start, Point end) {

		return null;
	}

	public void walkPath(List<Point> path) {

	}
}

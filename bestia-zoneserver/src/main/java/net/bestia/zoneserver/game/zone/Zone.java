package net.bestia.zoneserver.game.zone;

import java.util.HashMap;
import java.util.List;

import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.ecs.system.MovementSystem;
import net.bestia.zoneserver.game.manager.PlayerBestiaManager;
import net.bestia.zoneserver.game.zone.map.Map;

import com.artemis.World;

/**
 * The Zone holds the static mapdata as well is responsible for managing
 * entities, actors, scripts etc.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Zone {

	private class PlayerEntity {
		public final PlayerBestiaManager playerBestiaManager;
		public final com.artemis.Entity entity;

		public PlayerEntity(PlayerBestiaManager pbManager,
				com.artemis.Entity entity) {
			this.playerBestiaManager = pbManager;
			this.entity = entity;
		}
	}

	private final HashMap<Long, List<PlayerEntity>> playerEntities = new HashMap<>();

	private final String name;
	private final Map map;

	// EC System.
	private final World world;

	public Zone(BestiaConfiguration config, Map map) {
		this.map = map;
		this.name = map.getMapDbName();

		// Initialize ECS.
		this.world = new World();
		// Set all the managers.

		// Set all the systems.
		this.world.setSystem(new MovementSystem());
		this.world.initialize();
	}

	// =================== START GETTER AND SETTER =====================

	/**
	 * Gets the name of the zone.
	 * 
	 * @return Name of the zone.
	 */
	public String getName() {
		return name;
	}

	// ===================== END GETTER AND SETTER =====================

	/**
	 * Starts the entity system, initializes all message queues, load needed
	 * data and basically starts the zone simulation.
	 */
	public void start() {

	}

	/**
	 * Stops the zone and persists all data to the database. It also tries to
	 * persist zone dependent data to the database or a file for later
	 * reloading.
	 */
	public void stop() {

	}

	/**
	 * Checks if the zone/map is walkable at the given coordinates.
	 * 
	 * @param cords
	 *            Coordinates to be checked.
	 * @return TRUE if the tile is walkable, FALSE otherwise.
	 */
	public boolean isWalkable(Vector2 cords) {
		return map.isWalkable(cords);
	}

	/**
	 * Returns the given walkspeed for a given tile. The walkspeed is fixed
	 * point 1000 means 1.0, 500 means 0.5 and so on. If the tile is not
	 * walkable at all 0 will be returned.
	 * 
	 * @param cords
	 * @return
	 */
	public int getWalkspeed(Vector2 cords) {
		if (!map.isWalkable(cords)) {
			return 0;
		}

		// Ask the map for the general walking speed, then look for effects of
		// placed entities who might afflict these speed.
		int baseSpeed = map.getWalkspeed(cords);

		// TODO modify the baseSpeed by entities.

		return baseSpeed;
	}
}

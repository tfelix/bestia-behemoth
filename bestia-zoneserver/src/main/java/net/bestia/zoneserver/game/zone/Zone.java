package net.bestia.zoneserver.game.zone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.artemis.World;

import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.ecs.system.MovementSystem;
import net.bestia.zoneserver.game.manager.PlayerBestiaManager;
import net.bestia.zoneserver.game.zone.map.Map;

/**
 * The Zone holds the static mapdata as well is responsible for managing entities, actors, scripts etc.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Zone {

	private class PlayerEntity {
		public final PlayerBestiaManager playerBestiaManager;
		public final com.artemis.Entity entity;
		
		public PlayerEntity(PlayerBestiaManager pbManager, com.artemis.Entity entity) {	
			this.playerBestiaManager = pbManager;
			this.entity = entity;			
		}
	}
	
	private final HashMap<Long, List<PlayerEntity>> playerEntities = new HashMap<>();

	private final String name;
	private final Map map;
	
	// EC System.
	private final World world;

	/**
	 * Lock for modifying the entity storages tree and entities.
	 */
	private final ReadWriteLock entityLock = new ReentrantReadWriteLock();

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


	// TODO das hier mit einer richtigen implementierung austauschen.
	private List<Entity> temp = new ArrayList<>();

	/**
	 * Spawns a new entity on this map.
	 * 
	 * @param entity
	 */
	public void addEntity(Entity entity) {
		// Add the entity into the quad tree and hashmap.
		// registerEntity(entity);
		temp.add(entity);

		// Call the script trigger of the new entity.

		// Notify all listener about the new spawn.

		// Notify every observer in range... (must be done here?)
	}

	/*
	 * public Entity getEntity(long id) {
	 * 
	 * }
	 */

	/**
	 * Returns all entities located around a given entity id and within a given range. The choosen center entity with ID
	 * is not included. TODO Hier muss man noch klären wie genau der Identifier aussieht.
	 * 
	 * @param id
	 *            Entity ID.
	 * @param range
	 *            Range in tiles of which entites to include.
	 * @return A collection of entities.
	 */
	public Collection<Entity> getEntities(long id, int range) {	
		List<Entity> entities = new ArrayList<>(temp);
		
		entities.removeIf((x) -> x.accountId == id);
		
		return entities;
	}



	private void notifyObserver() {

	}

	/**
	 * Deletes a entity on this map.
	 * 
	 * @param entity
	 */
	public void removeEntity(Entity entity) {
		if (entity == null) {
			throw new IllegalArgumentException("Entity can not be null.");
		}

		// TODO Notify all listener about the new de-spawn.

		// TODO Call the script trigger of the entity.

		// Remove the entity into the quad tree.

		// TODO Notify every observer in range... (must be done here?)
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

		/*
		 * entityLock.readLock().lock(); List<Entity> entities = tree.get(cords); for (Entity ent : entities) { if
		 * (!ent.isColliding()) { continue; } if (ent.getCollision().collide(cords)) { baseWalk = false; break; } }
		 * entityLock.readLock().unlock();
		 */

		// return baseWalk;
		return true;
	}

	/**
	 * Returns the given walkspeed for a given tile. The walkspeed is fixed point 1000 means 1.0, 500 means 0.5 and so
	 * on. If the tile is not walkable at all 0 will be returned.
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
}

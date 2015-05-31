package net.bestia.core.game.zone;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.bestia.core.game.zone.entity.QuadTree2;
import net.bestia.core.game.zone.map.Map;
import net.bestia.util.BestiaConfiguration;

/**
 * The Zone holds the static mapdata as well is responsible for managing
 * entities, actors, scripts etc.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Zone {

	public enum Event {
		ON_ENTITY_SPAWN
	}

	public interface ZoneObserver {

	}

	private final ScheduledExecutorService executor;

	private final String name;
	private final Map map;

	/**
	 * Lock for modifying the entity storages tree and entities.
	 */
	private final ReadWriteLock entityLock = new ReentrantReadWriteLock();
	/**
	 * Lock for modifying the observer lists.
	 */
	private final ReadWriteLock observerLock = new ReentrantReadWriteLock();

	private final java.util.Map<Long, Entity> entities;
	private final QuadTree2 tree;
	private final java.util.Map<Event, Set<Entity>> observer;

	public Zone(BestiaConfiguration config, String name, Map map) {

		executor = Executors.newScheduledThreadPool(Integer.parseInt(config
				.getProperty("zoneThreads")));

		this.map = map;
		this.name = name;

		Dimension dimen = map.getDimension();
		tree = new QuadTree2(0, 0, dimen.getWidth(), dimen.getHeight());
		entities = new HashMap<Long, Entity>();

		observer = new EnumMap<Event, Set<Entity>>(Event.class);
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
	 * Adds the entity to the holding structures.
	 * 
	 * @param entity
	 *            Entity to register/add.
	 */
	private void registerEntity(Entity entity) {

		final Point cords = entity.getLocation();
		final Dimension shape = entity.getBoundingBox();

		entityLock.writeLock().lock();
		entities.put(entity.getId(), entity);
		for (int y = cords.y; y < cords.y + shape.getHeight(); y++) {
			for (int x = cords.x; x < cords.x + shape.getWidth(); x++) {
				tree.remove(x, y);
			}
		}
		entityLock.writeLock().unlock();

	}

	/**
	 * Removes the entity to the holding structures.
	 * 
	 * @param entity
	 *            Entity to unregister/remove.
	 */
	private void unregisterEntity(Entity entity) {

		final Point cords = entity.getLocation();
		final Dimension shape = entity.getBoundingBox();

		entityLock.writeLock().lock();
		entities.remove(entity.getId());
		for (int y = cords.y; y < cords.y + shape.getHeight(); y++) {
			for (int x = cords.x; x < cords.x + shape.getWidth(); x++) {
				tree.remove(x, y);
			}
		}
		entityLock.writeLock().unlock();

	}

	/**
	 * Spawns a new entity on this map.
	 * 
	 * @param entity
	 */
	public void addEntity(Entity entity) {
		// Add the entity into the quad tree and hashmap.
		registerEntity(entity);

		// Call the script trigger of the new entity.

		// Notify all listener about the new spawn.

		// Notify every observer in range... (must be done here?)
	}

	/**
	 * Registers a observer to the zone.
	 * 
	 * @param entity
	 */
	public void addObserver(Event event, Entity entity) {

		observerLock.writeLock().lock();

		if (!observer.containsKey(event)) {
			observer.put(event, new HashSet<Entity>());
		}
		observer.get(entity).add(entity);

		observerLock.writeLock().unlock();

	}

	/**
	 * Removes the observing entity from all listeners. MUST be called if the
	 * entity gets removed.
	 * 
	 * @param entity
	 */
	public void removeObserver(Entity entity) {

		observerLock.writeLock().lock();

		for (Set<Entity> obs : observer.values()) {
			obs.remove(entity);
		}

		observerLock.writeLock().unlock();
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
		unregisterEntity(entity);

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

		entityLock.readLock().lock();
		List<Entity> entities = tree.get(cords);
		for (Entity ent : entities) {
			if (!ent.isColliding()) {
				continue;
			}
			if (ent.getCollision().collide(cords)) {
				baseWalk = false;
				break;
			}
		}
		entityLock.readLock().unlock();

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
	 * Checks if the {@link Entity} is living inside this zone.
	 * 
	 * @param id
	 *            Id of the entity.
	 * @return TRUE if the Entity is active. FALSE otherwise.
	 */
	public boolean hasEntity(Long id) {
		entityLock.readLock().lock();
		boolean hasEntity = entities.containsKey(id);
		entityLock.readLock().unlock();
		return hasEntity;
	}

	/**
	 * @see #hasEntity(Long)
	 * 
	 * @param Entity
	 *            Entity to look up.
	 * @return TRUE if the Entity is active. FALSE otherwise.
	 */
	public boolean hasEntity(Entity entity) {
		return hasEntity(entity.getId());
	}

	/**
	 * Returns the number of entities currently managed by this zone.
	 * 
	 * @return Number of active entities.
	 */
	public int countEntities() {
		final int numEntities;
		entityLock.readLock().lock();
		numEntities = entities.size();
		entityLock.readLock().unlock();
		return numEntities;
	}

	/*
	public void scheduleNotify(final Duration duration, final Entity entity,
			final int requestCode) {
		executor.schedule(new Runnable() {

			@Override
			public void run() {
				// Check if the entity is still alive.
				if (!hasEntity(entity)) {
					return;
				}
				// We need to lock the entity since we dont know what
				// happens inside the callback.
				synchronized (entity) {
					entity.onTick(Zone.this, requestCode);
				}
			}
		}, duration.toMillis(), TimeUnit.MILLISECONDS);
	}*/
}

package net.bestia.zoneserver.game.zone;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import net.bestia.messages.Message;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.ecs.component.PlayerControlled;
import net.bestia.zoneserver.ecs.system.MovementSystem;
import net.bestia.zoneserver.ecs.system.PlayerControlSystem;
import net.bestia.zoneserver.game.manager.PlayerBestiaManager;
import net.bestia.zoneserver.game.zone.map.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.utils.EntityBuilder;

/**
 * The Zone holds the static mapdata as well is responsible for managing entities, actors, scripts etc.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Zone {

	private static final Logger log = LogManager.getLogger(Zone.class);

	private class ZoneTicker implements Runnable {

		private long lastRun = 0;

		@Override
		public void run() {
			lastRun = System.currentTimeMillis();

			while (true) {
				// Delta in s.
				final long now = System.currentTimeMillis();
				float delta = (now - lastRun) / 1000f;
				lastRun = now;
				world.setDelta(delta);
				world.process();

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// no op.
				}
			}
		}

	}

	/**
	 * Holds the player input for the registered bestias.
	 */
	private final HashMap<Integer, Queue<Message>> playerInput = new HashMap<>();
	private final Set<Integer> activePlayerBestias = new HashSet<>();

	private final String name;
	private final Map map;
	private AtomicBoolean hasStarted = new AtomicBoolean(false);

	private final Thread zoneTickerThread;

	// EC System.
	private final World world;

	public Zone(BestiaConfiguration config, Map map) {
		if (map == null) {
			throw new IllegalArgumentException("Map can not be null.");
		}

		this.map = map;
		this.name = map.getMapDbName();

		if (this.name == null || this.name.isEmpty()) {
			throw new IllegalArgumentException("Zone name can not be null or empty.");
		}

		// Initialize ECS.
		final WorldConfiguration worldConfig = new WorldConfiguration().register(this);
		this.world = new World(worldConfig);
		// Set all the managers.

		// Set all the systems.
		this.world.setSystem(new MovementSystem());
		this.world.setSystem(new PlayerControlSystem());

		this.world.initialize();

		zoneTickerThread = new Thread(null, new ZoneTicker(), "zoneECS-" + name);
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
	 * Starts the entity system, initializes all message queues, load needed data and basically starts the zone
	 * simulation.
	 */
	public void start() {
		zoneTickerThread.start();
		hasStarted.set(true);

		log.debug("Zone {} has started.", name);
	}

	/**
	 * Stops the zone and persists all data to the database. It also tries to persist zone dependent data to the
	 * database or a file for later reloading.
	 */
	public void stop() {

	}

	/**
	 * Checks if the zone/map is walkable at the given coordinates. It will consider all temporary effects and
	 * collidable entities on the ground aswell.
	 * 
	 * @param cords
	 *            Coordinates to be checked.
	 * @return TRUE if the tile is walkable, FALSE otherwise.
	 */
	public boolean isWalkable(Vector2 cords) {
		checkStart();
		return map.isWalkable(cords);
	}

	/**
	 * Adds this bestia to the zone and spawns it.
	 * 
	 * @param pb
	 *            PlayerBestia to add.
	 */
	public void addPlayerBestia(PlayerBestiaManager pb) {
		checkStart();
		log.debug("Adding {} to zone {}.", pb.toString(), name);

		// Prepare the communications.
		activePlayerBestias.add(pb.getBestia().getId());
		playerInput.put(pb.getBestia().getId(), new ConcurrentLinkedQueue<>());

		// Spawn the entity.
		new EntityBuilder(world).with(new PlayerControlled(pb)).build();

	}

	/**
	 * Removes the bestia from the entity system and
	 * 
	 * @param pb
	 */
	public void removePlayerBestia(PlayerBestia pb) {
		checkStart();

		log.debug("Removing {} from zone {}.", pb.toString(), name);
	}

	/**
	 * Returns the given walkspeed for a given tile. The walkspeed is fixed point 1000 means 1.0, 500 means 0.5 and so
	 * on. If the tile is not walkable at all 0 will be returned.
	 * 
	 * @param cords
	 * @return
	 */
	public int getWalkspeed(Vector2 cords) {
		checkStart();

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
	 * Checks if the zone is started. Throws a invalid state exception if this is not the case (since we assume it
	 * SHOULD be started if this method is invoked.
	 */
	private void checkStart() {
		if (!hasStarted.get()) {
			throw new IllegalStateException("Zone is not running. Call .start() first before doing this operation!");
		}
	}
}

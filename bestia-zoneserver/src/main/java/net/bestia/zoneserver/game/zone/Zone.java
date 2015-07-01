package net.bestia.zoneserver.game.zone;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import net.bestia.messages.Message;
import net.bestia.model.domain.Location;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.PlayerControlled;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.manager.MyTagManager;
import net.bestia.zoneserver.ecs.system.MovementSystem;
import net.bestia.zoneserver.ecs.system.PlayerControlSystem;
import net.bestia.zoneserver.game.manager.PlayerBestiaManager;
import net.bestia.zoneserver.game.zone.map.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Entity;
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
				final long now = System.currentTimeMillis();
				float delta = now - lastRun;
				lastRun = now;
				world.setDelta(delta);
				world.process();

				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					// no op.
				}
			}
		}

	}

	/**
	 * Holds the player input for the registered bestias.
	 */
	private final java.util.Map<Integer, Queue<Message>> playerInput = new ConcurrentHashMap<>();

	private final String name;
	private final Map map;
	private AtomicBoolean hasStarted = new AtomicBoolean(false);

	private final Thread zoneTickerThread;

	// EC System.
	private final World world;

	public Zone(CommandContext ctx, Map map) {
		if(ctx == null) {
			throw new IllegalArgumentException("Context can not be null.");
		}	
		if (map == null) {
			throw new IllegalArgumentException("Map can not be null.");
		}

		this.map = map;
		this.name = map.getMapDbName();

		if (this.name == null || this.name.isEmpty()) {
			throw new IllegalArgumentException("Zone name can not be null or empty.");
		}

		// Initialize ECS.
		final WorldConfiguration worldConfig = new WorldConfiguration();
		//worldConfig.register(this).register(map).register();
		this.world = new World(worldConfig);

		// Set all the systems.
		this.world.setSystem(new MovementSystem());
		this.world.setSystem(new PlayerControlSystem());

		// Set all the managers.
		this.world.setManager(new MyTagManager());

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
		playerInput.put(pb.getBestia().getId(), new ConcurrentLinkedQueue<>());

		// Spawn the entity.
		final Location curLoc = pb.getBestia().getCurrentPosition();
		Entity e = new EntityBuilder(world).with(new PlayerControlled(pb), new Position(curLoc.getX(), curLoc.getY()))
				.build();
		world.getManager(MyTagManager.class).register("PLAYER", e);
	}

	public void processPlayerInput(Message msg) {
		checkStart();
		playerInput.get(msg.getPlayerBestiaId()).add(msg);
	}

	/**
	 * Returns the input message queue of the player bestia with the given id. It is sure that the bestia is on this
	 * zone otherwise the messages would be redirected to a different zone.
	 * 
	 * @param playerBestiaId
	 * @return
	 */
	public Queue<Message> getPlayerInput(int playerBestiaId) {
		checkStart();
		return playerInput.get(playerBestiaId);
	}

	/**
	 * Removes the bestia from the entity system and
	 * 
	 * @param pb
	 */
	public void removePlayerBestia(PlayerBestia pb) {
		checkStart();

		// TODO

		log.debug("Removing {} from zone {}.", pb.toString(), name);
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

	@Override
	public String toString() {
		return String.format("Zone[name: %s, hasStarted: %s]", name, hasStarted.toString());
	}
}

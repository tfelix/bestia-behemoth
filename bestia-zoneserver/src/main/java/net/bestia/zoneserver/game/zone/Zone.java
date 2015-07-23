package net.bestia.zoneserver.game.zone;

import java.util.concurrent.atomic.AtomicBoolean;

import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.ecs.system.MovementSystem;
import net.bestia.zoneserver.ecs.system.PlayerNetworkUpdateSystem;
import net.bestia.zoneserver.ecs.system.PersistSystem;
import net.bestia.zoneserver.ecs.system.PlayerControlSystem;
import net.bestia.zoneserver.ecs.system.VisibleNetworkUpdateSystem;
import net.bestia.zoneserver.game.zone.map.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.AspectSubscriptionManager;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.GroupManager;
import com.artemis.managers.TagManager;
import com.artemis.managers.UuidEntityManager;
import com.artemis.utils.EntityBuilder;

/**
 * The Zone holds the static mapdata as well is responsible for managing entities, actors, scripts etc. The entity
 * management is done via an ECS. The important data (player bestias etc.) is periodically persistet into the database.
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

	private final String name;
	private final Map map;
	private AtomicBoolean hasStarted = new AtomicBoolean(false);

	private final Thread zoneTickerThread;

	// EC System.
	private final World world;

	public Zone(CommandContext ctx, Map map) {
		if (ctx == null) {
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

		// TODO Das ECS sollte auch in einer eigenen Klasse gekapselt sein.

		// Initialize ECS.
		final WorldConfiguration worldConfig = new WorldConfiguration();
		// Register all external helper objects.
		worldConfig.register(this);
		worldConfig.register(map);
		worldConfig.register(ctx);
		worldConfig.register(ctx.getServer().getInputController());

		// Set all the systems.
		worldConfig.setSystem(new PlayerControlSystem());
		worldConfig.setSystem(new MovementSystem());
		worldConfig.setSystem(new PlayerNetworkUpdateSystem());
		worldConfig.setSystem(new VisibleNetworkUpdateSystem());
		worldConfig.setSystem(new PersistSystem(10000));

		// Set all the managers.
		worldConfig.setManager(new GroupManager());
		worldConfig.setManager(new TagManager());
		worldConfig.setManager(new UuidEntityManager());

		this.world = new World(worldConfig);

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
	 * @depricated ECS soll das nun testen.
	 * @param cords
	 *            Coordinates to be checked.
	 * @return TRUE if the tile is walkable, FALSE otherwise.
	 */
	public boolean isWalkable(Vector2 cords) {
		checkStart();
		return map.isWalkable(cords);
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

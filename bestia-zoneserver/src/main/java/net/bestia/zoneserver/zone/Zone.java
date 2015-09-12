package net.bestia.zoneserver.zone;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import net.bestia.messages.InputMessage;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.BestiaRegister.InputControllerCallback;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Input;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.ecs.message.DespawnPlayerBestiaMessage;
import net.bestia.zoneserver.ecs.message.SpawnPlayerBestiaMessage;
import net.bestia.zoneserver.ecs.system.AISystem;
import net.bestia.zoneserver.ecs.system.ActiveSpawnSystem;
import net.bestia.zoneserver.ecs.system.BestiaMovementSystem;
import net.bestia.zoneserver.ecs.system.ChatSystem;
import net.bestia.zoneserver.ecs.system.InputSystem;
import net.bestia.zoneserver.ecs.system.PersistSystem;
import net.bestia.zoneserver.ecs.system.VisibleSpawnSystem;
import net.bestia.zoneserver.manager.PlayerBestiaManager;
import net.bestia.zoneserver.zone.map.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Entity;
import com.artemis.EntityEdit;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.PlayerManager;
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

	private class ZoneTicker implements Runnable, InputControllerCallback {

		private long lastRun = 0;

		/**
		 * Delay between ticks of the zone. Depending on the work on the zone the sleep time is adjusted to hit the
		 * delay as good as possible.
		 */
		private static final int DELAY_MS = 10;

		/**
		 * How many input messages are piped into the zone at each tick. Limit this to a reasonable number in order to
		 * avoid starvation of the zone on massive massge input.
		 */
		private static final int MAX_PROCESSED_MSGS = 10;

		private final World world;

		private final CommandContext ctx;

		public ZoneTicker(World world, CommandContext ctx) {
			this.world = world;
			this.ctx = ctx;
		}

		private void createInputEntity(InputMessage msg) {
			final EntityBuilder builder = new EntityBuilder(world);
			final Entity e = builder.build();
			final EntityEdit ee = e.edit();
			final Input input = ee.create(Input.class);
			input.inputMessage = msg;
			log.trace("Creating input entity: {}", msg.toString());
		}

		@Override
		public void run() {

			lastRun = System.currentTimeMillis();

			while (hasStarted.get()) {
				final long now = System.currentTimeMillis();
				final float delta = now - lastRun;

				lastRun = now;

				// We now pipe the messages into the zone as entities in order to let the system process them.
				InputMessage msg = messageQueue.poll();
				int i = 0;
				while (msg != null) {

					// Create entity with input message.
					createInputEntity(msg);

					if (i++ >= MAX_PROCESSED_MSGS) {
						log.warn("Too much input messages queued. Slowing processing to avoid starvation of zone.");
						break;
					}

					msg = messageQueue.poll();
				}

				try {

					// Let the world tick.
					world.setDelta(delta);
					world.process();

				} catch (Exception e) {
					log.error("Exception in zone: {}.", getName(), e);
				}

				try {
					if (delta > DELAY_MS) {
						continue;
					}
					Thread.sleep(DELAY_MS - (int) delta);
				} catch (InterruptedException e) {
					// no op.
				}
			}

			// TODO Persist the dying world.
			messageQueue.clear();
			// Add poisen pill and tick once more.
			world.setDelta(DELAY_MS);
			world.process();
			// World is now persisted.
		}

		@Override
		public void removedBestia(long accId, int bestiaId) {
			final DespawnPlayerBestiaMessage despawnMsg = new DespawnPlayerBestiaMessage(accId, bestiaId);
			sendInput(despawnMsg);
		}

		@Override
		public void removedAccount(long accountId) {
			// no op.
		}

		@Override
		public void addedAccount(long accountId) {
			// no op.
		}

		@Override
		public void addedBestia(long accId, int bestiaId) {
			// Check if this bestia belongs to this zone if so create a responsible spawn command.
			final PlayerBestiaManager pbm = ctx.getServer().getBestiaRegister().getSpawnedBestia(accId, bestiaId);
			
			if(!pbm.getLocation().getMapDbName().equals(name)) {
				return;
			}
			
			final SpawnPlayerBestiaMessage spawnMsg = new SpawnPlayerBestiaMessage(accId, bestiaId);
			sendInput(spawnMsg);
		}
	}

	private final String name;
	private final Map map;
	private AtomicBoolean hasStarted = new AtomicBoolean(false);
	private final Queue<InputMessage> messageQueue = new ConcurrentLinkedQueue<>();
	private final CommandContext cmdContext;

	private final ZoneTicker zoneTicker;
	private final Thread zoneTickerThread;

	public Zone(CommandContext ctx, Map map) {
		if (ctx == null) {
			throw new IllegalArgumentException("Context can not be null.");
		}
		if (map == null) {
			throw new IllegalArgumentException("Map can not be null.");
		}

		this.map = map;
		this.name = map.getMapDbName();
		this.cmdContext = ctx;

		if (this.name == null || this.name.isEmpty()) {
			throw new IllegalArgumentException("Zone name can not be null or empty.");
		}

		// Initialize ECS.
		final WorldConfiguration worldConfig = new WorldConfiguration();
		// Register all external helper objects.
		worldConfig.register(this);
		worldConfig.register(map);
		worldConfig.register(ctx);
		worldConfig.register(ctx.getServer().getBestiaRegister());

		// Set all the systems.
		worldConfig.setSystem(new InputSystem());
		worldConfig.setSystem(new BestiaMovementSystem());
		worldConfig.setSystem(new AISystem());
		worldConfig.setSystem(new ChatSystem());
		worldConfig.setSystem(new ActiveSpawnSystem());
		worldConfig.setSystem(new VisibleSpawnSystem());
		//worldConfig.setSystem(new PersistSystem(10000));

		// Set all the managers.
		worldConfig.setManager(new PlayerManager());
		worldConfig.setManager(new TagManager());
		worldConfig.setManager(new UuidEntityManager());

		zoneTicker = new ZoneTicker(new World(worldConfig), ctx);
		zoneTickerThread = new Thread(null, zoneTicker, "zoneECS-" + name);
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

	public void sendInput(InputMessage msg) {
		if (!hasStarted.get()) {
			log.warn("Zone already stopped. Does not process messages anymore.");
			return;
		}
		messageQueue.add(msg);
	}

	/**
	 * Starts the entity system, initializes all message queues, load needed data and basically starts the zone
	 * simulation.
	 *
	 */
	public void start() {
		if (hasStarted.get()) {
			throw new IllegalStateException("Zone can not be started twice.");
		}

		hasStarted.set(true);
		zoneTickerThread.start();

		cmdContext.getServer().getBestiaRegister().addCallback(zoneTicker);

		log.debug("Zone {} has started.", name);
	}

	/**
	 * Stops the zone and persists all data to the database. It also tries to persist zone dependent data to the
	 * database or a file for later reloading.
	 */
	public void stop() {
		if (!hasStarted.get()) {
			return;
		}

		hasStarted.set(false);

		log.debug("Zone {} has stopped.", name);
	}

	@Override
	public String toString() {
		return String.format("Zone[name: %s, hasStarted: %s]", name, hasStarted.toString());
	}
}

package net.bestia.zoneserver.zone;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import net.bestia.messages.InputMessage;
import net.bestia.messages.Message;
import net.bestia.zoneserver.MessageProcessor;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.BestiaRegister.InputControllerCallback;
import net.bestia.zoneserver.ecs.component.Input;
import net.bestia.zoneserver.ecs.manager.WorldPersistenceManager;
import net.bestia.zoneserver.ecs.message.DespawnPlayerBestiaMessage;
import net.bestia.zoneserver.ecs.message.SpawnPlayerBestiaMessage;
import net.bestia.zoneserver.manager.PlayerBestiaManager;
import net.bestia.zoneserver.zone.map.Map;
import net.bestia.zoneserver.zone.world.WorldExtender;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Entity;
import com.artemis.EntityEdit;
import com.artemis.World;
import com.artemis.utils.EntityBuilder;

/**
 * The Zone holds the static mapdata as well is responsible for managing
 * entities, actors, scripts etc. The entity management is done via an ECS. The
 * important data (player bestias etc.) is periodically persisted into the
 * database.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Zone implements MessageProcessor {

	private static final Logger log = LogManager.getLogger(Zone.class);

	private class ZoneTicker implements Runnable, InputControllerCallback {

		private long lastRun = 0;

		/**
		 * Delay between ticks of the zone. Depending on the work on the zone
		 * the sleep time is adjusted to hit the delay as good as possible.
		 */
		private static final int DELAY_MS = 10;

		/**
		 * How many input messages are piped into the zone at each tick. Limit
		 * this to a reasonable number in order to avoid starvation of the zone
		 * on massive massge input.
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

			// Load the persisted entities if it was desired.
			if (ctx.getConfiguration().getIntProperty("zone.cleanLoad") != null) {
				world.getSystem(WorldPersistenceManager.class).load();
			}

			lastRun = System.currentTimeMillis();

			while (hasStarted.get()) {
				final long now = System.currentTimeMillis();
				final float delta = now - lastRun;

				lastRun = now;

				// We now pipe the messages into the zone as entities in order
				// to let the system process them.
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

			// Persist the dying world.
			messageQueue.clear();
			try {
				world.getSystem(WorldPersistenceManager.class).save();
			} catch (IOException e) {
				log.error("Could not persist the zone entities. %s", e.getMessage(), e);
			}
		}

		@Override
		public void removedBestia(long accId, int bestiaId) {
			final DespawnPlayerBestiaMessage despawnMsg = new DespawnPlayerBestiaMessage(
					accId, bestiaId);
			processMessage(despawnMsg);
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
			// Check if this bestia belongs to this zone if so create a
			// responsible spawn command.
			final PlayerBestiaManager pbm = ctx.getServer().getBestiaRegister().getSpawnedBestia(accId, bestiaId);

			if (!pbm.getLocation().getMapDbName().equals(name)) {
				return;
			}

			final SpawnPlayerBestiaMessage spawnMsg = new SpawnPlayerBestiaMessage(accId, bestiaId);
			processMessage(spawnMsg);
		}
	}

	private final String name;
	private final Map map;
	private AtomicBoolean hasStarted = new AtomicBoolean(false);
	private final Queue<InputMessage> messageQueue = new ConcurrentLinkedQueue<>();
	private final CommandContext cmdContext;

	private ZoneTicker zoneTicker;
	private Thread zoneTickerThread;

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
			throw new IllegalArgumentException(
					"Zone name can not be null or empty.");
		}
	}

	/**
	 * Gets the name of the zone.
	 * 
	 * @return Name of the zone.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Starts the entity system, initializes all message queues, load needed
	 * data and basically starts the zone simulation.
	 *
	 */
	public void start() {
		if (hasStarted.get()) {
			throw new IllegalStateException("Zone can not be started twice.");
		}

		// Create and prepare the thread.
		final WorldExtender worldExtender = new WorldExtender(cmdContext.getConfiguration());
		final World world = worldExtender.createWorld(cmdContext, map);
		zoneTicker = new ZoneTicker(world, cmdContext);
		zoneTickerThread = new Thread(null, zoneTicker, "zoneECS-" + name);

		hasStarted.set(true);
		zoneTickerThread.start();

		// Add the ticker thread of the ECS so newly spawned bestias will be
		// created to the zone.
		cmdContext.getServer().getBestiaRegister().addCallback(zoneTicker);
		log.debug("Zone {} has started.", name);
	}

	/**
	 * Stops the zone and persists all data to the database. It also tries to
	 * persist zone dependent data to the database or a file for later
	 * reloading.
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
		return String.format("Zone[name: %s, hasStarted: %s]", name,
				hasStarted.toString());
	}

	@Override
	public void processMessage(Message msg) {

		if (!(msg instanceof InputMessage)) {
			throw new IllegalArgumentException("Message is not of type InputMessage.");
		}

		if (!hasStarted.get()) {
			log.warn("Zone already stopped. Does not process messages anymore.");
			return;
		}
		messageQueue.add((InputMessage) msg);
	}
}

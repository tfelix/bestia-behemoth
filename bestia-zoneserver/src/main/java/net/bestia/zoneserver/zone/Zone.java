package net.bestia.zoneserver.zone;

import java.io.IOException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.World;

import net.bestia.messages.Message;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.command.ecs.ECSCommandFactory;
import net.bestia.zoneserver.ecs.manager.WorldPersistenceManager;
import net.bestia.zoneserver.messaging.MessageHandler;
import net.bestia.zoneserver.zone.map.Map;
import net.bestia.zoneserver.zone.world.WorldExtender;

/**
 * The Zone holds the static mapdata as well is responsible for managing
 * entities, actors, scripts etc. The entity management is done via an ECS. The
 * important data (player bestias etc.) is periodically persisted into the
 * database.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Zone implements MessageHandler {

	private static final Logger LOG = LogManager.getLogger(Zone.class);

	private class ZoneRunnable implements Runnable {

		private long lastRun = 0;

		private static final int ZONE_FPS = 50;

		/**
		 * Delay between ticks of the zone. Depending on the work on the zone
		 * the sleep time is adjusted to hit the delay as good as possible.
		 */
		private static final int DELAY_MS = 1000 / ZONE_FPS;

		/**
		 * How many input messages are piped into the zone at each tick. Limit
		 * this to a reasonable number in order to avoid starvation of the zone
		 * on massive massage input.
		 */
		private static final int MAX_PROCESSED_MSGS = 10;

		private final World world;
		private final CommandContext ctx;
		private final ECSCommandFactory commandFactory;

		public ZoneRunnable(World world, ECSCommandFactory cmdFactory, CommandContext ctx, Map map) {
			this.world = world;
			this.ctx = ctx;
			this.commandFactory = cmdFactory;
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
				Message msg = messageQueue.poll();
				int i = 0;
				while (msg != null) {

					// Create a ECS command and immediately run it.
					final Command cmd = commandFactory.getCommand(msg);
					if (cmd != null) {
						try {
							cmd.run();
						} catch (Exception e) {
							LOG.error("Error while executing command: {}", cmd.toString(), e);
						}
					}

					if (i++ >= MAX_PROCESSED_MSGS) {
						LOG.warn("Too much input messages queued. Slowing processing to avoid starvation of zone.");
						break;
					}

					msg = messageQueue.poll();
				}

				// Let the world tick.
				try {
					world.setDelta(delta);
					world.process();
				} catch (Exception ex) {
					// Stop the server.
					LOG.fatal("Exception in zone: {}. Stopping.", name, ex);
					ctx.getServer().stop();
				}

				try {
					if (delta > DELAY_MS) {
						continue;
					}
					Thread.sleep(DELAY_MS - (int) delta);
				} catch (InterruptedException e) {
					// no op.
				}

				// hasStarted.set(false);
			}

			messageQueue.clear();

			// Persist the dying world.
			try {
				world.getSystem(WorldPersistenceManager.class).save();
			} catch (IOException e) {
				LOG.error("Could not persist the zone entities. %s", e.getMessage(), e);
			}
		}
	}

	private final String name;
	private final Map map;
	private final AtomicBoolean hasStarted = new AtomicBoolean(false);
	private final Queue<Message> messageQueue = new ConcurrentLinkedQueue<>();
	private final CommandContext cmdContext;

	private ZoneRunnable zoneTicker;
	private Thread zoneTickerThread;

	/**
	 * Ctor.
	 * 
	 * @param ctx
	 * @param map
	 */
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
		final WorldExtender worldExtender = new WorldExtender(cmdContext.getConfiguration(), this);
		final World world = worldExtender.createWorld(cmdContext, map);

		final ECSCommandFactory cmdFactory = new ECSCommandFactory(cmdContext, world, map, this);

		// A zone must handle the messages in their own thread so we need to
		// direct ALL messages we need to know into our own threading system. We
		// therefore can use a MessageCommandHelper.
		final Set<String> messageIDs = cmdFactory.getRegisteredMessageIds();
		messageIDs.forEach(id -> cmdContext.getMessageProvider().subscribe(id, this));

		zoneTicker = new ZoneRunnable(world, cmdFactory, cmdContext, map);
		zoneTickerThread = new Thread(null, zoneTicker, "zoneECS-" + name);

		hasStarted.set(true);
		zoneTickerThread.start();

		LOG.debug("Zone {} has started.", name);
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

		LOG.debug("Zone {} has stopped.", name);
	}

	@Override
	public String toString() {
		return String.format("Zone[name: %s, hasStarted: %s]", name, hasStarted.toString());
	}

	@Override
	public void handleMessage(Message msg) {

		if (!hasStarted.get()) {
			LOG.warn("Zone already stopped. Does not process messages anymore.");
			return;
		}

		LOG.trace("Message {} received. Path: {}.", msg.toString(), msg.getMessagePath());

		messageQueue.add(msg);
	}
}

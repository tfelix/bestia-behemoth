package net.bestia.zoneserver.environment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.model.shape.Point;

/**
 * The manager is responsible for controlling the added environment layer. The
 * layer itself are responsible for maintaining are correct state. Emitter are
 * placed inside the manager and they will set direct values of one ore more
 * layer as long as they are active. There is also a system of layer
 * interactions. After each tick of each layer is done and they are in an
 * equilibrium the layer interaction will and can change layer according to
 * their values.
 * <p>
 * These layers are currently in use:
 * </p>
 * <ul>
 * <li>humidity_sky</li>
 * <li>humidity_ground</li>
 * <li>temperature</li>
 * <li>rain</li>
 * <li>waterlevel</li>
 * <li>noise</li>
 * </ul>
 * 
 * @formatter:off
 * 
 * @formatter:on
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EnvironmentManager {

	private static final Logger LOG = LoggerFactory.getLogger(EnvironmentManager.class);

	private class EmitterKey {

		private final Point point;
		private final String layerName;

		public EmitterKey(String layerName, int x, int y) {
			this.point = new Point(x, y);
			this.layerName = layerName;
		}

		private EnvironmentManager getOuterType() {
			return EnvironmentManager.this;
		}

		@Override
		public int hashCode() {
			return Objects.hash(point, layerName);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			EmitterKey other = (EmitterKey) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (!layerName.equals(other.layerName))
				return false;
			if (!point.equals(other.point))
				return false;
			return true;
		}

	}

	private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
	private final Lock writeLock = readWriteLock.writeLock();
	private final Lock readLock = readWriteLock.readLock();

	private Map<String, Layer> layers = new HashMap<>();
	private List<Exchanger> exchangers = new ArrayList<>();
	private Map<EmitterKey, Emitter> emitters = new HashMap<>();

	private Thread thread;
	private final Runnable simulatorThread = new Runnable() {

		@Override
		public void run() {

			LOG.trace("Starting environment simulation.");

			while (hasSimulationStarted.get()) {

				readLock.lock();
				try {

					for (Layer l : layers.values()) {
						
						l.tick();
					}

					for (Exchanger e : exchangers) {
						e.calculateExchange(EnvironmentManager.this);
					}
					
				} finally {
					readLock.unlock();
				}

			}

			LOG.trace("Environment simulation stopped.");

		}
	};

	private AtomicBoolean hasSimulationStarted = new AtomicBoolean(false);

	/**
	 * Adds a new layer to the manager. This will only work before the
	 * {@link EnvironmentManager} has been started. If the simulation has
	 * started this wont work anymore.
	 * 
	 * @param layerName
	 * @param layer
	 */
	public void addLayer(String layerName, Layer layer) {
		if (hasSimulationStarted.get()) {
			throw new IllegalStateException(
					"Environment simulation has already started. Adding new layers is not possible anymore.");
		}

		layers.put(layerName, layer);
	}

	/**
	 * Returns the layer with the given name.
	 * 
	 * @param layerName
	 *            The name of the layer.
	 * @return The found {@link Layer} or NULL.
	 */
	public Layer getLayer(String layerName) {
		return layers.get(layerName);
	}

	public void removeEmitter() {
		writeLock.lock();
		try {

		} finally {
			writeLock.unlock();
		}
	}

	public void addEmitter(String layerName, Emitter emitter) {
		writeLock.lock();
		try {
			if (!layers.containsKey(layerName)) {
				throw new IllegalArgumentException("Layer not present needed for emitter: " + layerName);
			}

			// Check if the emitter is within the layer dimensions.
			final Layer l = layers.get(layerName);

			if (emitter.getX() > l.getWidth()) {
				throw new IllegalArgumentException("Emitter x coordinate is bigger then layer width.");
			}

			if (emitter.getY() > l.getHeight()) {
				throw new IllegalArgumentException("Emitter y coordinate is bigger then layer height.");
			}

			final EmitterKey key = new EmitterKey(layerName, emitter.getX(), emitter.getY());
			emitters.put(key, emitter);

		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * Starts the simulation of the environment.
	 */
	public void start() {
		if (!hasSimulationStarted.getAndSet(true)) {
			throw new IllegalStateException("Simulation can only be started once.");
		}

		thread = new Thread(simulatorThread);
		thread.start();
	}

	/**
	 * Stops the simulation.
	 */
	public void stop() {
		hasSimulationStarted.set(false);
		try {
			thread.join();
		} catch (InterruptedException e) {
			// no op.
		}
		thread = null;
	}

	public void addExchanger(Exchanger exchanger) {
		if (exchangers.contains(exchanger)) {
			throw new IllegalArgumentException("Exchanger was already included in the manager.");
		}

		exchangers.add(exchanger);
	}
}

package net.bestia.zoneserver.zone.wecs;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The manager is responsible for controlling the added environment layer. The
 * layer itself are responsible for maintaining are correct state. Emitter are
 * placed inside the manager and they will set direct values of one ore more
 * layer as long as they are active. There is also a system of layer
 * interactions. After each tick of each layer is done and they are in an
 * equilibrium the layer interaction will and can change layer according to
 * their values.
 * <p>These layers are currently in use:</p>
 * <ul>
 * <li>humidity_sky</li>
 * <li>humidity_ground</li>
 * <li>temperature</li>
 * <li>rain</li>
 * <li>waterlevel</li>
 * <li>noise</li>
 * </ul>
 * @formatter:off
 * 
 * @formatter:on
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EnvironmentManager {

	private Map<String, Layer> layers = new HashMap<>();

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
	 * Starts the simulation of the environment.
	 */
	public void start() {
		if (!hasSimulationStarted.getAndSet(true)) {
			throw new IllegalStateException("Simulation can only be started once.");
		}

	}
}

package net.bestia.zoneserver.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

import net.bestia.model.server.MaintenanceLevel;

/**
 * This configuration service holds information about the current state of the
 * server while they are running. These information might get changed during
 * runtime. It is saved via the in memory database.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class RuntimeConfigService {

	private final IMap<String, Object> config;

	@Autowired
	public RuntimeConfigService(HazelcastInstance hz) {

		this.config = hz.getMap("server.config");
	}

	/**
	 * Returns the flag if the server is in maintenance mode.
	 * 
	 * @return TRUE if the server is in maintenance mode. FALSE otherwise.
	 */
	public MaintenanceLevel getMaintenanceMode() {
		return (MaintenanceLevel) config.getOrDefault("serverMaintenanceMode", MaintenanceLevel.NONE);
	}

	/**
	 * Sets the flag if the server is in maintenance mode.
	 * 
	 * @param flag
	 *            The flag to set the server into maintenance mode.
	 */
	public void setMaintenanceMode(MaintenanceLevel flag) {
		config.set("serverMaintenanceMode", flag);
	}

}

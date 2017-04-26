package net.bestia.zoneserver.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

/**
 * This configuration service holds information about the current state of the
 * server while they are running. These information might get changed during
 * runtime. It is saved via the in memory database.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
@Profile({ "production", "test" })
public class RuntimeConfigurationService {

	private final IMap<String, Object> config;

	@Autowired
	public RuntimeConfigurationService(HazelcastInstance hz) {

		this.config = hz.getMap("server.config");
	}

	/**
	 * Returns the flag if the server is in maintenance mode.
	 * 
	 * @return TRUE if the server is in maintenance mode. FALSE otherwise.
	 */
	public boolean isMaintenanceMode() {
		return (Boolean) config.getOrDefault("serverMaintenanceMode", false);
	}

	/**
	 * Sets the flag if the server is in maintenance mode.
	 * 
	 * @param flag
	 *            The flag to set the server into maintenance mode.
	 */
	public void setMaintenanceMode(boolean flag) {
		config.set("serverMaintenanceMode", flag);
	}

}

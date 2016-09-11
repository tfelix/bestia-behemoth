package net.bestia.zoneserver.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;

/**
 * This configuration service holds information about the current state of the
 * server while they are running. These information might get changed during
 * runtime.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class ServerRuntimeConfiguration extends CacheManager<String, Object> {

	@Autowired
	public ServerRuntimeConfiguration(HazelcastInstance cache) {
		super("server.config", cache);

	}

	/**
	 * Returns the flag if the server is in maintenance mode.
	 * 
	 * @return TRUE if the server is in maintenance mode. FALSE otherwise.
	 */
	public boolean isMaintenanceMode() {
		return (Boolean) get("serverMaintenanceMode", false);
	}

	/**
	 * Sets the flag if the server is in maintenance mode.
	 * 
	 * @param flag
	 *            The flag to set the server into maintenance mode.
	 */
	public void setMaintenanceMode(boolean flag) {
		set("serverMaintenanceMode", flag);
	}

}

package net.bestia.next.zoneserver.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Central configuration class for this server. Is backed up by the spring
 * property and value framework. Zentral configurations belonging to this server
 * are found here.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class ConfigurationService {

	@Value("${server.name}")
	private String serverName;

	@Value("${server.port}")
	private int serverPort;

	/**
	 * Returns the name of this server. By default this is an auto generated
	 * value.
	 * 
	 * @return The server name.
	 */
	public String getServerName() {
		return serverName;
	}

	/**
	 * Returns the port on which the cluster should listen for incoming message.
	 * 
	 * @return Server port.
	 */
	public int getServerPort() {
		return serverPort;
	}

}

package net.bestia.zoneserver.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Central configuration class for this server. Is backed up by the spring
 * property and value framework. This configuration values are set during
 * startup of the server via spring configuration files or command line
 * arguments.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class ServerStartupConfiguration {

	@Value("${server.name}")
	private String serverName;

	@Value("${server.port}")
	private int serverPort;

	@Value("${map.file}")
	private String mapfile;
	
	public ServerStartupConfiguration() {
		// no op.
	}

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

	/**
	 * Returns the development mapfile which gets loaded by the system at
	 * startup.
	 * 
	 * @return
	 */
	public String getMapfile() {
		return mapfile;
	}
}

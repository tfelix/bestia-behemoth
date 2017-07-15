package net.bestia.zoneserver.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Holds configuration variables for the server. These config variables are
 * either obtained by application.properties or via commandline attributes. They
 * are not meant to be changed during runtime.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class StaticConfigService {

	@Value("${server.name}")
	private String serverName;

	@Value("${server.entityBuffer}")
	private int entityBufferSize = 10;

	/**
	 * Directory of the script files.
	 */
	@Value("${server.scriptDir}")
	private String scriptDir;

	@Value("${server.version}")
	private String serverVersion;

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
	 * Returns the script directories for the custom item, mob, entity scripts
	 * etc.
	 * 
	 * @return The path to the scripts.
	 */
	public String getScriptDir() {
		return scriptDir;
	}

	/**
	 * Returns the server string of its version.
	 * 
	 * @return The server version.
	 */
	public String getServerVersion() {
		return serverVersion;
	}

	/**
	 * Returns the size of the entity buffer inside the recycler.
	 * 
	 * @return Size of the entity buffer.
	 */
	public int getEntityBufferSize() {
		return entityBufferSize;
	}
}
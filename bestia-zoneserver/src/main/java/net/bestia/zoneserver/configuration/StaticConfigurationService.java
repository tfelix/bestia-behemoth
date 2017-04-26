package net.bestia.zoneserver.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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
@Profile("production")
public class StaticConfigurationService {

	@Value("${server.name}")
	private String serverName;

	/**
	 * Directory of the script files.
	 */
	@Value("${server.scriptDir}")
	private String scriptDir;

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
}

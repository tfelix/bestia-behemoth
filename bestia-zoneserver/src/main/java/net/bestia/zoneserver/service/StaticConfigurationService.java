package net.bestia.zoneserver.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Holds configuration variables for the server. These config variables are
 * either obtained by application.properties or via commandline attributes.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class StaticConfigurationService {

	@Value("${server.name}")
	private String serverName;

	/**
	 * Directory of the script files.
	 */
	@Value("${scriptDir}")
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
}

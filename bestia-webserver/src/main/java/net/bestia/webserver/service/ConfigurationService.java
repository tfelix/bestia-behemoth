package net.bestia.webserver.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds configuration variables for the server.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class ConfigurationService {

	@Value("${server.name}")
	private String serverName;

	@Value("${server.maxDeadLetters}")
	private int maxDeadLetters;

	private AtomicBoolean isConnectedToCluster = new AtomicBoolean(false);

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
	 * The maximum number of dead letters until the connection to the cluster is
	 * cut and the webserver terminates itself.
	 * 
	 * @return The max number of dead letters.
	 */
	public int getMaxDeadLetters() {
		return maxDeadLetters;
	}
}

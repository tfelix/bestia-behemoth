package bestia.webserver.exceptions;

import org.springframework.web.bind.annotation.ResponseStatus;

import bestia.webserver.service.ConfigurationService;

import org.springframework.http.HttpStatus;

/**
 * This exception is thrown upon requests if the server is currently not
 * connected to the bestia cluster and thus can not react upon requests.
 * 
 * @author Thomas Felix
 *
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class NoConnectedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * Checks if the server is currently connected to the bestia cluster. If not
	 * this exception is thrown to signal the requesting client the request can
	 * not be digested.
	 * 
	 * @param config
	 *            The configuration service.
	 */
	public static void isConnectedOrThrow(ConfigurationService config) {
		if (!config.isConnectedToCluster()) {
			throw new NoConnectedException();
		}
	}
}

package net.bestia.zoneserver.script;

/**
 * Global script API used by all scripts in the bestia system to interact with
 * the behemoth server.
 * 
 * @author Thomas Felix
 *
 */
public interface ScriptApi {

	/**
	 * Logs the info text into the script logger.
	 * 
	 * @param text
	 *            The text to log.
	 */
	void info(String text);

	/**
	 * Logs the debug text into the script logger.
	 * 
	 * @param text
	 *            The text to log.
	 */
	void debug(String text);

}

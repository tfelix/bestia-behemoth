package net.bestia.zoneserver.ecs;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Keeps track of the currently active bestias of a certain account. Can be used
 * to get the latest active bestia ID. This class is threadsafe.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ActiveBestiaRegistry {

	private static final Logger LOG = LogManager.getLogger(ActiveBestiaRegistry.class);

	private Map<Long, Integer> activeBestias = new HashMap<>();

	/**
	 * Returns the id of the active bestia for this account id. If no active
	 * bestia was found 0 is returned (which is no valid bestia id).
	 * 
	 * @param accId
	 *            Account id too look up the active bestia.
	 * @return The bestia id or 0 if no bestia was marked as active.
	 */

	public synchronized int getActiveBestia(long accId) {
		final Integer id = activeBestias.get(accId);

		if (id == null) {
			return 0;
		} else {
			return id;
		}
	}

	/**
	 * Sets a active bestia for this account.
	 * 
	 * @param accountId
	 *            Account ID to set the active bestia.
	 * @param playerBestiaId
	 *            The player bestia ID to set active for this certain account.
	 */
	public synchronized void setActiveBestia(long accountId, int playerBestiaId) {
		LOG.trace("Account {} has active bestia {}", accountId, playerBestiaId);
		activeBestias.put(accountId, playerBestiaId);
	}

	/**
	 * The two parameter are asked in order to deal with problems with
	 * multithreading. If another thread was faster setting the new active
	 * bestia, this will avoid problems.
	 * 
	 * @param accountId
	 *            The account ID to set the new active bestia.
	 * @param playerBestiaId
	 *            The player bestia ID to set inactive for this account.
	 */
	public synchronized void unsetActiveBestia(long accountId, int playerBestiaId) {
		final Integer id = activeBestias.get(accountId);

		if (id == null || id != playerBestiaId) {
			return;
		}
		
		LOG.trace("Account {} bestia {} is set inactive.", accountId, playerBestiaId);

		activeBestias.remove(accountId);
	}
}

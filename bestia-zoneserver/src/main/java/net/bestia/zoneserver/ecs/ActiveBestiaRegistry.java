package net.bestia.zoneserver.ecs;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the currently active bestias.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ActiveBestiaRegistry {

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
	 * @param accId
	 * @param bestiaId
	 */
	public synchronized void setActiveBestia(long accId, int bestiaId) {
		activeBestias.put(accId, bestiaId);
	}

	/**
	 * The two parameter are asked in order to deal with problems with
	 * multithreading. If another thread was faster setting the new active
	 * bestia, this will avoid problems.
	 * 
	 * @param accountId
	 * @param i
	 */
	public synchronized void unsetActiveBestia(long accountId, int playerBestiaId) {
		
		final Integer id = activeBestias.get(accountId);
		
		if(id == null || id != playerBestiaId) {
			return;
		}
		
		activeBestias.remove(accountId);
	}
}

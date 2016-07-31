package net.bestia.zoneserver.service;

import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;

/**
 * This class will manage and save the active bestia inside the caching
 * mechanism.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class ActiveBestiaManager {

	private final static String ACTIVE_BESTIA_KEY = "bestia.active";

	private final HazelcastInstance cache;

	@Autowired
	public ActiveBestiaManager(HazelcastInstance cache) {

		this.cache = Objects.requireNonNull(cache);
	}

	/**
	 * Sets the currently active bestia for this account.
	 * 
	 * @param accountId
	 * @param bestiaId
	 */
	public void setActive(long accountId, int bestiaId) {
		final Map<Long, Integer> actives = cache.getMap(ACTIVE_BESTIA_KEY);
		actives.put(accountId, bestiaId);
	}

	/**
	 * Returns the currently active bestia id for this account. 0 if there is no
	 * active bestia set.
	 * 
	 * @param accountId
	 *            The account id.
	 * @return The active bestia id for this account or 0 if there was no active
	 *         bestia.
	 */
	public int getActive(long accountId) {
		final Map<Long, Integer> actives = cache.getMap(ACTIVE_BESTIA_KEY);
		final Integer active = actives.get(accountId);

		if (active == null) {
			return 0;
		} else {
			return active;
		}
	}
}

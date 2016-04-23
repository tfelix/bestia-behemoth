package net.bestia.zoneserver.messaging;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.interserver.InterserverSubscriber;
import net.bestia.messages.AccountMessage;

/**
 * Keeps track of the accounts and their online bestias. If there is an account
 * online with at least one bestia the {@link AccountRegistry} will register the
 * server to the intersever for a topic regarding this account. If the last
 * bestia went offline it will unsubscribe it and take the account offline. The
 * class is threadsafe.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AccountRegistry {

	private static final Logger LOG = LogManager.getLogger(AccountRegistry.class);

	private class Counter {
		private int count = 0;

		public int get() {
			return count;
		}

		public void inc() {
			count++;
		}

		public void dec() {
			count--;
		}
	}

	private final Map<Long, String> tokenRegistry = new HashMap<>();
	private final Map<Long, Integer> activeBestiaRegistry = new HashMap<>();
	private final Map<Long, Counter> onlineEntityCountRegistry = new HashMap<>();

	private final InterserverSubscriber server;

	public AccountRegistry(InterserverSubscriber server) {
		if (server == null) {
			throw new IllegalArgumentException("Server can not be null.");
		}

		this.server = server;
	}

	public synchronized void registerLogin(long accId, String token) {
		if (token == null || token.isEmpty()) {
			throw new IllegalArgumentException("Token can not be null or empty.");
		}

		tokenRegistry.put(accId, token);
		onlineEntityCountRegistry.put(accId, new Counter());
		subscribe(accId);
	}

	public synchronized void unregisterLogin(long accId) {
		tokenRegistry.remove(accId);
		onlineEntityCountRegistry.remove(accId);
		activeBestiaRegistry.remove(accId);
		unsubscribe(accId);
	}

	public synchronized boolean hasLogin(long accId) {
		return tokenRegistry.containsKey(accId);
	}

	/**
	 * Sees if there is an account online with this ID and with the given login
	 * token. Only if both matches true is returned, else false.
	 * 
	 * @param accid
	 * @param token
	 * @return
	 */
	public synchronized boolean hasLogin(long accId, String token) {
		final String savedToken = tokenRegistry.get(accId);
		return (savedToken == null) ? false : savedToken.equals(token);
	}

	/**
	 * Sets the active bestia for the given account. If the account does not
	 * exist prior before calling this method and exception is thrown.
	 * 
	 * @param accId
	 *            Account ID for which the active bestia should be set.
	 * @param bestiaId
	 *            The bestia ID to set as active bestia.
	 */
	public synchronized void setActiveBestia(long accId, int bestiaId) {
		// Is account existing?
		if (!tokenRegistry.containsKey(accId)) {
			throw new IllegalArgumentException(
					String.format("Account with ID %d was not registered. Call registerLogin() first.", accId));
		}

		LOG.trace("Account {} has active bestia {}", accId, bestiaId);
		activeBestiaRegistry.put(accId, bestiaId);
		incrementBestiaOnline(accId);
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
		final Integer id = activeBestiaRegistry.get(accountId);

		if (id == null || id != playerBestiaId) {
			return;
		}

		LOG.trace("Account {} bestia {} is set inactive.", accountId, playerBestiaId);

		activeBestiaRegistry.remove(accountId);
		decrementBestiaOnline(accountId);
	}

	/**
	 * The concurrent online number of users.
	 * 
	 * @return The currently online users.
	 */
	public synchronized int countOnlineUsers() {
		return onlineEntityCountRegistry.size();
	}

	/**
	 * The concurrent online number of bestias.
	 * 
	 * @return
	 */
	public synchronized int countOnlineBestias() {
		int number = 0;
		for (Counter c : onlineEntityCountRegistry.values()) {
			number += c.get();
		}
		return number;
	}

	public synchronized void incrementBestiaOnline(Long accId) {

		if (!tokenRegistry.containsKey(accId)) {
			return;
		}

		onlineEntityCountRegistry.get(accId).inc();
	}

	public synchronized void decrementBestiaOnline(Long accId) {

		final Counter counter = onlineEntityCountRegistry.get(accId);

		if (counter == null) {
			LOG.warn("decrementBestiaOnline was called even though there was no bestia anymore for the account {}!",
					accId);
			return;

		}

		if (counter.count == 0) {
			LOG.warn("decrementBestiaOnline was called and account {} had already 0 actives.",
					accId);
			return;
		}
		
		// If active bestia still set, can not decrement under 1.
		if(getActiveBestia(accId) != 0 && counter.count == 1) {
			return;
		}
		
		counter.dec();
	}

	/**
	 * Returns the ID of the active player bestia, or 0 if there is no active
	 * player bestia for this account.
	 * 
	 * @param accId
	 *            The account id to return the active bestia.
	 * @return The active player bestia or 0 if there is no such bestia.
	 */
	public synchronized int getActiveBestia(long accId) {
		final Integer id = activeBestiaRegistry.get(accId);
		return (id == null) ? 0 : id.intValue();
	}

	private void subscribe(long accId) {
		// Subscribe the server to message for this account.
		server.subscribe(AccountMessage.getZoneMessagePath(accId) + ".*");
		LOG.trace("Subscribed for account {}.", accId);
	}

	private void unsubscribe(long accId) {
		server.subscribe(AccountMessage.getZoneMessagePath(accId) + ".*");
		LOG.trace("Unsubscribed for account {}.", accId);
	}
}

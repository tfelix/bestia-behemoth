package net.bestia.zoneserver.routing;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.zoneserver.Zoneserver;

/**
 * This class keeps track on how many subscriptions are currenty online for a
 * single account. If more then one bestia is there it will subscribe the server
 * for messages from this very account. If all bestias go offline it will
 * unsubscribe the server again. This router is therefore crucial for receiving
 * data from the clients. Can also be used to count the concurrent online user.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ServerSubscriptionManager {

	private static final Logger LOG = LogManager.getLogger(ServerSubscriptionManager.class);

	private class Counter {
		private int count = 1;

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

	private final Map<Long, Counter> onlineUser = new HashMap<>();
	private final Zoneserver server;

	public ServerSubscriptionManager(Zoneserver server) {
		if (server == null) {
			throw new IllegalArgumentException("Server can not be null.");
		}

		this.server = server;
	}

	/**
	 * The concurrent online number of users.
	 * 
	 * @return The currently online users.
	 */
	public synchronized int countUser() {
		return onlineUser.size();
	}

	/**
	 * The concurrent online number of bestias.
	 * 
	 * @return
	 */
	public synchronized int countBestias() {
		int number = 0;
		for(Counter c : onlineUser.values()) {
			number += c.get();
		}
		return number;
	}

	public synchronized void setOnline(Long accId) {

		if (onlineUser.containsKey(accId)) {
			onlineUser.get(accId).inc();
		} else {
			onlineUser.put(accId, new Counter());
			subscribe(accId);
		}
	}

	public synchronized void setOffline(Long accId) {

		final Counter counter = onlineUser.get(accId);

		if (counter != null) {
			counter.dec();
			if (counter.get() <= 0) {
				unsubscribe(accId);
				onlineUser.remove(accId);
			}
		} else {
			LOG.warn("SetOffline was called even though there was no bestia anymore for the account {}!", accId);
		}
	}

	private void subscribe(long accId) {
		// Subscribe the server to message for this account.
		final String subStr = String.format("zone/account/%d", accId);
		server.subscribe(subStr);
		LOG.trace("Registered account {} on zone {}.", accId);
	}

	private void unsubscribe(long accId) {
		final String subStr = String.format("zone/account/%d", accId, server.getName());
		server.subscribe(subStr);
		LOG.trace("Unregistered account {} from zone {}.", accId, server.getName());
	}
}

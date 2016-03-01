package net.bestia.zoneserver.messaging;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.interserver.InterserverSubscriber;

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
	private static final String ACCOUNT_TOPIC = "zone/account/%d";
	private static final Logger LOG = LogManager.getLogger(UserRegistry.class);
	
	private class LoginData {
		public final String token;
		public int onlineEntities = 0;
		
		public LoginData(String token) {
			this.token = token;
		}
		
		public LoginData() {
			this.token = UUID.randomUUID().toString();
		}
	}

	private final Map<Long, Integer> activeBestias = new HashMap<>();	
	private final Map<Long, LoginData> onlineEntities = new HashMap<>();
	private final InterserverSubscriber server;

	public  AccountRegistry(InterserverSubscriber server) {
		if (server == null) {
			throw new IllegalArgumentException("Server can not be null.");
		}

		this.server = server;
	}

	private void subscribe(long accId) {
		// Subscribe the server to message for this account.
		final String subStr = String.format(ACCOUNT_TOPIC, accId);
		server.subscribe(subStr);
		LOG.trace("Subscribed for account {}.", accId);
	}

	private void unsubscribe(long accId) {
		final String subStr = String.format(ACCOUNT_TOPIC, accId);
		server.unsubscribe(subStr);
		LOG.trace("Unsubscribed for account {}.", accId);
	}
}

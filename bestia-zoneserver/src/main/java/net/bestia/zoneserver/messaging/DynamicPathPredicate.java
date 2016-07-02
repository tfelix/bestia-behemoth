package net.bestia.zoneserver.messaging;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.Message;

public class DynamicPathPredicate implements Predicate<Message> {
	
	private final static Logger LOG = LogManager.getLogger(DynamicPathPredicate.class);

	private final Set<String> subscribedPaths = ConcurrentHashMap.newKeySet();

	public DynamicPathPredicate() {
		// no op.
	}

	/**
	 * Subscribes to messages directed to this account.
	 * 
	 * @param accId
	 */
	public void subscribe(String subString) {
		LOG.trace("Subscribed to {}", subString);
		subscribedPaths.add(subString);
	}
	
	public void unsubscribe(String subString) {
		LOG.trace("Unsubscribed to {}", subString);
		subscribedPaths.remove(subString);
	}

	@Override
	public boolean test(Message msg) {
		return subscribedPaths.contains(msg.getMessagePath());
	}

}

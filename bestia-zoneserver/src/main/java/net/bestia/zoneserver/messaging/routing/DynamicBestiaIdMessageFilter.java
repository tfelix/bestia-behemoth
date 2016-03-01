package net.bestia.zoneserver.messaging.routing;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.bestia.messages.InputMessage;
import net.bestia.messages.Message;

/**
 * This filter allows to dynamically subscribe to bestia ids and messages (most
 * likly {@link InputMessage}s) for this bestias.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class DynamicBestiaIdMessageFilter implements MessageFilter {

	// There is no concurrent set, we must build it from map.
	private final Set<Integer> interestedBestiaIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

	public void addPlayerBestiaId(Integer id) {
		interestedBestiaIds.add(id);
	}

	public void removePlayerBestiaId(Integer id) {
		interestedBestiaIds.remove(id);
	}

	@Override
	public boolean handlesMessage(Message msg) {
		if (!(msg instanceof InputMessage)) {
			return false;
		}

		final InputMessage inputMsg = (InputMessage) msg;
		return interestedBestiaIds.contains(inputMsg.getPlayerBestiaId());
	}

	@Override
	public String toString() {
		return String.format("DynamicBestiaIdMessageFilter[ids: %s]", interestedBestiaIds.toString());
	}

}

package net.bestia.zoneserver.routing;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.bestia.messages.InputMessage;
import net.bestia.messages.Message;

public class DynamicMessageFilter implements MessageFilter {

	// There is nos concurrent set, we must build it from map.
	private final Set<Integer> interestedBestiaIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
	
	public void subscribeId(Integer id) {
		interestedBestiaIds.add(id);
	}
	
	public void removeId(Integer id) {
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

}

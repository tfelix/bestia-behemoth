package net.bestia.zoneserver.messaging.routing;

import net.bestia.messages.Message;
import net.bestia.messages.ZoneWrapperMessage;

public class ZoneWrapperFilter implements MessageFilter {

	private final String zonename;

	public ZoneWrapperFilter(String zonename) {
		if (zonename == null || zonename.isEmpty()) {
			throw new IllegalArgumentException("Zonename can not be empty or null.");
		}
		this.zonename = zonename;
	}

	@Override
	public boolean handlesMessage(Message msg) {
		if (!(msg instanceof ZoneWrapperMessage<?>)) {
			return false;
		}

		final ZoneWrapperMessage<?> wrapperMsg = (ZoneWrapperMessage<?>) msg;
		return wrapperMsg.getReceiverZones().contains(zonename);
	}

}

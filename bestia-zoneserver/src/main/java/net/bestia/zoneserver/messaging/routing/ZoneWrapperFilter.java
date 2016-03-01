package net.bestia.zoneserver.messaging.routing;

import net.bestia.messages.Message;
import net.bestia.messages.ZoneMessageDecorator;

/**
 * Messages covered with a {@link ZoneMessageDecorator} will be redirected to
 * the contained zone(s) where they are hopefully processed. Can be used to
 * direct messages directly to zones. Zones should register this filter.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ZoneWrapperFilter implements MessageFilter {

	private final String zonename;

	/**
	 * 
	 * @param zonename
	 *            The name of this zone.
	 */
	public ZoneWrapperFilter(String zonename) {
		if (zonename == null || zonename.isEmpty()) {
			throw new IllegalArgumentException("Zonename can not be empty or null.");
		}
		this.zonename = zonename;
	}

	@Override
	public boolean handlesMessage(Message msg) {
		if (!(msg instanceof ZoneMessageDecorator<?>)) {
			return false;
		}

		final ZoneMessageDecorator<?> wrapperMsg = (ZoneMessageDecorator<?>) msg;
		return wrapperMsg.getReceiverZones().contains(zonename);
	}

	@Override
	public String toString() {
		return String.format("ZoneWrapperFilter[zoneName: %s]", zonename);
	}

}

package net.bestia.zoneserver.messaging.routing;

import net.bestia.messages.Message;

/**
 * This filter evaluates to true if the message is a direct descendant of a
 * class specified in the constructor of the filter. This is useful for example
 * when the server wants to execute messages directly derived from the message
 * class only.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MessageDirectDescandantFilter implements MessageFilter {
	
	private final Class<?> clazz;
	
	public MessageDirectDescandantFilter(Class<?> clazz) {
		this.clazz = clazz;
	}

	@Override
	public boolean handlesMessage(Message msg) {
		return msg.getClass().getSuperclass().equals(clazz);
	}

}

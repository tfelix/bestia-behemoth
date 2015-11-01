package net.bestia.zoneserver.routing;

import java.util.HashSet;
import java.util.Set;

import net.bestia.messages.Message;

/**
 * Processes a certain message if the message ID matches at least one of the
 * added IDs.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MessageIdFilter implements MessageFilter {

	private Set<String> messageIDs = new HashSet<>();
	
	public MessageIdFilter() {
		// no op.
	}

	public MessageIdFilter(String msgID) {
		addMessageId(msgID);
	}
	
	public void addMessageId(String id) {
		messageIDs.add(id);
	}

	@Override
	public boolean handlesMessage(Message msg) {
		return messageIDs.contains(msg.getMessageId());
	}

}

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
public class MessageIDFilter implements MessageFilter {

	private Set<String> messageIDs = new HashSet<>();
	
	public MessageIDFilter() {
		// no op.
	}

	public MessageIDFilter(String msgID) {
		addMessageID(msgID);
	}
	
	public void addMessageID(String id) {
		messageIDs.add(id);
	}

	@Override
	public boolean handlesMessage(Message msg) {
		return messageIDs.contains(msg.getMessageId());
	}

}

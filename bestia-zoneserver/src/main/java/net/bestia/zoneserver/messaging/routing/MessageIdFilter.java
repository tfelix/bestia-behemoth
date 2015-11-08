package net.bestia.zoneserver.messaging.routing;

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

	private final Set<String> messageIDs = new HashSet<>();

	public MessageIdFilter() {
		// no op.
	}

	/**
	 * Initialize the filter with all the message IDs given in the set.
	 * 
	 * @param messageIDs
	 *            All the message IDs this filter should be allow.
	 */
	public MessageIdFilter(Set<String> messageIDs) {
		messageIDs.addAll(messageIDs);
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

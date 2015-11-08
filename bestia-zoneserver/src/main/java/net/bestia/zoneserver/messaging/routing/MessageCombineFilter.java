package net.bestia.zoneserver.messaging.routing;

import java.util.ArrayList;

import net.bestia.messages.Message;

/**
 * Multiple filder can be combined in a AND fashion. Incoming messages must
 * match all filters listed here to be accepted for the designated consumer.
 * 
 * @author Thomas
 *
 */
public class MessageCombineFilter implements MessageFilter {

	private ArrayList<MessageFilter> filters = new ArrayList<>();

	public void addFilter(MessageFilter filter) {
		filters.add(filter);
	}

	@Override
	public boolean handlesMessage(Message msg) {
		for(MessageFilter filter : filters) {
			if(!filter.handlesMessage(msg)) {
				return false;
			}
		}
		return true;
	}

}

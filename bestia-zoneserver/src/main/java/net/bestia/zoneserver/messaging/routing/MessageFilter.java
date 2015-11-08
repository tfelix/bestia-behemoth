package net.bestia.zoneserver.messaging.routing;

import net.bestia.messages.Message;

public interface MessageFilter {
	
	boolean handlesMessage(Message msg);

}

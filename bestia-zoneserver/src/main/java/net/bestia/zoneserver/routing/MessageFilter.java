package net.bestia.zoneserver.routing;

import net.bestia.messages.Message;

public interface MessageFilter {
	
	boolean handlesMessage(Message msg);

}

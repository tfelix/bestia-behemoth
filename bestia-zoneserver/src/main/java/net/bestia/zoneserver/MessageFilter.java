package net.bestia.zoneserver;

import net.bestia.messages.Message;

public interface MessageFilter {
	
	boolean handlesMessage(Message msg);

}

package net.bestia.messages.system;

import net.bestia.messages.Message;

public class ShutdownMessage extends Message {
	
	public static final String MESSAGE_ID = "system.shutdown";

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		// TODO Auto-generated method stub
		return null;
	}

}

package net.bestia.zoneserver.ecs.message;

import net.bestia.messages.Message;

public class DespawnPlayerBestiaMessage extends Message {

	private static final long serialVersionUID = 1L;
	
	public static final String MESSAGE_ID = "playerbestia.despawn";

	public DespawnPlayerBestiaMessage(long accId) {
		super(accId);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getNullMessagePath();
	}

}

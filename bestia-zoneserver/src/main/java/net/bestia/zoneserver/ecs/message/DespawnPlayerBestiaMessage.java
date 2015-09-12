package net.bestia.zoneserver.ecs.message;

import net.bestia.messages.InputMessage;

public class DespawnPlayerBestiaMessage extends InputMessage {

	private static final long serialVersionUID = 1L;
	
	public static final String MESSAGE_ID = "playerbestia.despawn";

	public DespawnPlayerBestiaMessage(long accId, int bestiaId) {
		super(accId, bestiaId);
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

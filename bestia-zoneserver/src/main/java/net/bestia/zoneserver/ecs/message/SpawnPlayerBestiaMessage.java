package net.bestia.zoneserver.ecs.message;

import net.bestia.messages.InputMessage;

public class SpawnPlayerBestiaMessage extends InputMessage {
	
	private static final long serialVersionUID = 1L;
	public final static String MESSAGE_ID = "playerbestia.spawn";
	
	public SpawnPlayerBestiaMessage(long accId, int playerBestiaId) {
		setAccountId(accId);
		setPlayerBestiaId(playerBestiaId);
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

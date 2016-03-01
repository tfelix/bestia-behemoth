package net.bestia.zoneserver.command.ecs;

import net.bestia.messages.LogoutBroadcastMessage;
import net.bestia.messages.Message;
import net.bestia.messages.ZoneMessageDecorator;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;

public class LogoutBroadcastCommand extends ECSCommand {

	private PlayerBestiaSpawnManager spawnManager;

	/**
	 * Make sure we are receiving only wrapped messages.
	 */
	@Override
	public String handlesMessageId() {
		return ZoneMessageDecorator.getWrappedMessageId(LogoutBroadcastMessage.MESSAGE_ID);
	}

	@Override
	protected void initialize() {
		super.initialize();

		spawnManager = world.getSystem(PlayerBestiaSpawnManager.class);
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		
		spawnManager.despawnAllBestias(message.getAccountId());
		
	}
	
	@Override
	public String toString() {
		return "SpawnPlayerBestiaCommand[]";
	}
}

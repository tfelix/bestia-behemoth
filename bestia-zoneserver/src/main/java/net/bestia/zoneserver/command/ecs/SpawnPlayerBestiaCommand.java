package net.bestia.zoneserver.command.ecs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.Message;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.message.SpawnPlayerBestiaMessage;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

public class SpawnPlayerBestiaCommand extends ECSCommand {
	
	private final static Logger log = LogManager.getLogger(SpawnPlayerBestiaMessage.class);

	@Override
	public String handlesMessageId() {
		return SpawnPlayerBestiaMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		// TODO Wird die bestia nun noch richtig gespawned?
		final SpawnPlayerBestiaMessage spawnMsg = (SpawnPlayerBestiaMessage) message;
		
		final long accId = spawnMsg.getAccountId();
		final int bestiaId = spawnMsg.getPlayerBestiaId();

		final PlayerBestiaManager pbm = ctx.getServer().getBestiaRegister().getSpawnedBestia(accId, bestiaId);
		log.debug("Adding {} to ecs.", pbm.toString());
	}
	
	@Override
	public String toString() {
		return "SpawnPlayerBestiaCommand[]";
	}

}

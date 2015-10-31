package net.bestia.zoneserver.ecs.command;

import net.bestia.messages.Message;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.ecs.message.SpawnPlayerBestiaMessage;
import net.bestia.zoneserver.manager.BestiaManager;
import net.bestia.zoneserver.manager.PlayerBestiaManager;
import net.bestia.zoneserver.zone.shape.Vector2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Entity;
import com.artemis.EntityEdit;
import com.artemis.utils.EntityBuilder;

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

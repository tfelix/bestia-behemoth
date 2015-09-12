package net.bestia.zoneserver.ecs.command;

import net.bestia.messages.Message;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.message.DespawnPlayerBestiaMessage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Entity;
import com.artemis.managers.TagManager;

public class DespawnPlayerBestiaCommand extends ECSCommand {
	
	private final static Logger log = LogManager.getLogger(DespawnPlayerBestiaMessage.class);
	
	private TagManager tagManager;

	@Override
	public String handlesMessageId() {
		return DespawnPlayerBestiaMessage.MESSAGE_ID;
	}
	
	@Override
	protected void initialize() {
		tagManager = world.getManager(TagManager.class);
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		final DespawnPlayerBestiaMessage spawnMsg = (DespawnPlayerBestiaMessage) message;
		
		final int bestiaId = spawnMsg.getPlayerBestiaId();
		
		final Entity playerBestiaEntity = tagManager.getEntity(Integer.toString(bestiaId));
		
		if(playerBestiaEntity == null) {
			// Strange should normally not happen.
			return;
		}
		
		log.debug("Removing playerBestiaId: {} from ecs.", bestiaId);
		playerBestiaEntity.edit().deleteEntity();
	}
	
	@Override
	public String toString() {
		return "DespawnPlayerBestiaCommand[]";
	}

}

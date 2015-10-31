package net.bestia.zoneserver.ecs.command;

import java.util.ArrayList;
import java.util.List;

import net.bestia.messages.AttackSetMessage;
import net.bestia.messages.Message;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.BestiaRegister;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

/**
 * Lists the attacks of the currently active bestia and returns it to the
 * client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AttackSetCommand extends ECSCommand {

	@Override
	public String handlesMessageId() {
		return AttackSetMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {

		final AttackSetMessage attackSetMsg = (AttackSetMessage) message;

		// Get the bestia id of the currently selected bestia.
		final BestiaRegister register = ctx.getServer().getBestiaRegister();
		final long accId = message.getAccountId();
		final int playerBestiaId = attackSetMsg.getPlayerBestiaId();
		final PlayerBestiaManager pbManager = register.getSpawnedBestia(accId, playerBestiaId);
		
		// Create a list with attack IDs.
		final List<Integer> atkIds = new ArrayList<>();
		atkIds.add(attackSetMsg.getAtkSlotId1());
		atkIds.add(attackSetMsg.getAtkSlotId2());
		atkIds.add(attackSetMsg.getAtkSlotId3());
		atkIds.add(attackSetMsg.getAtkSlotId4());
		atkIds.add(attackSetMsg.getAtkSlotId5());
		
		// If this worked assign the bestia attacks to the ecs.
		pbManager.setAttacks(atkIds);
	}
}

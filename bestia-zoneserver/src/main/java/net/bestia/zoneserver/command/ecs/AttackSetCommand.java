package net.bestia.zoneserver.command.ecs;

import java.util.ArrayList;
import java.util.List;

import net.bestia.messages.AttackSetMessage;
import net.bestia.messages.Message;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

public class AttackSetCommand extends ECSCommand {

	@Override
	public String handlesMessageId() {
		return AttackSetMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		final AttackSetMessage atkSetMsg = (AttackSetMessage) message;
		
		// Set the attacks.
		final PlayerBestiaManager pbm = getPlayerBestiaManager();
		final List<Integer> attackIds = new ArrayList<>();
		attackIds.add(atkSetMsg.getAtkSlotId1());
		attackIds.add(atkSetMsg.getAtkSlotId2());
		attackIds.add(atkSetMsg.getAtkSlotId3());
		attackIds.add(atkSetMsg.getAtkSlotId4());
		attackIds.add(atkSetMsg.getAtkSlotId5());
		pbm.setAttacks(attackIds);
	}

}

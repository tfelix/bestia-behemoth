package net.bestia.zoneserver.command.ecs;

import java.util.ArrayList;
import java.util.List;

import net.bestia.messages.Message;
import net.bestia.messages.attack.AttackSetMessage;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.proxy.PlayerBestiaEntityProxy;

/**
 * Sets the attacks of the currently active bestia.
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
		final AttackSetMessage atkSetMsg = (AttackSetMessage) message;
		
		// Set the attacks.
		final PlayerBestiaEntityProxy pbm = getPlayerBestiaProxy();
		final List<Integer> attackIds = new ArrayList<>();
		attackIds.add(atkSetMsg.getAtkSlotId1());
		attackIds.add(atkSetMsg.getAtkSlotId2());
		attackIds.add(atkSetMsg.getAtkSlotId3());
		attackIds.add(atkSetMsg.getAtkSlotId4());
		attackIds.add(atkSetMsg.getAtkSlotId5());
		pbm.setAttacks(attackIds);
	}

}

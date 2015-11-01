package net.bestia.zoneserver.ecs.manager;

import java.util.ArrayList;
import java.util.List;

import com.artemis.annotations.Wire;

import net.bestia.messages.AttackSetMessage;
import net.bestia.messages.Message;
import net.bestia.zoneserver.manager.PlayerBestiaManager;
import net.bestia.zoneserver.routing.MessageIdFilter;

/**
 * Manages attacks.
 * TODO Das wieder zu einem command machen.
 * @author Thomas
 *
 */
@Wire(injectInherited = true)
public class PlayerAttackSetManager extends MessageProcessSystem {
	
	private PlayerBestiaSpawnManager spawnManager;
	
	private final MessageIdFilter messageFilter = new MessageIdFilter(AttackSetMessage.MESSAGE_ID);
	
	@Override
	protected void initialize() {
		super.initialize();
		
		ctx.getServer().getMessageRouter().registerFilter(messageFilter, this);
	}


	@Override
	protected void handleMessage(Message msg) {
		final AttackSetMessage attackSetMsg = (AttackSetMessage) msg;

		// Get the bestia id of the currently selected bestia.
		final int playerBestiaId = attackSetMsg.getPlayerBestiaId();
		
		final PlayerBestiaManager pbManager = spawnManager.getPlayerBestiaManager(playerBestiaId);
		
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

package net.bestia.zoneserver.command;

import java.util.List;

import net.bestia.messages.AttackListRequestMessage;
import net.bestia.messages.AttackListResponseMessage;
import net.bestia.messages.Message;
import net.bestia.model.dao.AttackLevelDAO;
import net.bestia.model.domain.AttackLevel;
import net.bestia.model.domain.Bestia;
import net.bestia.zoneserver.ecs.BestiaRegister;

/**
 * Lists the attacks of the currently active bestia and returns it to the
 * client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AttackListRequestCommand extends Command {

	@Override
	public String handlesMessageId() {
		return AttackListRequestMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		
		final AttackLevelDAO attackLvDao = ctx.getServiceLocator().getBean(AttackLevelDAO.class);
		
		// Get the bestia id of the currently selected bestia.
		final BestiaRegister register = ctx.getServer().getBestiaRegister();
		final long accId = message.getAccountId();
		final int activePbId = register.getActiveBestia(accId);		
		
		// Might have no selected bestia.
		if(activePbId == 0) {
			return;
		}
		
		final Bestia bestia = register.getSpawnedBestia(accId, activePbId).getPlayerBestia().getOrigin();
		
		final List<AttackLevel> attacks = attackLvDao.getAllAttacksForBestia(bestia);
		
		final AttackListResponseMessage response = new AttackListResponseMessage(message);		
		response.setAttacks(attacks);
		
		ctx.getServer().sendMessage(response);
	}

}

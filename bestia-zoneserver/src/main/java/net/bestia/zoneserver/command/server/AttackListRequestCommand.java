package net.bestia.zoneserver.command.server;

import java.util.List;

import net.bestia.messages.AttackListRequestMessage;
import net.bestia.messages.AttackListResponseMessage;
import net.bestia.messages.Message;
import net.bestia.model.domain.BestiaAttack;
import net.bestia.model.service.PlayerBestiaService;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;

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
		
		final PlayerBestiaService pbService = ctx.getServiceLocator().getBean(PlayerBestiaService.class);
		
		// Get the bestia id of the currently selected bestia.
		final long accId = message.getAccountId();
		final int activePbId = ctx.getAccountRegistry().getActiveBestia(accId);		
		
		// Might have no selected bestia.
		if(activePbId == 0) {
			return;
		}
		
		final List<BestiaAttack> attacks = pbService.getAllAttacksForPlayerBestia(activePbId);
		
		final AttackListResponseMessage response = new AttackListResponseMessage(message);		
		response.setAttacks(attacks);
		
		ctx.getServer().sendMessage(response);
	}

	@Override
	public String toString() {
		return "AttackListRequestCommand[]";
	}
}

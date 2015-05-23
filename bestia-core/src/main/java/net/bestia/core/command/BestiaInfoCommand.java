package net.bestia.core.command;

import java.util.ArrayList;

import net.bestia.core.game.model.PlayerBestia;
import net.bestia.core.game.service.AccountService;
import net.bestia.core.game.service.AccountServiceFactory;
import net.bestia.messages.BestiaInfoMessage;
import net.bestia.messages.Message;

/**
 * Gathers information about all bestias which are currently under the control
 * of this account.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaInfoCommand extends Command {

	@Override
	public String handlesMessageId() {
		return BestiaInfoMessage.MESSAGE_ID;
	}

	@Override
	public void execute(Message message, CommandContext ctx) {

		BestiaInfoMessage reply = new BestiaInfoMessage(message);
		
		AccountService accService = ctx.getServiceFactory().getAccountServiceFactory().getAccount(message.getAccountId());
		
		// Setup the bestia information inside this message.
		reply.setMaster(accService.getAccount().getMaster());
		reply.setNumberOfSlots(accService.getBestiaSlotNumber());
		reply.setBestias(accService.getAccount().getBestias());
		
		ctx.getMessenger().sendMessage(reply);
		
	}

}

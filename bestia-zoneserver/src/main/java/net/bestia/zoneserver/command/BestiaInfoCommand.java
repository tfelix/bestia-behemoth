package net.bestia.zoneserver.command;

import java.util.ArrayList;

import net.bestia.core.game.service.AccountService;
import net.bestia.core.game.service.AccountServiceFactory;
import net.bestia.messages.BestiaInitMessage;
import net.bestia.messages.Message;
import net.bestia.model.PlayerBestia;

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
		return BestiaInitMessage.MESSAGE_ID;
	}

	@Override
	public void execute(Message message, CommandContext ctx) {

		BestiaInitMessage reply = new BestiaInitMessage(message);
		
		AccountService accService = ctx.getServiceFactory().getAccountServiceFactory().getAccount(message.getAccountId());
		
		// Setup the bestia information inside this message.
		reply.setMaster(accService.getAccount().getMaster());
		reply.setNumberOfSlots(accService.getBestiaSlotNumber());
		reply.setBestias(accService.getAccount().getBestias());
		
		//ctx.getMessenger().sendMessage(reply);
		
	}

}

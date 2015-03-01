package net.bestia.core.command;

import net.bestia.core.message.BestiaInfoMessage;
import net.bestia.core.message.Message;

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
		
		ctx.getMessenger().sendMessage(reply);
		
	}

}

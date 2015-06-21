package net.bestia.zoneserver.command;

import net.bestia.messages.LoginBroadcastMessage;
import net.bestia.messages.Message;

/**
 * This command will be executed if a new user wants to join. He needs a few information in order to boot the client
 * properly. We will gather the following: * Informations about all bestias connected to this account.
 * 
 * But we will also perform a few action: * Spawn the bestia master into the world.
 * 
 * As soon as the bestia master has become active. This will send all changes of entities inside his view to the client.
 * But we will have to send an initial sync message.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class RequestLoginCommand extends Command {

	@Override
	public String handlesMessageId() {
		return LoginBroadcastMessage.MESSAGE_ID;
	}

	@Override
	public void execute(Message message, CommandContext ctx) {

		ctx.getServer().registerAccount(message.getAccountId());

	}
	
	@Override
	public String toString() {
		return "RequestLoginCommand[]";
	}

}

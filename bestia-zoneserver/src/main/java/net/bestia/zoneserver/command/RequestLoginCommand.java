package net.bestia.zoneserver.command;

import java.util.Set;

import net.bestia.messages.BestiaInitMessage;
import net.bestia.messages.LoginBroadcastMessage;
import net.bestia.messages.Message;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.PlayerBestia;

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

		// Register the bestias to the server.
		ctx.getServer().registerAccount(message.getAccountId());
		
		// Gather all the needed data for the client to completely display everything...
		
		// gather bestias.
		final PlayerBestiaDAO bestiaDao = ctx.getServiceLocator().getBean(PlayerBestiaDAO.class);
		final AccountDAO accountDao = ctx.getServiceLocator().getBean(AccountDAO.class);
		
		final Account account = accountDao.find(message.getAccountId());
		final Set<PlayerBestia> bestias = bestiaDao.findPlayerBestiasForAccount(message.getAccountId());
		
		final BestiaInitMessage msg = new BestiaInitMessage(message, 1, account.getMaster(), bestias);
		ctx.getServer().sendMessage(msg);

	}
	
	@Override
	public String toString() {
		return "RequestLoginCommand[]";
	}

}

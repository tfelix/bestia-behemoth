package net.bestia.zoneserver.command;

import java.util.Set;

import net.bestia.messages.BestiaInfoMessage;
import net.bestia.messages.LoginBroadcastMessage;
import net.bestia.messages.Message;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.Zoneserver;
import net.bestia.zoneserver.ecs.InputController;
import net.bestia.zoneserver.game.manager.PlayerBestiaManager;

/*-
 * This command will be executed if a new user wants to join. He needs a few information in order to boot the client
 * properly. We will gather the following: 
 * - Informations about all bestias connected to this account.
 * 
 * But we will also perform a few action: 
 * - Spawn the bestia master into the world.
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

		// Gather all the needed data for the client to completely display everything...

		// gather bestias.
		final PlayerBestiaDAO bestiaDao = ctx.getServiceLocator().getBean(PlayerBestiaDAO.class);
		final AccountDAO accountDao = ctx.getServiceLocator().getBean(AccountDAO.class);

		final Account account = accountDao.find(message.getAccountId());
		final Set<PlayerBestia> bestias = bestiaDao.findPlayerBestiasForAccount(message.getAccountId());

		bestias.add(account.getMaster());
		checkAndRegister(message.getAccountId(), bestias, ctx);

		// Get all the bestias added to this server.
		final BestiaInfoMessage msg = new BestiaInfoMessage(message, 1, account.getMaster(), bestias);
		ctx.getServer().sendMessage(msg);
	}

	/**
	 * Checks if a certain bestia is managed by this particular zone. If this is the case register the bestia in the ECS
	 * of the server and then add the account to this server to it listens for incoming messages.
	 */
	private void checkAndRegister(Long accId, Set<PlayerBestia> bestias, CommandContext ctx) {

		final Zoneserver server = ctx.getServer();
		final Set<String> zones = server.getResponsibleZones();
		final InputController ecsInput = ctx.getServer().getInputController();

		for (PlayerBestia playerBestia : bestias) {
			if (!zones.contains(playerBestia.getCurrentPosition().getMapDbName())) {
				continue;
			}

			// Register on zone.
			ecsInput.addPlayerBestia(accId, new PlayerBestiaManager(playerBestia, server));
		}
	}

	@Override
	public String toString() {
		return "RequestLoginCommand[]";
	}

}

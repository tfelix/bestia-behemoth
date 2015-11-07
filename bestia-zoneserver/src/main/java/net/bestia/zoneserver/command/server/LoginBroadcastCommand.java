package net.bestia.zoneserver.command.server;

import java.util.Set;

import net.bestia.messages.LoginBroadcastMessage;
import net.bestia.messages.Message;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.Zoneserver;
import net.bestia.zoneserver.command.Command;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.message.SpawnPlayerBestiaMessage;

/*-
 * This command will be executed if a new user wants to join. He needs a few information in order to boot the client
 * properly. First of all we need to check if this zone is responsible for this account in any way, this means if at
 * least one bestia is present on one of the responsible zones. If this is the case we will spawn all this bestias to
 * the ECS. This is also the reason we dont let the zone handle the command which would be possible. But we avoid all
 * zones checking if they are responsible for the to be spanwed bestia. Instead we handle it in a centralized manner.
 * If the bestia master is currently active on one of our responsible zones we act as the "main zoneserver" for this
 * account sending additional information to boot up the client (inventory list for example).
 * As soon as the bestia master has become active. This will send all changes of entities inside his view to the client.
 * But we will have to send an initial sync message.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class LoginBroadcastCommand extends Command {

	private PlayerBestiaDAO bestiaDao;
	private AccountDAO accountDao;
	private CommandContext ctx;

	private Account account;
	private Set<PlayerBestia> bestias;

	@Override
	public String handlesMessageId() {
		return LoginBroadcastMessage.MESSAGE_ID;
	}

	@Override
	public void execute(Message message, CommandContext ctx) {

		// Gather all the needed data for the client to completely display
		// everything...
		this.ctx = ctx;

		// gather bestias.
		bestiaDao = ctx.getServiceLocator().getBean(PlayerBestiaDAO.class);
		accountDao = ctx.getServiceLocator().getBean(AccountDAO.class);

		account = accountDao.find(message.getAccountId());
		bestias = bestiaDao.findPlayerBestiasForAccount(message.getAccountId());

		// Add master as well since its not listed as a "player bestia".
		bestias.add(account.getMaster());

		registerPlayerBestias(message.getAccountId(), bestias);
	}

	/**
	 * Checks if a certain bestia is managed by this particular zone. If this is
	 * the case register the bestia in the ECS of the server and then add the
	 * account to this server to it listens for incoming messages.
	 * 
	 */
	private void registerPlayerBestias(Long accId, Set<PlayerBestia> bestias) {

		for (PlayerBestia playerBestia : bestias) {
			if (!isBestiaOnZone(playerBestia)) {
				continue;
			}

			// Spawn on this zone.
			final SpawnPlayerBestiaMessage spawnMsg = new SpawnPlayerBestiaMessage(accId, playerBestia.getId());
			ctx.getServer().getMessageRouter().processMessage(spawnMsg);
		}
	}

	private boolean isBestiaOnZone(PlayerBestia playerBestia) {
		final Zoneserver server = ctx.getServer();
		final Set<String> zones = server.getResponsibleZones();
		return zones.contains(playerBestia.getCurrentPosition().getMapDbName());
	}

	@Override
	public String toString() {
		return "RequestLoginCommand[]";
	}

}

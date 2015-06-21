package net.bestia.zoneserver.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.bestia.messages.LoginBroadcastMessage;
import net.bestia.messages.Message;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.service.AccountService;
import net.bestia.zoneserver.Zoneserver;
import net.bestia.zoneserver.game.manager.PlayerBestiaManager;
import net.bestia.zoneserver.game.zone.Zone;

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

		final Zoneserver server = ctx.getServer();

		final AccountService accService = ctx.getServiceLocator().getBean(AccountService.class);
		final Account account = accService.getAccount(message.getAccountId());

		boolean hasAtLeastOneEntity = false;

		// Check if this player has bestias or his master on this zone.
		PlayerBestia master = account.getMaster();

		if (isOnZone(server, master)) {
			// Spawn the master as the entity
			Zone z = getZone(server, master);
			z.addPlayerBestia(new PlayerBestiaManager(master, server));
			hasAtLeastOneEntity = true;
		}

		// Find the playerbestias associated with this account.
		List<PlayerBestia> bestias = new ArrayList<PlayerBestia>();

		// Create bestia entity.

		// Add to the zone.

		// We have bestias from this account on this zone.
		// Register this zone now as responsible for handling messages regarding this account.
		if (hasAtLeastOneEntity) {
			ctx.getServer().subscribe("zone/account/" + message.getAccountId());
		}
	}

	private boolean isOnZone(Zoneserver server, PlayerBestia pb) {
		final Set<String> responsibleZones = server.getResponsibleZones();
		return responsibleZones.contains(pb.getCurrentPosition().getMapDbName());
	}
	
	private Zone getZone(Zoneserver server, PlayerBestia pb) {
		final String mapDbName = pb.getCurrentPosition().getMapDbName();
		return server.getZone(mapDbName);
	}
	
	@Override
	public String toString() {
		return "RequestLoginCommand[]";
	}

}

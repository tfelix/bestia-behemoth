package net.bestia.zoneserver.command;

import java.util.ArrayList;
import java.util.List;

import net.bestia.messages.BestiaInitMessage;
import net.bestia.messages.LoginBroadcastMessage;
import net.bestia.messages.Message;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Location;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.service.AccountService;
import net.bestia.zoneserver.game.zone.Entity;

/**
 * This command will be executed if a new user wants to join. He needs a few
 * information in order to boot the client properly. We will gather the
 * following: * Informations about all bestias connected to this account.
 * 
 * But we will also perform a few action: * Spawn the bestia master into the
 * world.
 * 
 * As soon as the bestia master has become active. This will send all changes of
 * entities inside his view to the client. But we will have to send an initial
 * sync message.
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
		
		AccountService accService = ctx.getServiceLocator().getBean(AccountService.class);
		Account account = accService.getAccount(message.getAccountId());

		

		// TODO finalisiere und unit testen.
		// See if the master is on this zone.
		/*PlayerBestia master = null;
		// If this zone is not responsible stop processing.
		if (!ctx.getServer().getResponsibleZones()
				.contains(master.getCurrentPosition().getMapDbName())) {
			return;
		}*/

		// Find the playerbestias associated with this account.
		List<PlayerBestia> bestias = new ArrayList<PlayerBestia>();

		// TEMP
		PlayerBestia b = new PlayerBestia();
		b.setCurrentPosition(new Location("test-zone1", 20, 20));
		b.setExp(100);
		b.setName("Blubber");
		b.setSavePosition(new Location("test-zone1", 10, 10));
		bestias.add(b);

		BestiaInitMessage bestiaInitMessage = new BestiaInitMessage(message);
		bestiaInitMessage.setBestias(bestias);
		bestiaInitMessage.setMaster(b);
		bestiaInitMessage.setNumberOfSlots(0);
		ctx.getServer().sendMessage(bestiaInitMessage);

		// Create bestia entity.

		// Add to the zone.
		Entity entity = new Entity(account.getId());
		ctx.getZone(b.getCurrentPosition().getMapDbName()).addEntity(entity);


		// Register this zone now as responsible for handling messages regarding this account.
		ctx.getServer().subscribe("zone/account/" + message.getAccountId());
	}

}

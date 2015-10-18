package net.bestia.zoneserver.command;

import java.util.List;
import java.util.Set;

import net.bestia.messages.BestiaInfoMessage;
import net.bestia.messages.InventoryListMessage;
import net.bestia.messages.LoginBroadcastMessage;
import net.bestia.messages.Message;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.dao.PlayerItemDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.PlayerItem;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.Zoneserver;
import net.bestia.zoneserver.ecs.BestiaRegister;
import net.bestia.zoneserver.manager.InventoryManager;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

/*-
 * This command will be executed if a new user wants to join. He needs a few information in order to boot the client
 * properly. First of all we need to check if this zone is responsible for this account in any way, this means if at
 * least one bestia is present on one of the responsible zones. If this is the case we will spawn all this bestias to
 * the ECS.
 * If the bestia master is currently active on one of our responsible zones we act as the "main zoneserver" for this
 * account sending additional information to boot up the client (inventory list for example).
 * As soon as the bestia master has become active. This will send all changes of entities inside his view to the client.
 * But we will have to send an initial sync message.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class RequestLoginCommand extends Command {
	
	private PlayerBestiaDAO bestiaDao;
	private AccountDAO accountDao;
	private CommandContext ctx;
	
	private Account account;
	private Set<PlayerBestia> bestias;
	private Message message;

	@Override
	public String handlesMessageId() {
		return LoginBroadcastMessage.MESSAGE_ID;
	}

	
	@Override
	public void execute(Message message, CommandContext ctx) {

		// Gather all the needed data for the client to completely display everything...
		this.message = message;
		this.ctx = ctx;

		// gather bestias.
		bestiaDao = ctx.getServiceLocator().getBean(PlayerBestiaDAO.class);
		accountDao = ctx.getServiceLocator().getBean(AccountDAO.class);

		account = accountDao.find(message.getAccountId());
		bestias = bestiaDao.findPlayerBestiasForAccount(message.getAccountId());

		// Add master as well since its not listed as a "player bestia".
		bestias.add(account.getMaster());
		
		registerPlayerBestias(message.getAccountId(), bestias);
		registerMaster(account.getMaster());
	}
	
	private boolean isBestiaOnZone(PlayerBestia playerBestia) {
		final Zoneserver server = ctx.getServer();
		final Set<String> zones = server.getResponsibleZones();
		return zones.contains(playerBestia.getCurrentPosition().getMapDbName());
	}
	
	private void registerMaster(PlayerBestia master) {
		if(!isBestiaOnZone(master)) {
			// Master is not here. Puh. Not responsible.
			return;
		}
		
		final PlayerBestiaManager masterManager = new PlayerBestiaManager(master, ctx.getServer());
		final InventoryService invService = ctx.getServiceLocator().getBean(InventoryService.class);
		final InventoryManager invManager = new InventoryManager(masterManager, invService, ctx.getServer());
		
		// Master is here. It will be spawned by registerPlayerBestias anyways so only do the house keeping work.
		final PlayerItemDAO playerItemDao = ctx.getServiceLocator().getBean(PlayerItemDAO.class);
		
		final List<PlayerItem> items = playerItemDao.findPlayerItemsForAccount(account.getId());
		
		// Generate a list of inventory items.
		final InventoryListMessage invMsg = new InventoryListMessage(message);
		invMsg.setPlayerItems(items);
		invMsg.setCurrentWeight(invManager.getCurrentWeight());
		invMsg.setMaxWeight(invManager.getMaxWeight());
		ctx.getServer().sendMessage(invMsg);
		
		// Calculate current status values.
		for (PlayerBestia playerBestia : bestias) {
			final PlayerBestiaManager manager = new PlayerBestiaManager(playerBestia, ctx.getServer());
			manager.updateStatusValues();
		}
		
		// Generate a list of bestias for this account.
		final BestiaInfoMessage msg = new BestiaInfoMessage(message, 1, master, bestias);
		ctx.getServer().sendMessage(msg);
	}


	/**
	 * Checks if a certain bestia is managed by this particular zone. If this is the case register the bestia in the ECS
	 * of the server and then add the account to this server to it listens for incoming messages.
	 * 
	 */
	private void registerPlayerBestias(Long accId, Set<PlayerBestia> bestias) {

		final Zoneserver server = ctx.getServer();
		final BestiaRegister register = ctx.getServer().getBestiaRegister();

		for (PlayerBestia playerBestia : bestias) {
			if(!isBestiaOnZone(playerBestia)) {
				continue;
			}

			// Register on zone.
			final PlayerBestiaManager pbm = new PlayerBestiaManager(playerBestia, server);
			register.addPlayerBestia(accId, pbm);
		}
	}

	@Override
	public String toString() {
		return "RequestLoginCommand[]";
	}

}

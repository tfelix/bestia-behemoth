package net.bestia.zoneserver.command.ecs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.Message;
import net.bestia.messages.inventory.InventoryItemCastConfirmMessage;
import net.bestia.messages.inventory.InventoryItemCastMessage;
import net.bestia.model.domain.Item;
import net.bestia.model.domain.ItemType;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;
import net.bestia.zoneserver.messaging.AccountRegistry;
import net.bestia.zoneserver.proxy.InventoryProxy;
import net.bestia.zoneserver.proxy.PlayerBestiaEntityProxy;
import net.bestia.zoneserver.script.ItemScript;

/**
 * This will handle all messages for casting an item onto the map. It will then
 * send confirmation to the client if the cast has been successful.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ItemCastCommand extends ECSCommand {
	
	private final static Logger LOG = LogManager.getLogger(ItemCastCommand.class);
	
	private PlayerBestiaSpawnManager playerBestiaManager;
	
	@Override
	protected void initialize() {
		playerBestiaManager = world.getSystem(PlayerBestiaSpawnManager.class);
	}

	@Override
	public String handlesMessageId() {
		return InventoryItemCastMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		// Cast msg.
		final InventoryItemCastMessage castMsg = (InventoryItemCastMessage) message;
		
		final AccountRegistry register = ctx.getAccountRegistry();
		final long accId = castMsg.getAccountId();

		final int activeBestiaId = register.getActiveBestia(accId);
		final PlayerBestiaEntityProxy owner = playerBestiaManager.getPlayerBestiaProxy(activeBestiaId);

		final InventoryService invService = ctx.getServiceLocator().getBean(InventoryService.class);
		final InventoryProxy inventory = new InventoryProxy(owner, invService, ctx.getServer());
		
		
		// Has the use the item in inventory? No? Abort and notify the user.
		if(!inventory.hasPlayerItem(castMsg.getPlayerItemId(), 1)) {
			final InventoryItemCastConfirmMessage confirmMsg = new InventoryItemCastConfirmMessage(castMsg, false);
			ctx.getServer().sendMessage(confirmMsg);
			return;
		}
		
		final Item item = inventory.getPlayerItem(castMsg.getPlayerItemId()).getItem();
		boolean success = true;
		
		// Can the item be cast onto the given coordinates (range check?)
		// Yes: do the casting (execute item script) and notify the user.
		if(item.getType() != ItemType.CASTABLE) {
			LOG.trace("Item not castable.");
			success = false;
		}
		
		if(!isInRange(item, castMsg.getX(), castMsg.getY(), owner)) {
			LOG.trace("Item not in castrange.");
			success = false;
		}
		
		if(!success) {
			final InventoryItemCastConfirmMessage confirmMsg = new InventoryItemCastConfirmMessage(castMsg, false);
			ctx.getServer().sendMessage(confirmMsg);
			return;
		}
		
		// TODO Hier ggf. ein anderes Script verwenden bzw checken ob das so okay it (bezug zur map per binding benötigt?).
		final ItemScript iScript = new ItemScript(item.getItemDbName(), owner, inventory);
		success = ctx.getScriptManager().execute(iScript);

		if (success) {
			inventory.removeItem(item.getId(), 1);
			final InventoryItemCastConfirmMessage confirmMsg = new InventoryItemCastConfirmMessage(castMsg, true);
			ctx.getServer().sendMessage(confirmMsg);
			LOG.info("Cast item: {}, accId: {}, x: {}, y: {}", item, accId, castMsg.getX(), castMsg.getY());
		} else {
			final InventoryItemCastConfirmMessage confirmMsg = new InventoryItemCastConfirmMessage(castMsg, false);
			ctx.getServer().sendMessage(confirmMsg);
			LOG.info("Could not cast item: {}, accId: {},  x: {}, y: {}", item, accId, castMsg.getX(), castMsg.getY());
		}
	}
	
	@Override
	public String toString() {
		return "ItemCastCommand[]";
	}

	private boolean isInRange(Item item, int targetX, int targetY, PlayerBestiaEntityProxy caster) {
		final int range = item.getUsableRange();
		
		final int x = caster.getLocation().getX();
		final int y = caster.getLocation().getY();
		
		final int dX = targetX - x;
		final int dY = targetY - y;
		
		return Math.sqrt(dX * dX + dY * dY) < range;	
	}
}
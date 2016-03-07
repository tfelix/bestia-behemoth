package net.bestia.zoneserver.command.ecs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.Message;
import net.bestia.messages.inventory.InventoryItemUseMessage;
import net.bestia.model.domain.Item;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;
import net.bestia.zoneserver.manager.InventoryProxy;
import net.bestia.zoneserver.manager.PlayerBestiaEntityProxy;
import net.bestia.zoneserver.messaging.AccountRegistry;
import net.bestia.zoneserver.script.ItemScript;

/**
 * Tries to use the given item in the context of the currently active bestia.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ItemUseCommand extends ECSCommand {

	private final static Logger log = LogManager.getLogger(ItemUseCommand.class);

	@Override
	public String handlesMessageId() {
		return InventoryItemUseMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {

		final PlayerBestiaSpawnManager spawnManager = world.getSystem(PlayerBestiaSpawnManager.class);
		
		final InventoryItemUseMessage useMessage = (InventoryItemUseMessage) message;

		final AccountRegistry register = ctx.getAccountRegistry();
		final long accId = useMessage.getAccountId();

		final int activeBestiaId = register.getActiveBestia(accId);
		final PlayerBestiaEntityProxy owner = spawnManager.getPlayerBestiaManager(activeBestiaId);

		final InventoryService invService = ctx.getServiceLocator().getBean(InventoryService.class);
		final InventoryProxy inventory = new InventoryProxy(owner, invService, ctx.getServer());

		// TODO Das von PLayer Item ID auf Item ID ändern!
		if (!inventory.hasItem(useMessage.getItemId(), 1)) {
			// Can not use this item.
			return;
		}

		final Item item = inventory.getPlayerItemById(useMessage.getItemId()).getItem();
		final ItemScript iScript = new ItemScript(item.getItemDbName(), owner, inventory);
		final boolean success = ctx.getScriptManager().execute(iScript);

		if (success) {
			inventory.removeItem(useMessage.getItemId(), 1);
			log.info("Used item: {}, accId: {}", useMessage.getItemId(), useMessage.getAccountId());
		} else {
			log.debug("Could not use item: {}, accId: {}", useMessage.getItemId(), useMessage.getAccountId());
		}
	}

	@Override
	public String toString() {
		return "UseItemCommand[]";
	}

}

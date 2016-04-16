package net.bestia.zoneserver.command.ecs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.annotations.Wire;

import net.bestia.messages.Message;
import net.bestia.messages.inventory.InventoryItemUseMessage;
import net.bestia.model.domain.Item;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;
import net.bestia.zoneserver.messaging.AccountRegistry;
import net.bestia.zoneserver.proxy.InventoryProxy;
import net.bestia.zoneserver.proxy.PlayerBestiaEntityProxy;
import net.bestia.zoneserver.script.ItemScript;
import net.bestia.zoneserver.script.MapScriptAPI;

/**
 * Tries to use the given item in the context of the currently active bestia.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class ItemUseCommand extends ECSCommand {

	private final static Logger log = LogManager.getLogger(ItemUseCommand.class);
	
	@Wire
	private MapScriptAPI mapScriptApi;

	@Override
	public String handlesMessageId() {
		return InventoryItemUseMessage.MESSAGE_ID;
	}
	
	@Override
	protected void initialize() {
		super.initialize();
		
		world.inject(this);
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {

		final PlayerBestiaSpawnManager spawnManager = world.getSystem(PlayerBestiaSpawnManager.class);
		
		final InventoryItemUseMessage useMessage = (InventoryItemUseMessage) message;

		final AccountRegistry register = ctx.getAccountRegistry();
		final long accId = useMessage.getAccountId();

		final int activeBestiaId = register.getActiveBestia(accId);
		final PlayerBestiaEntityProxy owner = spawnManager.getPlayerBestiaProxy(activeBestiaId);

		final InventoryService invService = ctx.getServiceLocator().getBean(InventoryService.class);
		final InventoryProxy inventory = new InventoryProxy(owner, invService, ctx.getServer());

		// TODO Das von PLayer Item ID auf Item ID ändern!
		if (!inventory.hasItem(useMessage.getItemId(), 1)) {
			// Can not use this item.
			return;
		}

		final Item item = inventory.getPlayerItemById(useMessage.getItemId()).getItem();
		final ItemScript iScript = new ItemScript(item.getItemDbName(), owner, mapScriptApi, inventory);
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

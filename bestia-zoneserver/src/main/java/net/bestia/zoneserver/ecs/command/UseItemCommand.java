package net.bestia.zoneserver.ecs.command;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.InventoryItemUseMessage;
import net.bestia.messages.Message;
import net.bestia.model.domain.Item;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.manager.InventoryManager;
import net.bestia.zoneserver.manager.PlayerBestiaManager;
import net.bestia.zoneserver.script.ItemScript;

public class UseItemCommand extends ECSCommand {

	private final static Logger log = LogManager.getLogger(UseItemCommand.class);

	@Override
	public String handlesMessageId() {
		return InventoryItemUseMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {

		final InventoryItemUseMessage useMessage = (InventoryItemUseMessage) message;

		final InventoryService invService = ctx.getServiceLocator().getBean(InventoryService.class);	
		final InventoryManager inventory = new InventoryManager(message.getAccountId(), invService, ctx.getServer());

		if (!inventory.hasItem(useMessage.getPlayerItemId(), 1)) {
			// Can not use this item.
			return;
		}

		final Item item = inventory.getItem(useMessage.getPlayerItemId()).getItem();
		final PlayerBestiaManager owner = ctx.getServer().getBestiaRegister()
				.getSpawnedBestia(useMessage.getAccountId(), useMessage.getPlayerBestiaId());

		final ItemScript iScript = new ItemScript(item.getItemDbName(), owner, inventory);
		final boolean success = ctx.getScriptManager().execute(iScript);

		if (success) {
			inventory.removeItem(useMessage.getPlayerItemId(), 1);
			log.info("Used item: {}, accId: {}", useMessage.getPlayerItemId(), useMessage.getAccountId());
		} else {
			log.debug("Could not use item: {}, accId: {}", useMessage.getPlayerItemId(), useMessage.getAccountId());
		}
	}

	@Override
	public String toString() {
		return "UseItemCommand[]";
	}

}

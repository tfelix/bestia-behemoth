package net.bestia.zoneserver.ecs.command;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.InventoryItemUseMessage;
import net.bestia.messages.Message;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Item;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.BestiaRegister;
import net.bestia.zoneserver.manager.InventoryManager;
import net.bestia.zoneserver.manager.PlayerBestiaManager;
import net.bestia.zoneserver.manager.PlayerBestiaManagerInterface;
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

		final BestiaRegister register = ctx.getServer().getBestiaRegister();
		final long accId = useMessage.getAccountId();
		final int userBestiaId = useMessage.getPlayerBestiaId();

		final PlayerBestiaManager owner = register.getSpawnedBestia(accId, userBestiaId);
		
		final AccountDAO dao = ctx.getServiceLocator().getBean(AccountDAO.class);
		final PlayerBestia masterBestia = dao.find(accId).getMaster();
		
		final PlayerBestiaManagerInterface master = new PlayerBestiaManager(masterBestia, ctx.getServer());
		
		final InventoryService invService = ctx.getServiceLocator().getBean(InventoryService.class);
		final InventoryManager inventory = new InventoryManager(master, invService, ctx.getServer());

		if (!inventory.hasItem(useMessage.getPlayerItemId(), 1)) {
			// Can not use this item.
			return;
		}

		final Item item = inventory.getPlayerItem(useMessage.getPlayerItemId()).getItem();
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

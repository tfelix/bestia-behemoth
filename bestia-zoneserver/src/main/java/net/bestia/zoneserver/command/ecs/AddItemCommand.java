package net.bestia.zoneserver.command.ecs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.AddItemMessage;
import net.bestia.messages.ChatMessage;
import net.bestia.messages.Message;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.manager.InventoryManager;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

public class AddItemCommand extends ECSCommand {

	private static final Logger LOG = LogManager.getLogger(AddItemCommand.class);

	@Override
	public String handlesMessageId() {
		return AddItemMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		final AddItemMessage msg = (AddItemMessage) message;

		final PlayerBestiaManager pbm = getPlayerBestiaManager();
		final AccountDAO accDao = ctx.getServiceLocator().getBean(AccountDAO.class);
		final Account acc = accDao.find(pbm.getAccountId());
		final InventoryService invService = ctx.getServiceLocator().getBean(InventoryService.class);
		final InventoryManager invManager = new InventoryManager(pbm, invService, ctx.getServer());

		if (msg.getItemId().matches("\\d+")) {
			// Its a number.
			try {
				final int itemId = Integer.parseInt(msg.getItemId());
				final boolean success = invManager.addItem(itemId, msg.getAmount());
				if (success) {
					LOG.info("GM: Spawning item: {}, amount: {} for account: {}.", pbm.getAccountId());
				}
			} catch (IllegalArgumentException ex) {
				final ChatMessage responseMsg = ChatMessage.getSystemMessage(acc, "etc.unknown_item", msg.getItemId());
				ctx.getServer().sendMessage(responseMsg);
			}
		} else {
			try {
				final boolean success = invManager.addItem(msg.getItemId(), msg.getAmount());
				if (success) {
					LOG.info("GM: Spawning item: {}, amount: {} for account: {}.", pbm.getAccountId());
				}

			} catch (IllegalArgumentException ex) {
				final ChatMessage responseMsg = ChatMessage.getSystemMessage(acc, "etc.unknown_item", msg.getItemId());
				ctx.getServer().sendMessage(responseMsg);
			}
		}
	}

}

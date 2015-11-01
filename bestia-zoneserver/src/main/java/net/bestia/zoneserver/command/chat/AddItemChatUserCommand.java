package net.bestia.zoneserver.command.chat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.ChatMessage;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.manager.InventoryManager;

/**
 * Chat commands which will spawn and item and adds it to the inventory.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AddItemChatUserCommand implements ChatUserCommand {

	private static final Logger LOG = LogManager.getLogger(AddItemChatUserCommand.class);

	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.command.chat.ChatUserCommand2#execute(net.bestia.messages.Message, net.bestia.zoneserver.command.CommandContext)
	 */
	@Override
	public void execute(ChatMessage message, CommandContext ctx) {

		final ChatMessage m = (ChatMessage) message;
		final AccountDAO accDAO = ctx.getServiceLocator().getBean(AccountDAO.class);

		// Find the player who send the message.
		final Account acc = accDAO.find(m.getAccountId());
		final long accId = acc.getId();
		final int activeBestiaId = ctx.getServer().getBestiaRegister().getActiveBestia(accId);

		final InventoryService invService = ctx.getServiceLocator().getBean(InventoryService.class);
		final InventoryManager invManager = new InventoryManager(null, invService, ctx.getServer());

		// Get the item name and the amount.
		final String[] tokens = m.getText().split(" ");

		if (tokens.length < 3) {
			return;
		}

		// Check if the item is a id or a string.
		final String itemDbNameStr = tokens[1];

		final String amountStr = tokens[2];
		final int amount;
		try {
			amount = Integer.parseInt(amountStr);
		} catch (NumberFormatException ex) {
			return;
		}

		if (itemDbNameStr.matches("\\d+")) {
			try {
				final int itemId = Integer.parseInt(itemDbNameStr);
				final boolean success = invManager.addItem(itemId, amount);
				if(success) {
					LOG.info("GM: Spawning item: {}, amount: {} for account: {}.", accId);
				}				
				
			} catch (IllegalArgumentException ex) {
				// TODO MSG

			}
		} else {
			try {
				final boolean success = invManager.addItem(itemDbNameStr, amount);
				if(success) {
				LOG.info("GM: Spawning item: {}, amount: {} for account: {}.", accId);
				}
				
			} catch (IllegalArgumentException ex) {
				// TODO MSG

			}
		}
	}
	
	/* (non-Javadoc)
	 * @see net.bestia.zoneserver.command.chat.ChatUserCommand#getChatToken()
	 */
	@Override
	public String getChatToken() {
		return "/item";
	}

	@Override
	public UserLevel getNeededUserLevel() {
		return UserLevel.SUPER_GM;
	}
}

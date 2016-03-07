package net.bestia.zoneserver.command.chat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.messages.ChatMessage;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.manager.InventoryProxy;
import net.bestia.zoneserver.manager.PlayerBestiaEntityProxy;

/*-
 * Chat commands which will spawn and item and adds it to the inventory.
 * 
 * Usage:
 *	/item [ID] [AMOUNT]
 *	/item [ITEM_DB_NAME] [AMOUNT]
 *
 * Amount is optional though. If no amount is given an amount of 1 is used.
 * 
 *
 *
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AddItemChatUserCommand implements ChatUserCommand {

	private static final Logger LOG = LogManager.getLogger(AddItemChatUserCommand.class);

	private class ItemDesc {
		public String itemDbName;
		public int itemdId = -1;
		public int amount;
	}

	@Override
	public void execute(ChatMessage message, PlayerBestiaEntityProxy player, CommandContext ctx) {

		final ChatMessage m = (ChatMessage) message;

		final ItemDesc desc = parseCommand(m.getText());

		if (desc == null) {
			final ChatMessage reply = ChatMessage.getEchoRawMessage(message.getAccountId(),
					"Wrong format. Use /item <NAME> <AMOUNT>");
			ctx.getServer().sendMessage(reply);
		}

		// Find the player who send the message.
		final InventoryService invService = ctx.getServiceLocator().getBean(InventoryService.class);
		final InventoryProxy invManager = new InventoryProxy(player, invService, ctx.getServer());

		try {
			if (desc.itemdId != -1) {
				invManager.addItem(desc.itemdId, desc.amount);
			} else {
				invManager.addItem(desc.itemDbName, desc.amount);
			}
		} catch (IllegalArgumentException ex) {
			LOG.warn("Could not add item to account: {}. Reason: {}", player.getAccountId(), ex.getMessage());
		}
	}

	private ItemDesc parseCommand(String text) {
		// Get the item name and the amount.
		final String[] tokens = text.split(" ");

		final ItemDesc desc = new ItemDesc();

		// Wrong format.
		if (tokens.length < 2) {
			return null;
		}

		// Check if the item is a id or a string.
		desc.itemDbName = tokens[1];

		if (desc.itemDbName.matches("\\d+")) {
			try {
				desc.itemdId = Integer.parseInt(desc.itemDbName);
			} catch (NumberFormatException ex) {
				desc.itemdId = -1;
			}
		}

		if (tokens.length == 3) {
			final String amountStr = tokens[2];
			try {
				desc.amount = Integer.parseInt(amountStr);
			} catch (NumberFormatException ex) {
				desc.amount = 1;
			}
		}

		return desc;
	}

	/*
	 * (non-Javadoc)
	 * 
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

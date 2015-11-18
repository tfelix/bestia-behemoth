package net.bestia.zoneserver.command.ecs;

import java.util.ArrayList;
import java.util.List;

import net.bestia.messages.InventoryItemSetMessage;
import net.bestia.messages.Message;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

/**
 * Sets the item shortcuts for this bestia to the database.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ItemSetCommand extends ECSCommand {

	@Override
	public String handlesMessageId() {
		return InventoryItemSetMessage.MESSAGE_ID;
	}

	@Override
	protected void execute(Message message, CommandContext ctx) {
		final InventoryItemSetMessage itemSetMsg = (InventoryItemSetMessage) message;

		// Set the attacks.
		final PlayerBestiaManager pbm = getPlayerBestiaManager();
		final List<Integer> itemIds = new ArrayList<>();
		itemIds.add(itemSetMsg.getItemSlotId1());
		itemIds.add(itemSetMsg.getItemSlotId2());
		itemIds.add(itemSetMsg.getItemSlotId3());
		itemIds.add(itemSetMsg.getItemSlotId4());
		itemIds.add(itemSetMsg.getItemSlotId5());
		pbm.setItems(itemIds);
	}

}

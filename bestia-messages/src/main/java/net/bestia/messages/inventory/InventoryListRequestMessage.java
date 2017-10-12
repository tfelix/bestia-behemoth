package net.bestia.messages.inventory;

import net.bestia.messages.JsonMessage;

/**
 * Client requests to list the inventory.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InventoryListRequestMessage extends JsonMessage {

	public final static String MESSAGE_ID = "inventory.requestlist";

	private static final long serialVersionUID = 1L;

	public InventoryListRequestMessage(long accId) {
		super(accId);
		// no op.
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public InventoryListRequestMessage createNewInstance(long accountId) {
		return new InventoryListRequestMessage(accountId);
	}
}

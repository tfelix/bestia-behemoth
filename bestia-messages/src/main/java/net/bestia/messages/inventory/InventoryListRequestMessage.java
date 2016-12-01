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

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}

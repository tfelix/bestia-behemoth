package net.bestia.messages.inventory;

import net.bestia.messages.JasonMessage;

/**
 * Client requests to list the inventory.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InventoryListRequestMessage extends JasonMessage {

	public final static String MESSAGE_ID = "inventory.requestlist";
	
	private static final long serialVersionUID = 1L;

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}

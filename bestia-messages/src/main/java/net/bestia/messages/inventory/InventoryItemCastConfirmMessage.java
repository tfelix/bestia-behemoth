package net.bestia.messages.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The server confirms the casting of an item to the client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InventoryItemCastConfirmMessage extends InventoryItemCastMessage {

	private static final long serialVersionUID = 1L;
	
	public static final String MESSAGE_ID = "inventory.item.castconfirm";
	
	@JsonProperty("s")
	private boolean success = true;
	
	public InventoryItemCastConfirmMessage() {
		// no op.
	}
	
	public InventoryItemCastConfirmMessage(InventoryItemCastMessage msg, boolean success) {
		super(msg);
		
		this.success = success;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
	
	/**
	 * Message is delivered to the client.
	 */
	@Override
	public String getMessagePath() {
		return getClientMessagePath();
	}
	
	public boolean getSuccess() {
		return this.success;
	}
	
	public void setSuccess(boolean success) {
		this.success = success;
	}
}

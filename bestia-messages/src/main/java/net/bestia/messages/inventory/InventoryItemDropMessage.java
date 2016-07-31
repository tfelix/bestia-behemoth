package net.bestia.messages.inventory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.InputMessage;

/**
 * Send if the player wants to drop an item to the ground.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InventoryItemDropMessage extends InputMessage {

	@JsonIgnore
	private static final long serialVersionUID = 1L;

	public final static String MESSAGE_ID = "inventory.item.drop";

	@JsonProperty("iid")
	private int itemId;

	@JsonProperty("a")
	private int amount;

	/**
	 * Std. Ctor.
	 */
	public InventoryItemDropMessage() {

	}

	public int getItemId() {
		return itemId;
	}

	public int getAmount() {
		return amount;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("InventoryItemDropMessage[accId: %d, pbId: %d,  itemId: %d, amount: %d]", getAccountId(),
				getPlayerBestiaId(), getItemId(), getAmount());
	}

}

package net.bestia.messages.inventory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;

/**
 * Send if the player wants to drop an item to the ground.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InventoryItemDropMessage extends JsonMessage {

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
	protected InventoryItemDropMessage() {

	}

	/**
	 * Ctor.
	 * 
	 * @param accId
	 *            The account originating this message.
	 * @param itemId
	 *            The item id.
	 * @param amount
	 *            The amount of the item to be dropped.
	 */
	public InventoryItemDropMessage(long accId, int itemId, int amount) {
		super(accId);

		this.itemId = itemId;
		this.amount = amount;
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
		return String.format("InventoryItemDropMessage[itemId: %d, amount: %d]", getItemId(),
				getAmount());
	}

	@Override
	public InventoryItemDropMessage createNewInstance(long accountId) {
		return new InventoryItemDropMessage(accountId, itemId, amount);
	}

}

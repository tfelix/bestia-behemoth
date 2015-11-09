package net.bestia.messages;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.model.domain.PlayerItem;

/**
 * This message is send from a {@link InventoryManager} to the client. It
 * contains changes in the inventory. This means: items which are lost from the
 * inventory are propagated via this mechanism to the client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InventoryUpdateMessage extends Message {

	@JsonIgnore
	private static final long serialVersionUID = 1L;

	public class ItemAmount implements Serializable {

		private static final long serialVersionUID = 1L;

		@JsonProperty("i")
		public PlayerItem item;

		@JsonProperty("a")
		public int amount;

		public ItemAmount() {

		}

		public ItemAmount(PlayerItem item, int amount) {
			this.item = item;
			this.amount = amount;
		}
		
		@Override
		public String toString() {
			return String.format("ItemAmount[PlayerItem: %s, amount: %d]", item.toString(), amount);
		}
	}
	
	public InventoryUpdateMessage() {
		
	}
	
	public InventoryUpdateMessage(long accId) {
		super(accId);
	}

	public static final String MESSAGE_ID = "inventory.update";

	@JsonProperty("pis")
	private List<ItemAmount> playerItems = new ArrayList<>();

	@JsonProperty("cw")
	private int currentWeight;

	/**
	 * Adds an item with an updated count to this message. If the amount is
	 * negative the client will remove the item count (eventually removing the
	 * item altogether if none is left in its inventory) or adding the item if
	 * the amount is positive.
	 * 
	 * @param item
	 *            Item to be added or removed.
	 * @param amount
	 *            Amount of the item to be added or removed.
	 */
	public void updateItem(PlayerItem item, int amount) {
		playerItems.add(new ItemAmount(item, amount));
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getClientMessagePath();
	}

}

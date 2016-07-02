package net.bestia.messages.inventory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.model.domain.Item;

/**
 * This message is send from a {@link InventoryManager} to the client. It
 * contains changes in the inventory. This means: items which are lost from the
 * inventory are propagated via this mechanism to the client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InventoryUpdateMessage extends AccountMessage {

	@JsonIgnore
	private static final long serialVersionUID = 1L;

	public class UpdateItem implements Serializable {

		private static final long serialVersionUID = 1L;

		@JsonProperty("a")
		private int amount;

		@JsonProperty("i")
		private Item item;

		public int getAmount() {
			return amount;
		}

		public Item getItem() {
			return item;
		}

		public UpdateItem() {

		}

		public UpdateItem(Item item, int amount) {
			this.amount = amount;
			this.item = item;
		}

		@Override
		public String toString() {
			return String.format("[item: %s, id: %d, amount: %d]", item.getItemDbName(), item.getId(), amount);
		}
	}

	public InventoryUpdateMessage() {

	}

	public InventoryUpdateMessage(long accId) {
		super(accId);
	}

	public static final String MESSAGE_ID = "inventory.update";

	@JsonProperty("pis")
	private List<UpdateItem> playerItems = new ArrayList<>();

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
	public void updateItem(Item item, int amount) {
		playerItems.add(new UpdateItem(item, amount));
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getClientMessagePath(getAccountId());
	}

	@Override
	public String toString() {
		return String.format("InventoryUpdateMessage[accId: %d, updates: %s]", getAccountId(), playerItems.toString());
	}
}

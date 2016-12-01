package net.bestia.messages.inventory;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.model.domain.PlayerItem;

/**
 * This will send a complete list with all items in the inventory to the client.
 * Upon receiving this message the client should trigger a reset of the invntory
 * and display the new list of items.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InventoryListMessage extends JsonMessage {

	@JsonIgnore
	private static final long serialVersionUID = 1L;

	public final static String MESSAGE_ID = "inventory.list";
	
	@JsonProperty("pis")
	private List<PlayerItem> playerItems = new ArrayList<>(0);
	
	@JsonProperty("mw")
	private int maxWeight;
	
	/**
	 * Std. Ctor.
	 */
	public InventoryListMessage() {

	}

	/**
	 * Ctor.
	 * 
	 * @param message
	 */
	public InventoryListMessage(AccountMessage message) {
		super(message);
	}
	
	public List<PlayerItem> getPlayerItems() {
		return playerItems;
	}
	
	public void setPlayerItems(List<PlayerItem> playerItems) {
		this.playerItems = playerItems;
	}
	
	public void setMaxWeight(int maxWeight) {
		this.maxWeight = maxWeight;
	}
	
	public int getMaxWeight() {
		return maxWeight;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
	
	@Override
	public String toString() {
		return String.format("InventoryListMessage[accId: %d, items: %d]", getAccountId(), playerItems.size());
	}

}

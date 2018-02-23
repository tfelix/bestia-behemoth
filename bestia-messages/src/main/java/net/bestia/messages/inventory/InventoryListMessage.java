package net.bestia.messages.inventory;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;
import net.bestia.model.domain.PlayerItem;

/**
 * This will send a complete list with all items in the inventory to the client.
 * Upon receiving this message the client should trigger a reset of the invntory
 * and display the new list of items.
 * 
 * @author Thomas Felix
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
	
	private InventoryListMessage() {
		super(0);
	}

	/**
	 * Ctor.
	 * 
	 * @param message
	 */
	public InventoryListMessage(long accId) {
		super(accId);
	}
	
	public InventoryListMessage(long accId, List<PlayerItem> playerItems, int maxWeight) {
		super(accId);
		
		this.playerItems = new ArrayList<>(playerItems);
		this.maxWeight = maxWeight;
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

	@Override
	public InventoryListMessage createNewInstance(long accountId) {
		return new InventoryListMessage(accountId, playerItems, maxWeight);
	}

}

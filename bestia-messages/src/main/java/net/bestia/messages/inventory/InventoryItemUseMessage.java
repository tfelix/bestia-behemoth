package net.bestia.messages.inventory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.InputMessage;

/**
 * Advises the server to use an item from the inventory of the player
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InventoryItemUseMessage extends InputMessage {

	@JsonIgnore
	private static final long serialVersionUID = 1L;

	public final static String MESSAGE_ID = "inventory.item.use";

	@JsonProperty("iid")
	private int itemId;

	/**
	 * Std. Ctor.
	 */
	public InventoryItemUseMessage() {

	}

	public InventoryItemUseMessage(long accId, int pbid) {
		this.setAccountId(accId);
		this.setPlayerBestiaId(pbid);
	}

	public InventoryItemUseMessage(AccountMessage msg, int pbid) {
		super(msg, pbid);
	}

	public int getItemId() {
		return itemId;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getZoneMessagePath();
	}

	@Override
	public String toString() {
		return String.format("InventoryItemUseMessage[accId: %d, bestiaId: %d, itemId: %d]", getAccountId(),
				getPlayerBestiaId(), itemId);
	}

}

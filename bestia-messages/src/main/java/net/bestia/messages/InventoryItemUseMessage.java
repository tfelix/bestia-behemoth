package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

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

	@JsonProperty("pid")
	private int playerItemId;

	/**
	 * Std. Ctor.
	 */
	public InventoryItemUseMessage() {

	}

	public InventoryItemUseMessage(long accId, int pbid) {
		this.setAccountId(accId);
		this.setPlayerBestiaId(pbid);
	}

	public InventoryItemUseMessage(Message msg, int pbid) {
		super(msg, pbid);
	}

	public int getPlayerItemId() {
		return playerItemId;
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
				getPlayerBestiaId(), playerItemId);
	}

}

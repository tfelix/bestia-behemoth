package net.bestia.messages.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.InputMessage;

/**
 * Signals the server to use an castable item on the map possibly spawning map
 * entities or doing things to the map/zone itself.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InventoryItemCastMessage extends InputMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "inventory.item.cast";

	@JsonProperty("piid")
	private int playerItemId;

	/**
	 * Token for identifying the cast request on the client and receive the
	 * confirm message.
	 */
	@JsonProperty("t")
	private String token;

	private int x;

	private int y;

	/**
	 * Std. Ctor.
	 */
	public InventoryItemCastMessage() {
		// no op.
	}

	public InventoryItemCastMessage(InventoryItemCastMessage msg) {
		
		this.playerItemId = msg.playerItemId;
		this.token = msg.token;
		this.x = msg.x;
		this.y = msg.y;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getZoneMessagePath();
	}

	public int getPlayerItemId() {
		return playerItemId;
	}

	public void setPlayerItemId(int playerItemId) {
		this.playerItemId = playerItemId;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}
	
	public String getToken() {
		return token;
	}
	
	public void setToken(String token) {
		this.token = token;
	}
}
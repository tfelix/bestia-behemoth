package bestia.messages.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;

import bestia.messages.JsonMessage;

/**
 * Signals the server to use an castable item on the map possibly spawning map
 * entities or doing things to the map/zone itself.
 * 
 * @author Thomas Felix
 *
 */
public class InventoryItemCastMessage extends JsonMessage {

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

	protected InventoryItemCastMessage() {
		super(0);
		// no op.
	}
	
	public InventoryItemCastMessage(long accId, InventoryItemCastMessage copy) {
		super(accId);

		this.playerItemId = copy.playerItemId;
		this.token = copy.token;
		this.x = copy.x;
		this.y = copy.y;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
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

	@Override
	public String toString() {
		return String.format("InventoryItemCastMessage[piid: %d, x: %d, y: %d, token: %s]", playerItemId, x, y, token);
	}

	@Override
	public InventoryItemCastMessage createNewInstance(long accountId) {
		return new InventoryItemCastMessage(accountId, this);
	}
}

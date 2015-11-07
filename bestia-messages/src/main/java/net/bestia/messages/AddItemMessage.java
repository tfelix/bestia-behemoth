package net.bestia.messages;

/**
 * Workaround, internal message. In order to get into the ECS. When the message
 * routing gets updated and unified then this message can be removed.
 * 
 * @author Thomas
 *
 */
public class AddItemMessage extends InputMessage {

	private static final long serialVersionUID = 1L;

	public final static String MESSAGE_ID = "add.item";
	
	private String itemId;
	private int amount;
	
	public AddItemMessage(long accId, int pbid, String itemId, int amount) {
		super(accId, pbid);
		this.setItemId(itemId);
		this.setAmount(amount);
	}
	
	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getNullMessagePath();
	}

	public String getItemId() {
		return itemId;
	}

	public void setItemId(String itemId) {
		this.itemId = itemId;
	}

	public int getAmount() {
		return amount;
	}

	public void setAmount(int amount) {
		this.amount = amount;
	}
	
	

	
}

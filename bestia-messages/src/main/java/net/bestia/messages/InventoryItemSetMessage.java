package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sets the item shortcuts for the currently selected bestias. The shortcuts can
 * then be used to trigger fast item usage.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class InventoryItemSetMessage extends InputMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "inventory.item.set";

	@JsonProperty("s1")
	private int itemSlotId1;

	@JsonProperty("s2")
	private int itemSlotId2;

	@JsonProperty("s3")
	private int itemSlotId3;

	@JsonProperty("s4")
	private int itemSlotId4;

	@JsonProperty("s5")
	private int itemSlotId5;

	/**
	 * Ctor.
	 */
	public InventoryItemSetMessage() {
		// no op.
	}

	public int getItemSlotId1() {
		return itemSlotId1;
	}

	public void setItemSlotId1(int itemSlotId1) {
		this.itemSlotId1 = itemSlotId1;
	}

	public int getItemSlotId2() {
		return itemSlotId2;
	}

	public void setItemSlotId2(int itemSlotId2) {
		this.itemSlotId2 = itemSlotId2;
	}

	public int getItemSlotId3() {
		return itemSlotId3;
	}

	public void setItemSlotId3(int itemSlotId3) {
		this.itemSlotId3 = itemSlotId3;
	}

	public int getItemSlotId4() {
		return itemSlotId4;
	}

	public void setItemSlotId4(int itemSlotId4) {
		this.itemSlotId4 = itemSlotId4;
	}

	public int getItemSlotId5() {
		return itemSlotId5;
	}

	public void setItemSlotId5(int itemSlotId5) {
		this.itemSlotId5 = itemSlotId5;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getZoneMessagePath();
	}

}

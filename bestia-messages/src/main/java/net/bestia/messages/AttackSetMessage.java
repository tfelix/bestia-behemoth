package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sets the attacks of the currently active bestia.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AttackSetMessage extends InputMessage {

	@JsonIgnore
	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "attack.set";

	@JsonProperty("s1")
	private int atkSlotId1;

	@JsonProperty("s2")
	private int atkSlotId2;

	@JsonProperty("s3")
	private int atkSlotId3;

	@JsonProperty("s4")
	private int atkSlotId4;

	@JsonProperty("s5")
	private int atkSlotId5;

	public AttackSetMessage() {
		// no op.
	}
	
	public int getAtkSlotId1() {
		return atkSlotId1;
	}

	public void setAtkSlotId1(int atkSlotId1) {
		this.atkSlotId1 = atkSlotId1;
	}

	public int getAtkSlotId2() {
		return atkSlotId2;
	}

	public void setAtkSlotId2(int atkSlotId2) {
		this.atkSlotId2 = atkSlotId2;
	}

	public int getAtkSlotId3() {
		return atkSlotId3;
	}

	public void setAtkSlotId3(int atkSlotId3) {
		this.atkSlotId3 = atkSlotId3;
	}

	public int getAtkSlotId4() {
		return atkSlotId4;
	}

	public void setAtkSlotId4(int atkSlotId4) {
		this.atkSlotId4 = atkSlotId4;
	}

	public int getAtkSlotId5() {
		return atkSlotId5;
	}

	public void setAtkSlotId5(int atkSlotId5) {
		this.atkSlotId5 = atkSlotId5;
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
		return String.format("AttackSetMessage[slot1: %d, slot2: %d, slot3: %d, slot4: %d, slot5: %d]", atkSlotId1,
				atkSlotId2, atkSlotId3, atkSlotId4, atkSlotId5);
	}

}

package net.bestia.messages.attack;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;

/**
 * A message from the client to the server to use an attack.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AttackUseMessage extends JsonMessage {

	@JsonIgnore
	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "attack.use";

	@JsonProperty("aid")
	private int attackId;
	
	@JsonProperty("s")
	private int slot;
	
	private int x;
	private int y;
	
	@JsonProperty("tid")
	private String targetEntityId;

	public int getAttackId() {
		return attackId;
	}

	public void setAttackId(int attackId) {
		this.attackId = attackId;
	}

	public int getSlot() {
		return slot;
	}

	public void setSlot(int slot) {
		this.slot = slot;
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
	
	public String getTargetEntityId() {
		return targetEntityId;
	}
	
	public void setTargetEntityId(String targetEntityId) {
		this.targetEntityId = targetEntityId;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("AttackUseMessage[attackId: %d, x: %d, y: %d]", attackId, x, y);
	}
}

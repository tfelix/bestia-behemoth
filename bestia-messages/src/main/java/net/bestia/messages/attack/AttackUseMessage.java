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

	@JsonProperty("x")
	private long x;

	@JsonProperty("y")
	private long y;

	@JsonProperty("tid")
	private long targetEntityId;
	
	/**
	 * Priv. ctor. Used for jackson.
	 */
	protected AttackUseMessage() {
		// no op.
	}
	
	public AttackUseMessage(long accId) {
		super(accId);
		// no op.
	}

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

	public long getX() {
		return x;
	}

	public void setX(long x) {
		this.x = x;
	}

	public long getY() {
		return y;
	}

	public void setY(long y) {
		this.y = y;
	}

	public long getTargetEntityId() {
		return targetEntityId;
	}

	public void setTargetEntityId(long targetEntityId) {
		this.targetEntityId = targetEntityId;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("AttackUseMessage[attackId: %d, x: %d, y: %d, targetEid: %d]", attackId, x, y,
				targetEntityId);
	}

	@Override
	public AttackUseMessage createNewInstance(long accountId) {
		final AttackUseMessage msg = new AttackUseMessage(accountId);
		msg.attackId = this.attackId;
		msg.slot = this.slot;
		msg.targetEntityId = this.targetEntityId;
		msg.x = this.x;
		msg.y = this.y;
		return msg;
	}
}

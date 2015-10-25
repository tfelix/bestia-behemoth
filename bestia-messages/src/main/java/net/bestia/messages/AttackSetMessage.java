package net.bestia.messages;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sets the attacks of the currently active bestia.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AttackSetMessage extends Message {
	
	@JsonIgnore
	private static final long serialVersionUID = 1L;
	
	public static final String MESSAGE_ID = "attack.set";
	
	@JsonProperty("atkIds")
	private List<Integer> attacks = new ArrayList<>();
	
	public AttackSetMessage() {
		// no op.
	}
	
	public List<Integer> getAttacks() {
		return attacks;
	}
	
	public void setAttacks(List<Integer> attacks) {
		attacks.clear();
		attacks.addAll(attacks);
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
		return String.format("AttackSetMessage[attackIds: %s]", attacks.toString());
	}

}

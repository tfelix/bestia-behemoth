package net.bestia.messages;

import java.util.List;

import net.bestia.model.domain.AttackLevel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lists the current learned attacks of an bestia. The attacks are sorted in the
 * order of the minimum level in order to use them. The attacks of the currently
 * selected bestia are returned.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AttackListResponseMessage extends Message {

	@JsonIgnore
	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "attack.list.response";

	@JsonProperty("atks")
	private List<AttackLevel> attacks;
	
	public AttackListResponseMessage() {
		// no op.
	}
	
	public AttackListResponseMessage(Message message) {
		super(message);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getClientMessagePath();
	}

	@Override
	public String toString() {
		return String.format("AttackListResponseMessage[attacks: %s]", attacks.toString());
	}

	public List<AttackLevel> getAttacks() {
		return attacks;
	}

	public void setAttacks(List<AttackLevel> attacks) {
		this.attacks = attacks;
	}
}

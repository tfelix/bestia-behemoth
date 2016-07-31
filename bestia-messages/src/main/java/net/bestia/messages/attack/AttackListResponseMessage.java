package net.bestia.messages.attack;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.model.domain.BestiaAttack;

/**
 * Lists the current learned attacks of an bestia. The attacks are sorted in the
 * order of the minimum level in order to use them. The attacks of the currently
 * selected bestia are returned.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AttackListResponseMessage extends AccountMessage {

	@JsonIgnore
	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "attack.list.response";

	@JsonProperty("atks")
	private List<BestiaAttack> attacks;
	
	public AttackListResponseMessage() {
		// no op.
	}
	
	public AttackListResponseMessage(AccountMessage message) {
		super(message);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("AttackListResponseMessage[attacks: %s]", attacks.toString());
	}

	public List<BestiaAttack> getAttacks() {
		return attacks;
	}

	public void setAttacks(List<BestiaAttack> attacks) {
		this.attacks = attacks;
	}
}

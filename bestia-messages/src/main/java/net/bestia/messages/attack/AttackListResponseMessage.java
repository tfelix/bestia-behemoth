package net.bestia.messages.attack;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;
import net.bestia.model.domain.BestiaAttack;

/**
 * Lists the current learned attacks of an bestia. The attacks are sorted in the
 * order of the minimum level in order to use them. The attacks of the currently
 * selected bestia are returned.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AttackListResponseMessage extends JsonMessage {

	@JsonIgnore
	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "attack.list.response";

	@JsonProperty("atks")
	private List<BestiaAttack> attacks;
	
	/**
	 * Priv. ctor. This is needed for jackson.
	 */
	protected AttackListResponseMessage() {
		// no op.
	}
	
	public AttackListResponseMessage(long accId) {
		super(accId);
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
		this.attacks = Objects.requireNonNull(attacks);
	}

	@Override
	public AttackListResponseMessage createNewInstance(long accountId) {
		final AttackListResponseMessage msg = new AttackListResponseMessage(accountId);
		msg.attacks.addAll(this.attacks);
		return msg;
	}
}

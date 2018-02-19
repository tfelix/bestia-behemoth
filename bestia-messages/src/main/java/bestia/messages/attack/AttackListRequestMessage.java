package bestia.messages.attack;

import com.fasterxml.jackson.annotation.JsonIgnore;

import bestia.messages.JsonMessage;

/**
 * Lists the current learned attacks of an bestia. The attacks are sorted in the
 * order of the minimum level in order to use them. The attacks of the currently
 * selected bestia are returned.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AttackListRequestMessage extends JsonMessage {
	
	public AttackListRequestMessage(long accId) {
		super(accId);
		// no op.
	}

	@JsonIgnore
	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "attack.list.request";
	
	/**
	 * Needed for MessageTypeIdResolver 
	 */
	private AttackListRequestMessage() {
		super(0);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("AttackListRequestMessage[accId: %d]", getAccountId());
	}

	@Override
	public AttackListRequestMessage createNewInstance(long accountId) {
		return new AttackListRequestMessage(accountId);
	}
}

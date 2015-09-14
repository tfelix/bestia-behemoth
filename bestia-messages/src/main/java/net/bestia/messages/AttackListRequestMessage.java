package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Lists the current learned attacks of an bestia. The attacks are sorted in the
 * order of the minimum level in order to use them. The attacks of the currently
 * selected bestia are returned.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AttackListRequestMessage extends Message {

	@JsonIgnore
	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "attack.list.request";

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
		return "AttackListRequestMessage[]";
	}
}

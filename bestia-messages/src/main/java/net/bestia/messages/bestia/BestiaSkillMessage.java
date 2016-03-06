package net.bestia.messages.bestia;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.InputMessage;

/**
 * This message is used if the bestia uses a skill. The skill can have a target
 * (also the own bestia can be a target).
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaSkillMessage extends InputMessage {

	private static final long serialVersionUID = 1L;
	
	public static final String MESSAGE_ID = "bestia.skill";
	
	@JsonProperty("aid")
	private int attackId;
	
	/**
	 * Id of the target.
	 */
	@JsonProperty("uuid")
	private String targetUuid;

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getZoneMessagePath();
	}

}

package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Client sends this message if it wants to switch to another active bestia. This bestia from now on is responsible for
 * gathering all visual information. And the client will get updated about these data.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaActivateMessage extends Message {

	private static final long serialVersionUID = 1L;
	public final static String MESSAGE_ID = "bestia.activate";

	@JsonProperty("pbid")
	private int activatePlayerBestiaId;

	/**
	 * Ctor.
	 */
	public BestiaActivateMessage() {

	}

	/**
	 * Ctor. Will create a copy of the given message.
	 * 
	 * @param message
	 *            Message to copy its parameter from.
	 */
	public BestiaActivateMessage(BestiaActivateMessage message) {
		super(message);
		
		activatePlayerBestiaId = message.getActivatePlayerBestiaId();
	}

	public int getActivatePlayerBestiaId() {
		return activatePlayerBestiaId;
	}

	public void setActivatePlayerBestiaId(int activatePlayerBestiaId) {
		this.activatePlayerBestiaId = activatePlayerBestiaId;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getZoneMessagePath();
	}

}

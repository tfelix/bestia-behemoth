package net.bestia.core.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Requests a login from the bestia server. This message must be the 
 * first message which is sent zo the server or the user gets
 * disconnected.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class RequestLoginMessage extends Message {

	private final String messageId = "req.login";
	
	@JsonProperty("accident")
	private String accountIdentifier;
	@JsonProperty("t")
	private String passwordToken;
	@JsonIgnore
	private String uuid;
	
	/**
	 * Ctor.
	 */
	public RequestLoginMessage() {
		// no op.
	}

	@Override
	public String getMessageId() {
		return messageId;
	}

	public String getPasswordToken() {
		return passwordToken;
	}

	public void setPasswordToken(String passwordToken) {
		this.passwordToken = passwordToken;
	}

	public String getAccountIdentifier() {
		return accountIdentifier;
	}

	public void setAccoundIdentifier(String accountIdentifier) {
		this.accountIdentifier = accountIdentifier;
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

}

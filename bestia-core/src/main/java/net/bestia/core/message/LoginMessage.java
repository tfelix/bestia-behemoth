package net.bestia.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Message from a client to request a login.
 * 
 * @author Thomas
 *
 */
public class LoginMessage extends Message {
	
	public enum LoginStatus {
		ERROR,
		SUCCESS
	}

	public static final String MESSAGE_ID = "login";

	@JsonProperty("s")
	private LoginStatus status;
	@JsonProperty("t")
	private String token;

	public LoginMessage() {
		setStatus(LoginStatus.ERROR);
		setToken("");
	}

	public LoginMessage(int accountId, LoginStatus status, String token) {
		setStatus(status);
		setToken(token);
		setAccountId(accountId);
	}

	/**
	 * @return the status
	 */
	public LoginStatus getStatus() {
		return status;
	}

	/**
	 * @param status
	 *            the status to set
	 */
	public void setStatus(LoginStatus status) {
		this.status = status;
	}

	/**
	 * @return the token
	 */
	public String getToken() {
		return token;
	}

	/**
	 * @param token
	 *            the token to set
	 */
	public void setToken(String token) {
		this.token = token;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("LoginMsg: [account id: {}, status: {}, token: {}]", getAccountId(), status, token);
	}
}

package net.bestia.core.message;

import net.bestia.core.game.model.Account;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoginMessage extends Message {
	
	public enum LoginStatus {
		ERROR,
		SUCCESS
	}

	@JsonProperty("s")
	private LoginStatus status;
	@JsonProperty("t")
	private String token;

	public LoginMessage() {
		setStatus(LoginStatus.ERROR);
		setToken("");
	}

	public LoginMessage(Account account, LoginStatus status, String token) {
		setStatus(status);
		setToken(token);
		setAccountId(account.getId());
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
		return "system.login";
	}

	@Override
	public String toString() {
		return String.format("LoginMsg: [account id: {}, status: {}, token: {}]", getAccountId(), status, token);
	}
}

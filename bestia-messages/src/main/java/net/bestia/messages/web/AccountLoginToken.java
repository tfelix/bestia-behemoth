package net.bestia.messages.web;

/**
 * Used as a HTTP response to send login response data as JSON to a successful
 * logged in account.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AccountLoginToken {

	private String token;
	private String username;
	private long accId;

	/**
	 * Ctor.
	 * 
	 * @param accId
	 *            The logged in account id.
	 * @param username
	 *            The username of the account.
	 * @param token
	 *            The generated login token.
	 */
	public AccountLoginToken(long accId, String username, String token) {
		this.accId = accId;
		this.username = username;
		this.token = token;
	}

	public long getAccId() {
		return accId;
	}

	public String getUsername() {
		return username;
	}

	public String getToken() {
		return token;
	}
}

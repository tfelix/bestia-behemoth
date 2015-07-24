package net.bestia.loginserver.rest.response;

/**
 * Used as a response to send login response data to a successful logged in account.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AccountLoginResponse {

	private String token;
	private String username;
	private long accId;

	public AccountLoginResponse(long accId, String username, String token) {
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

package net.bestia.messages.account;

import java.io.Serializable;

/**
 * Sends a request to login a account and thus create a new login token.
 * 
 * @author Thomas Felix
 *
 */
public final class AccountLoginRequest implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String username;
	private final String password;
	private final long accountId;
	private final String token;

	public AccountLoginRequest(String username, String password) {
		this.username = username;
		this.password = password;

		this.accountId = 0;
		this.token = "";
	}

	private AccountLoginRequest(String username, String password, long accId, String token) {
		this.username = username;
		this.password = password;

		this.accountId = accId;
		this.token = token;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public long getAccountId() {
		return accountId;
	}

	public String getToken() {
		return token;
	}

	public AccountLoginRequest success(long accountId, String token) {
		return new AccountLoginRequest(username, "", accountId, token);
	}

	/**
	 * Empty token is send back.
	 * 
	 * @return
	 */
	public AccountLoginRequest fail() {
		return new AccountLoginRequest(username, "");
	}

	@Override
	public String toString() {
		return String.format("AccountLoginReq[username: %s]", username);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((password == null) ? 0 : password.hashCode());
		result = prime * result + ((username == null) ? 0 : username.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AccountLoginRequest other = (AccountLoginRequest) obj;
		if (password == null) {
			if (other.password != null)
				return false;
		} else if (!password.equals(other.password))
			return false;
		if (username == null) {
			if (other.username != null)
				return false;
		} else if (!username.equals(other.username))
			return false;
		return true;
	}
}

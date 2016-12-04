package net.bestia.webserver.model;

import java.util.Objects;

/**
 * Small helper class to hold the account credential information send to the
 * client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class AccountCreds {

	private final String token;
	private final long accId;
	private final String masterName;

	public AccountCreds(long accId, String token, String masterName) {

		this.accId = accId;
		this.token = Objects.requireNonNull(token);
		this.masterName = Objects.requireNonNull(masterName);
	}

	public String getToken() {
		return token;
	}

	public long getAccId() {
		return accId;
	}

	public String getMasterName() {
		return masterName;
	}
}

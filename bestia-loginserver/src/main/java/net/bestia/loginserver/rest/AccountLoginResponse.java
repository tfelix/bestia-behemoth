package net.bestia.loginserver.rest;

public class AccountLoginResponse {

	public String token;
	public String username;
	public long accId;
	
	public AccountLoginResponse(long accId, String username, String token) {
		this.accId = accId;
		this.token = token;
		this.username = username;
	}
}

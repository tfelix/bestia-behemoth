package net.bestia.loginserver.rest;

public class AccountLoginResponse {

	public String token;
	public long accId;
	
	public AccountLoginResponse(long accId, String token) {
		this.accId = accId;
		this.token = token;
	}
}

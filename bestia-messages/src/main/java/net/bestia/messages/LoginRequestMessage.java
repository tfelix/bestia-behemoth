package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoginRequestMessage {
	
	@JsonProperty("accId")
	private long accountId;
	
	@JsonProperty("token")
	private String token;
	
	public LoginRequestMessage() {
		
	}
	
	public long getAccountId() {
		return accountId;
	}
	
	public String getToken() {
		return token;
	}

}

package net.bestia.messages;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoginRequestMessage implements Serializable {
	
	private static final long serialVersionUID = 1L;

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

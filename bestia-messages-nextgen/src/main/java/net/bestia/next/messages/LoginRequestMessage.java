package net.bestia.next.messages;

public class LoginRequestMessage {
	
	private long accountId;
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

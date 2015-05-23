package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.model.Account;

public class AccountInfoMessage extends Message {

	private final static String messageId = "acc.info";
	
	@JsonProperty("acc")
	private Account account;
	
	public AccountInfoMessage() {
		
	}
	
	
	@Override
	public String getMessageId() {
		return messageId;
	}
	
	@Override
	public String toString() {
		return String.format("AccountInfoMessage[account: {0}]", account.toString());
	}

}

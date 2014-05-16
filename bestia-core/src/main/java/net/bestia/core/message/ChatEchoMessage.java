package net.bestia.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChatEchoMessage extends Message {
	
	public enum EchoCode {
		OK,
		ERROR,
		RECEIVER_UNKNOWN //< Receiver offline or unknown to the server or blocked.	
	}
	
	@JsonProperty("ec")
	private EchoCode echoCode;
	@JsonProperty("txt")
	private String text;
	@JsonProperty("cmid")
	private int chatMessageId;
	
	/**
	 * Creates a ChatEchoMessage from a incoming ChatMessage. Please note
	 * that the ChatMessage must be fully initialized in order to use this
	 * method. So account fields and message id must be set. Otherwise an exception
	 * is thrown.
	 * 
	 * @param msg
	 * @return
	 */
	public static ChatEchoMessage getEchoMessage(ChatMessage msg) {
		if(msg.getAccountId() == 0) {
			throw new IllegalArgumentException("AccountId can not be null.");
		}
		if(msg.getChatMessageId() == 0) {
			throw new IllegalArgumentException("MessageId can not be null.");
		}
		
		ChatEchoMessage cem = new ChatEchoMessage();
		cem.setAccountId(msg.getAccountId());
		cem.setChatMessageId(msg.getChatMessageId());
		
		return cem;
	}

	public EchoCode getEchoCode() {
		return echoCode;
	}

	public void setEchoCode(EchoCode echoCode) {
		this.echoCode = echoCode;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public int getChatMessageId() {
		return chatMessageId;
	}

	private void setChatMessageId(int chatMessageId) {
		this.chatMessageId = chatMessageId;
	}

	@Override
	public String getMessageId() {
		return "chat.echo";
	}
}

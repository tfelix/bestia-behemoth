package net.bestia.messages.chat;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;

public class ChatEchoMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;

	public enum EchoCode {
		OK, ERROR, RECEIVER_UNKNOWN // < Receiver offline or unknown to the
									// server or blocked.
	}

	@JsonProperty("ec")
	private EchoCode echoCode;

	@JsonProperty("txt")
	private String text;

	@JsonProperty("cmid")
	private int chatMessageId;
	
	/**
	 * Needed for MessageTypeIdResolver 
	 */
	private ChatEchoMessage() {
		super(0);
	}

	private ChatEchoMessage(ChatMessage rhs) {
		super(rhs.getAccountId());

		this.echoCode = EchoCode.OK;
		this.text = rhs.getText();
		this.chatMessageId = rhs.getChatMessageId();
	}

	public ChatEchoMessage(long accId, EchoCode code, String txt, int chatMsgId) {
		super(accId);

		this.echoCode = Objects.requireNonNull(code);
		this.text = Objects.requireNonNull(txt);
		this.chatMessageId = chatMsgId;
	}

	/**
	 * Creates a ChatEchoMessage from a incoming ChatMessage. Please note that
	 * the ChatMessage must be fully initialized in order to use this method. So
	 * account fields and message id must be set. Otherwise an exception is
	 * thrown.
	 * 
	 * @param msg
	 * @return
	 */
	public static ChatEchoMessage getEchoMessage(ChatMessage msg) {

		return new ChatEchoMessage(msg);
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

	@Override
	public String getMessageId() {
		return "chat.echo";
	}

	@Override
	public ChatEchoMessage createNewInstance(long accountId) {
		return new ChatEchoMessage(accountId, this.echoCode, this.text, this.chatMessageId);
	}
}

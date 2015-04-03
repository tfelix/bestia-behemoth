package net.bestia.core.message;

import net.bestia.core.game.model.Account;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Chatmessage is sent from the user to the server and vice versa.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ChatMessage extends Message {

	public final static String MESSAGE_ID = "chat.message";
	
	public enum Mode {
		PUBLIC,
		PARTY,
		GUILD,
		WHISPER,
		SYSTEM,
		GM_BROADCAST,
		ERROR,
		COMMAND
	}
	
	@JsonProperty("m")
	private Mode chatMode;
	@JsonProperty("txt")
	private String text;
	@JsonProperty("sn")
	private String senderNickname;
	@JsonProperty("rxn")
	private String receiverNickname;
	@JsonProperty("cmid")
	private int chatMessageId;
	@JsonProperty("t")
	private long time;
	
	/**
	 * Std. Ctor
	 * So the Jason Library can create this object.
	 */
	public ChatMessage() {
		// no op.
	}
	
	public static ChatMessage getSystemMessage(Account account, String text) {
		ChatMessage msg =  new ChatMessage();
		msg.setAccountId(account.getId());
		msg.setText(text);
		msg.setTime(System.currentTimeMillis() / 1000L);
		msg.setChatMode(Mode.SYSTEM);
		return msg;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	public Mode getChatMode() {
		return chatMode;
	}

	public void setChatMode(Mode chatMode) {
		this.chatMode = chatMode;
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

	public void setChatMessageId(int chatMessageId) {
		this.chatMessageId = chatMessageId;
	}

	public long getTime() {
		return time;
	}

	public void setTime(long time) {
		this.time = time;
	}
}

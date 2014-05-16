package net.bestia.core.message;

import net.bestia.core.game.model.Account;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;

/**
 * Chatmessage is sent from the user to the server and vice versa.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ChatMessage extends Message {

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
	@JsonProperty("rxn")
	private String receiverNickname;
	@JsonProperty("cmid")
	private int chatMessageId;
	@JsonProperty("t")
	private long time;
	
	public ChatMessage() {
		
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
		return "chat.message";
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

	public String getReceiverNickname() {
		return receiverNickname;
	}

	public void setReceiverNickname(String receiverNickname) {
		this.receiverNickname = receiverNickname;
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

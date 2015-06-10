package net.bestia.messages;

import net.bestia.model.domain.Account;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Chatmessage is sent from the user to the server and vice versa.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ChatMessage extends Message {

	private static final long serialVersionUID = 1L;
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
	
	@JsonIgnore
	private String messagePath;
	
	/**
	 * Std. Ctor
	 * So the Jason Library can create this object.
	 */
	public ChatMessage() {
		messagePath = getServerMessagePath();
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

	@Override
	public String getMessagePath() {
		return messagePath;
	}

	/**
	 * 
	 * @param receiverId
	 * @return
	 */
	public ChatMessage getForwardMessage(long receiverId) {
		ChatMessage forwardMsg = new ChatMessage();
		
		forwardMsg.setAccountId(getAccountId());
		forwardMsg.setChatMessageId(chatMessageId);
		forwardMsg.setChatMode(chatMode);
		forwardMsg.setText(text);
		forwardMsg.setTime(time);
		forwardMsg.messagePath = String.format("account/%d", receiverId);
		
		return forwardMsg;
	}
}

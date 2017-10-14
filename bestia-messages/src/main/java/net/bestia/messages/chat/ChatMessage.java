package net.bestia.messages.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.EntityJsonMessage;
import net.bestia.model.I18n;
import net.bestia.model.domain.Account;

/**
 * Chatmessage is sent from the user to the server and vice versa.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage extends EntityJsonMessage {

	private static final long serialVersionUID = 1L;

	public final static String MESSAGE_ID = "chat.message";

	public enum Mode {
		PUBLIC, PARTY, GUILD, WHISPER, SYSTEM, GM_BROADCAST, ERROR, COMMAND, BATTLE
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
	 * Needed for MessageTypeIdResolver
	 */
	private ChatMessage() {
		super(0, 0);
	}

	public ChatMessage(long accId,
			long entityId,
			String message,
			Mode mode) {
		super(accId, entityId);

		this.chatMode = mode;
		this.text = message;
		setTime(System.currentTimeMillis() / 1000L);
	}

	public ChatMessage(long newAccountId, long entityId, ChatMessage chat) {
		super(newAccountId, entityId);

		this.chatMessageId = chat.chatMessageId;
		this.chatMode = chat.chatMode;
		this.receiverNickname = chat.receiverNickname;
		this.senderNickname = chat.senderNickname;
		this.text = chat.text;
		this.time = chat.time;
	}

	public ChatMessage(long accId, String text, Mode chatMode) {
		super(accId, 0);

		this.text = text;
		this.chatMode = chatMode;
		this.time = System.currentTimeMillis() / 1000L;
	}

	public static ChatMessage getSystemMessage(Account account, String translationKey, Object... args) {

		final String text = I18n.t(account, translationKey, args);

		ChatMessage msg = new ChatMessage(account.getId(), text, Mode.SYSTEM);
		return msg;
	}

	/**
	 * Creates a new chat message in the mode as a system message.
	 * 
	 * @param account
	 *            A account to receive the message.
	 * @param text
	 *            A text to send to the client.
	 * @return The generated message.
	 */
	public static ChatMessage getSystemMessage(long accId, String text) {

		final ChatMessage msg = new ChatMessage(accId, 0, text, Mode.SYSTEM);
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

	public void setSenderNickname(String senderNickname) {
		this.senderNickname = senderNickname;
	}

	public String getReceiverNickname() {
		return receiverNickname;
	}

	@Override
	public String toString() {
		return String.format("ChatMessage[mode: %s, txNick: %s, rxNick: %s, txt: %s, time: %d]",
				chatMode, senderNickname, receiverNickname, text, time);
	}

	@Override
	public ChatMessage createNewInstance(long accountId) {
		return new ChatMessage(accountId, getEntityId(), this);
	}
}

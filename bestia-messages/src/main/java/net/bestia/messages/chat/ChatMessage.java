package net.bestia.messages.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;
import net.bestia.model.I18n;
import net.bestia.model.domain.Account;

/**
 * Chatmessage is sent from the user to the server and vice versa.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage extends JsonMessage {

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
	 * Std. Ctor So the Jason Library can create this object.
	 */
	public ChatMessage() {
		// no op.
	}

	public ChatMessage(long accId, String message, Mode mode) {
		super(accId);

		this.chatMode = mode;
		this.text = message;
		setTime(System.currentTimeMillis() / 1000L);
	}

	public static ChatMessage getSystemMessage(Account account, String translationKey, Object... args) {

		final String text = I18n.t(account, translationKey, args);

		ChatMessage msg = new ChatMessage();
		msg.setText(text);
		msg.setTime(System.currentTimeMillis() / 1000L);
		msg.setChatMode(Mode.SYSTEM);
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

		final ChatMessage msg = new ChatMessage();
		msg.setText(text);
		msg.setTime(System.currentTimeMillis() / 1000L);
		msg.setChatMode(Mode.SYSTEM);
		msg.setAccountId(accId);

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

	/**
	 * This message has a different path so it gets delivered back to the client
	 * given in the receiver account id.
	 * 
	 * @param receiverAccountId
	 * @param msg
	 * @return
	 */
	public static ChatMessage getEchoMessage(long receiverAccountId, ChatMessage msg) {
		final ChatMessage forwardMsg = new ChatMessage();

		forwardMsg.senderNickname = msg.senderNickname;
		forwardMsg.receiverNickname = msg.receiverNickname;

		forwardMsg.setAccountId(receiverAccountId);
		forwardMsg.setChatMessageId(msg.chatMessageId);
		forwardMsg.setChatMode(msg.chatMode);
		forwardMsg.setText(msg.text);
		forwardMsg.setTime(msg.time);

		return forwardMsg;
	}

	public static ChatMessage getEchoRawMessage(long receiverAccoundId, String text) {
		final ChatMessage forwardMsg = new ChatMessage();

		forwardMsg.setAccountId(receiverAccoundId);
		forwardMsg.setChatMode(Mode.SYSTEM);
		forwardMsg.setText(text);

		return forwardMsg;
	}
}

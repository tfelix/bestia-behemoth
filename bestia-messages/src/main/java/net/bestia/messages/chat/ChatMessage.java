package net.bestia.messages.chat;

import net.bestia.messages.Message;
import net.bestia.model.I18n;
import net.bestia.model.domain.Account;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Chatmessage is sent from the user to the server and vice versa.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ChatMessage extends Message {

	public static class ReplyChatMessage extends ChatMessage {

		private static final long serialVersionUID = 1L;

		public ReplyChatMessage() {
			// no op.
		}
	}

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

	public static ChatMessage getSystemMessage(Account account, String translationKey, Object... args) {

		final String text = I18n.t(account, translationKey, args);

		ChatMessage msg = new ChatMessage();
		msg.setText(text);
		msg.setTime(System.currentTimeMillis() / 1000L);
		msg.setChatMode(Mode.SYSTEM);
		return msg;
	}

	/**
	 * @param account
	 * @param text
	 * @return
	 */
	public static ChatMessage getSystemMessage(Account account, String text) {

		final ChatMessage msg = new ReplyChatMessage();
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

	public void setSenderNickname(String senderNickname) {
		this.senderNickname = senderNickname;
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
		final ChatMessage forwardMsg = new ReplyChatMessage();

		forwardMsg.senderNickname = msg.senderNickname;
		forwardMsg.receiverNickname = msg.receiverNickname;

		forwardMsg.setChatMessageId(msg.chatMessageId);
		forwardMsg.setChatMode(msg.chatMode);
		forwardMsg.setText(msg.text);
		forwardMsg.setTime(msg.time);

		return forwardMsg;
	}

	public static ChatMessage getEchoRawMessage(long receiverAccoundId, String text) {
		final ChatMessage forwardMsg = new ReplyChatMessage();

		forwardMsg.setChatMode(Mode.SYSTEM);
		forwardMsg.setText(text);

		return forwardMsg;
	}
}

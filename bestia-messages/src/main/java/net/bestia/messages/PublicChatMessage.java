package net.bestia.messages;

/**
 * Wrapper class to "hide" a public chat message as an InputMessage for forwarding to the ECS. Maybe this class can be
 * generalized if more conversions then this special case have to be done later.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PublicChatMessage extends InputMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "chat.public";

	private final ChatMessage chatMsg;

	public PublicChatMessage(ChatMessage msg, int playerBestiaId) {
		super(msg, playerBestiaId);

		if (msg.getChatMode() != ChatMessage.Mode.PUBLIC) {
			throw new IllegalArgumentException("Chat was not of type public!");
		}

		this.chatMsg = msg;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getZoneMessagePath();
	}

	/**
	 * Returns the wrapped chat message.
	 * 
	 * @return The wrapped chat message.
	 */
	public ChatMessage getChatMessage() {
		return chatMsg;
	}

}

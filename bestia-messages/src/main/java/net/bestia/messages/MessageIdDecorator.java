package net.bestia.messages;

/**
 * Messages can be decorated with this class in order to change their ID to a
 * new one. Might be needed when messages must be re-routed for different
 * endpoints like the chat message for example.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 * @param <T>
 */
public class MessageIdDecorator<T extends Message> extends AccountMessage {

	private static final long serialVersionUID = 1L;

	private final T message;
	private final String messageId;

	public MessageIdDecorator() {
		message = null;
		messageId = "";
	}

	public MessageIdDecorator(T msg, String messageId) {
		if (msg == null) {
			throw new IllegalArgumentException("Message can not be null.");
		}
		this.message = msg;
		this.messageId = messageId;
	}

	@Override
	public String getMessageId() {
		return messageId;
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
	public T getMessage() {
		return message;
	}
}

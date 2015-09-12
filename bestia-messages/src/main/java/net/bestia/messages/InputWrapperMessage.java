package net.bestia.messages;

// TODO kommentiern.
public class InputWrapperMessage<T extends Message> extends InputMessage {

	private static final long serialVersionUID = 1L;

	private static final String MESSAGE_ID = "inputwrapper";
	
	private final T message;
	
	public InputWrapperMessage() {
		message = null;
	}

	public InputWrapperMessage(T msg, int playerBestiaId) {
		super(msg, playerBestiaId);

		this.message = msg;
	}

	@Override
	public String getMessageId() {	
		return (message == null) ? MESSAGE_ID : message.getMessageId();
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

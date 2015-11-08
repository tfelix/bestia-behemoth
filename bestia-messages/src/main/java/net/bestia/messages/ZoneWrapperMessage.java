package net.bestia.messages;

import java.util.HashSet;
import java.util.Set;

/**
 * This message wrapper can be used to direct messages directly to zones. Just
 * include one or multiple zone names (the name of the map) the zone is
 * responsible for and the zone will get notified about this message by a
 * specialized message filter in the router.
 * 
 * @author Thomas
 *
 * @param <T>
 */
public class ZoneWrapperMessage<T extends Message> extends Message {

	private static final long serialVersionUID = 1L;

	private static final String MESSAGE_ID = "zonewrapper";

	private final T message;
	
	private Set<String> receiverZones = new HashSet<>();

	public ZoneWrapperMessage() {
		message = null;
	}

	public ZoneWrapperMessage(T msg) {
		super(msg);

		this.message = msg;
	}
	
	public void addReceiverZone(String name) {
		receiverZones.add(name);
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

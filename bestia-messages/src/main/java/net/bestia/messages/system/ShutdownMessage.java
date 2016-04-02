package net.bestia.messages.system;

import net.bestia.messages.ZoneserverMessage;

/**
 * A message which will upon receiving shut down the server.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ShutdownMessage extends ZoneserverMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "system.shutdown";

	private String messagePath = null;
	
	/**
	 * Ctd. Ctor.
	 */
	public ShutdownMessage() {
		
	}

	public ShutdownMessage(String receiverServerName, String senderServerName) {
		super(receiverServerName, senderServerName);
	}

	/**
	 * Creates a {@link ShutdownMessage} which is directed to ALL zoneservers to
	 * shut them all down.
	 * 
	 * @return A shutdown message which is broadcasted to all server.
	 */
	public static ShutdownMessage getShutdownBroadcast() {
		// Create a new message but set the message path to the broadcast path.
		final ShutdownMessage msg = new ShutdownMessage("broadcast", "behemoth");
		msg.messagePath = msg.getServerBroadcastPath();

		return msg;
	}

	@Override
	public String getMessagePath() {
		if (messagePath != null) {
			return messagePath;
		} else {
			return super.getMessagePath();
		}
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

}

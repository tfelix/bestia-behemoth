package net.bestia.messages;

/**
 * Children of this message class are directed towards a special zone server.
 * These messages are used as inter-server messages. They will not be send to
 * the clients.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class ZoneserverMessage extends Message {

	private static final long serialVersionUID = 1L;

	private final static String MSG_PATH_ZONE_NAME = "zone.%s";

	private String receiverServer = "";
	private String senderServer = "";

	public ZoneserverMessage() {
		// no op.
	}

	/**
	 * 
	 * @param receiverServerName
	 *            Server to which this message is directed.
	 * @param senderServerName
	 *            The name of the server which send the message.
	 */
	public ZoneserverMessage(String receiverServerName, String senderServerName) {
		this.receiverServer = receiverServerName;
		this.senderServer = senderServerName;
	}

	@Override
	public String getMessagePath() {
		return getZonePath(receiverServer);
	}

	@Override
	public String toString() {
		return String.format("ZoneserverMessage[path: %s]", getMessageId());
	}

	public String getReceiverServer() {
		return receiverServer;
	}

	public String getSenderServer() {
		return senderServer;
	}

	/**
	 * Helper method to get a properly formatted zone path (useful for
	 * registering).
	 * 
	 * @param zoneName
	 * @return
	 */
	public static String getZonePath(String zoneName) {
		return String.format(MSG_PATH_ZONE_NAME, zoneName);
	}
}

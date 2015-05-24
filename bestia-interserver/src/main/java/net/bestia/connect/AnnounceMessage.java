package net.bestia.connect;

import java.io.Serializable;

abstract class AnnounceMessage implements Serializable {

	private static final long serialVersionUID = 2015052301L;

	/**
	 * This determines the type of the announce message. If something goes wrong and the interserver does deny a
	 * connection of the newly server the reply will be a REPLY_DENY type otherwise a REPLY_ACK. This is especially
	 * important for zones to prevent two zones from joining the network which are responsible for the same maps. This
	 * is effectively checked by the interserver.
	 *
	 */
	public static enum Type {
		REQUEST, REPLY_ACK, REPLY_DENY
	}

	private final Type type;
	private final String subscribeUrl;
	private final String name;

	public AnnounceMessage(Type type, String name, String subscribeUrl) {
		this.type = type;
		this.name = name;
		this.subscribeUrl = subscribeUrl;
	}

	/**
	 * Name of the server who sends the request.
	 * 
	 * @return
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the type of the AnnounceMessage.
	 * 
	 * @return Type of the AnnounceMessage.
	 */
	public Type getType() {
		return type;
	}

	/**
	 * Gets the subscribe URL.
	 * 
	 * @return The url to which the receiver of this message should subscribe.
	 */
	public String getSubscribeUrl() {
		return subscribeUrl;
	}

	@Override
	public String toString() {
		return String.format("AnnounceMessage[type: %s, name: %s, subURL: %s]", type.toString(), name, subscribeUrl);
	}
}

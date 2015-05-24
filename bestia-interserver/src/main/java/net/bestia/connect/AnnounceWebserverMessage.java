package net.bestia.connect;

/**
 * Message class to announce a new webserver to the interserver.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
class AnnounceWebserverMessage extends AnnounceMessage {

	private static final long serialVersionUID = 2015052301L;

	public AnnounceWebserverMessage(Type type, String name, String subscribeUrl) {
		super(type, name, subscribeUrl);
		// no op.
	}

	/**
	 * Returns a AnnounceMessage of type request to send to the the interserver as a new zone or webserver which wants
	 * to connect to the interserver.
	 * 
	 * @return
	 */
	public static AnnounceWebserverMessage getRequestMessage(String name, String subscribeUrl) {
		return new AnnounceWebserverMessage(Type.REQUEST, name, subscribeUrl);
	}
	
	public static AnnounceWebserverMessage getReplyMessage(String name, String subscribeUrl) {
		return new AnnounceWebserverMessage(Type.REPLY_ACK, name, subscribeUrl);
	}

}

package net.bestia.messages;

/**
 * Client sends this message if it wants to switch to another active bestia. This bestia from now on is responsible for
 * gathering all visual information. And the client will get updated about these data.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaActivateMessage extends InputMessage {

	private static final long serialVersionUID = 1L;
	public final static String MESSAGE_ID = "bestia.activate";

	/**
	 * Ctor.
	 */
	public BestiaActivateMessage() {

	}
	
	public BestiaActivateMessage(Message msg, int playerBestiaId) {
		super(msg, playerBestiaId);
		// no op.
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getZoneMessagePath();
	}
	
	public static BestiaActivateMessage getClientActivateMessage(Message msg, int playerBestiaId) {
		final BestiaActivateMessage responseMsg = new BestiaActivateMessage(msg, playerBestiaId);
		return responseMsg;
	}

}

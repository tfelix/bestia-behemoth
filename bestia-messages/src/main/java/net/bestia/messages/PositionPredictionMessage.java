package net.bestia.messages;

/**
 * This message will get send in advance, as soon as the server knows a path of
 * the bestia. The information will get send to the client so the client can
 * perform a movement interpolation to avoid lags.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PositionPredictionMessage extends AccountMessage {
	

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "entity.predict";

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getClientMessagePath();
	}

	
}

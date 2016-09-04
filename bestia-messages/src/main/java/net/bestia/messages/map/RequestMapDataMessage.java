package net.bestia.messages.map;

import net.bestia.messages.AccountMessage;

/**
 * Asks the server to send the data of the map in the current viewport (with
 * some additional extra data to buffer some movements).
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class RequestMapDataMessage extends AccountMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "map.requestdata";

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}

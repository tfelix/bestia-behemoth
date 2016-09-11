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
	
	private int patchX;
	private int patchY;
	
	public RequestMapDataMessage() {
		this.patchX = 0;
		this.patchY = 0;
	}
	
	public RequestMapDataMessage(int x, int y) {
		this.patchX = x;
		this.patchY = y;
	}
	
	public int getPatchX() {
		return patchX;
	}
	
	public int getPatchY() {
		return patchY;
	}
	
	@Override
	public String toString() {
		return String.format("RequestMapDataMessage[patchX: %d, patchY: %d]", patchX, patchY);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}

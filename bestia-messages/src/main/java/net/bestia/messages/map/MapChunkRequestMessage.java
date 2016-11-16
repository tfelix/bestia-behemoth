package net.bestia.messages.map;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JacksonMessage;

/**
 * Asks the server to send the data of the map in the current viewport (with
 * some additional extra data to buffer some movements).
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapChunkRequestMessage extends JacksonMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "map.requestdata";
	
	@JsonProperty("x")
	private int patchX;
	
	@JsonProperty("y")
	private int patchY;
	
	public MapChunkRequestMessage() {
		this.patchX = 0;
		this.patchY = 0;
	}
	
	public MapChunkRequestMessage(int x, int y) {
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

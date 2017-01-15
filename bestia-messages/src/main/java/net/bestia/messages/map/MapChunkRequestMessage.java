package net.bestia.messages.map;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;
import net.bestia.model.geometry.Point;

/**
 * Asks the server to send the data of the map in the current viewport (with
 * some additional extra data to buffer some movements).
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapChunkRequestMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "map.requestdata";

	@JsonProperty("c")
	private List<Point> chunks = new ArrayList<>();

	public MapChunkRequestMessage() {
		// no op.
	}

	/**
	 * A list of chunks which are requested from the client. The server checks
	 * if the chunks are eligible to send to the client before sending them.
	 * 
	 * @return The list of chunk coordinates.
	 */
	public List<Point> getChunks() {
		return chunks;
	}

	@Override
	public String toString() {
		return String.format("RequestMapDataMessage[patches: %s]", chunks.toString());
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}

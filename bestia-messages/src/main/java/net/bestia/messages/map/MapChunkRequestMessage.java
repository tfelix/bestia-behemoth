package net.bestia.messages.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.MessageId;
import net.bestia.model.geometry.Point;

/**
 * Asks the server to send the data of the map in the current viewport (with
 * some additional extra data to buffer some movements).
 * 
 * @author Thomas Felix
 *
 */
public class MapChunkRequestMessage extends AccountMessage implements MessageId {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "map.requestdata";

	@JsonProperty("c")
	private final List<Point> chunks = new ArrayList<>();

	/**
	 * Needed for MessageTypeIdResolver 
	 */
	private MapChunkRequestMessage() {
		super(0);
	}

	public MapChunkRequestMessage(long accId, List<Point> chunks) {
		super(accId);

		this.chunks.addAll(Objects.requireNonNull(chunks));
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

	@Override
	public MapChunkRequestMessage createNewInstance(long accountId) {
		return new MapChunkRequestMessage(accountId, chunks);
	}
}

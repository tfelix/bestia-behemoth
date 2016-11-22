package net.bestia.messages.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.JacksonMessage;
import net.bestia.model.map.MapChunk;

/**
 * This message contains all needed information for the client to load and
 * display a piece of a map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapChunkMessage extends JacksonMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "map.chunk";

	@JsonProperty("c")
	private final List<MapChunk> chunks = new ArrayList<>();

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
	
	public MapChunkMessage() {
		// no op.
	}

	public MapChunkMessage(AccountMessage msg, List<MapChunk> chunks) {
		super(msg);
		Objects.requireNonNull(chunks, "Chunks can not be null.");

		this.chunks.addAll(chunks);
	}
	
	@Override
	public String toString() {
		return String.format("MapChunkMessage[chunks: %d]", chunks.size());
	}
}

package net.bestia.messages.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.MessageId;
import net.bestia.model.map.MapChunk;

/**
 * This message contains all needed information for the client to load and
 * display a piece of a map.
 * 
 * @author Thomas Felix
 *
 */
public class MapChunkMessage extends AccountMessage implements MessageId {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "map.chunk";

	@JsonProperty("c")
	private final List<MapChunk> chunks = new ArrayList<>();

	/**
	 * Needed for MessageTypeIdResolver
	 */
	private MapChunkMessage() {
		super(0);
	}

	public MapChunkMessage(long accId, List<MapChunk> chunks) {
		super(accId);
		
		
		Objects.requireNonNull(chunks, "Chunks can not be null.");

		this.chunks.addAll(chunks);
	}
	
	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("MapChunkMessage[chunks: %d]", chunks.size());
	}

	@Override
	public MapChunkMessage createNewInstance(long accountId) {
		return new MapChunkMessage(accountId, chunks);
	}
}

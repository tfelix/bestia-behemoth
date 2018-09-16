package net.bestia.messages.map;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.messages.MessageId;
import net.bestia.model.map.Tileset.SimpleTileset;

/**
 * Tiles are only referenced by so called gids. This ids are globally unique and
 * can be found inside the tilesets. These tilest data can be requested from the
 * server via such an request.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapTilesetMessage extends AccountMessage implements MessageId {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "map.tileset";

	@JsonProperty("ts")
	private final SimpleTileset tileset;

	/**
	 * Needed for MessageTypeIdResolver 
	 */
	private MapTilesetMessage() {
		super(0);
		
		tileset = null;
	}

	public MapTilesetMessage(long accId, SimpleTileset tileset) {
		super(accId);
		
		this.tileset = Objects.requireNonNull(tileset);
	}

	public SimpleTileset getTileset() {
		return tileset;
	}

	@Override
	public String toString() {
		return String.format("MapTileset[%s]", getTileset().toString());
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public MapTilesetMessage createNewInstance(long accountId) {
		
		return new MapTilesetMessage(accountId, tileset);
	}
}

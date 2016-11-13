package net.bestia.messages.map;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.JacksonMessage;
import net.bestia.model.domain.Tileset;

/**
 * Tiles are only referenced by so called gids. This ids are globally unique and
 * can be found inside the tilesets. These tilest data can be requested from the
 * server via such an request.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapTilesetMessage extends JacksonMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "map.tileset";

	@JsonProperty("ts")
	private final Tileset tileset;

	public MapTilesetMessage() {
		tileset = null;
	}

	public MapTilesetMessage(AccountMessage msg, Tileset tileset) {
		super(msg);
		this.tileset = tileset;
	}

	public Tileset getTileset() {
		return tileset;
	}

	@Override
	public String toString() {
		return String.format("MapTileset[accId: %d]", getTileset());
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}

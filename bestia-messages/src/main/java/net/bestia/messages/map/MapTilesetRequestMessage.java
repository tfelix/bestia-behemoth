package net.bestia.messages.map;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JacksonMessage;

/**
 * Tiles are only referenced by so called gids. This ids are globally unique and
 * can be found inside the tilesets. These tilest data can be requested from the
 * server via such an request.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapTilesetRequestMessage extends JacksonMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "map.tilesetrequest";

	@JsonProperty("gid")
	private long tileId;

	public MapTilesetRequestMessage() {
		tileId = 0;
	}

	public MapTilesetRequestMessage(long gid) {
		this.tileId = gid;
	}

	public long getTileId() {
		return tileId;
	}

	@Override
	public String toString() {
		return String.format("RequeMapTilesetRequest[gid: %d]", getTileId());
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}

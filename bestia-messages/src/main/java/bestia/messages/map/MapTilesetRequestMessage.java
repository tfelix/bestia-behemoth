package bestia.messages.map;

import com.fasterxml.jackson.annotation.JsonProperty;

import bestia.messages.JsonMessage;

/**
 * Tiles are only referenced by so called gids. This ids are globally unique and
 * can be found inside the tilesets. These tilest data can be requested from the
 * server via such an request.
 * 
 * @author Thomas Felix
 *
 */
public class MapTilesetRequestMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "map.tilesetrequest";

	@JsonProperty("gid")
	private int tileId;
	
	/**
	 * Needed for MessageTypeIdResolver 
	 */
	private MapTilesetRequestMessage() {
		super(0);
	}

	public MapTilesetRequestMessage(long accId, int gid) {
		super(accId);
		
		this.tileId = gid;
	}

	public int getTileId() {
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

	@Override
	public MapTilesetRequestMessage createNewInstance(long accountId) {
		return new MapTilesetRequestMessage(accountId, tileId);
	}
}

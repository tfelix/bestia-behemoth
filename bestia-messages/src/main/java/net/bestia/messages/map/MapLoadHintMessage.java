package net.bestia.messages.map;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;
import net.bestia.model.domain.SpriteInfo;

/**
 * This message is send by the server to the client which has newly connected to
 * a map. It will contain assets of currently active entities or bestias on this
 * map and suggests a loading of these. The server regenerates this list from
 * time to time and resends it.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapLoadHintMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "map.loadhint";

	@JsonProperty("s")
	private List<SpriteInfo> sprites = new ArrayList<>();


	public MapLoadHintMessage(long accId, List<SpriteInfo> sprites) {
		super(accId);

		this.sprites = new ArrayList<>(sprites);
	}

	public void addSprite(SpriteInfo sprite) {
		sprites.add(sprite);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public MapLoadHintMessage createNewInstance(long accountId) {
		return new MapLoadHintMessage(accountId, sprites);
	}

}

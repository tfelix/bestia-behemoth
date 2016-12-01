package net.bestia.messages.map;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.MessageId;
import net.bestia.model.entity.Sprite;

/**
 * This message is send by the server to the client which has newly connected to
 * a map. It will contain assets of currently active entities or bestias on this
 * map and suggests a loading of these. The server regenerates this list from
 * time to time and resends it.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapLoadHintMessage extends AccountMessage implements MessageId {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "map.loadhint";
	
	@JsonProperty("s")
	private List<Sprite> sprites = new ArrayList<>();
	
	public void addSprite(Sprite sprite) {
		sprites.add(sprite);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

}

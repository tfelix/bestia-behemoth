package net.bestia.messages.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.JacksonMessage;
import net.bestia.model.shape.Point;

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

	@JsonProperty("l")
	private final List<Map<Point, Integer>> layers;
	
	@JsonProperty("gl")
	private final List<Integer> groundLayer;
	
	// Leads to back reference? Dunno why.
	@JsonProperty("p")
	private final Point position;

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
	
	public MapChunkMessage() {

		layers = Collections.emptyList();
		groundLayer = Collections.emptyList();
		position = new Point(0, 0);
	}

	public MapChunkMessage(AccountMessage msg, net.bestia.model.map.Map map) {
		super(msg);
		Objects.requireNonNull(map);

		this.position = map.getRect().getOrigin();

		this.groundLayer = new ArrayList<>(map.getGroundLayer());
		this.layers = map.getSparseLayers();
	}

	/**
	 * Returns the ids of the ground layer of this map.
	 * 
	 * @return
	 */
	public List<Integer> getGroundLayer() {
		return groundLayer;
	}

	/**
	 * Returns the list of layers of the map. It will start with the ground
	 * layer 0. All layers above will be
	 * 
	 * @return The list
	 */
	public List<Map<Point, Integer>> getLayers() {
		return layers;
	}

	public Point getPosition() {
		return position;
	}
	
	@Override
	public String toString() {
		return String.format("MapChunkMessage[pos: %s]", getPosition().toString());
	}
}

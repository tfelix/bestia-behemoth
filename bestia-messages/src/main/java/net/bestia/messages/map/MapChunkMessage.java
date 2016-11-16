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
import net.bestia.model.shape.Size;

/**
 * This message contains all needed information for the client to load and
 * display a piece of a map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapChunkMessage extends JacksonMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "map.mapdata";

	@JsonProperty("l")
	private final List<Map<Point, Integer>> layers;
	
	@JsonProperty("gl")
	private final List<Integer> groundLayer;
	
	@JsonProperty("s")
	private final Size size;

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
	
	public MapChunkMessage() {

		layers = Collections.emptyList();
		groundLayer = Collections.emptyList();
		size = new Size(0, 0);
	}

	public MapChunkMessage(AccountMessage msg, net.bestia.model.map.Map map) {
		super(msg);
		Objects.requireNonNull(map);

		this.size = map.getSize();

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

	/**
	 * Returns the size of the map part.
	 * 
	 * @return The size of this map part.
	 */
	public Size getSize() {
		return size;
	}
}

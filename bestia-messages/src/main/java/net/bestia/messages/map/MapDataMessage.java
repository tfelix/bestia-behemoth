package net.bestia.messages.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.MessageId;
import net.bestia.model.shape.Point;
import net.bestia.model.shape.Size;

/**
 * This message contains all needed information for the client to load and
 * display a piece of a map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapDataMessage extends AccountMessage implements MessageId {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "map.mapdata";

	private final List<Map<Point, Integer>> layers;
	private final List<Integer> groundLayer;
	private final Size size;

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
	
	public MapDataMessage() {

		layers = Collections.emptyList();
		groundLayer = Collections.emptyList();
		size = new Size(0, 0);
	}

	public MapDataMessage(net.bestia.model.map.Map map) {

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

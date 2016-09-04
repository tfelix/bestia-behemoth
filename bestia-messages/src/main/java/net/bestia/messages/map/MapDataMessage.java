package net.bestia.messages.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.bestia.messages.ClientMessage;
import net.bestia.messages.Message;
import net.bestia.model.shape.Point;
import net.bestia.model.shape.Size;

/**
 * This message contains all needed information for the client to load and
 * display a piece of a map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapDataMessage extends ClientMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "map.mapdata";

	private final List<Map<Point, Integer>> layers;
	private final List<Integer> groundLayer;
	private final Map<Point, Boolean> walkable;
	private final List<String> tilesets;
	private final Size size;

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	public MapDataMessage(net.bestia.model.map.Map map) {

		Objects.requireNonNull(map);

		this.size = map.getSize();

		this.tilesets = new ArrayList<>(map.getTilesetNames());
		this.groundLayer = new ArrayList<>(map.getGroundLayer());
		this.layers = map.getSparseLayers();
		this.walkable = map.getSparseCollisionLayer();

	}

	public List<String> getTilesets() {
		return tilesets;
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

	/**
	 * Returns the list with walkable tiles.
	 * 
	 * @return The list with walkable tiles.
	 */
	public Map<Point, Boolean> getWalkable() {
		return walkable;
	}
}

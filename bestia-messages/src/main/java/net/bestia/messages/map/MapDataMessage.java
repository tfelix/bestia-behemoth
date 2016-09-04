package net.bestia.messages.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.bestia.messages.Message;
import net.bestia.model.zone.Size;

/**
 * This message contains all needed information for the client to load and
 * display a piece of a map.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapDataMessage extends Message {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "map.mapdata";

	private List<List<Integer>> layers = new ArrayList<>();
	private List<Boolean> walkable = new ArrayList<>();
	private Size size;

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	public MapDataMessage(Size size, List<List<Integer>> layers) {

		this.size = Objects.requireNonNull(size);

		// Copy the maplayers.
		for(List<Integer> layer : layers) {
			this.layers.add(new ArrayList<Integer>(layer));
		}
	}

	/**
	 * Returns the list of layers of the map. It will start with the ground
	 * layer 0. All layers above will be
	 * 
	 * @return The list
	 */
	public List<List<Integer>> getLayer() {
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
	public List<Boolean> getWalkable() {
		return walkable;
	}
}

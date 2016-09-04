package net.bestia.messages.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.bestia.messages.Message;

/**
 * This message contains the needed tilesets for the client to load in order to
 * display the tile ids in sight.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class TilesetDataMassage extends Message {

	private static final long serialVersionUID = 1L;

	public final static String MESSAGE_ID = "map.tilesetdata";

	private final List<String> tilesets = new ArrayList<>();

	/**
	 * Std. ctor.
	 */
	public TilesetDataMassage() {
		// no op.
	}

	/**
	 * Adds the given list of tileset names to the message.
	 * 
	 * @param tilesets
	 *            Name of tilesets to be added to this message.
	 */
	public TilesetDataMassage(List<String> tilesets) {

		Objects.requireNonNull(tilesets);
		this.tilesets.addAll(tilesets);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

}

package net.bestia.zoneserver.generator.map;

import java.util.Objects;

import net.bestia.model.zone.Size;

/**
 * Temporary map layer containing map data for dynamic map generation. Note that
 * the data is not necessairy tied to tile data. This is mostly some kind of
 * noise data later used by the algorithms to generate the map data.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapLayer {

	private Size size;
	private int data[][];

	public MapLayer(Size size) {
		
		this.size = Objects.requireNonNull(size);
		this.data = new int[size.getWidth()][size.getHeight()];
	}
	
	public Size getSize() {
		return size;
	}

}

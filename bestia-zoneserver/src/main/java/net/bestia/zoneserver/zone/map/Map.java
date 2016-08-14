package net.bestia.zoneserver.zone.map;

import java.util.List;

import net.bestia.model.zone.Point;
import net.bestia.model.zone.Size;
import net.bestia.zoneserver.zone.shape.Rect;

public class Map {
	
	private String name;
	private Size size;
	
	private java.util.Map<Point, Tile> tiles;
	
	public List<Tile> getTiles(Rect range) {
		return null;
	}

}

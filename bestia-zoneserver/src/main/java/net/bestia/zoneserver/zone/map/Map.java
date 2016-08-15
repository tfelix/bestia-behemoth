package net.bestia.zoneserver.zone.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import net.bestia.model.zone.Point;
import net.bestia.model.zone.Size;
import net.bestia.zoneserver.zone.shape.Rect;

public class Map {
	
	public static class MapBuilder {
		
	}
	
	private final String name;
	private final Size size;
	
	private java.util.Map<Integer, java.util.Map<Point, Tile>> tileLayer = new HashMap<>();
	private boolean[] walkableData;
	
	public Map() {
		name = "";
		size = new Size(1, 1);
	}
	
	public Map(String name, Size size) {
		
		this.name = name;
		this.size = Objects.requireNonNull(size);
		
		this.walkableData = new boolean[size.getHeight() * size.getWidth()];
		
	}

	public boolean isWalkable(long x, long y) {
		return isWalkable(new Point(x, y));
	}

	public boolean isWalkable(Point p) {
		final int index = (int) (p.getY() * size.getWidth() + p.getX());
		return walkableData[index];
	}
}

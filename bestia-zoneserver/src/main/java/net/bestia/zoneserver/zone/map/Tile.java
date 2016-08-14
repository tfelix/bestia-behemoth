package net.bestia.zoneserver.zone.map;

import net.bestia.model.zone.Point;

public class Tile {

	public static class TileBuilder {
		
		private Point position;
		private boolean isWalkable;
		private int walkspeed;
		private int layer;
		private int tileGid;
		
		public TileBuilder() {
			// TODO Auto-generated constructor stub
		}
		
		public TileBuilder setTileGid(int gid) {
			this.tileGid = gid;
			return this;
		}
		
		public TileBuilder setPosition(Point pos) {
			this.position = pos;
			return this;
		}
		
		public TileBuilder setWalkable(boolean isWalkable) {
			this.isWalkable = isWalkable;
			return this;
		}
		
		public TileBuilder setWalkspeed(int walkspeed) {
			this.walkspeed = walkspeed;
			return this;
		}
		
		public TileBuilder setLayer(int layer) {
			this.layer = layer;
			return this;
		}
		
		public Tile build() {
			return new Tile(this);
		}
	}
	
	private final Point position;
	private final int gid;
	private final boolean isWalkable;
	private final int walkspeed;
	private final int layer;
	
	private Tile(TileBuilder builder) {
		
		this.position = builder.position;
		this.isWalkable = builder.isWalkable;
		this.layer = builder.layer;
		this.walkspeed = builder.walkspeed;
		this.gid = builder.tileGid;
	}
	
	public Point getPoint() {
		return position;
	}

	public int getGid() {
		return gid;
	}

	public boolean isWalkable() {
		return isWalkable;
	}

	public int getWalkspeed() {
		return walkspeed;
	}

	public int getLayer() {
		return layer;
	}
}

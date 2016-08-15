package net.bestia.zoneserver.zone.map;

import net.bestia.model.zone.Point;

public class Tile {

	public static class TileBuilder {
		
		private Point position;
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
	private final int layer;
	
	private Tile(TileBuilder builder) {
		
		this.position = builder.position;
		this.layer = builder.layer;
		this.gid = builder.tileGid;
	}
	
	public Point getPoint() {
		return position;
	}

	public int getGid() {
		return gid;
	}

	public int getLayer() {
		return layer;
	}
}

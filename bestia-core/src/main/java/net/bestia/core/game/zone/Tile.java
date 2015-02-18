package net.bestia.core.game.zone;

public class Tile {
	
	private final boolean isWalkable;
	private final int graphicId;
	private final int walkspeed;
	
	public Tile(int gaphicId, boolean isWalkable, int walkspeed) {
		
		this.graphicId = gaphicId;
		this.isWalkable = isWalkable;
		this.walkspeed = walkspeed;
		
	}

	public boolean isWalkable() {
		return isWalkable;
	}

	public int getWalkspeed() {
		return walkspeed;
	}

}

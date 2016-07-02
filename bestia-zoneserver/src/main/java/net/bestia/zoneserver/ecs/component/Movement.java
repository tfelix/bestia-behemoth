package net.bestia.zoneserver.ecs.component;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.artemis.Component;

import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * Allows an entity to be moved. It has can have a path and if it has a
 * walkspeed component the path will be followed.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Movement extends Component implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final float TILES_PER_SECOND = 3f;

	public Queue<Vector2> path = new LinkedList<>();
	private float walkspeed;
	public float nextMove;
	
	private boolean hasSendPredictions;

	public Movement() {
		this.walkspeed = 1.0f;
		this.nextMove = 1f;
		// Clear it if artemis recycles an component.
		this.path.clear();
		hasSendPredictions = false;
	}

	public Movement(float walkspeed, List<Vector2> path) {
		this.walkspeed = walkspeed;
		// Clear it if artemis recycles an component.
		this.path.clear();
		this.path.addAll(path);
		this.nextMove = 1f;
	}
	
	public void clear() {
		this.path.clear();
		this.nextMove = 1f;
		this.hasSendPredictions = false;
	}
	
	public boolean hasSendPredictions() {
		return hasSendPredictions;
	}
	
	public void setSendPredictions(boolean flag) {
		this.hasSendPredictions = flag;
	}
	
	public float getWalkspeed() {
		return walkspeed;
	}
	
	public int getWalkspeedInt() {
		return (int) (100 * walkspeed);
	}
	
	public void setWalkspeed(float walkspeed) {
		this.walkspeed = walkspeed;
		// Invalidate predictions.
		hasSendPredictions = false;
	}

}

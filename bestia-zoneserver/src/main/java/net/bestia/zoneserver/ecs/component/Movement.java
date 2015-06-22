package net.bestia.zoneserver.ecs.component;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import net.bestia.zoneserver.game.zone.Vector2;

import com.artemis.Component;

/**
 * Allows an entity to be moved. It has can have a path and if it has a walkspeed component the path will be followed.
 * 
 * @author Thomas
 *
 */
public class Movement extends Component {
	
	public static final float TILES_PER_SECOND = 3f;

	public Queue<Vector2> path = new LinkedList<>();
	public float walkspeed;
	public float nextMove;
	
	public Movement() {
		this.walkspeed = 1.0f;
		this.nextMove = 1f;
	}
	
	public Movement(float walkspeed, List<Vector2> path) {
		this.walkspeed = walkspeed;
		this.path.addAll(path);
		this.nextMove = 1f;
	}

}

package net.bestia.zoneserver.ecs.component;

import java.util.ArrayList;
import java.util.List;

import net.bestia.zoneserver.game.zone.Vector2;

import com.artemis.Component;

/**
 * Allows an entity to be moved. It has can have a path and if it has a walkspeed component the path will be followed.
 * 
 * @author Thomas
 *
 */
public class Movement extends Component {

	public final List<Vector2> path = new ArrayList<Vector2>();
	public int walkspeed;
	public int lastCheck = 0;
	
	public Movement(int walkspeed) {
		this.walkspeed = walkspeed;
	}

}

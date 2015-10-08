package net.bestia.zoneserver.ecs.system;

import net.bestia.model.domain.Location;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Collision;
import net.bestia.zoneserver.ecs.component.Movement;
import net.bestia.zoneserver.manager.BestiaManager;
import net.bestia.zoneserver.zone.shape.Vector2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.systems.DelayedEntityProcessingSystem;

/**
 * Process linear movement along a given path. Calculates the delay until the next move to the next tile is completed.
 * Then the system will be called again.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class MovementSystem extends DelayedEntityProcessingSystem {

	private final static Logger log = LogManager.getLogger(MovementSystem.class);

	private ComponentMapper<Bestia> bestiaMapper;
	private ComponentMapper<Movement> movementMapper;
	private ComponentMapper<Collision> collisionMapper;

	public MovementSystem() {
		super(Aspect.all(Bestia.class, Movement.class, Collision.class));
	}

	@Override
	protected float getRemainingDelay(Entity e) {
		final Movement m = movementMapper.get(e);
		return m.nextMove;
	}

	@Override
	protected void processDelta(Entity e, float accumulatedDelta) {
		Movement m = movementMapper.get(e);
		m.nextMove -= accumulatedDelta;
	}

	@Override
	protected void processExpired(Entity e) {
		final Movement m = movementMapper.get(e);

		Vector2 pos = m.path.poll();
		if (pos == null) {
			e.edit().remove(Movement.class);
			return;
		}
		
		// Check if we handle a bestia or a generic position only entity.
		final BestiaManager manager = bestiaMapper.get(e).bestiaManager;
		
		// Check that the next move position is only one tile away.
		final int distance = getDistance(manager.getLocation(), pos);
		if(distance > 1) {
			// Something is wrong. Path is no longer valid.
			m.path.clear();
			e.edit().remove(Movement.class);
			return;
		}

		manager.getLocation().setX(pos.x);
		manager.getLocation().setY(pos.y);
		
		// TODO das hier besser lösen. Update the collision.
		collisionMapper.get(e).shape = new Vector2(pos.x, pos.y);

		log.trace("Moved to: {}", pos.toString());

		m.nextMove = 1000 / (m.walkspeed * Movement.TILES_PER_SECOND);
		offerDelay(m.nextMove);
	}
	
	private int getDistance(Location p1, Vector2 p2) {
		return (int) Math.sqrt(Math.pow(p1.getX() - p2.x, 2) + Math.pow(p1.getY() - p2.y, 2));
	}

}

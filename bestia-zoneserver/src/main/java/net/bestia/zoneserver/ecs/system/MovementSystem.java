package net.bestia.zoneserver.ecs.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.zoneserver.ecs.component.ChangedData;
import net.bestia.zoneserver.ecs.component.Movement;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.game.zone.Vector2;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntityTransmuter;
import com.artemis.EntityTransmuterFactory;
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

	private ComponentMapper<Movement> moveM;
	private ComponentMapper<Position> posM;

	private EntityTransmuter changedTransmuter;

	@SuppressWarnings({ "unchecked", "deprecation" })
	public MovementSystem() {
		super(Aspect.getAspectForAll(Position.class, Movement.class));

	}

	@Override
	protected float getRemainingDelay(Entity e) {
		Movement m = moveM.get(e);
		return m.nextMove;
	}

	@Override
	protected void processDelta(Entity e, float accumulatedDelta) {
		Movement m = moveM.get(e);
		m.nextMove -= accumulatedDelta;
	}

	@Override
	protected void begin() {
		super.begin();
		changedTransmuter = new EntityTransmuterFactory(world).add(ChangedData.class).build();
	};

	@Override
	protected void processExpired(Entity e) {
		Movement m = moveM.get(e);
		Position p = posM.get(e);

		Vector2 pos = m.path.poll();
		if (pos == null) {
			e.edit().remove(Movement.class);
			return;
		}
		
		// Check that the next move position is only one tile away.
		final int distance = getDistance(p, pos);
		if(distance > 1) {
			// Something is wrong. Path is no longer valid.
			m.path.clear();
			e.edit().remove(Movement.class);
			return;
		}

		p.x = pos.x;
		p.y = pos.y;

		changedTransmuter.transmute(e);

		log.trace("Moved to: {}", pos.toString());

		m.nextMove = 1000 / (m.walkspeed * Movement.TILES_PER_SECOND);
		offerDelay(m.nextMove);
	}
	
	private int getDistance(Position p1, Vector2 p2) {
		return (int) Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
	}

}

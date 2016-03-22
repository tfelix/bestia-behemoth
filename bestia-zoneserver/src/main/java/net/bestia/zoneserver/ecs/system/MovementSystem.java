package net.bestia.zoneserver.ecs.system;

import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.model.domain.Location;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Changed;
import net.bestia.zoneserver.ecs.component.Movement;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;
import net.bestia.zoneserver.proxy.BestiaEntityProxy;
import net.bestia.zoneserver.zone.shape.Vector2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.managers.UuidEntityManager;
import com.artemis.systems.DelayedEntityProcessingSystem;

/**
 * Process linear movement along a given path. Calculates the delay until the
 * next move to the next tile is completed. Then the system will be called
 * again.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class MovementSystem extends DelayedEntityProcessingSystem {

	private final static Logger log = LogManager.getLogger(MovementSystem.class);

	private ComponentMapper<Bestia> bestiaMapper;
	private ComponentMapper<Movement> movementMapper;
	private PlayerBestiaSpawnManager pbSpawnManager;
	private UuidEntityManager uuidManager;

	public MovementSystem() {
		super(Aspect.all(Bestia.class, Movement.class, Position.class));
	}

	@Override
	protected float getRemainingDelay(Entity e) {
		final Movement m = movementMapper.get(e);
		return m.nextMove;
	}

	@Override
	protected void processDelta(Entity e, float accumulatedDelta) {
		final Movement m = movementMapper.get(e);
		m.nextMove -= accumulatedDelta;
	}

	@Override
	protected void processExpired(Entity e) {
		final Movement m = movementMapper.get(e);
		
		if(!m.hasSendPredictions()) {
			final String uuid = uuidManager.getUuid(e).toString();
			final EntityMoveMessage movePredictMsg = EntityMoveMessage.fromPath(m.path, uuid);
			m.setSendPredictions(true);
			pbSpawnManager.sendMessageToSightrange(e.getId(), movePredictMsg);
		}

		final Vector2 pos = m.path.poll();
		if (pos == null) {
			e.edit().remove(Movement.class);
			return;
		}

		// Check if we handle a bestia or a generic position only entity.
		final BestiaEntityProxy manager = bestiaMapper.get(e).manager;

		// Check that the next move position is only one tile away.
		final Location loc = manager.getLocation();
		final int distance = getDistance(loc, pos);
		if (distance > 1) {
			// Something is wrong. Path is no longer valid.
			m.path.clear();
			e.edit().remove(Movement.class);
			return;
		}

		loc.setX(pos.x);
		loc.setY(pos.y);

		// Mark entity as changed.
		e.edit().create(Changed.class);

		log.trace("Moved to: {}", pos.toString());

		m.nextMove = 1000 / (m.getWalkspeed() * Movement.TILES_PER_SECOND);
		offerDelay(m.nextMove);
	}

	private int getDistance(Location p1, Vector2 p2) {
		return (int) Math.sqrt(Math.pow(p1.getX() - p2.x, 2) + Math.pow(p1.getY() - p2.y, 2));
	}

}

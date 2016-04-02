package net.bestia.zoneserver.ecs.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.EntitySubscription.SubscriptionListener;
import com.artemis.annotations.Wire;
import com.artemis.managers.UuidEntityManager;
import com.artemis.systems.DelayedIteratingSystem;
import com.artemis.utils.IntBag;

import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.messages.entity.EntityPositionMessage;
import net.bestia.model.domain.Location;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Movement;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;
import net.bestia.zoneserver.zone.shape.Vector2;

/**
 * Process linear movement along a given path. Calculates the delay until the
 * next move to the next tile is completed. Then the system will be called
 * again.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class MovementSystem extends DelayedIteratingSystem {

	private final static Logger LOG = LogManager.getLogger(MovementSystem.class);

	private ComponentMapper<Bestia> bestiaMapper;
	private ComponentMapper<Movement> movementMapper;
	private PlayerBestiaSpawnManager pbSpawnManager;
	private UuidEntityManager uuidManager;

	private EntitySubscription movementSubscription;
	

	public MovementSystem() {
		super(Aspect.all(Bestia.class, Movement.class, Position.class));
	}

	@Override
	protected void initialize() {
		// We need to subscribe to movement changed.
		final AspectSubscriptionManager asm = world.getAspectSubscriptionManager();
		movementSubscription = asm.get(Aspect.all(Movement.class));

		movementSubscription.addSubscriptionListener(new SubscriptionListener() {

			@Override
			public void removed(IntBag entities) {
				// no op.
			}

			@Override
			public void inserted(IntBag entities) {
				// New movement has started. Send to all players in range the
				// movement prediction.
				
				for(int i = 0; i  < entities.size(); i++) {
					final int entityId = entities.get(i);
					sendMovementPrediction(entityId);
				}
			}
		});
	}

	@Override
	protected float getRemainingDelay(int e) {
		final Movement m = movementMapper.get(e);
		return m.nextMove;
	}

	@Override
	protected void processDelta(int e, float accumulatedDelta) {
		final Movement m = movementMapper.get(e);
		m.nextMove -= accumulatedDelta;
	}

	@Override
	protected void processExpired(int e) {
		final Movement m = movementMapper.get(e);

		final Vector2 pos = m.path.poll();
		if (pos == null) {
			world.getEntity(e).edit().remove(Movement.class);
			return;
		}

		// Check if we handle a bestia or a generic position only entity.
		final Bestia manager = bestiaMapper.getSafe(e);

		// Did something change and we need to resend prediction?
		if (!m.hasSendPredictions()) {
			sendMovementPrediction(e);
		}

		if (manager != null) {
			// Check that the next move position is only one tile away.
			final Location loc = manager.manager.getLocation();
			final int distance = getDistance(loc, pos);
			if (distance > 1) {
				// Something is wrong. Path is no longer valid.
				m.path.clear();
				world.getEntity(e).edit().remove(Movement.class);
				return;
			}

			loc.setX(pos.x);
			loc.setY(pos.y);
		}

		LOG.trace("Entity {} moved to: {}", e, pos.toString());
		
		// Send position update to the clients in range.
		final String uuid = uuidManager.getUuid(world.getEntity(e)).toString();
		final EntityPositionMessage posMsg = new EntityPositionMessage(uuid, pos.x, pos.y);
		pbSpawnManager.sendMessageToSightrange(e, posMsg);

		m.nextMove = 1000 / (m.getWalkspeed() * Movement.TILES_PER_SECOND);
		offerDelay(m.nextMove);
	}

	private int getDistance(Location p1, Vector2 p2) {
		return (int) Math.sqrt(Math.pow(p1.getX() - p2.x, 2) + Math.pow(p1.getY() - p2.y, 2));
	}
	
	private void sendMovementPrediction(int entityId) {
		final Movement m = movementMapper.get(entityId);
		final String uuid = uuidManager.getUuid(world.getEntity(entityId)).toString();
		
		final EntityMoveMessage mm = new EntityMoveMessage(uuid, m.getWalkspeedInt());
		
		m.path.stream().forEach(p -> { mm.addCord(p.x, p.y); });
		
		pbSpawnManager.sendMessageToSightrange(entityId, mm);
		
		m.setSendPredictions(true);
	}

}

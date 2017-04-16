package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.entity.EntityAction;
import net.bestia.messages.entity.EntityPositionMessage;
import net.bestia.messages.entity.EntityUpdateMessage;
import net.bestia.model.domain.SpriteInfo;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerEntity;
import net.bestia.zoneserver.entity.ecs.EcsEntityService;
import net.bestia.zoneserver.entity.ecs.Entity;
import net.bestia.zoneserver.entity.traits.Locatable;
import net.bestia.zoneserver.entity.traits.Visible;

/**
 * This actor has an crucial role in checking if a position update of an entity
 * leads to the triggering of scripts. It does also check if the moved entity is
 * now in viewing range of other (player) entities and will inform them about a
 * newly seen entity and also sends this information into their AI scripting
 * agents which in turn will evaluate new actions.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class PositionActor extends BestiaRoutingActor {

	public final static String NAME = "position";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final EcsEntityService entityService;

	@Autowired
	public PositionActor(EcsEntityService entityService) {
		super(Arrays.asList(EntityPositionMessage.class));

		this.entityService = Objects.requireNonNull(entityService);
	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug("Received internal position update.");

		final EntityPositionMessage posMsg = (EntityPositionMessage) msg;
		final Entity e = entityService.getEntity(posMsg.getEntityId());

		// TODO NPC AI important checks.
		if (e instanceof PlayerEntity) {
			doVisualChecks((PlayerEntity) e);
		}

		// Update the client.
		// FIXME
		// sendActiveInRangeClients(posMsg);
	}

	/**
	 * Gets all entities in visible rect.
	 * 
	 * @param e
	 */
	private void doVisualChecks(PlayerEntity e) {

		Map<Long, Visible> entities = entityService
				.getEntitiesInRange(net.bestia.model.map.Map.getUpdateRect(e.getPosition())).stream()
				.filter(x -> x instanceof Visible).map(x -> (Visible) x)
				.collect(Collectors.toMap(Entity::getId, Function.identity()));

		// Remove own entity.
		entities.remove(e.getId());

		final Set<Long> lastSeen = e.getLastSeenEntities();

		// get all entities which where seen since the last invocation.
		SetView<Long> oldEntities = Sets.difference(lastSeen, entities.keySet());
		SetView<Long> newEntities = Sets.difference(entities.keySet(), lastSeen);

		// Send update to client.
		for (Long eid : oldEntities) {
			final EntityUpdateMessage euMsg = new EntityUpdateMessage(e.getAccountId(), eid, 0, 0,
					SpriteInfo.placeholder(), EntityAction.VANISH);
			sendClient(euMsg);
		}

		// Send update to client.
		for (Long eid : newEntities) {
			// If the entity is not locatable skip it (should not happen because
			// it was selected in the first place).
			final Visible v = entities.get(eid);

			if (!(v instanceof Locatable)) {
				continue;
			}

			final Point p = ((Locatable) v).getPosition();

			final EntityUpdateMessage euMsg = new EntityUpdateMessage(e.getAccountId(), eid, p.getX(), p.getY(),
					v.getVisual());
			sendClient(euMsg);
		}

		// Update the last seen.
		lastSeen.clear();
		lastSeen.addAll(entities.keySet());

		entityService.save(e);
	}

}

package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.PlayerEntityService;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.VisibleComponent;
import net.bestia.messages.entity.EntitySyncRequestMessage;
import net.bestia.messages.entity.EntityUpdateMessage;
import net.bestia.model.domain.SpriteInfo;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.AkkaSender;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.map.MapService;

/**
 * This actor listens to engine requests to perform a full entity
 * synchronization in the visible update rect. This is used if the engine thinks
 * it needs a complete update (maybe after a reload or mapload).
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class EntitySyncActor extends BestiaRoutingActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().getSystem(), this);

	public static final String NAME = "entitySync";

	private final EntityService entityService;
	private final PlayerEntityService playerEntityService;

	@Autowired
	public EntitySyncActor(EntityService entityService, PlayerEntityService playerEntityService) {
		super(Arrays.asList(EntitySyncRequestMessage.class));

		this.entityService = Objects.requireNonNull(entityService);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
	}

	@Override
	protected void handleMessage(Object msg) {

		final EntitySyncRequestMessage syncMsg = (EntitySyncRequestMessage) msg;
		final long requestAccId = syncMsg.getAccountId();

		LOG.debug("Account {} requests a full entity sync.", requestAccId);

		final Entity activeEntity = playerEntityService.getActivePlayerEntity(syncMsg.getAccountId());
		final Point activePos = entityService.getComponent(activeEntity, PositionComponent.class)
				.orElseThrow(IllegalArgumentException::new)
				.getPosition();

		final Rect updateRect = MapService.getUpdateRect(activePos);

		final Set<Entity> visibleEntities = entityService.getCollidingEntities(updateRect)
				.stream()
				.filter(e -> entityService.hasComponent(e, VisibleComponent.class))
				.collect(Collectors.toSet());

		for (Entity e : visibleEntities) {

			final VisibleComponent visComp = entityService.getComponent(activeEntity, VisibleComponent.class).get();
			final PositionComponent posComp = entityService.getComponent(activeEntity, PositionComponent.class).get();

			final SpriteInfo sprite = visComp.getVisual();
			final EntityUpdateMessage updateMsg = EntityUpdateMessage.getUpdate(requestAccId,
					e.getId(),
					posComp.getPosition(),
					sprite);

			AkkaSender.sendClient(getContext(), updateMsg);
		}
	}

}

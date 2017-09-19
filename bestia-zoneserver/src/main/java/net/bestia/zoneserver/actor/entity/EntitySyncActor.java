package net.bestia.zoneserver.actor.entity;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.PlayerEntityService;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.TagComponent;
import net.bestia.entity.component.VisibleComponent;
import net.bestia.messages.entity.EntityAction;
import net.bestia.messages.entity.EntitySyncRequestMessage;
import net.bestia.messages.entity.EntityUpdateMessage;
import net.bestia.model.domain.SpriteInfo;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.AkkaSender;
import net.bestia.zoneserver.actor.zone.IngestExActor.RedirectMessage;
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
public class EntitySyncActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().getSystem(), this);

	public static final String NAME = "entitySync";

	private final EntityService entityService;
	private final PlayerEntityService playerEntityService;

	@Autowired
	public EntitySyncActor(EntityService entityService,
			PlayerEntityService playerEntityService) {

		this.entityService = Objects.requireNonNull(entityService);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(EntitySyncRequestMessage.class, this::onSyncRequest)
				.build();
	}

	@Override
	public void preStart() throws Exception {
		final RedirectMessage msg = RedirectMessage.get(EntitySyncRequestMessage.class);
		context().parent().tell(msg, getSelf());
	}

	private void onSyncRequest(EntitySyncRequestMessage msg) {

		final long requestAccId = msg.getAccountId();

		LOG.debug("Account {} requests a full entity sync.", requestAccId);

		final Entity activeEntity = playerEntityService.getActivePlayerEntity(msg.getAccountId());
		final Point activePos = entityService.getComponent(activeEntity, PositionComponent.class)
				.orElseThrow(IllegalArgumentException::new)
				.getPosition();

		final Rect updateRect = MapService.getUpdateRect(activePos);

		final Set<Entity> visibleEntities = entityService.getCollidingEntities(updateRect)
				.stream()
				.filter(e -> entityService.hasComponent(e, VisibleComponent.class))
				.filter(e -> entityService.hasComponent(e, TagComponent.class))
				.filter(e -> entityService.hasComponent(e, PositionComponent.class))
				.collect(Collectors.toSet());
		
		// Prepare the builder so it does not need to get created every time.
		EntityUpdateMessage.Builder builder = new EntityUpdateMessage.Builder();
		builder.setAction(EntityAction.UPDATE);

		for (Entity e : visibleEntities) {

			final VisibleComponent visComp = entityService.getComponent(e, VisibleComponent.class).get();
			final PositionComponent posComp = entityService.getComponent(e, PositionComponent.class).get();
			final TagComponent tagComp = entityService.getComponent(e, TagComponent.class).get();

			final SpriteInfo sprite = visComp.getVisual();
			
			builder.setSpriteInfo(sprite);
			builder.setPosition(posComp.getPosition());
			builder.setEid(e.getId());
			
			builder.getTags().clear();
			builder.getTags().addAll(tagComp.getAllTags());

			final EntityUpdateMessage updateMsg = builder.build();

			AkkaSender.sendClient(getContext(), updateMsg);
		}
	}

}

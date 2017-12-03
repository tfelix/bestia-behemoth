package net.bestia.zoneserver.actor.entity;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.ComponentSync;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.SyncType;
import net.bestia.messages.entity.EntityComponentMessage;
import net.bestia.messages.entity.EntitySyncRequestMessage;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.ClientMessageHandlerActor.RedirectMessage;
import net.bestia.zoneserver.actor.zone.SendClientActor;
import net.bestia.zoneserver.actor.zone.SendClientsInRangeActor;
import net.bestia.zoneserver.map.MapService;
import net.bestia.zoneserver.service.PlayerEntityService;

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
	private final ActorRef sendClient;
	private final ActorRef sendAllClients;

	@Autowired
	public EntitySyncActor(EntityService entityService,
			PlayerEntityService playerEntityService) {

		this.entityService = Objects.requireNonNull(entityService);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.sendClient = SpringExtension.actorOf(getContext(), SendClientActor.class);
		this.sendAllClients = SpringExtension.actorOf(getContext(), SendClientsInRangeActor.class);
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

		final List<net.bestia.entity.component.Component> test = entityService.getCollidingEntities(updateRect)
				.stream()
				.map(e -> entityService.getAllComponents(e))
				.flatMap(c -> c.stream())
				.filter(c -> {
					return c.getClass().isAnnotationPresent(ComponentSync.class);
				})
				.collect(Collectors.toList());
		
		test.forEach(c -> {
			final EntityComponentMessage ecm = new EntityComponentMessage(0, c, 0);
			ComponentSync syncAnno = c.getClass().getAnnotation(ComponentSync.class);
			
			if (syncAnno.value() == SyncType.ALL) {
				sendAllClients.tell(ecm, getSelf());
			} else {
				sendClient.tell(ecm, getSelf());
			}
		});
	}

}

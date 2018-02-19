package bestia.zoneserver.actor.entity;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import bestia.entity.Entity;
import bestia.entity.EntityService;
import bestia.entity.component.ComponentSync;
import bestia.entity.component.EntityComponentSyncMessageFactory;
import bestia.entity.component.PositionComponent;
import bestia.entity.component.SyncType;
import bestia.messages.entity.EntityComponentSyncMessage;
import bestia.messages.entity.EntitySyncRequestMessage;
import bestia.model.geometry.Point;
import bestia.model.geometry.Rect;
import bestia.zoneserver.actor.SpringExtension;
import bestia.zoneserver.actor.zone.ClientMessageActor.RedirectMessage;
import bestia.zoneserver.actor.zone.SendClientActor;
import bestia.zoneserver.actor.zone.SendClientsInRangeActor;
import bestia.zoneserver.entity.EntitySearchService;
import bestia.zoneserver.entity.PlayerEntityService;
import bestia.zoneserver.map.MapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This actor listens to engine requests to perform a full entity
 * synchronization in the visible update rect. This is used if the engine thinks
 * it needs a complete update (maybe after a reload or mapload).
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
public class EntitySyncRequestActor extends AbstractActor {

  private final LoggingAdapter LOG = Logging.getLogger(getContext().getSystem(), this);

  public static final String NAME = "entitySync";

  private final EntityService entityService;
  private final PlayerEntityService playerEntityService;
  private final EntitySearchService entitySearchService;
  private final ActorRef sendClient;
  private final ActorRef sendAllClients;
  private final EntityComponentSyncMessageFactory messageHelper = new EntityComponentSyncMessageFactory();

  @Autowired
  public EntitySyncRequestActor(EntityService entityService,
                                EntitySearchService entitySearchService,
                                PlayerEntityService playerEntityService) {

    this.entityService = Objects.requireNonNull(entityService);
    this.entitySearchService = Objects.requireNonNull(entitySearchService);
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

    final List<bestia.entity.component.Component> components = entitySearchService.getCollidingEntities(updateRect)
            .stream()
            .map(entityService::getAllComponents)
            .flatMap(Collection::stream)
            .filter(c -> c.getClass().isAnnotationPresent(ComponentSync.class))
            .collect(Collectors.toList());

    components.forEach(c -> {

      final EntityComponentSyncMessage syncMessage = messageHelper.forComponent(c);
      ComponentSync syncAnno = c.getClass().getAnnotation(ComponentSync.class);

      if (syncAnno.value() == SyncType.ALL) {
        sendAllClients.tell(syncMessage, getSelf());
      } else {
        sendClient.tell(syncMessage, getSelf());
      }
    });
  }
}
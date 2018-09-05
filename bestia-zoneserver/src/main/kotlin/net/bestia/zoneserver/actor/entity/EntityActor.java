package net.bestia.zoneserver.actor.entity;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.bestia.messages.entity.ComponentEnvelope;
import net.bestia.messages.entity.ComponentIntall;
import net.bestia.messages.entity.ComponentRemove;
import net.bestia.messages.entity.ToEntityEnvelope;
import net.bestia.zoneserver.actor.entity.component.EntityComponentActorFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * The {@link EntityActor} is a persistent actor managing all aspects of a
 * spawned entity. This means it will keep references to AI actors or attached
 * component actors. This actor has to be used as an persisted shared actor.
 * <p>
 * If there are no more active component actors this actor will cease operation.
 * However incoming component messages will restart this actor and attach the
 * component actor once more.
 * <p>
 * This actor has to react on certain incoming request messages like for example
 * attaching a ticking script to the entity.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
public class EntityActor extends AbstractActor {
  private final LoggingAdapter LOG = Logging.getLogger(getContext().getSystem(), this);

  private final EntityComponentActorFactory factory;

  /**
   * Saves the references for the actors managing components.
   */
  private BiMap<Long, ActorRef> componentActors = HashBiMap.create();

  private long entityId;

  @Autowired
  public EntityActor(EntityComponentActorFactory factory) {

    this.factory = Objects.requireNonNull(factory);
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
            .match(ToEntityEnvelope.class, this::handleEntityEnvelope)
            .match(Terminated.class, this::handleTerminated)
            .build();
  }

  private void handleMessage(Object msg) {
    if(msg instanceof  ComponentEnvelope) {
      handleComponentPayload((ComponentEnvelope) msg);
    } else {
      unhandled(msg);
    }
  }

  private void handleEntityEnvelope(ToEntityEnvelope envelope) {
    checkStartup(envelope.getEntityId());
    handleMessage(envelope.getContent());
  }

  /**
   * Checks if we have such a associated component and if so delivers the
   * message.
   */
  private void handleComponentPayload(ComponentEnvelope msg) {
    final long compId = msg.getComponentId();

    if(msg.getContent() instanceof ComponentIntall) {
     handleComponentInstall(compId);
    } else if(msg.getContent() instanceof ComponentRemove) {
      handleComponentRemove(compId);
    } else {
      if (!componentActors.containsKey(compId)) {
        LOG.debug("Component message unhandled: {}.", msg);
        unhandled(msg);
        return;
      }

      final ActorRef compActor = componentActors.get(compId);
      LOG.debug("Forwarding comp message: {} to: {}.", msg, compActor);
      compActor.tell(msg.getContent(), getSelf());
    }
  }

  /**
   * Adds or removes a active component actor from the entity.
   */
  private void handleComponentInstall(long componentId) {
    LOG.debug("Installing component: {} on entity: {}.", componentId, entityId);

    // Install the component.
    final ActorRef compActor = factory.startActor(getContext(), componentId);

    if (compActor == null) {
      LOG.warning("Component actor for comp id {} was not created.", componentId);
      return;
    }

    context().watch(compActor);
    componentActors.put(componentId, compActor);
  }

  private void handleComponentRemove(long componentId) {
    LOG.debug("Removing component: {} on entity: {}.", componentId, entityId);

    // Remove the component.
    final ActorRef compRef = componentActors.getOrDefault(componentId, ActorRef.noSender());
    compRef.tell(PoisonPill.getInstance(), getSelf());
  }

  /**
   * Check which actor was terminated and remove it.
   *
   * @param term The termination message from akka.
   */
  private void handleTerminated(Terminated term) {
    componentActors.inverse().remove(term.actor());

    // Terminate if no components active anymore.
    if (componentActors.size() == 0) {
      getContext().stop(getSelf());
    }
  }

  /**
   * Checks if we have already a defined entity id and if not we use it.
   */
  private void checkStartup(long entityId) {
    if (entityId == 0) {
      this.entityId = entityId;
    }
  }
}

package net.bestia.zoneserver.actor.entity;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.EntityMessage;
import net.bestia.messages.internal.entity.ComponentPayloadWrapper;
import net.bestia.zoneserver.actor.SpringExtension;

/**
 * The job of this actor is to receive the incoming messages for this shard. The
 * incoming messages will get delivered to the entities.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class EntityShardManagerActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().getSystem(), this);

	public static final String NAME = "entity";

	private final Map<Long, ActorRef> idToEntity = new HashMap<>();
	private final Map<ActorRef, Long> entityToId = new HashMap<>();

	public EntityShardManagerActor() {
		// no op.
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(Long.class, this::onEntityStartMessage)
				.match(EntityMessage.class, msg -> {
					sendToEntityActor(msg);
				})
				.match(ComponentPayloadWrapper.class, msg -> {
					sendToEntityActor(msg);
				})
				.build();
	}

	private void sendToEntityActor(EntityMessage msg) {
		LOG.debug("Received: {}.", msg);

		if (idToEntity.containsKey(msg.getEntityId())) {
			idToEntity.get(msg.getEntityId()).tell(msg, getSelf());
		}
	}

	/**
	 * Starts a new entity actor in the system.
	 * 
	 * @param entityId
	 */
	private void onEntityStartMessage(Long entityId) {

		final String actorName = EntityActor.getActorName(entityId);
		LOG.debug("Received start request for entity: {}. Actor name: {}", entityId, actorName);

		final ActorRef entityActor = SpringExtension.actorOf(getContext(), EntityActor.class, actorName, entityId);

		LOG.debug("Started actor: {}", entityActor);

		idToEntity.put(entityId, entityActor);
		entityToId.put(entityActor, entityId);
	}
}

package net.bestia.zoneserver.actor.chat;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.PositionComponent;
import net.bestia.messages.entity.EntityMoveRequestMessage;
import net.bestia.messages.internal.entity.ComponentEnvelope;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.ClientMessageHandlerActor.RedirectMessage;
import net.bestia.zoneserver.actor.zone.SendEntityActor;

/**
 * Incoming player requests to move a bestia must be send towards the actor of
 * the entity which will handle the movement. Message musst be wrapped in an
 * component envelope in order to get delivered.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class PlayerMoveRequestActor extends AbstractActor {

	private final ActorRef entityActor;
	private final EntityService entityService;

	@Autowired
	public PlayerMoveRequestActor(EntityService entityService) {

		this.entityActor = SpringExtension.actorOf(getContext(), SendEntityActor.class);
		this.entityService = Objects.requireNonNull(entityService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(EntityMoveRequestMessage.class, this::handleMoveRequest)
				.build();
	}

	@Override
	public void preStart() throws Exception {
		// Register for chat commands.
		final RedirectMessage redirMsg = RedirectMessage.get(EntityMoveRequestMessage.class);
		getContext().parent().tell(redirMsg, getSelf());
	}

	private void handleMoveRequest(EntityMoveRequestMessage msg) {

		entityService.getComponent(msg.getEntityId(), PositionComponent.class).ifPresent(posComp -> {
			final ComponentEnvelope ce = new ComponentEnvelope(msg.getEntityId(), posComp.getId(), msg);
			entityActor.tell(ce, getSender());
		});

	}
}

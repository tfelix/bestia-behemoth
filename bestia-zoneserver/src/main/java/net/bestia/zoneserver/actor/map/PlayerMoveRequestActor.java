package net.bestia.zoneserver.actor.map;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import net.bestia.entity.EntityService;
import net.entity.component.MoveComponent;
import net.bestia.messages.entity.EntityMoveRequestMessage;
import net.bestia.zoneserver.actor.zone.ClientMessageActor.RedirectMessage;

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

	private final EntityService entityService;

	@Autowired
	public PlayerMoveRequestActor(EntityService entityService) {

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
		
		final MoveComponent mc = entityService.getComponentOrCreate(msg.getEntityId(), MoveComponent.class);
		mc.setPath(msg.getPath());
		entityService.updateComponent(mc);

	}
}

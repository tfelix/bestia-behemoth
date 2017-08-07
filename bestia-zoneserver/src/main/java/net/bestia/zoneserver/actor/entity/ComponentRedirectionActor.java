package net.bestia.zoneserver.actor.entity;

import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;

import akka.actor.AbstractActor;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.Component;
import net.bestia.messages.ComponentMessage;
import net.bestia.messages.internal.entity.ComponentPayloadWrapper;

/**
 * Some messages which are coming from the client can not directly associated
 * with certain component ids. But this is necessary in order to direct the
 * message to certain component actors attached to entities which then will act
 * upon the received message.
 * 
 * @author Thomas Felix
 *
 */
@org.springframework.stereotype.Component
@Scope("prototype")
public class ComponentRedirectionActor extends AbstractActor {

	public static final String NAME = "compRedir";

	private final EntityService entityService;

	@Autowired
	public ComponentRedirectionActor(EntityService entityService) {

		this.entityService = Objects.requireNonNull(entityService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ComponentMessage.class, this::onComponentDirectedMessage)
				.build();
	}

	public void onComponentDirectedMessage(ComponentMessage msg) {

		Optional<? extends Component> compOpt = entityService.getComponent(
				msg.getEntityId(),
				msg.targetsComponent());

		if (!compOpt.isPresent()) {
			// LOG.

			return;
		}

		final Component comp = compOpt.get();
		final ComponentPayloadWrapper wrappedMsg = new ComponentPayloadWrapper(
				comp.getEntityId(),
				comp.getId(),
				msg);
		getSender().tell(wrappedMsg, getSelf());
	}
}

package net.bestia.zoneserver.actor.zone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.EntityMessage;
import net.bestia.messages.JsonMessage;

/**
 * Central message control hub. Incoming messages are either delivered to
 * connected clients or entity actors. The message actors are set via a message
 * to break a circular dependency.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class MessageRouterActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public static class SetMessageRoutes {

		private final ActorRef entities;
		private final ActorRef clients;

		public SetMessageRoutes(ActorRef entities, ActorRef clients) {

			this.entities = entities;
			this.clients = clients;

		}
	}

	public static final String NAME = "messageRouter";

	private ActorRef entities;
	private ActorRef clients;

	@Autowired
	public MessageRouterActor() {
		// no op.
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(JsonMessage.class, this::sendToClient)
				.match(EntityMessage.class, this::sendToEntityActor)
				.match(SetMessageRoutes.class, this::onSetMessageRoutes)
				.build();
	}

	private void sendToEntityActor(EntityMessage msg) {
		LOG.debug("Sending to entity: {}.", msg);
		entities.tell(msg, getSender());
	}

	private void sendToClient(JsonMessage msg) {
		LOG.debug("Sending to client: {}.", msg);
		clients.tell(msg, getSender());
	}

	private void onSetMessageRoutes(SetMessageRoutes routes) {
		LOG.debug("Setting message routes: client; {} entity: {}", routes.clients, routes.entities);
		this.entities = routes.entities;
		this.clients = routes.clients;
	}
}

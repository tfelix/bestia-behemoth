package net.bestia.zoneserver.actor.zone;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorPath;
import akka.actor.ActorSelection;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.EntityService;
import net.bestia.entity.PlayerEntityService;
import net.bestia.entity.component.PositionComponent;
import net.bestia.messages.AccountMessage;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.actor.BestiaActor;
import net.bestia.zoneserver.map.MapService;
import net.bestia.zoneserver.service.ConnectionService;

/**
 * The {@link SendClientActor} is responsible for the delivery of messages
 * directed to clients. It will lookup the information (actor ref) of the the
 * account given in the account message, serialize the message and send it back
 * to the webserver the client is currently connected.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class SendClientActor extends BestiaActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "sendToClient";

	private final ConnectionService connectionService;
	private final PlayerEntityService playerEntityService;
	private final EntityService entityService;

	@Autowired
	public SendClientActor(ConnectionService connectionService, 
			PlayerEntityService playerEntityService,
			EntityService entityService) {

		this.connectionService = Objects.requireNonNull(connectionService);
		this.entityService = Objects.requireNonNull(entityService);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(EntityJsonMessage.class, this::sendToActiveInRange)
				.match(AccountMessage.class, this::sendToClient)
				.build();
	}

	/**
	 * Sends to all active players in range. Maybe automatically detecting this
	 * method by message type does not work out. Then we might need to create a
	 * whole new actor only responsible for sending active range messages. Might
	 * be better idea anyways.
	 * 
	 * TODO Create own actor for range query messages.
	 * 
	 * @param msg
	 */
	private void sendToActiveInRange(EntityJsonMessage msg) {

		// Get position of the entity.
		final Optional<PositionComponent> posComp = entityService.getComponent(msg.getEntityId(), PositionComponent.class);
		
		if(!posComp.isPresent()) {
			LOG.warning("Position component of entity in message {} not present. Can not send range update.", msg);
			return;
		}
		
		final Point pos = posComp.get().getPosition();
		final Rect updateRect = MapService.getUpdateRect(pos);
		
		final List<Long> activeIds = playerEntityService.getActiveAccountIdsInRange(updateRect);
		
		for(Long activeId : activeIds) {
			JsonMessage newMsg = msg.createNewInstance(activeId);
			sendToClient(newMsg);
		}
		
	}

	private void sendToClient(AccountMessage msg) {

		final ActorPath originPath = connectionService.getPath(msg.getAccountId());

		// Origin path might be null because of race conditions or because the
		// actor
		// was logged out.
		if (originPath == null) {
			LOG.debug("Webserver path was null. Message not delivered: {}", msg);
			return;
		}

		final ActorSelection origin = context().actorSelection(originPath);

		if (origin == null) {
			LOG.warning("Could not find origin ref for message: {}", msg.toString());
			unhandled(msg);
			return;
		}

		// Send the client message.
		LOG.debug(String.format("Sending to client %d: %s", msg.getAccountId(), msg));
		origin.tell(msg, getSelf());

	}

}

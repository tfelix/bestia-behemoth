package net.bestia.zoneserver.actor.zone;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.PositionComponent;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.map.MapService;
import net.bestia.zoneserver.service.PlayerEntityService;

/***
 * If a {@link EntityJsonMessage} is received by this actor it will check if the
 * given entity contains a {@link PositionComponent} and if it does it will
 * detect all active player entities in the update range of the game and forward
 * the message to them via a {@link SendClientActor}.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class SendClientsInRangeActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "sendToClientsInRange";

	private final PlayerEntityService playerEntityService;
	private final EntityService entityService;
	private final ActorRef sendClient;

	@Autowired
	public SendClientsInRangeActor(PlayerEntityService playerEntityService,
			EntityService entityService) {

		this.entityService = Objects.requireNonNull(entityService);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.sendClient = SpringExtension.actorOf(getContext(), SendClientActor.class);
		
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(EntityJsonMessage.class, this::sendToActiveInRange)
				.build();
	}

	/**
	 * Sends to all active players in range. Maybe automatically detecting this
	 * method by message type does not work out. Then we might need to create a
	 * whole new actor only responsible for sending active range messages. Might
	 * be better idea anyways.
	 * 
	 * @param msg
	 */
	private void sendToActiveInRange(EntityJsonMessage msg) {

		// Get position of the entity.
		final Optional<PositionComponent> posComp = entityService.getComponent(msg.getEntityId(),
				PositionComponent.class);

		if (!posComp.isPresent()) {
			LOG.warning("Position component of entity in message {} not present. Can not send range update.", msg);
			return;
		}

		final Point pos = posComp.get().getPosition();
		final Rect updateRect = MapService.getUpdateRect(pos);

		final List<Long> activeIds = playerEntityService.getActiveAccountIdsInRange(updateRect);

		for (Long activeId : activeIds) {		
			final JsonMessage newMsg = msg.createNewInstance(activeId);		
			sendClient.tell(newMsg, getSelf());
		}
	}
}


package net.bestia.zoneserver.actor.bestia;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.Entity;
import net.bestia.messages.bestia.BestiaInfoMessage;
import net.bestia.messages.bestia.BestiaInfoRequestMessage;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.ClientMessageHandlerActor.RedirectMessage;
import net.bestia.zoneserver.actor.zone.SendClientActor;
import net.bestia.zoneserver.entity.PlayerEntityService;

/**
 * This actor gathers all needed information about the bestias in the players
 * possession and will deliver this information to the player.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class BestiaInfoActor extends AbstractActor {

	public static final String NAME = "bestiaInfo";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final PlayerEntityService playerEntityService;

	private final ActorRef sendClient;

	@Autowired
	public BestiaInfoActor(PlayerEntityService playerEntityService) {

		this.playerEntityService = Objects.requireNonNull(playerEntityService);

		this.sendClient = SpringExtension.actorOf(getContext(), SendClientActor.class);
	}

	@Override
	public void preStart() throws Exception {
		final RedirectMessage msg = RedirectMessage.get(BestiaInfoRequestMessage.class);
		context().parent().tell(msg, getSelf());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(BestiaInfoRequestMessage.class, this::handleInfoRequest)
				.build();
	}

	private void handleInfoRequest(BestiaInfoRequestMessage msg) {
		LOG.debug(String.format("Received: %s", msg.toString()));

		final long accId = msg.getAccountId();

		final Set<Long> bestiasEids = playerEntityService.getPlayerEntities(accId)
				.stream()
				.map(Entity::getId)
				.collect(Collectors.toSet());

		final long masterEid = playerEntityService.getMasterEntity(accId)
				.map(Entity::getId)
				.orElse(0L);

		// Send the normal bestia info message.
		final BestiaInfoMessage bimsg = new BestiaInfoMessage(accId, masterEid, bestiasEids);
		sendClient.tell(bimsg, getSelf());
	}
}

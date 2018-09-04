package net.bestia.zoneserver.actor.bestia;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.bestia.BestiaActivateMessage;
import net.bestia.zoneserver.actor.client.ClientMessageActor.RedirectMessage;
import net.bestia.zoneserver.entity.PlayerEntityService;

/**
 * Upon receiving an activation request from this account we check if the
 * account is able to uses this bestia. It will then get activated and all
 * needed information about the newly activated bestia is send to the client.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class ActivateBestiaActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public final static String NAME = "activateBestia";

	private final PlayerEntityService playerService;

	@Autowired
	public ActivateBestiaActor(PlayerEntityService playerService) {

		this.playerService = Objects.requireNonNull(playerService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(BestiaActivateMessage.class, this::handleActivateBestia)
				.build();
	}
	
	@Override
	public void preStart() throws Exception {
		final RedirectMessage msg = RedirectMessage.Companion.get(BestiaActivateMessage.class);
		context().parent().tell(msg, getSelf());
	}

	private void handleActivateBestia(BestiaActivateMessage msg) {
		
		final BestiaActivateMessage bestiaMsg = (BestiaActivateMessage) msg;

		try {
			playerService.setActiveEntity(bestiaMsg.getAccountId(), bestiaMsg.getEntityId());
			LOG.debug("Activated player bestia from accId: {}, entityId: {}",
					bestiaMsg.getAccountId(),
					bestiaMsg.getEntityId());

		} catch (IllegalArgumentException ex) {
			LOG.warning("Can not activiate entity: {}", bestiaMsg.toString());
		}
		
	}
}

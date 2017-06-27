package net.bestia.zoneserver.actor.bestia;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.PlayerEntityService;
import net.bestia.messages.bestia.BestiaActivateMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;

/**
 * Upon receiving an activation request from this account we check if the
 * account is able to uses this bestia. It will then get activated and all
 * needed information about the newly activated bestia is send to the client.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class ActivateBestiaActor extends BestiaRoutingActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public final static String NAME = "activateBestia";

	private final PlayerEntityService playerService;
	@Autowired
	public ActivateBestiaActor(PlayerEntityService playerService) {
		super(Arrays.asList(BestiaActivateMessage.class));

		this.playerService = Objects.requireNonNull(playerService);
	}

	@Override
	protected void handleMessage(Object msg) {

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

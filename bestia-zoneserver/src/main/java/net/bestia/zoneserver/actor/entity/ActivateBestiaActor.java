package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.bestia.BestiaActivateMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.service.PlayerEntityService;

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

		// Check if the user really owns this bestia.
		final Optional<PlayerBestiaEntity> bestia = playerService.getPlayerBestiaEntities(bestiaMsg.getAccountId())
				.parallelStream()
				.filter(x -> x.getPlayerBestiaId() == bestiaMsg.getPlayerBestiaId())
				.findAny();

		// If he owns it then you we can make it active.
		if (!bestia.isPresent()) {
			return;
		}
		
		final PlayerBestiaEntity pbe = bestia.get();

		LOG.debug("Activated player bestia id: {} from accId: {}, entityId: {}", 
				pbe.getPlayerBestiaId(), 
				pbe.getAccountId(), 
				pbe.getId());
		playerService.setActiveEntity(bestiaMsg.getAccountId(), pbe.getId());
	}

}

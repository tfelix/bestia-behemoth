package net.bestia.zoneserver.actor.bestia;

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
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.PlayerEntityService;
import net.bestia.zoneserver.entity.components.PlayerComponent;

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
	private final EntityService entityService;

	@Autowired
	public ActivateBestiaActor(PlayerEntityService playerService, EntityService entityService) {
		super(Arrays.asList(BestiaActivateMessage.class));

		this.playerService = Objects.requireNonNull(playerService);
		this.entityService = Objects.requireNonNull(entityService);
	}

	@Override
	protected void handleMessage(Object msg) {

		final BestiaActivateMessage bestiaMsg = (BestiaActivateMessage) msg;

		// Check if the user really owns this bestia.
		final Optional<Entity> bestia = playerService.getPlayerEntities(bestiaMsg.getAccountId())
				.stream()
				.filter(e -> {
					return entityService.getComponent(e, PlayerComponent.class)
							.filter(c -> c.getPlayerBestiaId() == bestiaMsg.getPlayerBestiaId())
							.map(x -> true)
							.orElse(false);
				})
				.findAny();

		// If he owns it then you we can make it active.
		if (!bestia.isPresent()) {
			return;
		}

		final PlayerComponent playerComp = entityService.getComponent(bestia.get(), PlayerComponent.class)
				.orElseThrow(IllegalStateException::new);

		LOG.debug("Activated player bestia id: {} from accId: {}, entityId: {}",
				playerComp.getPlayerBestiaId(),
				playerComp.getOwnerAccountId(),
				playerComp.getId());
		playerService.setActiveEntity(bestiaMsg.getAccountId(), playerComp.getId());
	}
}

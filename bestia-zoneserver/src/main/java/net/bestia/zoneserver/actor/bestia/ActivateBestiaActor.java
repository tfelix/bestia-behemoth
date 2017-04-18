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
import net.bestia.zoneserver.entity.ComponentService;
import net.bestia.zoneserver.entity.Entity;
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
	private final ComponentService componentService;

	@Autowired
	public ActivateBestiaActor(PlayerEntityService playerService, ComponentService componentService) {
		super(Arrays.asList(BestiaActivateMessage.class));

		this.playerService = Objects.requireNonNull(playerService);
		this.componentService = Objects.requireNonNull(componentService);
	}

	@Override
	protected void handleMessage(Object msg) {

		final BestiaActivateMessage bestiaMsg = (BestiaActivateMessage) msg;

		// Check if the user really owns this bestia.
		final Optional<Entity> bestia = playerService.getPlayerEntities(bestiaMsg.getAccountId())
				.stream()
				.filter(x -> {
					componentService.getComponent(x, PlayerComponent.class).ifPresent(x -> x.)
					//x.getPlayerBestiaId() == bestiaMsg.getPlayerBestiaId()
				})
				.findAny();

		// If he owns it then you we can make it active.
		if (!bestia.isPresent()) {
			return;
		}

		final PlayerEntity pbe = bestia.get();

		LOG.debug("Activated player bestia id: {} from accId: {}, entityId: {}",
				pbe.getPlayerBestiaId(),
				pbe.getAccountId(),
				pbe.getId());
		playerService.setActiveEntity(bestiaMsg.getAccountId(), pbe.getId());

		// Send the responding message to the client.
		final BestiaActivateMessage bam = new BestiaActivateMessage(bestiaMsg.getAccountId(), pbe.getPlayerBestiaId());
		sendClient(bam);
	}
}

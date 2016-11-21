package net.bestia.zoneserver.actor.bestia;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.bestia.BestiaInfoMessage;
import net.bestia.messages.bestia.RequestBestiaInfoMessage;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.service.PlayerBestiaService;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.service.PlayerEntityService;

/**
 * This actor gathers all needed information about the bestias in the players
 * possession and will deliver this information to the player.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class BestiaInfoActor extends BestiaRoutingActor {

	public static final String NAME = "bestiaInfo";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final PlayerBestiaService playerBestiaService;
	private final PlayerEntityService entityService;

	@Autowired
	public BestiaInfoActor(PlayerEntityService entityService, PlayerBestiaService pbService) {
		super(Arrays.asList(RequestBestiaInfoMessage.class));

		this.entityService = Objects.requireNonNull(entityService);
		this.playerBestiaService = Objects.requireNonNull(pbService);

	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug(String.format("Received: %s", msg.toString()));

		final RequestBestiaInfoMessage rbimsg = (RequestBestiaInfoMessage) msg;

		final Set<PlayerBestiaEntity> bestias = entityService.getPlayerBestiaEntities(rbimsg.getAccountId());
		final Set<PlayerBestia> pbs = playerBestiaService.getAllBestias(rbimsg.getAccountId());

		for (PlayerBestiaEntity bestia : bestias) {
			// Get the player bestia.
			Optional<PlayerBestia> pb = pbs.stream()
					.filter(x -> x.getId() == bestia.getPlayerBestiaId())
					.findAny();
			
			if(!pb.isPresent()) {
				continue;
			}

			// We must send for each bestia a single message.
			bestia.updateModel(pb.get());

			final BestiaInfoMessage bimsg = new BestiaInfoMessage(rbimsg,
					pb.get(),
					bestia.getStatusPoints());
			sendClient(bimsg);
		}
	}
}

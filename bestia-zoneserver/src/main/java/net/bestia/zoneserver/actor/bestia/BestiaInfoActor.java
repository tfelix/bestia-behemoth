package net.bestia.zoneserver.actor.bestia;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.bestia.BestiaInfoMessage;
import net.bestia.messages.bestia.RequestBestiaInfoMessage;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPoints;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.PlayerEntityService;
import net.bestia.zoneserver.entity.StatusService;
import net.bestia.zoneserver.entity.component.PlayerComponent;

/**
 * This actor gathers all needed information about the bestias in the players
 * possession and will deliver this information to the player.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class BestiaInfoActor extends BestiaRoutingActor {

	public static final String NAME = "bestiaInfo";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final EntityService entityService;
	private final PlayerEntityService playerEntityService;
	private final PlayerBestiaDAO playerBestiaDao;
	private final StatusService statusService;

	@Autowired
	public BestiaInfoActor(
			EntityService entityService,
			PlayerBestiaDAO playerBestiaDao,
			StatusService statusService,
			PlayerEntityService playerEntityService) {
		super(Arrays.asList(RequestBestiaInfoMessage.class));

		this.entityService = Objects.requireNonNull(entityService);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.playerBestiaDao = Objects.requireNonNull(playerBestiaDao);
		this.statusService = Objects.requireNonNull(statusService);

	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug(String.format("Received: %s", msg.toString()));

		final RequestBestiaInfoMessage rbimsg = (RequestBestiaInfoMessage) msg;

		final Set<Entity> bestias = playerEntityService.getPlayerEntities(rbimsg.getAccountId());

		for (Entity pbe : bestias) {

			final PlayerComponent pbComp = entityService.getComponent(pbe, PlayerComponent.class)
					.orElseThrow(IllegalStateException::new);

			final PlayerBestia pb = playerBestiaDao.findOne(pbComp.getPlayerBestiaId());
			final StatusPoints statusPoints = statusService.getStatusPoints(pbe).get();

			final BestiaInfoMessage bimsg = new BestiaInfoMessage(rbimsg.getAccountId(),
					pbe.getId(),
					pb,
					statusPoints);

			sendClient(bimsg);
		}
	}
}

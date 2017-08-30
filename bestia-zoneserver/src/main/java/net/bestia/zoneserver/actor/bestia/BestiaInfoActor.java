package net.bestia.zoneserver.actor.bestia;

import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.PlayerEntityService;
import net.bestia.entity.StatusService;
import net.bestia.entity.component.PlayerComponent;
import net.bestia.messages.bestia.BestiaInfoMessage;
import net.bestia.messages.bestia.BestiaInfoRequestMessage;
import net.bestia.messages.entity.EntityStatusUpdateMessage;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusValues;
import net.bestia.model.entity.StatusBasedValues;
import net.bestia.zoneserver.AkkaSender;
import net.bestia.zoneserver.actor.zone.IngestExActor.RedirectMessage;

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

		this.entityService = Objects.requireNonNull(entityService);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.playerBestiaDao = Objects.requireNonNull(playerBestiaDao);
		this.statusService = Objects.requireNonNull(statusService);

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

		final Set<Entity> bestias = playerEntityService.getPlayerEntities(msg.getAccountId());

		for (Entity pbe : bestias) {

			final PlayerComponent pbComp = entityService.getComponent(pbe, PlayerComponent.class)
					.orElseThrow(IllegalStateException::new);

			final PlayerBestia pb = playerBestiaDao.findOne(pbComp.getPlayerBestiaId());

			final StatusPoints statusPoints = statusService.getStatusPoints(pbe).get();
			final StatusPoints unmodStatusPoints = statusService.getUnmodifiedStatusPoints(pbe).get();
			final StatusValues statusValues = statusService.getStatusValues(pbe).get();
			final StatusBasedValues statusBasedValues = statusService.getStatusBasedValues(pbe).get();

			// Send the normal bestia info message.
			final BestiaInfoMessage bimsg = new BestiaInfoMessage(accId, pbe.getId(), pb);
			AkkaSender.sendClient(getContext(), bimsg);

			// Now send the bestia status messages.
			final EntityStatusUpdateMessage esmsg = new EntityStatusUpdateMessage(
					accId,
					pbe.getId(),
					statusPoints,
					unmodStatusPoints,
					statusValues,
					statusBasedValues);
			AkkaSender.sendClient(getContext(), esmsg);
		}
	}
}

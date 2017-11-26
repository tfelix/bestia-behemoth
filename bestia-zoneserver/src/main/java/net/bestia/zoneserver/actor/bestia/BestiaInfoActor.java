package net.bestia.zoneserver.actor.bestia;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.PlayerComponent;
import net.bestia.entity.component.StatusComponent;
import net.bestia.messages.bestia.BestiaInfoMessage;
import net.bestia.messages.bestia.BestiaInfoRequestMessage;
import net.bestia.messages.entity.EntityStatusUpdateMessage;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.ConditionValues;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.entity.StatusBasedValues;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.SendClientActor;
import net.bestia.zoneserver.actor.zone.ClientMessageHandlerActor.RedirectMessage;
import net.bestia.zoneserver.service.PlayerEntityService;

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
	
	private final ActorRef msgHub;
	private final ActorRef sendClient;

	@Autowired
	public BestiaInfoActor(
			EntityService entityService,
			PlayerBestiaDAO playerBestiaDao,
			PlayerEntityService playerEntityService,
			ActorRef msgHub) {

		this.entityService = Objects.requireNonNull(entityService);
		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.playerBestiaDao = Objects.requireNonNull(playerBestiaDao);
		this.msgHub = Objects.requireNonNull(msgHub);
		
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

		final Set<Entity> bestias = playerEntityService.getPlayerEntities(msg.getAccountId());

		for (Entity pbe : bestias) {

			final PlayerComponent pbComp = entityService.getComponent(pbe, PlayerComponent.class)
					.orElseThrow(IllegalStateException::new);

			final PlayerBestia pb = playerBestiaDao.findOne(pbComp.getPlayerBestiaId());

			final Optional<StatusComponent> statusComp = entityService.getComponent(pbe, StatusComponent.class);
			
			if(!statusComp.isPresent()) {
				LOG.error("StatusComponent of bestia entity is missing. Skipping entity.");
				continue;
			}
			
			final StatusComponent status = statusComp.get();
			final StatusPoints statusPoints = status.getStatusPoints();
			final StatusPoints unmodStatusPoints = status.getOriginalStatusPoints();
			final ConditionValues condValues = status.getConditionValues();
			final StatusBasedValues statusBasedValues = status.getStatusBasedValues();

			// Send the normal bestia info message.
			final BestiaInfoMessage bimsg = new BestiaInfoMessage(accId, pbe.getId(), pb);
			msgHub.tell(bimsg, getSelf());
			sendClient.tell(bimsg, getSelf());

			// Now send the bestia status messages.
			final EntityStatusUpdateMessage esmsg = new EntityStatusUpdateMessage(
					accId,
					pbe.getId(),
					statusPoints,
					unmodStatusPoints,
					condValues,
					statusBasedValues);
			msgHub.tell(esmsg, getSelf());
		}
	}
}

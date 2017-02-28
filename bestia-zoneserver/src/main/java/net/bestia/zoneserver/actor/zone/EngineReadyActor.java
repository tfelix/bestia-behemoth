package net.bestia.zoneserver.actor.zone;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.bestia.BestiaActivateMessage;
import net.bestia.messages.entity.EntityAction;
import net.bestia.messages.entity.EntityUpdateMessage;
import net.bestia.messages.login.EngineReadyMessage;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.Map;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.entity.PlayerEntity;
import net.bestia.zoneserver.entity.traits.Visible;
import net.bestia.zoneserver.service.EntityService;
import net.bestia.zoneserver.service.PlayerEntityService;

/**
 * This actor will listen to incoming {@link EngineReadyMessage}s. If such a
 * message is encountered it will generate a few sets of messages to help the
 * engine on the client side to preload some assets and to generate the map
 * (with all entities in sight).
 * 
 * The messages generated are:
 * <ul>
 * <li>{@link BestiaActivateMessage} with the current active bestia id.</li>
 * <li>{@link EntityUpdateMessage} with the currently entities in sight.</li>
 * </ul>
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class EngineReadyActor extends BestiaRoutingActor {

	public static final String NAME = "engineReadyActor";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final PlayerEntityService playerService;
	private final EntityService entityService;

	/**
	 * 
	 */
	@Autowired
	public EngineReadyActor(EntityService entityService, PlayerEntityService playerEntityService) {
		super(Arrays.asList(EngineReadyMessage.class));

		this.entityService = Objects.requireNonNull(entityService);
		this.playerService = Objects.requireNonNull(playerEntityService);
	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug("LoginRequestMessage received: {}", msg.toString());

		final EngineReadyMessage readyMsg = (EngineReadyMessage) msg;
		final long accId = readyMsg.getAccountId();

		// Find the position of the active bestia.
		final PlayerEntity playerEntity = playerService.getActivePlayerEntity(accId);
		if (playerEntity == null) {
			LOG.warning("No active player bestia entity was found. Aborting.");
			return;
		}

		final Rect sightRect = Map.getUpdateRect(playerEntity.getPosition());
		final Collection<Visible> visibles = entityService.getEntitiesInRange(sightRect, Visible.class);

		// Send a select message for the given player entity to the engine.
		final BestiaActivateMessage bestiaMsg = new BestiaActivateMessage(readyMsg.getAccountId(),
				playerEntity.getPlayerBestiaId());
		sendClient(bestiaMsg);

		for (Visible visible : visibles) {
			// Steps over them and send them to client as update messages.
			final Point pos = visible.getPosition();
			final EntityUpdateMessage entityMsg = new EntityUpdateMessage(accId, visible.getId(), pos.getX(),
					pos.getY(), visible.getVisual(), EntityAction.UPDATE);
			sendClient(entityMsg);
		}
	}

}

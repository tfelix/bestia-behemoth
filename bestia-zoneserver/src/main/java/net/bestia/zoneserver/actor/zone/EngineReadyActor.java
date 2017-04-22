package net.bestia.zoneserver.actor.zone;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.PlayerEntityService;
import net.bestia.zoneserver.entity.components.PlayerComponent;
import net.bestia.zoneserver.entity.components.PositionComponent;
import net.bestia.zoneserver.entity.components.VisibleComponent;

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
		LOG.debug("EngineReadyMessage received: {}", msg.toString());

		final EngineReadyMessage readyMsg = (EngineReadyMessage) msg;
		final long accId = readyMsg.getAccountId();

		// Find the position of the active bestia.
		final Entity playerEntity = playerService.getActivePlayerEntity(accId);
		final PlayerComponent playerComp = entityService.getComponent(playerEntity, PlayerComponent.class)
				.orElseThrow(IllegalStateException::new);

		if (playerEntity == null) {
			LOG.warning("No active player bestia entity was found. Aborting.");
			return;
		}

		final Point p = entityService.getComponent(playerEntity, PositionComponent.class)
				.map(x -> x.getPosition())
				.orElseThrow(IllegalStateException::new);
		final Rect sightRect = Map.getUpdateRect(p);
		final Set<Entity> visibles = entityService.getEntitiesInRange(sightRect, VisibleComponent.class);

		// Send a select message for the given player entity to the engine.
		final BestiaActivateMessage bestiaMsg = new BestiaActivateMessage(readyMsg.getAccountId(),
				playerComp.getPlayerBestiaId());
		sendClient(bestiaMsg);

		// Send info/update messages for all other bestias.
		for (Entity entity : visibles) {
			// Steps over them and send them to client as update messages.
			Optional<PositionComponent> pos = entityService.getComponent(entity, PositionComponent.class);
			Optional<VisibleComponent> visible = entityService.getComponent(entity, VisibleComponent.class);

			if (!pos.isPresent() || !visible.isPresent()) {
				return;
			}

			final EntityUpdateMessage entityMsg = new EntityUpdateMessage(accId, entity.getId(),
					pos.get().getPosition().getX(),
					pos.get().getPosition().getY(), visible.get().getVisual(), EntityAction.UPDATE);
			sendClient(entityMsg);

		}
	}

}

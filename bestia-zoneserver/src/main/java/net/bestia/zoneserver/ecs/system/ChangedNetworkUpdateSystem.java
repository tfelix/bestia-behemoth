package net.bestia.zoneserver.ecs.system;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.annotations.Wire;
import com.artemis.managers.UuidEntityManager;
import com.artemis.systems.EntityProcessingSystem;
import com.artemis.utils.IntBag;

import net.bestia.messages.BestiaInfoMessage;
import net.bestia.messages.MapEntitiesMessage;
import net.bestia.messages.MapEntitiesMessage.EntityAction;
import net.bestia.model.domain.Location;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Changed;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Visible;
import net.bestia.zoneserver.manager.BestiaManager;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

/**
 * This system looks for changed and visible entities and transmit the changes
 * to any active player in the visible range.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Wire
public class ChangedNetworkUpdateSystem extends EntityProcessingSystem {

	private static final Logger LOG = LogManager.getLogger(ChangedNetworkUpdateSystem.class);

	// BEGIN DUPLICATE CODE FROM NETWORKUPDATESYSTEM. REFACTOR
	@Wire
	private CommandContext ctx;
	private ComponentMapper<PlayerBestia> playerMapper;
	private ComponentMapper<Bestia> bestiaMapper;
	private ComponentMapper<Visible> visibleMapper;
	private UuidEntityManager uuidManager;
	// END

	private EntitySubscription activePlayerEntities;

	public ChangedNetworkUpdateSystem() {
		super(Aspect.all(Visible.class, Changed.class));

	}

	@Override
	protected void initialize() {
		final AspectSubscriptionManager asm = world.getSystem(AspectSubscriptionManager.class);
		activePlayerEntities = asm.get(Aspect.all(Active.class, PlayerBestia.class));
	}

	@Override
	protected void process(Entity e) {

		// First of all check if this is a player bestia entity. And if so send
		// the update to the corresponding player.
		if (playerMapper.has(e)) {
			final PlayerBestiaManager pbm = playerMapper.get(e).playerBestiaManager;
			final BestiaInfoMessage bestiaInfoMsg = new BestiaInfoMessage(pbm.getPlayerBestia(), pbm.getStatusPoints());
			ctx.getServer().sendMessage(bestiaInfoMsg);
		}

		// All active bestias on this zone.
		final IntBag entityIds = activePlayerEntities.getEntities();

		for (int i = 0; i < entityIds.size(); i++) {
			final Entity receiverEntity = world.getEntity(entityIds.get(i));
			// TODO In sight range?
			sendUpdate(receiverEntity, e, EntityAction.UPDATE);
		}

		// Remove changed.
		e.edit().remove(Changed.class);
	}

	/**
	 * DUPLICATE
	 * 
	 * @param playerEntity
	 * @param visibleEntit
	 * @param action
	 *            {@link EntityAction} of the message.
	 */
	protected void sendUpdate(Entity playerEntity, Entity visibleEntity, EntityAction action) {

		final PlayerBestia playerControlled = playerMapper.getSafe(playerEntity);

		if (playerControlled == null) {
			return;
		}

		final long accId = playerControlled.playerBestiaManager.getAccountId();

		final MapEntitiesMessage.Entity msg = getMessageFromEntity(visibleEntity, action);
		final MapEntitiesMessage updateMsg = new MapEntitiesMessage();

		updateMsg.setAccountId(accId);
		updateMsg.getEntities().add(msg);

		LOG.trace("Sending update for entity: {} to accId: {}", msg.getUuid(), accId);

		ctx.getServer().sendMessage(updateMsg);
	}

	/**
	 * DUPLICATE
	 * 
	 * @param e
	 *            Entity to convert to a message for the client.
	 * @return A message containing all needed information about this entity for
	 *         the client.
	 */
	protected MapEntitiesMessage.Entity getMessageFromEntity(Entity e, EntityAction action) {
		final UUID uuid = uuidManager.getUuid(e);
		final Visible visible = visibleMapper.get(e);

		final BestiaManager manager = bestiaMapper.get(e).bestiaManager;
		final Location loc = manager.getLocation();

		final MapEntitiesMessage.Entity msg = new MapEntitiesMessage.Entity(uuid.toString(), loc.getX(), loc.getY());
		msg.setAction(action);
		msg.addSprite(visible.sprite);

		final PlayerBestia playerManager = playerMapper.getSafe(e);
		if (playerManager != null) {
			msg.setPlayerBestiaId(playerManager.playerBestiaManager.getPlayerBestiaId());
		}

		return msg;
	}

}

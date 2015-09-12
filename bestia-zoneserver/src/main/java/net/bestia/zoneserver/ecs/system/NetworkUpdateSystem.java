package net.bestia.zoneserver.ecs.system;

import java.util.UUID;

import net.bestia.messages.MapEntitiesMessage;
import net.bestia.messages.MapEntitiesMessage.EntityAction;
import net.bestia.model.domain.Location;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Visible;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect.Builder;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.annotations.Wire;
import com.artemis.managers.UuidEntityManager;
import com.artemis.systems.EntityProcessingSystem;

/**
 * Should be abstract. Does not work with @Wire dependency injection.
 * 
 * @author Thomas
 *
 */
@Wire
public abstract class NetworkUpdateSystem extends EntityProcessingSystem {

	private static final Logger log = LogManager.getLogger(NetworkUpdateSystem.class);

	@Wire
	private CommandContext ctx;

	private ComponentMapper<PlayerBestia> playerMapper;
	private ComponentMapper<Visible> visibleMapper;

	private UuidEntityManager uuidManager;

	public NetworkUpdateSystem(Builder aspect) {
		super(aspect);
	}

	/**
	 * Workaround since the wireing of artemis does not work here. Must be set by child class.
	 * 
	 * @param ctx
	 */
	protected void setCommandContext(CommandContext ctx) {
		this.ctx = ctx;
	}

	@Override
	protected void initialize() {
		super.initialize();

		// Autowiring does not work.
		uuidManager = world.getManager(UuidEntityManager.class);

		playerMapper = world.getMapper(PlayerBestia.class);
		visibleMapper = world.getMapper(Visible.class);
	}

	/**
	 * Checks if one entity is in sight of another.
	 * 
	 * @param playerEntity
	 * @param visibleEntity
	 * @return
	 */
	protected boolean isInSightDistance(Entity playerEntity, Entity visibleEntity) {
		// TODO
		return true;
	}

	/**
	 * Creates a update message from a visible entity for a given player entity.
	 * 
	 * @param playerEntity
	 * @param visibleEntit
	 * @param action
	 *            {@link EntityAction} of the message.
	 */
	protected void sendUpdate(Entity playerEntity, Entity visibleEntity, EntityAction action) {

		final PlayerBestia playerControlled = playerMapper.getSafe(playerEntity);
		
		if(playerControlled == null) {
			return;
		}
		
		final long accId = playerControlled.playerBestiaManager.getAccountId();

		final MapEntitiesMessage.Entity msg = getMessageFromEntity(visibleEntity, action);
		final MapEntitiesMessage updateMsg = new MapEntitiesMessage();

		updateMsg.setAccountId(accId);
		updateMsg.getEntities().add(msg);

		log.trace("Sending update for entity: {} to accId: {}", msg.getUuid(), accId);

		ctx.getServer().sendMessage(updateMsg);
	}

	/**
	 * Converts a simple "map entity" from the ECS to a {@link MapEntitiesMessage.Entity}.
	 * 
	 * @param e
	 *            Entity to convert to a message for the client.
	 * @return A message containing all needed information about this entity for the client.
	 */
	protected MapEntitiesMessage.Entity getMessageFromEntity(Entity e, EntityAction action) {
		final UUID uuid = uuidManager.getUuid(e);
		final Visible visible = visibleMapper.get(e);
		
		final PlayerBestia playerControlled = playerMapper.getSafe(e);
		final Location loc = playerControlled.playerBestiaManager.getLocation();

		final MapEntitiesMessage.Entity msg = new MapEntitiesMessage.Entity(uuid.toString(), loc.getX(), loc.getY());
		msg.setAction(action);
		msg.addSprite(visible.sprite);
		
		if(playerControlled != null) {
			msg.setPlayerBestiaId(playerControlled.playerBestiaManager.getPlayerBestiaId());
		}

		return msg;
	}

}
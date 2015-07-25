package net.bestia.zoneserver.ecs.system;

import java.util.UUID;

import net.bestia.messages.MapEntitiesMessage;
import net.bestia.messages.MapEntitiesMessage.EntityAction;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Changable;
import net.bestia.zoneserver.ecs.component.PlayerControlled;
import net.bestia.zoneserver.ecs.component.Position;
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

	private static final Logger log = LogManager.getLogger(VisibleNetworkUpdateSystem.class);

	@Wire
	private CommandContext ctx;

	private ComponentMapper<Changable> changableMapper;
	private ComponentMapper<PlayerControlled> pcm;
	private ComponentMapper<Position> positionMapper;
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

		changableMapper = world.getMapper(Changable.class);
		pcm = world.getMapper(PlayerControlled.class);
		positionMapper = world.getMapper(Position.class);
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

		final PlayerControlled playerControlled = pcm.get(playerEntity);
		final long accId = playerControlled.playerBestia.getBestia().getOwner().getId();

		final MapEntitiesMessage.Entity msg = getMessageFromEntity(visibleEntity, action);
		final MapEntitiesMessage updateMsg = new MapEntitiesMessage();

		updateMsg.setAccountId(accId);
		updateMsg.getEntities().add(msg);

		log.trace("Sending update for entity: {} to accId: {}", msg.getUuid(), accId);

		ctx.getServer().sendMessage(updateMsg);
		markUnchanged(visibleEntity);
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
		final Position pos = positionMapper.get(e);
		final Visible visible = visibleMapper.get(e);

		final MapEntitiesMessage.Entity msg = new MapEntitiesMessage.Entity(uuid.toString(), pos.x, pos.y);
		msg.setAction(action);
		msg.addSprite(visible.sprite);

		return msg;
	}

	private void markUnchanged(Entity e) {
		final Changable changable = changableMapper.getSafe(e);
		if (changable != null) {
			changable.changed = false;
		}
	}

	@Override
	protected void process(Entity e) {
		// no op.
	}

}
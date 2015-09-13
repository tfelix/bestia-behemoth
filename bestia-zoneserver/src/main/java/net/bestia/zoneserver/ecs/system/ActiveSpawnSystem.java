package net.bestia.zoneserver.ecs.system;

import net.bestia.messages.MapEntitiesMessage;
import net.bestia.messages.MapEntitiesMessage.EntityAction;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.ecs.component.Active;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.component.Visible;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.EntitySubscription.SubscriptionListener;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;

@Wire
public class ActiveSpawnSystem extends NetworkUpdateSystem {

	public ActiveSpawnSystem() {
		super(Aspect.all(Active.class, PlayerBestia.class));
		setPassive(true);
	}

	private static final Logger log = LogManager.getLogger(ActiveSpawnSystem.class);

	private AspectSubscriptionManager asm;

	@Wire
	private CommandContext ctx;

	private EntitySubscription visibleSubscription;

	private ComponentMapper<PlayerBestia> playerMapper;

	@Override
	protected void initialize() {
		super.initialize();

		// Workaround must be set since parent gets no wireing.
		setCommandContext(ctx);

		visibleSubscription = asm.get(Aspect.all(Visible.class));

		subscription.addSubscriptionListener(new SubscriptionListener() {

			@Override
			public void inserted(IntBag entities) {
				final IntBag visibleEntities = visibleSubscription.getEntities();

				log.trace("{} New active player, sending {} entities.", entities.size(), visibleEntities.size());

				for (int i = 0; i < entities.size(); i++) {
					final int newEntityId = entities.get(i);
					final Entity newActiveEntity = world.getEntity(newEntityId);

					sendAllVisibleInRange(newActiveEntity, EntityAction.APPEAR);
				}
			}

			@Override
			public void removed(IntBag entities) {
				// no op.
			}
		});
	}

	/**
	 * Sends all visible entities in the view distance to the given (player controlled) entity e.
	 * 
	 * @param playerEntity
	 *            Entity with Active and PlayerControlled component.
	 */
	private void sendAllVisibleInRange(Entity playerEntity, EntityAction action) {

		final PlayerBestia player = playerMapper.getSafe(playerEntity);

		final long accId = player.playerBestiaManager.getAccountId();

		final IntBag activeEntities = visibleSubscription.getEntities();

		log.trace("Found {} entities in sight.", activeEntities.size());

		// Dont waste a message if nothing is in sight.
		if (activeEntities.size() == 0) {
			return;
		}

		final MapEntitiesMessage entitiesMessage = new MapEntitiesMessage();
		entitiesMessage.setAccountId(accId);

		for (int i = 0; i < activeEntities.size(); i++) {
			final int id = activeEntities.get(i);
			final Entity visibleEntity = world.getEntity(id);

			final MapEntitiesMessage.Entity msg = getMessageFromEntity(visibleEntity, action);

			entitiesMessage.getEntities().add(msg);
		}

		ctx.getServer().sendMessage(entitiesMessage);
	}

	@Override
	protected void process(Entity e) {
		// no op.
	}
}
